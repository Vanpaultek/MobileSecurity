// SPDX-FileCopyrightText: 2026 VanPaulTek Inc
// SPDX-License-Identifier: AGPL-3.0-or-later
import Foundation
import UserNotifications

/// Monthly local notification scheduler for Breach Watch (FREE tier).
///
/// Uses `UNCalendarNotificationTrigger` with `repeats = true` set to the first day of
/// the month at 09:00 local time. The notification is advisory only — it reminds the
/// user to open Breach Watch, which re-runs the local lookup. We do NOT auto-run the
/// lookup in the background (iOS cannot wake us for a UNUserNotification trigger), so
/// the notification body says "tap to review" rather than claiming to have scanned.
enum BreachWatchScheduler {

    private static let notificationId = "breach_watch.v1.monthly"

    /// Request notification permission if not yet determined. Idempotent — safe to call
    /// every time the Breach Watch screen appears.
    static func requestPermissionIfNeeded(_ completion: @escaping @Sendable @MainActor (Bool) -> Void) {
        let center = UNUserNotificationCenter.current()
        center.getNotificationSettings { settings in
            switch settings.authorizationStatus {
            case .authorized, .provisional, .ephemeral:
                Task { @MainActor in completion(true) }
            case .notDetermined:
                center.requestAuthorization(options: [.alert, .sound]) { granted, _ in
                    Task { @MainActor in completion(granted) }
                }
            default:
                Task { @MainActor in completion(false) }
            }
        }
    }

    /// Schedules the monthly reminder. Overwrites any previously-scheduled copy.
    static func scheduleMonthlyReminder() {
        let center = UNUserNotificationCenter.current()
        center.removePendingNotificationRequests(withIdentifiers: [notificationId])

        let content = UNMutableNotificationContent()
        content.title = "Breach Watch monthly check"
        content.body = "Tap to review your monitored email for new breach hits."
        content.sound = .default

        var components = DateComponents()
        components.day = 1
        components.hour = 9
        components.minute = 0
        let trigger = UNCalendarNotificationTrigger(dateMatching: components, repeats: true)

        let request = UNNotificationRequest(
            identifier: notificationId, content: content, trigger: trigger
        )
        center.add(request, withCompletionHandler: nil)
    }

    /// Removes the scheduled reminder (e.g. when the user clears their monitored email).
    static func cancel() {
        UNUserNotificationCenter.current()
            .removePendingNotificationRequests(withIdentifiers: [notificationId])
    }

    /// Posts an immediate local notification announcing a fresh breach hit. Called from
    /// `BreachWatchScreen` when "Check now" surfaces a new hit so the user sees the
    /// alert even if they background the app before the UI updates registers.
    static func postNewHitNotification() {
        let center = UNUserNotificationCenter.current()
        let content = UNMutableNotificationContent()
        content.title = "Breach Watch — new hit"
        content.body = "Your monitored email appeared in a breach list. Open OnlyTrusted to review."
        content.sound = .default

        let request = UNNotificationRequest(
            identifier: "breach_watch.v1.new_hit.\(UUID().uuidString)",
            content: content,
            trigger: nil
        )
        center.add(request, withCompletionHandler: nil)
    }
}
