// SPDX-FileCopyrightText: 2026 57Million LLC
// SPDX-License-Identifier: AGPL-3.0-or-later
import SwiftUI

@main
struct OnlyTrustedApp: App {
    @StateObject private var gate = AppGate()

    var body: some Scene {
        WindowGroup {
            RootView()
                .environmentObject(gate)
                // Midnight dark is OnlyTrusted's primary identity. We force the dark scheme
                // here so every user sees the brand palette on first open; the matched light
                // scheme (defined in Color tokens) remains available for future opt-in but is
                // not currently exposed to users. Change this to .none to let users switch.
                .preferredColorScheme(.dark)
        }
    }
}

struct RootView: View {
    @EnvironmentObject private var gate: AppGate

    var body: some View {
        Group {
            if !gate.splashDone {
                SplashScreen { gate.markSplashDone() }
            } else if !gate.onboardingComplete {
                OnboardingScreen { gate.markOnboardingComplete() }
            } else if !gate.consentComplete {
                ConsentScreen { gate.markConsentComplete() }
            } else if gate.isLocked {
                AppLockGate(onUnlocked: { gate.markUnlocked() })
            } else {
                MainTabs()
            }
        }
        .animation(.easeInOut(duration: 0.25), value: gate.splashDone)
        .animation(.easeInOut(duration: 0.2), value: gate.isLocked)
        .animation(.easeInOut(duration: 0.2), value: gate.onboardingComplete)
        .animation(.easeInOut(duration: 0.2), value: gate.consentComplete)
    }
}
