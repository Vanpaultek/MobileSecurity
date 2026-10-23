// SPDX-FileCopyrightText: 2026 VanPaulTek Inc
// SPDX-License-Identifier: AGPL-3.0-or-later
import Foundation
import Security
import SwiftUI

/// Observable state for Breach Watch (FREE tier).
///
/// Monitors ONE email address against the bundled breach-hash prefix list. Values live
/// in the Keychain so a filesystem copy of the app sandbox cannot read the user's email,
/// recent check-in date, or any hit cache. Keys:
///   * `breach_watch.v1.email`        — the monitored email (unencrypted at rest in the
///                                       Keychain item itself; still Keychain-ACL'd).
///   * `breach_watch.v1.last_check`   — ISO-8601 string of the last manual + scheduled
///                                       check timestamp.
///   * `breach_watch.v1.hits_json`    — JSON array of breach-hit records; empty when
///                                       the last check surfaced no hits.
///   * `breach_watch.v1.user_name`    — optional personalisation (first name) for the
///                                       remediation playbook. Not required.
///
/// Scope guarantee: FREE tier can watch exactly one email. The Elite upsell copy sits in
/// the UI; this state object does not try to persist > 1 email.
@MainActor
final class BreachWatchState: ObservableObject {

    static let shared = BreachWatchState()

    @Published var email: String {
        didSet { Keychain.set(email, for: Keys.email) }
    }

    @Published var userName: String {
        didSet { Keychain.set(userName, for: Keys.userName) }
    }

    @Published var lastChecked: Date? {
        didSet {
            if let lastChecked {
                Keychain.set(iso.string(from: lastChecked), for: Keys.lastCheck)
            } else {
                Keychain.delete(Keys.lastCheck)
            }
        }
    }

    /// Current breach hits for the monitored email. One synthetic record ("A known breach
    /// list"), because the bundled prefix feed does not carry breach names. The UI treats
    /// the hits list as a boolean + per-item remediation playbook target.
    @Published var hits: [BreachHit] = []

    struct BreachHit: Identifiable, Codable, Equatable {
        var id: String { source }
        /// Human-readable source label ("A known breach list"). The bundled prefix feed
        /// deliberately does not identify specific breaches — see `README.md` next to the
        /// data file — so this value is static for FREE tier.
        let source: String
        /// Date the hit was first observed for the current `email`.
        let firstSeen: Date
    }

    // MARK: - Keychain keys

    private enum Keys {
        static let email       = "breach_watch.v1.email"
        static let lastCheck   = "breach_watch.v1.last_check"
        static let hitsJSON    = "breach_watch.v1.hits_json"
        static let userName    = "breach_watch.v1.user_name"
    }

    private let iso: ISO8601DateFormatter = {
        let f = ISO8601DateFormatter()
        f.formatOptions = [.withInternetDateTime]
        return f
    }()

    private init() {
        let storedEmail = Keychain.get(Keys.email) ?? ""
        let storedName = Keychain.get(Keys.userName) ?? ""
        let storedCheck = Keychain.get(Keys.lastCheck).flatMap {
            ISO8601DateFormatter().date(from: $0)
        }
        self.email = storedEmail
        self.userName = storedName
        self.lastChecked = storedCheck
        if let json = Keychain.get(Keys.hitsJSON),
           let data = json.data(using: .utf8),
           let decoded = try? JSONDecoder().decode([BreachHit].self, from: data) {
            self.hits = decoded
        }
    }

    // MARK: - Public API

    /// Normalises and stores the email. Pass empty to clear.
    func saveEmail(_ raw: String) {
        let normalised = BreachLookup.normalise(raw)
        email = normalised
        // Changing the email invalidates existing hits.
        hits = []
        persistHits()
    }

    /// Runs the local lookup against the bundled feed. Updates `hits` + `lastChecked`.
    /// Returns the new hits so callers can show in-flow feedback (notification body etc.).
    @discardableResult
    func runCheck(now: Date = Date()) -> [BreachHit] {
        guard !email.isEmpty else {
            lastChecked = now
            return []
        }
        let hit = BreachLookup.isEmailBreached(email)
        if hit {
            let existing = hits.first { $0.source == "A known breach list" }
            if existing == nil {
                hits = [BreachHit(source: "A known breach list", firstSeen: now)]
            }
        } else {
            hits = []
        }
        lastChecked = now
        persistHits()
        return hits
    }

    func clearAll() {
        email = ""
        userName = ""
        hits = []
        lastChecked = nil
        Keychain.delete(Keys.email)
        Keychain.delete(Keys.userName)
        Keychain.delete(Keys.lastCheck)
        Keychain.delete(Keys.hitsJSON)
    }

    private func persistHits() {
        let data = (try? JSONEncoder().encode(hits)) ?? Data()
        let str = String(data: data, encoding: .utf8) ?? "[]"
        Keychain.set(str, for: Keys.hitsJSON)
    }
}

// MARK: - Minimal Keychain shim

/// Tiny Keychain wrapper used only by Breach Watch so we don't drag in a dependency.
/// Items are generic passwords scoped to this bundle id with
/// `kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly` — unavailable from iCloud, available
/// to the app after the first post-boot unlock.
private enum Keychain {

    static func set(_ value: String, for key: String) {
        let data = Data(value.utf8)
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrAccount as String: key
        ]
        let attrs: [String: Any] = [
            kSecValueData as String: data,
            kSecAttrAccessible as String: kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
        ]
        let status = SecItemUpdate(query as CFDictionary, attrs as CFDictionary)
        if status == errSecItemNotFound {
            var insert = query
            insert[kSecValueData as String] = data
            insert[kSecAttrAccessible as String] = kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
            SecItemAdd(insert as CFDictionary, nil)
        }
    }

    static func get(_ key: String) -> String? {
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrAccount as String: key,
            kSecReturnData as String: true,
            kSecMatchLimit as String: kSecMatchLimitOne
        ]
        var out: AnyObject?
        let status = SecItemCopyMatching(query as CFDictionary, &out)
        guard status == errSecSuccess, let data = out as? Data else { return nil }
        return String(data: data, encoding: .utf8)
    }

    static func delete(_ key: String) {
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrAccount as String: key
        ]
        SecItemDelete(query as CFDictionary)
    }
}
