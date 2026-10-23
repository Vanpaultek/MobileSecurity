// SPDX-FileCopyrightText: 2026 VanPaulTek Inc
// SPDX-License-Identifier: AGPL-3.0-or-later
import SwiftUI

@main
struct OnlyTrustedApp: App {
    @StateObject private var gate = AppGate()
    @StateObject private var simswap = SIMSwapState.shared
    @StateObject private var geofenceAlerts = GeofenceAlertCoordinator.shared
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
            // Geo-fence Auto-Lock (Elite). Re-register the user's trusted regions with
            // CoreLocation on every launch. If `isEnabled` is false the manager is a no-op.
            if GeofenceState.shared.isEnabled {
                GeofenceManager.shared.startMonitoring()
            }
            // Rogue Network Scanner (Elite). Snapshot the radio + Wi-Fi state once at
            // launch so a 2G downgrade or lookalike SSID gets flagged even before the
            // user visits the dedicated screen.
            RogueNetworkMonitor.scanOnce()
            // App Shield (Elite, Phase 2). Reload the persisted FamilyControls selection +
            // authorization snapshot so the Settings / dashboard rows reflect reality on
            // launch. On simulator / un-approved builds this is a safe no-op.
            AppShieldManager.rehydrate()
        }
        // Smart Link Guard ↔ Offline Threat Intel bridge. Lets the SmartLinkGuardRules
        // engine consult the bundled APWG phishing-URL set when inspecting URLs from
        // the main app target (the share extension stays on the built-in rules).
        SmartLinkGuardRules.phishingHostLookup = { host in
            ThreatIntelDatabase.shared.isPhishingHost(host)
        }
    }

    var body: some Scene {
        WindowGroup {
            RootView()
                .environmentObject(gate)
                .environmentObject(simswap)
                .environmentObject(geofenceAlerts)
                .onAppear {
                    // Geo-fence Auto-Lock (Elite): the `GeofenceManager` runs outside the
                    // SwiftUI graph (CoreLocation delegate callbacks come in from a
                    // non-UI thread) so we expose the live `AppGate` through a bridge that
                    // lets the manager fire `panic()` on exit.
                    AppLockBridge.shared.register(gate: gate)
                }
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
                // Auto Privacy Shield — stop every live camera/mic session OnlyTrusted
                // controls. The registry respects the user's `autoStopEnabled` pref.
                _ = CaptureRegistry.shared.stopAll(reason: .background)
            case .inactive:
                // `.inactive` also fires for the lock-screen auth sheet + control-center
                // pull-down; the brief wants capture stopped on both lock + background so
                // we trigger here too. The registry is idempotent: a second call with
                // no live handles is a no-op and does not pollute the history.
                _ = CaptureRegistry.shared.stopAll(reason: .background)
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
    @EnvironmentObject private var geofenceAlerts: GeofenceAlertCoordinator

    var body: some View {
        ZStack {
            Group {
                if !gate.splashDone {
                    SplashScreen { gate.markSplashDone() }
                } else if !gate.onboardingComplete {
                    OnboardingScreen { gate.markOnboardingComplete() }
                } else if !gate.consentComplete {
                    ConsentScreen { gate.markConsentComplete() }
                } else if !gate.profileSetupComplete {
                    ProfileSetupScreen { gate.markProfileSetupComplete() }
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
        // Geo-fence Auto-Lock (Elite) SMS composer. We present it from the root so it
        // overlays whichever screen the user ends up on (post-lock the user re-authenticates
        // and lands on their last tab — the composer stays queued until that happens).
        .sheet(item: $geofenceAlerts.pendingAlert) { alert in
            if GeofenceSMSComposer.canSendText {
                GeofenceSMSComposer(
                    recipients: [alert.recipient],
                    body: alert.body,
                    onFinish: { _ in geofenceAlerts.dismiss() }
                )
            } else {
                VStack(spacing: 16) {
                    Text("This device cannot send SMS.")
                        .font(.body)
                    Button("Close") { geofenceAlerts.dismiss() }
                        .buttonStyle(.borderedProminent)
                        .tint(Color.appAccent)
                }
                .padding(24)
            }
        }
    }
}
