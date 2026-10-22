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
    @Published private(set) var isLocked: Bool

    private let onboardingKey = "onboarding.v1.accepted"
    private let consentKey = "consent.v1.accepted"
    private let appLockEnabledKey = "applock.enabled"

    init() {
        let defaults = UserDefaults.standard
        self.onboardingComplete = defaults.bool(forKey: onboardingKey)
        self.consentComplete = defaults.bool(forKey: consentKey)
        self.isLocked = defaults.bool(forKey: appLockEnabledKey)
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
}
