// SPDX-FileCopyrightText: 2026 VanPaulTek Inc
// SPDX-License-Identifier: AGPL-3.0-or-later
import SwiftUI

@main
struct OnlyTrustedApp: App {
    @StateObject private var gate = AppGate()
    @StateObject private var simswap = SIMSwapState.shared
    @Environment(\.scenePhase) private var scenePhase

    init() {
        // Dead-Man Wipe (Elite): check the inactivity deadline before any UI renders. If the
        // window has already elapsed, wipe everything first — this makes the trip-wire fire
        // reliably even if the user keeps re-opening the app without unlocking it.
        // `performWipe()` is MainActor-isolated and so is the shared instance; dispatch
        // synchronously on the main queue (SwiftUI App init runs there anyway).
        MainActor.assumeIsolated {
            let state = DeadmanWipeState.shared
            if state.shouldWipeNow() {
                state.performWipe()
            }
        }
    }

    var body: some Scene {
        WindowGroup {
            RootView()
                .environmentObject(gate)
                .environmentObject(simswap)
                // Midnight dark is OnlyTrusted's primary identity. We force the dark scheme
                // here so every user sees the brand palette on first open; the matched light
                // scheme (defined in Color tokens) remains available for future opt-in but is
                // not currently exposed to users. Change this to .none to let users switch.
                .preferredColorScheme(.dark)
        }
        .onChange(of: scenePhase) { _, newPhase in
            // F2 — Scheduled auto-lock. Note the background timestamp when we move away,
            // then on return compute elapsed time against the user-chosen window and
            // re-arm the lock gate if the threshold was crossed.
            switch newPhase {
            case .background:
                gate.noteDidEnterBackground()
            case .active:
                gate.maybeAutoLock()
                // SIM-Swap Alarm — re-check the carrier identity against the baseline
                // every time the app returns to the foreground. Fires `pendingAlarm` on
                // the state object which the RootView overlays over the main UI.
                simswap.checkOnForeground()
            default:
                break
            }
        }
    }
}

struct RootView: View {
    @EnvironmentObject private var gate: AppGate
    @EnvironmentObject private var simswap: SIMSwapState

    var body: some View {
        ZStack {
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
                        .onAppear { simswap.checkOnForeground() }
                }
            }
            .animation(.easeInOut(duration: 0.25), value: gate.splashDone)
            .animation(.easeInOut(duration: 0.2), value: gate.isLocked)
            .animation(.easeInOut(duration: 0.2), value: gate.onboardingComplete)
            .animation(.easeInOut(duration: 0.2), value: gate.consentComplete)

            // SIM-Swap Alarm — full-screen overlay above the entire app surface. We render
            // it here (not inside MainTabs) so it also appears if a mismatch is detected
            // during the splash/lock phase and the user later unlocks.
            if let alarm = simswap.pendingAlarm,
               gate.splashDone, gate.onboardingComplete, gate.consentComplete, !gate.isLocked {
                SIMSwapAlarmSheet(
                    alarm: alarm,
                    onLock: {
                        simswap.dismissAlarm()
                        gate.panic()
                    },
                    onAcknowledge: {
                        simswap.acknowledgeAndRebaseline()
                    }
                )
                .transition(.opacity)
                .zIndex(10)
            }
        }
        .animation(.easeInOut(duration: 0.2), value: simswap.pendingAlarm)
    }
}
