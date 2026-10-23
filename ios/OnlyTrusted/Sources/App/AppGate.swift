// SPDX-FileCopyrightText: 2026 VanPaulTek Inc
// SPDX-License-Identifier: AGPL-3.0-or-later
import Foundation
import SwiftUI

/// Session-wide gatekeeper for onboarding + app lock. Owns the three states the root view
/// chooses between: not-onboarded, locked, or in-app.
@MainActor
final class AppGate: ObservableObject {
    @Published private(set) var splashDone: Bool = false
    @Published private(set) var onboardingComplete: Bool
    @Published private(set) var consentComplete: Bool
    @Published private(set) var profileSetupComplete: Bool
    @Published private(set) var isLocked: Bool

    private let onboardingKey = "onboarding.v1.accepted"
    private let consentKey = "consent.v1.accepted"
    private let appLockEnabledKey = "applock.enabled"

    init() {
        let defaults = UserDefaults.standard
        self.onboardingComplete = defaults.bool(forKey: onboardingKey)
        self.consentComplete = defaults.bool(forKey: consentKey)
        self.profileSetupComplete = ProfileStore.shared.isSetupComplete
        self.isLocked = defaults.bool(forKey: appLockEnabledKey)
    }

    /// Called by ProfileSetupScreen once the three-step flow (email → PIN → biometric)
    /// is complete. The flag itself is persisted inside ProfileStore; this just re-reads
    /// so the RootView can advance.
    func markProfileSetupComplete() {
        self.profileSetupComplete = ProfileStore.shared.isSetupComplete
    }

    func markSplashDone() { splashDone = true }

    func markOnboardingComplete() {
        UserDefaults.standard.set(true, forKey: onboardingKey)
        onboardingComplete = true
    }

    func markConsentComplete() {
        UserDefaults.standard.set(true, forKey: consentKey)
        consentComplete = true
    }

    func markUnlocked() {
        isLocked = false
    }

    /// Called by Panic Lock action from any screen.
    func panic() {
        isLocked = true
        EventStore.shared.append(type: SecurityEventRecord.Kind.panicLock)
    }

    var appLockEnabled: Bool {
        get { UserDefaults.standard.bool(forKey: appLockEnabledKey) }
        set {
            UserDefaults.standard.set(newValue, forKey: appLockEnabledKey)
            if !newValue { isLocked = false }
        }
    }

    /// Called from `OnlyTrustedApp` when the scene moves to the background. Records a
    /// timestamp so we can compute inactivity on return. No lock happens here.
    func noteDidEnterBackground() {
        AutoLockPrefs.markBackgroundedNow()
    }

    /// Called when the scene becomes active. If the configured auto-lock window has
    /// elapsed and App Lock is on, re-arm the lock screen. The user still has to
    /// authenticate through `AppLockGate` to unlock.
    func maybeAutoLock() {
        guard appLockEnabled else {
            AutoLockPrefs.clearBackgroundTimestamp()
            return
        }
        let seconds = AutoLockPrefs.currentSeconds
        guard seconds > 0 else {
            AutoLockPrefs.clearBackgroundTimestamp()
            return
        }
        guard let elapsed = AutoLockPrefs.elapsedSinceBackground() else { return }
        if elapsed >= TimeInterval(seconds) {
            isLocked = true
        }
        AutoLockPrefs.clearBackgroundTimestamp()
    }
}
