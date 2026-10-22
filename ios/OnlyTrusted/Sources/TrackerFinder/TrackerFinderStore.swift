// SPDX-FileCopyrightText: 2026 VanPaulTek Inc
// SPDX-License-Identifier: AGPL-3.0-or-later
import Foundation
import CryptoKit

/// On-device, encrypted store for BLE tracker sightings observed by Tracker Finder.
///
/// Everything stays local; this file never leaves the device. We hash BLE peripheral
/// identifiers (CBPeripheral's identifier string / resolvable MAC) before persisting so
/// the raw IDs never land on disk — even an attacker with the key would only recover
/// an opaque fingerprint.
///
/// Storage shape:
///   • Container: `Application Support/tracker_sightings.enc`
///   • Format: AES-GCM-sealed JSON (`Data` sealed via `AES.GCM.seal`).
///   • Key: 256-bit random key stored in the Keychain under
///     `com.onlytrusted.app.tracker_finder.key` (hardware-backed on A7+).
///
/// A tracker is classified as "following" when its hashed identifier appears in 3+ scan
/// sessions over a window of more than 15 minutes — see `followingTrackers()`.
///
/// Gated by the entry row on `SubscriptionState.hasHighPremium`. This file itself has
/// no notion of gating — if it is instantiated the user is already on Pro.
struct TrackerFinderStore {

    /// Classes of BLE device we deliberately care about. "unknownBle" covers the
    /// catch-all persistent-follower rule from the brief.
    enum TrackerType: String, Codable, CaseIterable {
        case appleAirTag
        case tile
        case samsungSmartTag
        case unknownBle

        var displayName: String {
            switch self {
            case .appleAirTag:     return "Apple AirTag"
            case .tile:            return "Tile"
            case .samsungSmartTag: return "Samsung SmartTag"
            case .unknownBle:      return "Unknown tracker"
            }
        }
    }

    /// Single sighting event. `idHash` is a SHA-256 digest of the peripheral identifier
    /// (plus a per-install salt stored next to the AES key). We never persist raw UUIDs.
    struct Sighting: Codable, Identifiable {
        var id: UUID = UUID()
        let idHash: String
        let type: TrackerType
        let rssi: Int
        let timestamp: Date
        /// Opaque session grouping — one UUID per scan session so we can count distinct
        /// sessions when applying the "3+ sessions over 15 min" rule.
        let sessionId: UUID
        /// Optional location hash. Reserved for a future feature; always `nil` today.
        var locationHash: String? = nil
    }

    /// Aggregated view of one tracker that we currently classify as a follower.
    struct FollowerTracker: Identifiable {
        let id: String        // == idHash
        let idHash: String
        let type: TrackerType
        let firstSeen: Date
        let lastSeen: Date
        let sessionCount: Int
        let totalSightings: Int
        let averageRssi: Int

        /// `aabbccdd…` → `AA·BB·CC·DD` (first 8 chars of hash grouped for readability).
        var shortLabel: String {
            let prefix = String(idHash.prefix(8)).uppercased()
            return stride(from: 0, to: prefix.count, by: 2)
                .compactMap { prefix.index(prefix.startIndex, offsetBy: $0, limitedBy: prefix.endIndex) }
                .compactMap { idx -> String? in
                    let end = prefix.index(idx, offsetBy: 2, limitedBy: prefix.endIndex) ?? prefix.endIndex
                    return String(prefix[idx..<end])
                }
                .joined(separator: "·")
        }
    }

    private static let fileName = "tracker_sightings.enc"
    private static let keychainKeyTag = "com.onlytrusted.app.tracker_finder.key"
    private static let keychainSaltTag = "com.onlytrusted.app.tracker_finder.salt"

    /// Minimum sessions / minimum age for the "following" classifier.
    static let followingMinSessions = 3
    static let followingMinAgeSeconds: TimeInterval = 15 * 60

    // MARK: - Public API

    /// Append a sighting to the encrypted log.
    static func record(_ sighting: Sighting) {
        var all = loadAll()
        all.append(sighting)
        // Keep the log bounded; older-than-7-days is noise for a "following" classifier
        // that only looks at >15 minutes.
        let cutoff = Date().addingTimeInterval(-7 * 24 * 3600)
        all.removeAll { $0.timestamp < cutoff }
        saveAll(all)
    }

    /// Hash a BLE peripheral identifier string with a per-install salt.
    static func hashIdentifier(_ rawIdentifier: String) -> String {
        let salt = installSalt()
        var hasher = SHA256()
        hasher.update(data: salt)
        hasher.update(data: Data(rawIdentifier.utf8))
        let digest = hasher.finalize()
        return digest.map { String(format: "%02x", $0) }.joined()
    }

    /// Recent sightings, newest first — used by the Alerts list on the screen.
    static func recentSightings(limit: Int = 100) -> [Sighting] {
        loadAll().sorted { $0.timestamp > $1.timestamp }.prefix(limit).map { $0 }
    }

    /// Devices currently classified as "following" per the brief's rule:
    ///   3+ sightings across 3+ distinct scan sessions over a window > 15 minutes.
    static func followingTrackers() -> [FollowerTracker] {
        let all = loadAll()
        let grouped = Dictionary(grouping: all, by: { $0.idHash })
        let now = Date()
        return grouped.compactMap { (hash, group) -> FollowerTracker? in
            guard group.count >= followingMinSessions else { return nil }
            let sessions = Set(group.map { $0.sessionId })
            guard sessions.count >= followingMinSessions else { return nil }
            guard let first = group.map({ $0.timestamp }).min(),
                  let last = group.map({ $0.timestamp }).max() else { return nil }
            guard last.timeIntervalSince(first) >= followingMinAgeSeconds else { return nil }
            let avgRssi = Int((group.map { Double($0.rssi) }.reduce(0, +) /
                               Double(max(group.count, 1))).rounded())
            // Use the latest-classification type (if a device started "unknown" then matched
            // AirTag mfr data, we want the specific label).
            let type = group.sorted { $0.timestamp > $1.timestamp }.first?.type ?? .unknownBle
            _ = now // retain for future "recency" refinement
            return FollowerTracker(
                id: hash,
                idHash: hash,
                type: type,
                firstSeen: first,
                lastSeen: last,
                sessionCount: sessions.count,
                totalSightings: group.count,
                averageRssi: avgRssi
            )
        }
        .sorted { $0.sessionCount > $1.sessionCount }
    }

    /// Wipe every sighting (user-visible "Clear history" button).
    static func clear() {
        saveAll([])
    }

    // MARK: - Encrypted I/O

    private static func storeURL() throws -> URL {
        let fm = FileManager.default
        let base = try fm.url(for: .applicationSupportDirectory,
                              in: .userDomainMask,
                              appropriateFor: nil,
                              create: true)
        return base.appendingPathComponent(fileName)
    }

    private static func loadAll() -> [Sighting] {
        guard let url = try? storeURL(),
              FileManager.default.fileExists(atPath: url.path),
              let sealedData = try? Data(contentsOf: url) else {
            return []
        }
        do {
            let key = try symmetricKey()
            let box = try AES.GCM.SealedBox(combined: sealedData)
            let plain = try AES.GCM.open(box, using: key)
            return (try? JSONDecoder().decode([Sighting].self, from: plain)) ?? []
        } catch {
            // Corrupted or key-rotated — pretend empty rather than crash. The user can
            // "Clear history" to replace the file with a fresh seal next write.
            return []
        }
    }

    private static func saveAll(_ sightings: [Sighting]) {
        do {
            let url = try storeURL()
            let key = try symmetricKey()
            let plain = try JSONEncoder().encode(sightings)
            let box = try AES.GCM.seal(plain, using: key)
            guard let combined = box.combined else { return }
            try combined.write(to: url, options: [.atomic, .completeFileProtectionUnlessOpen])
        } catch {
            // Can't surface here — called from the scan path. Silently drop; the next
            // write will try again.
        }
    }

    // MARK: - Keychain helpers (symmetric key + install salt)

    private static func symmetricKey() throws -> SymmetricKey {
        if let data = keychainData(tag: keychainKeyTag) {
            return SymmetricKey(data: data)
        }
        let fresh = SymmetricKey(size: .bits256)
        let bytes = fresh.withUnsafeBytes { Data($0) }
        try setKeychainData(bytes, tag: keychainKeyTag)
        return fresh
    }

    private static func installSalt() -> Data {
        if let data = keychainData(tag: keychainSaltTag) { return data }
        var random = Data(count: 32)
        _ = random.withUnsafeMutableBytes { buf -> Int32 in
            guard let base = buf.baseAddress else { return errSecAllocate }
            return SecRandomCopyBytes(kSecRandomDefault, 32, base)
        }
        try? setKeychainData(random, tag: keychainSaltTag)
        return random
    }

    private static func keychainData(tag: String) -> Data? {
        let query: [String: Any] = [
            kSecClass as String:       kSecClassGenericPassword,
            kSecAttrAccount as String: tag,
            kSecReturnData as String:  true,
            kSecMatchLimit as String:  kSecMatchLimitOne
        ]
        var out: AnyObject?
        let status = SecItemCopyMatching(query as CFDictionary, &out)
        return status == errSecSuccess ? out as? Data : nil
    }

    private static func setKeychainData(_ data: Data, tag: String) throws {
        let base: [String: Any] = [
            kSecClass as String:       kSecClassGenericPassword,
            kSecAttrAccount as String: tag
        ]
        SecItemDelete(base as CFDictionary)
        var add = base
        add[kSecValueData as String] = data
        add[kSecAttrAccessible as String] = kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
        let status = SecItemAdd(add as CFDictionary, nil)
        if status != errSecSuccess {
            throw NSError(
                domain: "TrackerFinderStore",
                code: Int(status),
                userInfo: [NSLocalizedDescriptionKey: "Keychain store failed (\(status))"]
            )
        }
    }
}
