// SPDX-FileCopyrightText: 2026 VanPaulTek Inc
// SPDX-License-Identifier: AGPL-3.0-or-later
import SwiftUI
import LocalAuthentication

/// Elite-tier UI for Panic Lockdown Mode. Companion to `BorderModeSetupScreen`, but tuned
/// for RIGHT-NOW emergencies rather than Border Mode's planned, triple-confirmation preset.
///
/// Layout:
///   • Big red "ACTIVATE PANIC LOCKDOWN NOW" button at the top (single tap, no multi-step
///     confirmation — this is explicitly a fast-trigger).
///   • Status line: "ARMED since …" / "INACTIVE".
///   • Explainer card with the 6-point list of what activation does.
///   • Compare-to-Border-Mode card so the owner picks the right tool.
///   • When armed, a red DISARM button that gates on biometric + PIN double-factor (and the
///     Elite hardware key tap when one is enrolled).
///   • Short legal footnote pointing at Settings → Legal → "Panic Lockdown & law".
struct PanicLockdownScreen: View {
    @EnvironmentObject private var gate: AppGate
    @StateObject private var state = PanicLockdownState.shared
    @StateObject private var duress = DuressState.shared
    @Environment(\.dismiss) private var dismiss

    @State private var showActivateConfirm = false
    @State private var activationError: String?
    @State private var showDisarmPin = false
    @State private var pinInput: String = ""
    @State private var pinError: String?
    @State private var disarmError: String?

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 20) {
                headerBanner
                activateButton
                statusCard
                explainerCard
                compareCard
                legalFootnote
            }
            .padding(20)
        }
        .appBackdrop()
        .navigationTitle("Panic Lockdown")
        .navigationBarTitleDisplayMode(.inline)
        .confirmationDialog(
            "Activate Panic Lockdown now?",
            isPresented: $showActivateConfirm,
            titleVisibility: .visible
        ) {
            Button("Activate", role: .destructive) { performActivate() }
            Button("Cancel", role: .cancel) {}
        } message: {
            Text("The app will lock immediately. Your next unlock will drop into the decoy vault and send a silent SOS, regardless of which PIN you enter. Disarm requires biometric + PIN.")
        }
        .sheet(isPresented: $showDisarmPin) { disarmPinSheet }
        .alert("Couldn't activate", isPresented: Binding(
            get: { activationError != nil },
            set: { if !$0 { activationError = nil } }
        )) {
            Button("OK", role: .cancel) {}
        } message: {
            Text(activationError ?? "")
        }
        .alert("Couldn't disarm", isPresented: Binding(
            get: { disarmError != nil },
            set: { if !$0 { disarmError = nil } }
        )) {
            Button("OK", role: .cancel) {}
        } message: {
            Text(disarmError ?? "")
        }
    }

    // MARK: - Header + Activate button

    private var headerBanner: some View {
        HStack(spacing: 14) {
            Image(systemName: "exclamationmark.octagon.fill")
                .font(.system(size: 34, weight: .bold))
                .foregroundStyle(.white)
                .padding(14)
                .background(
                    LinearGradient(
                        colors: [Color.appDanger, Color.appDanger.opacity(0.6)],
                        startPoint: .topLeading, endPoint: .bottomTrailing
                    ),
                    in: RoundedRectangle(cornerRadius: 16, style: .continuous)
                )
            VStack(alignment: .leading, spacing: 4) {
                Text("Panic Lockdown")
                    .font(.title2.weight(.bold))
                    .foregroundStyle(Color.appPrimaryText)
                Text(state.isArmed ? "ARMED" : "Elite · right-now emergency")
                    .font(.footnote.weight(.semibold))
                    .foregroundStyle(state.isArmed ? Color.appDanger : Color.appSecondaryText)
            }
            Spacer()
        }
    }

    @ViewBuilder
    private var activateButton: some View {
        if state.isArmed {
            Button {
                beginDisarm()
            } label: {
                Label("DISARM", systemImage: "lock.open.fill")
                    .font(.title3.weight(.heavy))
                    .frame(maxWidth: .infinity, minHeight: 60)
            }
            .buttonStyle(.borderedProminent)
            .tint(Color.appDanger)
        } else {
            Button {
                showActivateConfirm = true
            } label: {
                Label("ACTIVATE PANIC LOCKDOWN NOW", systemImage: "exclamationmark.octagon.fill")
                    .font(.title3.weight(.heavy))
                    .frame(maxWidth: .infinity, minHeight: 60)
            }
            .buttonStyle(.borderedProminent)
            .tint(Color.appDanger)
        }
    }

    // MARK: - Status card

    private var statusCard: some View {
        PremiumCard {
            HStack(spacing: 12) {
                Image(systemName: state.isArmed ? "exclamationmark.triangle.fill" : "checkmark.shield")
                    .font(.title2)
                    .foregroundStyle(state.isArmed ? Color.appDanger : Color.appSuccess)
                VStack(alignment: .leading, spacing: 2) {
                    Text(state.isArmed
                         ? "Panic Lockdown is ARMED since \(armedAtString)"
                         : "Panic Lockdown is INACTIVE")
                        .font(.subheadline.weight(.semibold))
                        .foregroundStyle(Color.appPrimaryText)
                        .fixedSize(horizontal: false, vertical: true)
                    Text(state.isArmed
                         ? "Next unlock drops into the decoy vault and sends a silent SOS."
                         : "Tap the red button above to arm instantly.")
                        .font(.caption)
                        .foregroundStyle(Color.appSecondaryText)
                        .fixedSize(horizontal: false, vertical: true)
                }
                Spacer(minLength: 0)
            }
        }
    }

    private var armedAtString: String {
        guard let t = state.armedAt else { return "now" }
        let f = DateFormatter()
        f.dateStyle = .short
        f.timeStyle = .short
        return f.string(from: t)
    }

    // MARK: - Explainer card

    private var explainerCard: some View {
        PremiumCard {
            VStack(alignment: .leading, spacing: 10) {
                CardHeader(icon: "exclamationmark.octagon.fill",
                           title: "What activation does")
                Text("One tap. Six emergency responses fire at once:")
                    .font(.footnote)
                    .foregroundStyle(Color.appSecondaryText)
                VStack(alignment: .leading, spacing: 8) {
                    checklistRow("1", "Locks the app immediately — you re-enter via the lock screen.")
                    checklistRow("2", "Arms Duress Mode. The very next unlock drops into the decoy vault and sends the silent SOS — real PIN or duress PIN, same outcome.")
                    checklistRow("3", "Forces Sensor Monitor to its most aggressive posture.")
                    checklistRow("4", "Disables the Smart Link Guard share target so nothing leaves the app accidentally.")
                    checklistRow("5", "Disables biometric unlock. PIN-only (Fifth-Amendment protection in US).")
                    checklistRow("6", "Logs a `PANIC_LOCKDOWN_ACTIVATED` event with a timestamp.")
                }
            }
        }
    }

    // MARK: - Compare-to-Border-Mode

    private var compareCard: some View {
        PremiumCard {
            VStack(alignment: .leading, spacing: 10) {
                CardHeader(icon: "scalemass",
                           title: "Panic Lockdown vs. Border Mode")
                Text("Both are Elite-tier emergency presets, but they serve different moments:")
                    .font(.footnote)
                    .foregroundStyle(Color.appSecondaryText)
                    .fixedSize(horizontal: false, vertical: true)
                VStack(alignment: .leading, spacing: 6) {
                    Text("• Border Mode — planned risky situations (airport, protest, border crossing). Multi-step triple-confirmation. Stacks nine defensive layers at once.")
                        .font(.footnote)
                        .foregroundStyle(Color.appPrimaryText)
                        .fixedSize(horizontal: false, vertical: true)
                    Text("• Panic Lockdown — the RIGHT-NOW emergency. Single tap. Instant lock + duress arming + sensor flag storm. Use when you can't safely explain what you're doing on the screen.")
                        .font(.footnote)
                        .foregroundStyle(Color.appPrimaryText)
                        .fixedSize(horizontal: false, vertical: true)
                }
            }
        }
    }

    private var legalFootnote: some View {
        Text("Panic Lockdown is a defensive tool. It must not be used to obstruct lawful authority. Read Settings → Legal → \"Panic Lockdown & law\" before you rely on it.")
            .font(.caption)
            .foregroundStyle(Color.appTertiaryText)
            .fixedSize(horizontal: false, vertical: true)
            .padding(.top, 4)
    }

    // MARK: - Activate flow

    private func performActivate() {
        guard SubscriptionState.shared.hasElite else {
            activationError = "Panic Lockdown is an Elite feature."
            return
        }
        state.activate()
        // Lock the app immediately — step 1 of the brief. AppGate handles the lock-screen
        // redraw; the owner re-enters via the standard unlock flow.
        gate.panic()
        dismiss()
    }

    // MARK: - Disarm (biometric + PIN double-factor + optional hardware key)

    private func beginDisarm() {
        let ctx = LAContext()
        var err: NSError?
        guard ctx.canEvaluatePolicy(.deviceOwnerAuthentication, error: &err) else {
            // No biometric or passcode set — fall back to PIN only + hardware key.
            promptDisarmPin()
            return
        }
        ctx.evaluatePolicy(
            .deviceOwnerAuthentication,
            localizedReason: "Disarm Panic Lockdown"
        ) { ok, _ in
            Task { @MainActor in
                if ok {
                    promptDisarmPin()
                } else {
                    disarmError = "Biometric check failed. Panic Lockdown stays armed."
                }
            }
        }
    }

    private func promptDisarmPin() {
        pinInput = ""; pinError = nil
        showDisarmPin = true
    }

    private var disarmPinSheet: some View {
        NavigationStack {
            VStack(spacing: 16) {
                Text("Enter PIN to disarm")
                    .font(.title3.weight(.bold))
                    .foregroundStyle(Color.appPrimaryText)
                Text("Panic Lockdown requires biometric AND PIN before it will stand down. If a hardware security key is enrolled, you'll also need to tap it.")
                    .font(.footnote)
                    .foregroundStyle(Color.appSecondaryText)
                    .multilineTextAlignment(.center)
                    .padding(.horizontal, 20)
                SecureField("PIN", text: $pinInput)
                    .keyboardType(.numberPad)
                    .textContentType(.oneTimeCode)
                    .multilineTextAlignment(.center)
                    .padding(16)
                    .background(RoundedRectangle(cornerRadius: 10).fill(Color.appElevatedSurface))
                    .padding(.horizontal, 24)
                if let pinError {
                    Text(pinError).font(.footnote).foregroundStyle(Color.appDanger)
                }
                Button {
                    submitPinAndDisarm()
                } label: {
                    Text("Disarm")
                        .font(.body.weight(.semibold))
                        .frame(maxWidth: .infinity, minHeight: 44)
                }
                .buttonStyle(.borderedProminent)
                .tint(Color.appAccent)
                .padding(.horizontal, 24)
                Spacer()
            }
            .padding(.top, 20)
            .appBackdrop()
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Button("Cancel") {
                        pinInput = ""; pinError = nil
                        showDisarmPin = false
                    }
                }
            }
        }
    }

    private func submitPinAndDisarm() {
        // Reuse the Duress PIN check — same reasoning as Border Mode: it is the only PIN this
        // app owns, so it doubles as proof of knowledge.
        if !duress.matches(pin: pinInput) {
            pinError = "Incorrect PIN."
            return
        }
        pinInput = ""; pinError = nil
        showDisarmPin = false

        // Final gate: hardware key tap when one is enrolled. If none is enrolled this is a
        // no-op (completion fires `.success` immediately).
        HardwareKeyGate.requireAssertion(
            reason: "Confirm disarm with your security key."
        ) { outcome in
            Task { @MainActor in
                switch outcome {
                case .success:
                    state.disarm()
                case .cancelled:
                    disarmError = "Disarm cancelled — security key tap missing."
                case .failed(let msg):
                    disarmError = "Hardware key required: \(msg)"
                }
            }
        }
    }

    private func checklistRow(_ num: String, _ text: String) -> some View {
        HStack(alignment: .top, spacing: 10) {
            Text(num)
                .font(.caption.weight(.bold))
                .foregroundStyle(Color.appDanger)
                .frame(width: 20)
            Text(text)
                .font(.footnote)
                .foregroundStyle(Color.appPrimaryText)
                .fixedSize(horizontal: false, vertical: true)
            Spacer(minLength: 0)
        }
    }
}
