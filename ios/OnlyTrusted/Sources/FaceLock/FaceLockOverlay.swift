// SPDX-FileCopyrightText: 2026 VanPaulTek Inc
// SPDX-License-Identifier: AGPL-3.0-or-later
import LocalAuthentication
import SwiftUI

/// Reusable modifier that attaches the Face-Change Shoulder-Surfing Lock monitor to any
/// view that renders sensitive content (vault screens, backup screens, etc.).
///
/// Behaviour:
///   * While the monitor is `.walkedAway`, overlay a translucent blur that still lets the
///     user see where they are — tapping anywhere resumes once the owner's face returns.
///   * While the monitor is `.lockStranger` or `.lockMultiple`, overlay a hard lock card
///     that requires a biometric / passcode tap to clear.
///
/// Elite-gated: if the subscription state does not include Elite or no face is enrolled,
/// the modifier is a no-op pass-through.
struct FaceLockShieldModifier: ViewModifier {
    @StateObject private var monitor = FaceLockMonitor.shared
    @StateObject private var state = FaceLockState.shared
    @StateObject private var sub = SubscriptionState.shared
    @Environment(\.scenePhase) private var scenePhase

    func body(content: Content) -> some View {
        content
            .overlay {
                if shouldApplyLock {
                    overlayView
                        .transition(.opacity)
                }
            }
            .animation(.easeInOut(duration: 0.2), value: monitor.decision)
            .onAppear { startIfEligible() }
            .onDisappear { monitor.stop() }
            .onChange(of: scenePhase) { _, phase in
                switch phase {
                case .active: startIfEligible()
                case .inactive, .background: monitor.stop()
                @unknown default: break
                }
            }
    }

    private var isEligible: Bool {
        sub.hasElite && state.isEnrolled && !FaceLockState.isRunningOnSimulator
    }

    private var shouldApplyLock: Bool {
        guard isEligible else { return false }
        switch monitor.decision {
        case .idle, .ok: return false
        case .walkedAway: return state.lockWhenOwnerLeaves && state.blurOnExit
        case .lockStranger, .lockMultiple: return state.lockWhenStrangerJoins
        }
    }

    private func startIfEligible() {
        guard isEligible else { return }
        monitor.start()
    }

    @ViewBuilder
    private var overlayView: some View {
        switch monitor.decision {
        case .walkedAway:
            softBlurOverlay
        case .lockStranger, .lockMultiple:
            hardLockOverlay
        case .idle, .ok:
            EmptyView()
        }
    }

    private var softBlurOverlay: some View {
        ZStack {
            Rectangle()
                .fill(.ultraThinMaterial)
                .ignoresSafeArea()
            VStack(spacing: 12) {
                Image(systemName: "eye.slash.fill")
                    .font(.system(size: 44, weight: .semibold))
                    .foregroundStyle(Color.appAccent)
                Text("Hidden while you're away")
                    .font(.headline.weight(.semibold))
                    .foregroundStyle(Color.appPrimaryText)
                Text("Look back at the camera to unblur.")
                    .font(.footnote)
                    .foregroundStyle(Color.appSecondaryText)
                    .multilineTextAlignment(.center)
                    .padding(.horizontal, 32)
            }
        }
        .accessibilityElement(children: .combine)
        .accessibilityLabel("Face Lock blur: content hidden until you look back at the camera.")
    }

    private var hardLockOverlay: some View {
        ZStack {
            Rectangle()
                .fill(Color.black.opacity(0.9))
                .ignoresSafeArea()
            VStack(spacing: 16) {
                Image(systemName: "lock.shield.fill")
                    .font(.system(size: 52, weight: .semibold))
                    .foregroundStyle(Color.appDanger)
                Text(headlineForDecision)
                    .font(.title3.weight(.bold))
                    .foregroundStyle(Color.appPrimaryText)
                Text(detailForDecision)
                    .font(.footnote)
                    .foregroundStyle(Color.appSecondaryText)
                    .multilineTextAlignment(.center)
                    .padding(.horizontal, 32)
                Button {
                    requestUnlock()
                } label: {
                    Text("Unlock with Face ID / passcode")
                        .font(.body.weight(.semibold))
                        .frame(maxWidth: .infinity, minHeight: 48)
                }
                .buttonStyle(.borderedProminent)
                .tint(Color.appAccent)
                .padding(.horizontal, 24)
            }
            .padding(.vertical, 40)
            .padding(.horizontal, 20)
        }
        .accessibilityAddTraits(.isModal)
    }

    private var headlineForDecision: String {
        switch monitor.decision {
        case .lockMultiple: return "Someone else is looking"
        case .lockStranger: return "Unknown face detected"
        default: return "Locked"
        }
    }

    private var detailForDecision: String {
        switch monitor.decision {
        case .lockMultiple:
            return "Two or more faces were visible to the front camera. Content is hidden until you confirm it's safe."
        case .lockStranger:
            return "The face in front of the camera doesn't match the enrolled owner."
        default:
            return "Content hidden."
        }
    }

    private func requestUnlock() {
        let ctx = LAContext()
        var err: NSError?
        guard ctx.canEvaluatePolicy(.deviceOwnerAuthentication, error: &err) else {
            // Fallback: no device auth available → clear anyway. Device auth gate is
            // elsewhere; this overlay is additive.
            monitor.acknowledgeUnlock()
            return
        }
        ctx.evaluatePolicy(.deviceOwnerAuthentication,
                           localizedReason: "Confirm it's you to re-reveal sensitive content.") { ok, _ in
            DispatchQueue.main.async {
                if ok { monitor.acknowledgeUnlock() }
            }
        }
    }
}

extension View {
    /// Attaches the Face-Change Shoulder-Surfing Lock monitor to a sensitive view. No-op on
    /// Simulator, when the user isn't on Elite, or when no face has been enrolled.
    func faceLockShield() -> some View {
        modifier(FaceLockShieldModifier())
    }
}
