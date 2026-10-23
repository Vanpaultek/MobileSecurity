// SPDX-FileCopyrightText: 2026 VanPaulTek Inc
// SPDX-License-Identifier: AGPL-3.0-or-later
import Foundation
import Security
import SwiftUI

/// Observable store for Private Email Aliases (FREE tier, unlimited count).
///
/// Honest claim: OnlyTrusted does **not** operate a mail server. We generate random
/// alias local-parts and store the user's bookkeeping (label, enabled toggle, the relay
/// domain they chose). The forwarding is done by whichever relay the user already pays
/// for — iCloud+ Hide My Email, DuckDuckGo Email Protection, SimpleLogin, Firefox Relay,
/// or a custom domain they own. Zero network, zero cloud on our side.
///
/// Keychain-backed under a single JSON blob at `aliases.v1.list`. Chosen relay is at
/// `aliases.v1.relay`.
@MainActor
final class AliasStore: ObservableObject {

    static let shared = AliasStore()

    @Published var aliases: [EmailAlias] = [] {
        didSet { persist() }
    }

    @Published var relay: RelayChoice = .iCloudHideMyEmail {
        didSet { Keychain.set(relay.rawValue, for: Keys.relay) }
    }

    /// Custom domain used when `relay == .customDomain`. Stored separately so switching
    /// relay and back preserves the user's entry.
    @Published var customDomain: String = "" {
        didSet { Keychain.set(customDomain, for: Keys.customDomain) }
    }

    private enum Keys {
        static let list         = "aliases.v1.list"
        static let relay        = "aliases.v1.relay"
        static let customDomain = "aliases.v1.custom_domain"
    }

    private init() {
        if let json = Keychain.get(Keys.list),
           let data = json.data(using: .utf8),
           let decoded = try? JSONDecoder().decode([EmailAlias].self, from: data) {
            self.aliases = decoded
        }
        if let r = Keychain.get(Keys.relay), let choice = RelayChoice(rawValue: r) {
            self.relay = choice
        }
        self.customDomain = Keychain.get(Keys.customDomain) ?? ""
    }

    // MARK: - Alias ops

    /// Generates a new random alias prefix (12 chars, lowercase alphanumerics +
    /// occasional hyphen for readability) and prepends it to the list. Returns the new
    /// alias so the caller can present a copy prompt.
    @discardableResult
    func generate(label: String = "") -> EmailAlias {
        let prefix = Self.generatePrefix()
        let a = EmailAlias(
            id: UUID().uuidString,
            prefix: prefix,
            label: label.trimmingCharacters(in: .whitespacesAndNewlines),
            enabled: true,
            createdAt: Date()
        )
        aliases.insert(a, at: 0)
        return a
    }

    func toggle(_ id: String) {
        guard let idx = aliases.firstIndex(where: { $0.id == id }) else { return }
        aliases[idx].enabled.toggle()
    }

    func rename(_ id: String, label: String) {
        guard let idx = aliases.firstIndex(where: { $0.id == id }) else { return }
        aliases[idx].label = label.trimmingCharacters(in: .whitespacesAndNewlines)
    }

    func delete(_ id: String) {
        aliases.removeAll { $0.id == id }
    }

    func clearAll() {
        aliases = []
    }

    // MARK: - Premium: bulk + CSV

    /// Premium: toggle `enabled = false` across every alias.
    func disableAll() {
        aliases = aliases.map { a in
            var out = a
            out.enabled = false
            return out
        }
    }

    /// Premium: delete every alias currently marked disabled.
    func deleteAllDisabled() {
        aliases.removeAll { !$0.enabled }
    }

    /// Premium: export the alias list as CSV. Columns:
    /// `prefix,label,enabled,created_at_iso`. The alias domain is NOT exported — the
    /// receiving device supplies its own relay choice, which is intentional (users may
    /// migrate between relay providers during a device swap).
    func exportCSV() -> String {
        let iso = ISO8601DateFormatter()
        iso.formatOptions = [.withInternetDateTime]
        var out = "prefix,label,enabled,created_at\n"
        for a in aliases {
            // Minimal CSV escape — quote label if it contains comma or quote.
            let labelEscaped = Self.csvEscape(a.label)
            out += "\(a.prefix),\(labelEscaped),\(a.enabled),\(iso.string(from: a.createdAt))\n"
        }
        return out
    }

    /// Premium: import aliases from a CSV string in the same format as `exportCSV()`.
    /// Returns the number of aliases added (duplicates by prefix are skipped). Malformed
    /// rows are silently dropped so a partially-corrupt file still restores what it can.
    @discardableResult
    func importCSV(_ raw: String) -> Int {
        let iso = ISO8601DateFormatter()
        iso.formatOptions = [.withInternetDateTime]
        let existingPrefixes = Set(aliases.map { $0.prefix })
        var added = 0
        let lines = raw.split(whereSeparator: { $0.isNewline })
        for (idx, line) in lines.enumerated() {
            // Skip header
            if idx == 0 && line.hasPrefix("prefix,") { continue }
            let cols = Self.csvSplit(String(line))
            guard cols.count >= 4 else { continue }
            let prefix = cols[0]
            let label = cols[1]
            let enabled = (cols[2].lowercased() == "true")
            let created = iso.date(from: cols[3]) ?? Date()
            guard !prefix.isEmpty, !existingPrefixes.contains(prefix) else { continue }
            aliases.insert(EmailAlias(
                id: UUID().uuidString,
                prefix: prefix,
                label: label,
                enabled: enabled,
                createdAt: created
            ), at: 0)
            added += 1
        }
        return added
    }

    // MARK: - CSV helpers

    private static func csvEscape(_ s: String) -> String {
        if s.contains(",") || s.contains("\"") || s.contains("\n") {
            return "\"" + s.replacingOccurrences(of: "\"", with: "\"\"") + "\""
        }
        return s
    }

    private static func csvSplit(_ line: String) -> [String] {
        var cols: [String] = []
        var cur = ""
        var inQuotes = false
        var i = line.startIndex
        while i < line.endIndex {
            let c = line[i]
            if inQuotes {
                if c == "\"" {
                    let next = line.index(after: i)
                    if next < line.endIndex, line[next] == "\"" {
                        cur.append("\"")
                        i = next
                    } else {
                        inQuotes = false
                    }
                } else {
                    cur.append(c)
                }
            } else {
                if c == "," {
                    cols.append(cur); cur = ""
                } else if c == "\"" {
                    inQuotes = true
                } else {
                    cur.append(c)
                }
            }
            i = line.index(after: i)
        }
        cols.append(cur)
        return cols
    }

    // MARK: - Address assembly

    /// Full address for the given alias, built from the current relay choice.
    func address(for alias: EmailAlias) -> String {
        "\(alias.prefix)@\(domain)"
    }

    /// Current effective domain string. Custom domain is used verbatim; blank falls
    /// back to a placeholder so the UI never renders `@`-only addresses.
    var domain: String {
        switch relay {
        case .iCloudHideMyEmail: return "icloud.com"
        case .duckDuckGo:        return "duck.com"
        case .simpleLogin:       return "aleeas.com"
        case .firefoxRelay:      return "mozmail.com"
        case .customDomain:
            let d = customDomain.trimmingCharacters(in: .whitespaces).lowercased()
            return d.isEmpty ? "yourdomain.example" : d
        }
    }

    // MARK: - Prefix generator

    private static let prefixAlphabet = Array("abcdefghjkmnpqrstuvwxyz23456789")

    static func generatePrefix() -> String {
        // 3-3-4 split with hyphens — e.g. "q7m-elk-83vf". Alphabet avoids visually
        // ambiguous chars (0/O/1/l/i) so copying by eye works. Entropy: 31^10 ≈ 8.2×10¹⁴.
        //
        // `Array.randomElement()` on Apple platforms uses `SystemRandomNumberGenerator`,
        // which is backed by `getentropy(2)` / Security framework — cryptographically
        // secure. The alias prefix becomes a quasi-secret identifier so we want this
        // not to be predictable even though it isn't a key.
        func chunk(_ n: Int) -> String {
            String((0..<n).compactMap { _ in prefixAlphabet.randomElement() })
        }
        return "\(chunk(3))-\(chunk(3))-\(chunk(4))"
    }

    // MARK: - Persist

    private func persist() {
        let data = (try? JSONEncoder().encode(aliases)) ?? Data()
        let s = String(data: data, encoding: .utf8) ?? "[]"
        Keychain.set(s, for: Keys.list)
    }
}

// MARK: - Model

struct EmailAlias: Codable, Identifiable, Equatable {
    let id: String
    let prefix: String
    var label: String
    var enabled: Bool
    let createdAt: Date
}

// MARK: - Relay choice

enum RelayChoice: String, CaseIterable, Identifiable {
    case iCloudHideMyEmail = "icloud"
    case duckDuckGo        = "ddg"
    case simpleLogin       = "simplelogin"
    case firefoxRelay      = "firefox"
    case customDomain      = "custom"

    var id: String { rawValue }

    var displayName: String {
        switch self {
        case .iCloudHideMyEmail: return "iCloud+ Hide My Email"
        case .duckDuckGo:        return "DuckDuckGo Email Protection"
        case .simpleLogin:       return "SimpleLogin"
        case .firefoxRelay:      return "Firefox Relay"
        case .customDomain:      return "Custom domain"
        }
    }

    var subtitle: String {
        switch self {
        case .iCloudHideMyEmail: return "Included with any paid iCloud+. Create aliases in Settings → Mail → Hide My Email."
        case .duckDuckGo:        return "Free. Enroll at duckduckgo.com/email."
        case .simpleLogin:       return "Free tier up to 15 aliases. Open simplelogin.io."
        case .firefoxRelay:      return "Free tier up to 5 masks. Open relay.firefox.com."
        case .customDomain:      return "Your own domain with a catch-all mailbox you already own."
        }
    }

    /// URL to open the relay provider's self-service portal. nil for custom domain.
    var portalURL: URL? {
        switch self {
        case .iCloudHideMyEmail: return URL(string: "https://www.icloud.com/settings/hidemyemail")
        case .duckDuckGo:        return URL(string: "https://duckduckgo.com/email/")
        case .simpleLogin:       return URL(string: "https://simplelogin.io/")
        case .firefoxRelay:      return URL(string: "https://relay.firefox.com/")
        case .customDomain:      return nil
        }
    }
}

// MARK: - Minimal Keychain shim (copied pattern from BreachWatchState)

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
