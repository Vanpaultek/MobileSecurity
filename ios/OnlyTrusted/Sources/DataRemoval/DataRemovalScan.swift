// SPDX-FileCopyrightText: 2026 VanPaulTek Inc
// SPDX-License-Identifier: AGPL-3.0-or-later
import Foundation
import SwiftUI
import Security

/// Static site catalog + per-mode template renderer for the Data Removal Scan (FREE,
/// visibility-only). Each site in `data_broker_sites.v1.json` may expose up to three
/// deep-link search URLs:
///   - `name` — search by First + Last (+ optional State)
///   - `phone` — search by E.164 digits-only phone
///   - `address` — search by Street + City + State
///
/// Sites that only support one or two of these simply omit the other URL keys. The
/// Data Removal screen filters the visible list to sites that support the selected
/// `SearchMode`. We never scrape — the feature only generates deep-links the user
/// taps to open in their default browser.

enum SearchMode: String, CaseIterable, Identifiable, Sendable {
    case name, phone, address

    var id: String { rawValue }

    var label: String {
        switch self {
        case .name:    return "Name"
        case .phone:   return "Phone"
        case .address: return "Address"
        }
    }
}

struct DataRemovalSite: Decodable, Identifiable, Equatable, Sendable {
    let name: String
    let search_urls: [String: String]
    var id: String { name }

    /// Returns the URL template for the selected mode, or nil if the site doesn't
    /// support that mode.
    func template(for mode: SearchMode) -> String? {
        search_urls[mode.rawValue]
    }
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

    /// Sites that support the given mode, preserving catalog order.
    static func sites(for mode: SearchMode) -> [DataRemovalSite] {
        sites.filter { $0.template(for: mode) != nil }
    }

    /// Builds the deep-link URL for a site + mode + form inputs. Returns nil when the
    /// required inputs for the mode are missing (e.g. phone mode with empty phone).
    static func renderURL(site: DataRemovalSite,
                          mode: SearchMode,
                          form: DataRemovalForm) -> URL? {
        guard let template = site.template(for: mode) else { return nil }
        switch mode {
        case .name:    return renderName(template: template, form: form)
        case .phone:   return renderPhone(template: template, form: form)
        case .address: return renderAddress(template: template, form: form)
        }
    }

    // MARK: - Per-mode renderers

    private static func renderName(template: String, form: DataRemovalForm) -> URL? {
        let fTrim = form.firstName.trimmingCharacters(in: .whitespacesAndNewlines)
        let lTrim = form.lastName.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !fTrim.isEmpty, !lTrim.isEmpty else { return nil }

        let fLow = fTrim.lowercased()
        let lLow = lTrim.lowercased()
        let fTitle = fTrim.prefix(1).uppercased() + fTrim.dropFirst().lowercased()
        let lTitle = lTrim.prefix(1).uppercased() + lTrim.dropFirst().lowercased()

        let out = template
            .replacingOccurrences(of: "{first}", with: enc(fLow))
            .replacingOccurrences(of: "{last}", with: enc(lLow))
            .replacingOccurrences(of: "{First}", with: enc(fTitle))
            .replacingOccurrences(of: "{Last}", with: enc(lTitle))
        return URL(string: out)
    }

    private static func renderPhone(template: String, form: DataRemovalForm) -> URL? {
        let digits = form.phone.filter(\.isNumber)
        guard digits.count >= 10 else { return nil }
        let out = template.replacingOccurrences(of: "{phone}", with: digits)
        return URL(string: out)
    }

    private static func renderAddress(template: String, form: DataRemovalForm) -> URL? {
        let street = form.street.trimmingCharacters(in: .whitespacesAndNewlines)
        let city = form.city.trimmingCharacters(in: .whitespacesAndNewlines)
        let state = form.state.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !street.isEmpty, !city.isEmpty, !state.isEmpty else { return nil }

        let out = template
            .replacingOccurrences(of: "{street}", with: enc(street.lowercased().replacingOccurrences(of: " ", with: "-")))
            .replacingOccurrences(of: "{city}", with: enc(city.lowercased().replacingOccurrences(of: " ", with: "-")))
            .replacingOccurrences(of: "{state}", with: enc(state.lowercased()))
        return URL(string: out)
    }

    private static func enc(_ s: String) -> String {
        s.addingPercentEncoding(withAllowedCharacters: .urlPathAllowed) ?? s
    }
}

/// All fields the catalog renderer may consume. The screen fills these from the
/// visible form, and only the fields relevant to the selected `SearchMode` need to be
/// non-empty for `renderURL` to succeed.
struct DataRemovalForm {
    var firstName: String = ""
    var lastName: String = ""
    var state: String = ""     // optional for name mode, required for address mode
    var phone: String = ""     // digits only (we strip non-digits in renderer)
    var street: String = ""
    var city: String = ""
}

/// Persistence for the user's last-entered search params. Keychain-backed so a
/// filesystem copy can't read the owner's name / phone / address.
@MainActor
final class DataRemovalState: ObservableObject {
    static let shared = DataRemovalState()

    @Published var searchMode: SearchMode {
        didSet { Keychain.set(searchMode.rawValue, for: Keys.mode) }
    }

    @Published var firstName: String {
        didSet { Keychain.set(firstName, for: Keys.firstName) }
    }
    @Published var lastName: String {
        didSet { Keychain.set(lastName, for: Keys.lastName) }
    }
    @Published var state: String {
        didSet { Keychain.set(state, for: Keys.state) }
    }
    @Published var phone: String {
        didSet { Keychain.set(phone, for: Keys.phone) }
    }
    @Published var street: String {
        didSet { Keychain.set(street, for: Keys.street) }
    }
    @Published var city: String {
        didSet { Keychain.set(city, for: Keys.city) }
    }

    private enum Keys {
        static let mode      = "data_removal.v2.mode"
        static let firstName = "data_removal.v2.first"
        static let lastName  = "data_removal.v2.last"
        static let state     = "data_removal.v2.state"
        static let phone     = "data_removal.v2.phone"
        static let street    = "data_removal.v2.street"
        static let city      = "data_removal.v2.city"
    }

    private init() {
        self.searchMode = (Keychain.get(Keys.mode).flatMap(SearchMode.init(rawValue:))) ?? .name
        self.firstName  = Keychain.get(Keys.firstName) ?? ""
        self.lastName   = Keychain.get(Keys.lastName) ?? ""
        self.state      = Keychain.get(Keys.state) ?? ""
        self.phone      = Keychain.get(Keys.phone) ?? ""
        self.street     = Keychain.get(Keys.street) ?? ""
        self.city       = Keychain.get(Keys.city) ?? ""
    }

    /// Snapshots the current fields into a `DataRemovalForm` the catalog can consume.
    var form: DataRemovalForm {
        DataRemovalForm(
            firstName: firstName,
            lastName: lastName,
            state: state,
            phone: phone,
            street: street,
            city: city
        )
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
/// framework). Used for the state filter on the Data Removal scan form.
enum USStates {
    static let all: [String] = [
        "AL","AK","AZ","AR","CA","CO","CT","DE","DC","FL","GA","HI","ID","IL","IN",
        "IA","KS","KY","LA","ME","MD","MA","MI","MN","MS","MO","MT","NE","NV","NH",
        "NJ","NM","NY","NC","ND","OH","OK","OR","PA","RI","SC","SD","TN","TX","UT",
        "VT","VA","WA","WV","WI","WY"
    ]
}
