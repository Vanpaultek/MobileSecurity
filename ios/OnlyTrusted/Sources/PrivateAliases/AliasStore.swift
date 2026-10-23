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
        func chunk(_ n: Int) -> String {
            String((0..<n).map { _ in prefixAlphabet.randomElement()! })
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
