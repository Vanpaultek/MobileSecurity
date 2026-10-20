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
                .preferredColorScheme(.none)
        }
    }
}

struct RootView: View {
    @EnvironmentObject private var gate: AppGate

    var body: some View {
        Group {
            if !gate.onboardingComplete {
                OnboardingScreen { gate.markOnboardingComplete() }
            } else if gate.isLocked {
                AppLockGate(onUnlocked: { gate.markUnlocked() })
            } else {
                MainTabs()
            }
        }
        .animation(.easeInOut(duration: 0.2), value: gate.isLocked)
        .animation(.easeInOut(duration: 0.2), value: gate.onboardingComplete)
    }
}
