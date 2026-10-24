// SPDX-FileCopyrightText: 2026 VanPaulTek Inc
// SPDX-License-Identifier: AGPL-3.0-or-later
import Foundation
import Security

/// Persists the latest Contact Safety Scan result so the Dashboard can show the match
/// count without re-running the scan on every launch. Keychain-backed; nothing leaves
/// the device.
enum ContactScanState {

    private enum Keys {
        static let contactsScanned = "contact_scan.v1.contacts_scanned"
        static let numbersScanned  = "contact_scan.v1.numbers_scanned"
        static let matchCount      = "contact_scan.v1.match_count"
        static let lastScanMillis  = "contact_scan.v1.last_scan_millis"
    }

    struct Snapshot {
        let contactsScanned: Int
        let numbersScanned: Int
        let matchCount: Int
        let lastScanMillis: Int64
    }

    static func snapshot() -> Snapshot? {
        let last = Int64(Keychain.get(Keys.lastScanMillis) ?? "0") ?? 0
        if last == 0 { return nil }
        return Snapshot(
            contactsScanned: Int(Keychain.get(Keys.contactsScanned) ?? "0") ?? 0,
            numbersScanned: Int(Keychain.get(Keys.numbersScanned) ?? "0") ?? 0,
            matchCount: Int(Keychain.get(Keys.matchCount) ?? "0") ?? 0,
            lastScanMillis: last
        )
    }

    static func matchCount() -> Int {
        Int(Keychain.get(Keys.matchCount) ?? "0") ?? 0
    }

    static func save(_ report: ContactsScamScanner.ScamScanReport) {
        let now = Int64(Date().timeIntervalSince1970 * 1000)
        Keychain.set(String(report.contactsScanned), for: Keys.contactsScanned)
        Keychain.set(String(report.numbersScanned), for: Keys.numbersScanned)
        Keychain.set(String(report.matches.count), for: Keys.matchCount)
        Keychain.set(String(now), for: Keys.lastScanMillis)
    }

    static func clear() {
        Keychain.delete(Keys.contactsScanned)
        Keychain.delete(Keys.numbersScanned)
        Keychain.delete(Keys.matchCount)
        Keychain.delete(Keys.lastScanMillis)
    }
}

private enum Keychain {
    static func set(_ value: String, for key: String) {
        let data = Data(value.utf8)
        let q: [String: Any] = [kSecClass as String: kSecClassGenericPassword, kSecAttrAccount as String: key]
        let attrs: [String: Any] = [
            kSecValueData as String: data,
            kSecAttrAccessible as String: kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
        ]
        if SecItemUpdate(q as CFDictionary, attrs as CFDictionary) == errSecItemNotFound {
            var insert = q
            insert[kSecValueData as String] = data
            insert[kSecAttrAccessible as String] = kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
            SecItemAdd(insert as CFDictionary, nil)
        }
    }
    static func get(_ key: String) -> String? {
        let q: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrAccount as String: key,
            kSecReturnData as String: true,
            kSecMatchLimit as String: kSecMatchLimitOne
        ]
        var out: AnyObject?
        guard SecItemCopyMatching(q as CFDictionary, &out) == errSecSuccess,
              let d = out as? Data else { return nil }
        return String(data: d, encoding: .utf8)
    }
    static func delete(_ key: String) {
        let q: [String: Any] = [kSecClass as String: kSecClassGenericPassword, kSecAttrAccount as String: key]
        SecItemDelete(q as CFDictionary)
    }
}
