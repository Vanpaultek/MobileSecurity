// SPDX-FileCopyrightText: 2026 VanPaulTek Inc
// SPDX-License-Identifier: AGPL-3.0-or-later
import Foundation
import CryptoKit

/// Elite-tier (`SubscriptionState.hasElite`) offline threat-intelligence database.
///
/// Loads the four bundled feed files (shipped under `OnlyTrusted/Data/`) into
/// in-memory lookup structures the moment the shared instance is first touched. No
/// network calls happen — the full point of the feature is to be useful on an
/// airplane / border crossing / hostile network.
///
/// Partner Kotlin impl lives at
/// `android/.../ui/threatintel/ThreatIntelDatabase.kt` and must stay in sync on the
/// data-file names + parsing conventions (`#` = comment, blank lines ignored).
final class ThreatIntelDatabase: @unchecked Sendable {
    static let shared = ThreatIntelDatabase()

    // MARK: - Models

    struct PegasusIoCs: Decodable, Sendable {
        let filenames: [String]
        let plist_entries: [String]
        let dylib_sha256: [String]
        let process_names: [String]
        let domains: [String]
        let updated_at: String?
    }

    struct VaultScanHit: Identifiable, Equatable {
        var id: String { "\(contactName)-\(reason)-\(value)" }
        let contactName: String
        let reason: String
        let value: String
    }

    struct DeviceScanHit: Identifiable, Equatable {
        var id: String { "\(kind)-\(value)" }
        let kind: String
        let value: String
    }

    // MARK: - Loaded data

    let scamNumbers: Set<String>
    let phishingPatterns: [String]
    let breachHashPrefixes: Set<String>
    let pegasus: PegasusIoCs?
    let lastUpdated: Date?

    var totalEntries: Int {
        scamNumbers.count + phishingPatterns.count + breachHashPrefixes.count
            + (pegasus.map { $0.filenames.count + $0.plist_entries.count
                             + $0.dylib_sha256.count + $0.process_names.count
                             + $0.domains.count } ?? 0)
    }

    private init() {
        let bundle = Bundle.main

        func loadLines(_ name: String) -> [String] {
            guard let url = bundle.url(forResource: name, withExtension: "txt",
                                       subdirectory: "Data")
                    ?? bundle.url(forResource: name, withExtension: "txt") else {
                return []
            }
            guard let text = try? String(contentsOf: url, encoding: .utf8) else {
                return []
            }
            return text
                .split(whereSeparator: { $0 == "\n" || $0 == "\r" })
                .map { $0.trimmingCharacters(in: .whitespaces) }
                .filter { !$0.isEmpty && !$0.hasPrefix("#") }
        }

        self.scamNumbers = Set(loadLines("scam_numbers.v1"))
        self.phishingPatterns = loadLines("phishing_urls.v1").map { $0.lowercased() }
        self.breachHashPrefixes = Set(loadLines("breach_hashes.v1").map { $0.lowercased() })

        if let url = bundle.url(forResource: "pegasus_iocs.v1", withExtension: "json",
                                subdirectory: "Data")
                        ?? bundle.url(forResource: "pegasus_iocs.v1", withExtension: "json"),
           let data = try? Data(contentsOf: url) {
            self.pegasus = try? JSONDecoder().decode(PegasusIoCs.self, from: data)
            self.lastUpdated = PegasusIoCs.parseDate(pegasus?.updated_at)
        } else {
            self.pegasus = nil
            self.lastUpdated = nil
        }
    }

    // MARK: - Lookups

    func isScamNumber(_ raw: String) -> Bool {
        let normalised = PhoneUtils.normalise(raw)
        if scamNumbers.contains(normalised) { return true }
        // Also tolerate the "+1xxx…" vs "1xxx…" variant for legacy data.
        if normalised.hasPrefix("+"), scamNumbers.contains(String(normalised.dropFirst())) {
            return true
        }
        return false
    }

    func isPhishingHost(_ host: String) -> Bool {
        guard !host.isEmpty else { return false }
        let needle = host.lowercased()
        return phishingPatterns.contains { needle.contains($0) || $0.contains(needle) }
    }

    /// Hashes an email with SHA-256 and checks the 10-hex-char prefix against the
    /// bundled breach set. Returns true iff the prefix is present.
    func isBreachedEmail(_ email: String) -> Bool {
        guard !email.isEmpty else { return false }
        let lowered = email.lowercased().trimmingCharacters(in: .whitespacesAndNewlines)
        guard let data = lowered.data(using: .utf8) else { return false }
        let digest = SHA256.hash(data: data)
        let hex = digest.map { String(format: "%02x", $0) }.joined()
        let prefix = String(hex.prefix(10))
        return breachHashPrefixes.contains(prefix)
    }

    // MARK: - Scans

    @MainActor
    func scanVault() -> [VaultScanHit] {
        var hits: [VaultScanHit] = []
        for contact in VaultStore.shared.contacts {
            for num in contact.normalisedNumbers {
                if isScamNumber(num) {
                    hits.append(.init(
                        contactName: contact.displayName,
                        reason: "Phone number appears in scam-numbers feed",
                        value: num
                    ))
                }
            }
            if let email = contact.maskedEmail, !email.isEmpty, email.contains("@") {
                if isBreachedEmail(email) {
                    hits.append(.init(
                        contactName: contact.displayName,
                        reason: "Email prefix matches a known breach dataset",
                        value: email
                    ))
                }
            }
        }
        return hits
    }

    /// iOS sandbox scan — walks the app's own Documents/Library directories for any
    /// filename that matches a bundled Pegasus IoC. On a real jailbreak compromise
    /// those filenames would surface in system paths we can't read; this scan is
    /// deliberately limited to the sandbox so it never misleads a user into thinking
    /// a full system sweep is possible on stock iOS.
    func scanSandbox() -> [DeviceScanHit] {
        guard let pegasus else { return [] }
        var hits: [DeviceScanHit] = []
        let roots: [URL?] = [
            FileManager.default.urls(for: .documentDirectory, in: .userDomainMask).first,
            FileManager.default.urls(for: .libraryDirectory, in: .userDomainMask).first,
            FileManager.default.urls(for: .cachesDirectory, in: .userDomainMask).first
        ]
        let names = Set(pegasus.filenames.map { $0.lowercased() })
        for root in roots.compactMap({ $0 }) {
            guard let enumerator = FileManager.default.enumerator(
                at: root, includingPropertiesForKeys: nil,
                options: [.skipsPackageDescendants]
            ) else { continue }
            for case let url as URL in enumerator {
                let name = url.lastPathComponent.lowercased()
                if names.contains(name) {
                    hits.append(.init(kind: "Pegasus IoC filename", value: url.path))
                }
            }
        }
        return hits
    }
}

extension ThreatIntelDatabase.PegasusIoCs {
    static func parseDate(_ s: String?) -> Date? {
        guard let s else { return nil }
        let fmt = DateFormatter()
        fmt.dateFormat = "yyyy-MM-dd"
        fmt.timeZone = TimeZone(identifier: "UTC")
        return fmt.date(from: s)
    }
}
