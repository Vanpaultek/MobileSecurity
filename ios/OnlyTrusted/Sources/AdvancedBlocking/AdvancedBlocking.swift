// SPDX-FileCopyrightText: 2026 VanPaulTek Inc
// SPDX-License-Identifier: AGPL-3.0-or-later
import Foundation
import SwiftUI
import Security

/// Premium-only Advanced Blocking rules:
///  * Quiet Hours — a daily window during which non-trusted-contact calls are treated
///    as blocked. Store start/end as minutes since midnight (0..1439).
///  * Area-code allowlist — a list of 3-digit NANP area codes whose numbers are never
///    marked as unknown-caller spam, even if they aren't in the vault.
///
/// Storage: Keychain (per-device, never iCloud-synced). The ILMessageFilterExtension can
/// re-read these on each incoming message. The CXCallDirectoryProvider rebuilds its
/// static list from the user's block list; quiet-hours enforcement for calls happens at
/// the main-app layer and at the (future) silent-mode routing API.
@MainActor
final class AdvancedBlocking: ObservableObject {

    static let shared = AdvancedBlocking()

    @Published var quietHoursEnabled: Bool {
        didSet { Keychain.set(quietHoursEnabled ? "1" : "0", for: Keys.quietEnabled) }
    }

    /// Minutes-since-midnight, 0..1439.
    @Published var quietStartMinutes: Int {
        didSet { Keychain.set(String(quietStartMinutes), for: Keys.quietStart) }
    }

    @Published var quietEndMinutes: Int {
        didSet { Keychain.set(String(quietEndMinutes), for: Keys.quietEnd) }
    }

    /// 3-digit area codes (NANP). Stored as a comma-joined string.
    @Published var areaCodeAllowlist: [String] = [] {
        didSet { Keychain.set(areaCodeAllowlist.joined(separator: ","), for: Keys.areaCodes) }
    }

    private enum Keys {
        static let quietEnabled = "advanced_blocking.v1.quiet_enabled"
        static let quietStart   = "advanced_blocking.v1.quiet_start"
        static let quietEnd     = "advanced_blocking.v1.quiet_end"
        static let areaCodes    = "advanced_blocking.v1.area_codes"
    }

    private init() {
        self.quietHoursEnabled = (Keychain.get(Keys.quietEnabled) == "1")
        self.quietStartMinutes = Int(Keychain.get(Keys.quietStart) ?? "") ?? (22 * 60) // 10pm
        self.quietEndMinutes   = Int(Keychain.get(Keys.quietEnd)   ?? "") ?? (7 * 60)  // 7am
        if let raw = Keychain.get(Keys.areaCodes), !raw.isEmpty {
            self.areaCodeAllowlist = raw.split(separator: ",").map(String.init)
        }
    }

    /// Returns true if the current wall-clock time falls inside the quiet-hours window.
    /// Handles cross-midnight ranges correctly (e.g. 22:00..07:00).
    func isQuietHoursNow(reference: Date = Date()) -> Bool {
        guard quietHoursEnabled else { return false }
        let comps = Calendar.current.dateComponents([.hour, .minute], from: reference)
        let now = (comps.hour ?? 0) * 60 + (comps.minute ?? 0)
        return isInWindow(now: now, start: quietStartMinutes, end: quietEndMinutes)
    }

    /// Returns true if the number's area code is on the allowlist. For NANP numbers only.
    func isAreaCodeAllowed(_ phoneNumber: String) -> Bool {
        let digits = phoneNumber.filter(\.isNumber)
        guard digits.count >= 10 else { return false }
        // US/CA: strip optional leading 1, take next 3 digits as area code.
        let noCountry = digits.hasPrefix("1") && digits.count == 11 ? String(digits.dropFirst()) : digits
        let areaCode = String(noCountry.prefix(3))
        return areaCodeAllowlist.contains(areaCode)
    }

    /// Convenience for UI: HH:mm label for a minute offset.
    static func minutesLabel(_ m: Int) -> String {
        let h = (m / 60) % 24
        let mm = m % 60
        return String(format: "%02d:%02d", h, mm)
    }

    private func isInWindow(now: Int, start: Int, end: Int) -> Bool {
        if start == end { return false }
        if start < end {
            return now >= start && now < end
        }
        // Cross-midnight window (e.g. 22:00..07:00).
        return now >= start || now < end
    }
}

private enum Keychain {
    static func set(_ value: String, for key: String) {
        let data = Data(value.utf8)
        let q: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrAccount as String: key
        ]
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
}
