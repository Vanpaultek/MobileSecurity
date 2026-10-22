// SPDX-FileCopyrightText: 2026 VanPaulTek Inc
// SPDX-License-Identifier: AGPL-3.0-or-later
import Foundation
import SwiftUI
import UserNotifications

/// Dead-Man Wipe (Elite).
///
/// If the user doesn't unlock the app for `intervalDays`, the entire app state is wiped on
/// next launch. The countdown is reset on every successful unlock through
/// `markUnlockNow()`, which `AppLockGate` calls after biometric / PIN success.
///
/// Backing store: `UserDefaults` (`deadmanwipe.v1.*`). We purposefully don't use the keychain
/// here — a dead-man wipe is a destructive-last-resort switch, not a secret; the attacker
/// defeating it would still have to prevent the launch-time check from firing (impossible
/// without jailbreaking + patching the binary), and keychain survival across uninstalls
/// would make the feature *more* dangerous, not less.
@MainActor
final class DeadmanWipeState: ObservableObject {
    static let shared = DeadmanWipeState()

    /// Valid dead-man intervals (days). 60 is the max — anything longer blurs into "forgot".
    static let allowedIntervals: [Int] = [7, 14, 30, 60]

    @Published var isEnabled: Bool {
        didSet { d.set(isEnabled, forKey: Keys.enabled) }
    }

    @Published var intervalDays: Int {
        didSet {
            let clamped = Self.allowedIntervals.contains(intervalDays) ? intervalDays : 30
            d.set(clamped, forKey: Keys.interval)
            // Re-arm the warning notification whenever the interval changes.
            if isEnabled { Self.scheduleWarningNotification(interval: clamped, lastUnlock: lastUnlock) }
        }
    }

    @Published private(set) var lastUnlock: Date

    private let d = UserDefaults.standard
    private enum Keys {
        static let enabled = "deadmanwipe.v1.enabled"
        static let interval = "deadmanwipe.v1.intervalDays"
        static let lastUnlock = "deadmanwipe.v1.lastUnlock"
    }

    private init() {
        self.isEnabled = d.bool(forKey: Keys.enabled)
        let stored = d.integer(forKey: Keys.interval)
        self.intervalDays = Self.allowedIntervals.contains(stored) ? stored : 30
        let ts = d.object(forKey: Keys.lastUnlock) as? Double
        self.lastUnlock = ts.flatMap { Date(timeIntervalSince1970: $0) } ?? Date()
    }

    // MARK: - Timer

    /// Called by `AppLockGate` after a real (non-duress) unlock succeeds. Also used by the
    /// setup screen's "Reset timer" button.
    func markUnlockNow() {
        let now = Date()
        lastUnlock = now
        d.set(now.timeIntervalSince1970, forKey: Keys.lastUnlock)
        if isEnabled {
            Self.scheduleWarningNotification(interval: intervalDays, lastUnlock: now)
        }
    }

    /// Date the wipe will fire if the user does not unlock before then.
    var wipeDeadline: Date {
        Calendar.current.date(byAdding: .day, value: intervalDays, to: lastUnlock) ?? lastUnlock
    }

    /// True if the dead-man condition is already satisfied — the launch flow should wipe.
    func shouldWipeNow(now: Date = Date()) -> Bool {
        guard isEnabled else { return false }
        return now >= wipeDeadline
    }

    // MARK: - Enable / disable

    func enable(intervalDays: Int) {
        self.intervalDays = intervalDays   // triggers scheduling via didSet
        self.isEnabled = true
        markUnlockNow()
    }

    func disable() {
        self.isEnabled = false
        Self.cancelWarningNotification()
    }

    // MARK: - Local notification (24h before wipe)

    private static let warningId = "deadmanwipe.warning.v1"

    static func scheduleWarningNotification(interval: Int, lastUnlock: Date) {
        cancelWarningNotification()
        let center = UNUserNotificationCenter.current()
        center.requestAuthorization(options: [.alert, .sound]) { granted, _ in
            guard granted else { return }
            let deadline = Calendar.current.date(byAdding: .day, value: interval, to: lastUnlock) ?? lastUnlock
            // Fire 24h before the deadline — but only if that fires in the future.
            let fireAt = Calendar.current.date(byAdding: .hour, value: -24, to: deadline) ?? deadline
            guard fireAt > Date() else { return }

            let content = UNMutableNotificationContent()
            content.title = "Dead-Man Wipe in 24h"
            content.body = "Unlock OnlyTrusted to reset the timer. Otherwise all contacts, blocked numbers, events, and settings will be wiped tomorrow."
            content.sound = .default

            let triggerDate = Calendar.current.dateComponents(
                [.year, .month, .day, .hour, .minute], from: fireAt
            )
            let trigger = UNCalendarNotificationTrigger(dateMatching: triggerDate, repeats: false)
            let req = UNNotificationRequest(identifier: warningId, content: content, trigger: trigger)
            center.add(req, withCompletionHandler: nil)
        }
    }

    static func cancelWarningNotification() {
        UNUserNotificationCenter.current().removePendingNotificationRequests(withIdentifiers: [warningId])
    }

    // MARK: - Wipe

    /// Full-state wipe — mirrors the Settings "Wipe everything" action.
    ///
    /// IMPORTANT: Call on launch *before* the UI renders if `shouldWipeNow()` is true.
    func performWipe() {
        try? VaultStore.shared.wipe()
        try? BlockListStore.shared.wipe()
        EventStore.shared.wipe()
        EventStore.shared.append(type: SecurityEventRecord.Kind.wipe, detail: "dead-man wipe (\(intervalDays)d)")

        // Reset preference objects to defaults by removing all app UserDefaults keys. This
        // clears NotificationPreferences, BlockingPreferences, SubscriptionState, DuressState,
        // SIMSwapState, AutoLockPrefs, and the dead-man wipe prefs themselves.
        if let bundleId = Bundle.main.bundleIdentifier {
            UserDefaults.standard.removePersistentDomain(forName: bundleId)
        }
        // Also clear duress keychain / local caches held by in-memory singletons.
        DuressState.shared.clearPin()
        Self.cancelWarningNotification()
    }
}
