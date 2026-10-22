// SPDX-FileCopyrightText: 2026 VanPaulTek Inc
// SPDX-License-Identifier: AGPL-3.0-or-later
import Foundation
import SwiftUI
import UserNotifications

/// Persisted state for Safety Check-In (Pro).
///
/// Tracks:
///  - `lastCheckInAt` — the most recent time the owner tapped "I'm safe".
///  - `scheduledEnabled` + `scheduledHour/Minute` — the optional daily nag.
///
/// When the scheduled nag is enabled we register a repeating `UNCalendarNotificationTrigger`
/// so that if the owner does not press "I'm safe" by the chosen time, iOS fires a local
/// notification. The daily reminder fires regardless; it's cancelled by `markCheckedIn()`
/// which re-schedules only for the next day.
@MainActor
final class CheckInState: ObservableObject {
    static let shared = CheckInState()

    @Published var lastCheckInAt: Date? {
        didSet {
            if let t = lastCheckInAt {
                d.set(t.timeIntervalSince1970, forKey: Keys.lastCheckInAt)
            } else {
                d.removeObject(forKey: Keys.lastCheckInAt)
            }
        }
    }

    @Published var scheduledEnabled: Bool {
        didSet {
            d.set(scheduledEnabled, forKey: Keys.scheduledEnabled)
            rescheduleNag()
        }
    }

    @Published var scheduledHour: Int {
        didSet {
            d.set(scheduledHour, forKey: Keys.scheduledHour)
            rescheduleNag()
        }
    }

    @Published var scheduledMinute: Int {
        didSet {
            d.set(scheduledMinute, forKey: Keys.scheduledMinute)
            rescheduleNag()
        }
    }

    private let d = UserDefaults.standard
    private let notificationId = "com.onlytrusted.checkin.nag"

    private enum Keys {
        static let lastCheckInAt = "checkin.v1.last_at"
        static let scheduledEnabled = "checkin.v1.scheduled_enabled"
        static let scheduledHour = "checkin.v1.scheduled_hour"
        static let scheduledMinute = "checkin.v1.scheduled_minute"
    }

    private init() {
        if let t = d.object(forKey: Keys.lastCheckInAt) as? Double {
            lastCheckInAt = Date(timeIntervalSince1970: t)
        } else {
            lastCheckInAt = nil
        }
        scheduledEnabled = d.bool(forKey: Keys.scheduledEnabled)
        scheduledHour = d.object(forKey: Keys.scheduledHour) as? Int ?? 21
        scheduledMinute = d.object(forKey: Keys.scheduledMinute) as? Int ?? 0
    }

    // MARK: - API used by the view

    /// Mark the check-in moment and persist. Also cancels today's nag — the next one is
    /// re-armed by `rescheduleNag()`.
    func markCheckedIn() {
        lastCheckInAt = Date()
        rescheduleNag()
    }

    /// Build the SOS body: "✅ OnlyTrusted check-in from <name>: I'm safe. HH:MM".
    static func composedBody(ownerName: String, at date: Date = Date()) -> String {
        let name = ownerName.trimmingCharacters(in: .whitespacesAndNewlines)
            .isEmpty ? "an OnlyTrusted user" : ownerName
        let f = DateFormatter()
        f.dateFormat = "HH:mm"
        return "\u{2705} OnlyTrusted check-in from \(name): I'm safe. \(f.string(from: date))"
    }

    // MARK: - Scheduled nag

    /// Request notification permission, idempotent — safe to call before every schedule.
    /// `completion` is called on the main actor.
    func requestNotificationPermissionIfNeeded(_ completion: @escaping @Sendable @MainActor (Bool) -> Void) {
        let center = UNUserNotificationCenter.current()
        center.getNotificationSettings { settings in
            let status = settings.authorizationStatus
            switch status {
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

    private func rescheduleNag() {
        let center = UNUserNotificationCenter.current()
        center.removePendingNotificationRequests(withIdentifiers: [notificationId])
        guard scheduledEnabled else { return }

        let content = UNMutableNotificationContent()
        content.title = "OnlyTrusted check-in"
        content.body = "Haven't heard from you today. Tap 'I'm safe' when you can."
        content.sound = .default

        var dateComponents = DateComponents()
        dateComponents.hour = scheduledHour
        dateComponents.minute = scheduledMinute
        let trigger = UNCalendarNotificationTrigger(dateMatching: dateComponents, repeats: true)
        let request = UNNotificationRequest(
            identifier: notificationId, content: content, trigger: trigger
        )
        center.add(request, withCompletionHandler: nil)
    }
}
