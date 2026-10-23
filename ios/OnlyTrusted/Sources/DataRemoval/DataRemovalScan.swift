// SPDX-FileCopyrightText: 2026 VanPaulTek Inc
// SPDX-License-Identifier: AGPL-3.0-or-later
import Foundation
import SwiftUI
import Security

/// Static site catalog + template renderer for the Data Removal Scan (FREE tier,
/// visibility-only). Reads `OnlyTrusted/Data/data_broker_sites.v1.json` at startup;
/// shared with the Android build via the same file name under `assets/threatintel/`.
///
/// We intentionally do NOT scrape or probe these sites. The feature only generates
/// deep-link search URLs the user can tap to open in their default browser. Every UI
/// surface for this feature says "View only — this scan shows exposure but cannot
/// remove it".
struct DataRemovalSite: Decodable, Identifiable, Equatable, Sendable {
    let name: String
    let search_url: String
    var id: String { name }
}

enum DataRemovalCatalog {

    nonisolated(unsafe) static let sites: [DataRemovalSite] = load()

    private static func load() -> [DataRemovalSite] {
        let bundle = Bundle.main
        guard let url = bundle.url(forResource: "data_broker_sites.v1", withExtension: "json",
                                   subdirectory: "Data")
                ?? bundle.url(forResource: "data_broker_sites.v1", withExtension: "json"),
              let data = try? Data(contentsOf: url),
              let decoded = try? JSONDecoder().decode([DataRemovalSite].self, from: data)
        else { return [] }
        return decoded
    }

    /// Expands `{first}` / `{last}` / `{First}` / `{Last}` placeholders. Values are
    /// percent-encoded. Returns `nil` if either name is empty after trimming.
    static func renderSearchURL(_ template: String,
                                firstName: String,
                                lastName: String) -> URL? {
        let fTrim = firstName.trimmingCharacters(in: .whitespacesAndNewlines)
        let lTrim = lastName.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !fTrim.isEmpty, !lTrim.isEmpty else { return nil }

        let fLow = fTrim.lowercased()
        let lLow = lTrim.lowercased()
        let fTitle = fTrim.prefix(1).uppercased() + fTrim.dropFirst().lowercased()
        let lTitle = lTrim.prefix(1).uppercased() + lTrim.dropFirst().lowercased()

        func enc(_ s: String) -> String {
            s.addingPercentEncoding(withAllowedCharacters: .urlPathAllowed) ?? s
        }

        var out = template
            .replacingOccurrences(of: "{first}", with: enc(fLow))
            .replacingOccurrences(of: "{last}", with: enc(lLow))
            .replacingOccurrences(of: "{First}", with: enc(fTitle))
            .replacingOccurrences(of: "{Last}", with: enc(lTitle))
        // The template file embeds "%20" as a literal; URL parsing is still fine with it.
        _ = out // kept for parity with Android
        return URL(string: out)
    }
}

/// Minimal persistence for the user's last-entered search params. Keychain-backed so
/// a filesystem copy can't read the owner's name. The feature is still useful without
/// this — persistence is pure convenience.
@MainActor
final class DataRemovalState: ObservableObject {
    static let shared = DataRemovalState()

    @Published var firstName: String {
        didSet { Keychain.set(firstName, for: Keys.firstName) }
    }

    @Published var lastName: String {
        didSet { Keychain.set(lastName, for: Keys.lastName) }
    }

    @Published var state: String {
        didSet { Keychain.set(state, for: Keys.state) }
    }

    private enum Keys {
        static let firstName = "data_removal.v1.first"
        static let lastName  = "data_removal.v1.last"
        static let state     = "data_removal.v1.state"
    }

    private init() {
        self.firstName = Keychain.get(Keys.firstName) ?? ""
        self.lastName = Keychain.get(Keys.lastName) ?? ""
        self.state = Keychain.get(Keys.state) ?? ""
    }
}

/// Tiny Keychain wrapper — kept local to this file to avoid cross-feature coupling.
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
}

/// The 50 US states + DC. Static table keeps the UI picker lightweight (no backing
/// framework). Used for the optional state filter on the Data Removal scan form.
enum USStates {
    static let all: [String] = [
        "AL","AK","AZ","AR","CA","CO","CT","DE","DC","FL","GA","HI","ID","IL","IN",
        "IA","KS","KY","LA","ME","MD","MA","MI","MN","MS","MO","MT","NE","NV","NH",
        "NJ","NM","NY","NC","ND","OH","OK","OR","PA","RI","SC","SD","TN","TX","UT",
        "VT","VA","WA","WV","WI","WY"
    ]
}
