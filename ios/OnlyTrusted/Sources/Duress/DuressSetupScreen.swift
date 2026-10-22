// SPDX-FileCopyrightText: 2026 VanPaulTek Inc
// SPDX-License-Identifier: AGPL-3.0-or-later
import SwiftUI

/// Multi-step Duress Mode setup. Reached from Settings → Security & data → "Duress Mode (Pro)".
/// Gated on `SubscriptionState.hasHighPremium`; the gating itself is handled by the entry row
/// so this screen can assume the user is on Pro.
///
/// Steps:
///  1. PIN entry + confirm (4–6 digits, must differ from any app PIN we hashed).
///  2. Pick an alert contact from the real vault.
///  3. Optional edit of three placeholder decoy contacts (Mom / Work / Bank).
///  4. Toggle to activate.
struct DuressSetupScreen: View {
    @Environment(\.dismiss) private var dismiss
    @StateObject private var state = DuressState.shared
    @StateObject private var vault = VaultStore.shared

    @State private var step: Int = 1
    @State private var pin: String = ""
    @State private var pinConfirm: String = ""
    @State private var pinError: String?
    @State private var selectedAlertId: UUID?
    @State private var decoy: [DecoyRow] = DecoyRow.defaults
    @State private var successMessage: String?
    // Hardware-key gate feedback shown when the user tries to disarm but cancels or fails
    // the FIDO2 tap. See `HardwareKeyGate.requireAssertion` wired below.
    @State private var hardwareKeyError: String?

    private var realContacts: [MaskedContact] {
        vault.realContactsForSetup()
    }

    var body: some View {
        ScrollView(.vertical, showsIndicators: false) {
            VStack(alignment: .leading, spacing: 20) {
                headerCard
                switch step {
                case 1: pinCard
                case 2: pickAlertCard
                case 3: decoyCard
                default: finalCard
                }
                limitationsNote
            }
            .padding(.horizontal, 18)
            .padding(.top, 10)
            .padding(.bottom, 32)
        }
        .appBackdrop()
        .navigationTitle("Duress Mode")
        .navigationBarTitleDisplayMode(.inline)
        .onAppear {
            selectedAlertId = state.alertContactId
            if state.ownerName.isEmpty, let owner = realContacts.first(where: { $0.isOwner }) {
                state.ownerName = owner.displayName
            }
        }
        .alert("Duress Mode ready", isPresented: Binding(
            get: { successMessage != nil },
            set: { if !$0 { successMessage = nil; dismiss() } }
        )) {
            Button("Done") { successMessage = nil; dismiss() }
        } message: {
            Text(successMessage ?? "")
        }
        .alert("Hardware key required", isPresented: Binding(
            get: { hardwareKeyError != nil },
            set: { if !$0 { hardwareKeyError = nil } }
        )) {
            Button("OK", role: .cancel) {}
        } message: {
            Text(hardwareKeyError ?? "")
        }
    }

    // MARK: - Header

    private var headerCard: some View {
        PremiumCard {
            VStack(alignment: .leading, spacing: 10) {
                HStack(spacing: 10) {
                    Image(systemName: "exclamationmark.shield.fill")
                        .font(.title3)
                        .foregroundStyle(Color.appAccent)
                    Text("Step \(step) of 4")
                        .font(.subheadline.weight(.semibold))
                        .foregroundStyle(Color.appBrightCyan)
                }
                Text(stepTitle)
                    .font(.title3.weight(.bold))
                    .foregroundStyle(Color.appPrimaryText)
                Text(stepHint)
                    .font(.footnote)
                    .foregroundStyle(Color.appSecondaryText)
                    .fixedSize(horizontal: false, vertical: true)
            }
        }
    }

    private var stepTitle: String {
        switch step {
        case 1: return "Set your duress PIN"
        case 2: return "Pick a duress alert contact"
        case 3: return "Decoy vault contents"
        default: return "Enable Duress Mode"
        }
    }

    private var stepHint: String {
        switch step {
        case 1: return "4–6 digits. Keep it different from your device passcode so you can type it under coercion without triggering muscle-memory."
        case 2: return "They receive the SOS when the duress PIN unlocks. Pick someone who always answers."
        case 3: return "What a coercer sees. Edit any field to make it believable."
        default: return "Everything is stored on this device only. Toggle on to arm."
        }
    }

    // MARK: - Step 1

    private var pinCard: some View {
        PremiumCard {
            VStack(alignment: .leading, spacing: 12) {
                SecureField("Duress PIN (4–6 digits)", text: $pin)
                    .keyboardType(.numberPad)
                    .textContentType(.oneTimeCode)
                    .padding(14)
                    .background(RoundedRectangle(cornerRadius: 10).fill(Color.appElevatedSurface))
                SecureField("Confirm PIN", text: $pinConfirm)
                    .keyboardType(.numberPad)
                    .textContentType(.oneTimeCode)
                    .padding(14)
                    .background(RoundedRectangle(cornerRadius: 10).fill(Color.appElevatedSurface))
                if let pinError {
                    Text(pinError)
                        .font(.footnote)
                        .foregroundStyle(Color.appDanger)
                }
                Button(action: savePinAndAdvance) {
                    Text("Continue")
                        .font(.body.weight(.semibold))
                        .frame(maxWidth: .infinity, minHeight: 44)
                }
                .buttonStyle(.borderedProminent)
                .tint(Color.appAccent)
            }
        }
    }

    private func savePinAndAdvance() {
        pinError = nil
        let digits = pin.allSatisfy(\.isNumber)
        guard digits, (4...6).contains(pin.count) else {
            pinError = "PIN must be 4–6 digits."
            return
        }
        guard pin == pinConfirm else {
            pinError = "PINs do not match."
            return
        }
        if state.collidesWithRealPin(pin) {
            pinError = "This matches your real app PIN. Pick something different."
            return
        }
        state.setDuressPin(pin)
        step = 2
    }

    // MARK: - Step 2

    private var pickAlertCard: some View {
        PremiumCard {
            VStack(alignment: .leading, spacing: 8) {
                if realContacts.isEmpty {
                    Text("You have no trusted contacts yet. Add at least one on the Contacts tab, then come back.")
                        .font(.footnote)
                        .foregroundStyle(Color.appSecondaryText)
                } else {
                    ForEach(realContacts) { contact in
                        Button {
                            selectedAlertId = contact.id
                            state.alertContactId = contact.id
                        } label: {
                            HStack(spacing: 12) {
                                Image(systemName: selectedAlertId == contact.id ? "largecircle.fill.circle" : "circle")
                                    .foregroundStyle(selectedAlertId == contact.id ? Color.appAccent : Color.appTertiaryText)
                                VStack(alignment: .leading, spacing: 2) {
                                    Text(contact.displayName)
                                        .font(.subheadline.weight(.semibold))
                                        .foregroundStyle(Color.appPrimaryText)
                                    if let first = contact.maskedNumbers.first {
                                        Text(first)
                                            .font(.caption)
                                            .foregroundStyle(Color.appSecondaryText)
                                    }
                                }
                                Spacer()
                            }
                            .padding(.vertical, 6)
                        }
                        .buttonStyle(.plain)
                    }
                }
                HStack {
                    Button("Back") { step = 1 }
                        .buttonStyle(.bordered)
                    Spacer()
                    Button("Continue") { step = 3 }
                        .buttonStyle(.borderedProminent)
                        .tint(Color.appAccent)
                        .disabled(selectedAlertId == nil)
                }
                .padding(.top, 8)
            }
        }
    }

    // MARK: - Step 3

    private var decoyCard: some View {
        PremiumCard {
            VStack(alignment: .leading, spacing: 12) {
                ForEach($decoy) { $row in
                    VStack(alignment: .leading, spacing: 6) {
                        TextField("Name", text: $row.name)
                            .textFieldStyle(.roundedBorder)
                        TextField("Number (optional)", text: $row.number)
                            .keyboardType(.phonePad)
                            .textFieldStyle(.roundedBorder)
                        Divider().background(Color.appDivider.opacity(0.3))
                    }
                }
                HStack {
                    Button("Back") { step = 2 }
                        .buttonStyle(.bordered)
                    Spacer()
                    Button("Continue") { step = 4 }
                        .buttonStyle(.borderedProminent)
                        .tint(Color.appAccent)
                }
                .padding(.top, 4)
            }
        }
    }

    // MARK: - Step 4

    private var finalCard: some View {
        PremiumCard {
            VStack(alignment: .leading, spacing: 14) {
                Toggle(isOn: Binding(
                    get: { state.isEnabled },
                    set: { enable in
                        if enable {
                            armWithDecoy()
                        } else {
                            // Elite hardware-key gate: when at least one key is enrolled,
                            // disarming Duress Mode requires a FIDO2 tap. No key enrolled =>
                            // the gate is a no-op and this path matches the pre-Elite behavior.
                            HardwareKeyGate.requireAssertion(
                                reason: "Confirm Duress disarm with your security key."
                            ) { outcome in
                                switch outcome {
                                case .success:
                                    state.isEnabled = false
                                case .cancelled:
                                    hardwareKeyError = "Disarm cancelled — security key tap missing."
                                case .failed(let msg):
                                    hardwareKeyError = "Hardware key required: \(msg)"
                                }
                            }
                        }
                    }
                )) {
                    VStack(alignment: .leading, spacing: 2) {
                        Text("Duress Mode enabled")
                            .font(.subheadline.weight(.semibold))
                            .foregroundStyle(Color.appPrimaryText)
                        Text("Arms the duress PIN and the decoy vault.")
                            .font(.caption)
                            .foregroundStyle(Color.appSecondaryText)
                    }
                }
                .tint(Color.appAccent)

                TextField("Your name (used in the SOS text)",
                          text: $state.ownerName)
                    .textFieldStyle(.roundedBorder)

                HStack {
                    Button("Back") { step = 3 }
                        .buttonStyle(.bordered)
                    Spacer()
                    Button("Finish") {
                        if !state.isEnabled { armWithDecoy() }
                        successMessage = "Duress Mode is armed. Keep your PIN memorable but hidden."
                    }
                    .buttonStyle(.borderedProminent)
                    .tint(Color.appAccent)
                    .disabled(state.alertContactId == nil || state.ownerName.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
                }
            }
        }
    }

    private func armWithDecoy() {
        // Seed the decoy vault from the three placeholders the owner just typed.
        let drafts: [TrustedContactDraft] = decoy.compactMap { row in
            let name = row.name.trimmingCharacters(in: .whitespacesAndNewlines)
            guard !name.isEmpty else { return nil }
            return TrustedContactDraft(
                id: nil,
                displayName: name,
                relation: nil,
                isOwner: false,
                email: nil,
                address: nil,
                notes: nil,
                phoneNumbers: row.number.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
                    ? []
                    : [(row.number, nil)]
            )
        }
        try? DuressVaultStore.shared.seedPlaceholdersIfEmpty(drafts)
        state.isEnabled = true
    }

    // MARK: - Footer

    private var limitationsNote: some View {
        PremiumCard {
            VStack(alignment: .leading, spacing: 6) {
                HStack(spacing: 8) {
                    Image(systemName: "info.circle.fill").foregroundStyle(Color.appWarning)
                    Text("iOS limitation")
                        .font(.subheadline.weight(.semibold))
                        .foregroundStyle(Color.appPrimaryText)
                }
                Text("On iOS the SOS pre-fills the Messages app and asks you to tap Send. Apple has no API for truly silent SMS. If you need silent send, run OnlyTrusted on Android.")
                    .font(.caption)
                    .foregroundStyle(Color.appSecondaryText)
            }
        }
    }

    // MARK: - Decoy model

    struct DecoyRow: Identifiable, Equatable {
        let id = UUID()
        var name: String
        var number: String

        static let defaults: [DecoyRow] = [
            DecoyRow(name: "Mom", number: "+1 (555) 010-0100"),
            DecoyRow(name: "Work", number: "+1 (555) 010-0200"),
            DecoyRow(name: "Bank", number: "+1 (555) 010-0300")
        ]
    }
}
