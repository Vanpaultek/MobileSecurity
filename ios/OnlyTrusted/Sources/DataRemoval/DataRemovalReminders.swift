// SPDX-FileCopyrightText: 2026 VanPaulTek Inc
// SPDX-License-Identifier: AGPL-3.0-or-later
import Foundation
import Security
import SwiftUI
import UserNotifications

/// Premium-only state: tracks which broker sites the user has flagged as "removal
/// requested" and schedules a local 90-day re-check notification per site. All state
/// lives in the Keychain; no network, no server.
///
/// Pattern mirrors BreachWatch/Alias stores: single JSON blob under one Keychain key.
@MainActor
final class DataRemovalReminders: ObservableObject {

    static let shared = DataRemovalReminders()

    /// `[siteName: timestampMillisWhenRequested]`. Reminder fires 90 days after that.
    @Published private(set) var requested: [String: Date] = [:]

    /// Window between request and re-check reminder. 90 days matches the typical broker
    /// SLA — most legit opt-outs are processed within 2–6 weeks, so 90 days gives time
    /// for the user's data to actually be removed before we nag them to verify.
    static let reminderDays: TimeInterval = 90

    private let key = "data_removal.v1.requested"

    private init() {
        if let raw = Keychain.get(key),
           let data = raw.data(using: .utf8),
           let decoded = try? JSONDecoder().decode([String: Date].self, from: data) {
            self.requested = decoded
        }
    }

    func isRequested(site: String) -> Bool { requested[site] != nil }

    func requestedAt(site: String) -> Date? { requested[site] }

    /// Marks the site as removal-requested now, schedules a local notification 90 days
    /// later. Idempotent — a second call re-anchors the 90-day window from `now`.
    func markRequested(site: String, now: Date = Date()) {
        requested[site] = now
        persist()
        scheduleReminder(site: site, at: now.addingTimeInterval(Self.reminderDays * 86400))
    }

    func unmark(site: String) {
        requested.removeValue(forKey: site)
        persist()
        cancelReminder(site: site)
    }

    func clearAll() {
        for site in requested.keys { cancelReminder(site: site) }
        requested = [:]
        Keychain.delete(key)
    }

    // MARK: - Notification plumbing

    private func scheduleReminder(site: String, at date: Date) {
        let content = UNMutableNotificationContent()
        content.title = "Re-check \(site)"
        content.body = "90 days since you requested removal from \(site). Open OnlyTrusted → Data Removal to search for your name again and confirm the record is gone."
        content.sound = .default
        let comps = Calendar.current.dateComponents([.year,.month,.day,.hour,.minute], from: date)
        let trigger = UNCalendarNotificationTrigger(dateMatching: comps, repeats: false)
        let req = UNNotificationRequest(identifier: Self.notifID(site: site), content: content, trigger: trigger)
        UNUserNotificationCenter.current().add(req) { _ in /* silent — permission UI lives in Breach Watch screen */ }
    }

    private func cancelReminder(site: String) {
        UNUserNotificationCenter.current().removePendingNotificationRequests(
            withIdentifiers: [Self.notifID(site: site)]
        )
    }

    private static func notifID(site: String) -> String {
        "data_removal.v1.reminder.\(site)"
    }

    private func persist() {
        let data = (try? JSONEncoder().encode(requested)) ?? Data()
        let str = String(data: data, encoding: .utf8) ?? "{}"
        Keychain.set(str, for: key)
    }
}

// Minimal Keychain shim (same pattern as BreachWatchState / AliasStore).
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
    static func delete(_ key: String) {
        let q: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrAccount as String: key
        ]
        SecItemDelete(q as CFDictionary)
    }
}
