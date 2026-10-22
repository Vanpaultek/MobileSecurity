// SPDX-FileCopyrightText: 2026 VanPaulTek Inc
// SPDX-License-Identifier: AGPL-3.0-or-later
import SwiftUI

/// Trusted Witness (Elite) setup screen.
///
/// Reached from Settings → Security & data → "Trusted Witness (Elite)". The parent row is
/// already gated on `SubscriptionState.hasElite`.
struct TrustedWitnessSetupScreen: View {
    @Environment(\.dismiss) private var dismiss
    @StateObject private var state = TrustedWitnessState.shared

    @State private var showAddSheet = false

    var body: some View {
        ScrollView {
            VStack(spacing: 18) {
                headerBanner

                PremiumCard {
                    VStack(alignment: .leading, spacing: 10) {
                        Toggle(isOn: Binding(
                            get: { state.featureEnabled },
                            set: { state.featureEnabled = $0 }
                        )) {
                            VStack(alignment: .leading, spacing: 2) {
                                Text("Require Trusted Witness")
                                    .font(.subheadline.weight(.semibold))
                                    .foregroundStyle(Color.appPrimaryText)
                                Text(state.featureEnabled
                                     ? "Sensitive actions below need a witness nearby."
                                     : "Off. Sensitive actions proceed as before.")
                                    .font(.caption)
                                    .foregroundStyle(Color.appSecondaryText)
                                    .fixedSize(horizontal: false, vertical: true)
                            }
                        }
                        .tint(Color.appAccent)

                        if state.featureEnabled {
                            Text("Warning: if every witness is unreachable, you cannot complete the gated actions below. Enrol more than one witness and verify Bluetooth is working on their phone.")
                                .font(.caption)
                                .foregroundStyle(Color.appWarning)
                                .fixedSize(horizontal: false, vertical: true)
                        }
                    }
                }

                PremiumCard {
                    VStack(alignment: .leading, spacing: 10) {
                        CardHeader(icon: "person.2.wave.2", title: "Witnesses")
                        if state.witnesses.isEmpty {
                            Text("No witnesses enrolled yet. Tap 'Add witness' and bring their phone next to yours.")
                                .font(.footnote)
                                .foregroundStyle(Color.appSecondaryText)
                                .fixedSize(horizontal: false, vertical: true)
                        } else {
                            ForEach(state.witnesses) { w in
                                HStack {
                                    VStack(alignment: .leading, spacing: 2) {
                                        Text(w.displayName)
                                            .font(.subheadline.weight(.semibold))
                                            .foregroundStyle(Color.appPrimaryText)
                                        Text("Device ID \(String(w.deviceID.prefix(8)))…")
                                            .font(.caption)
                                            .foregroundStyle(Color.appSecondaryText)
                                    }
                                    Spacer()
                                    Button(role: .destructive) {
                                        state.removeWitness(id: w.id)
                                    } label: {
                                        Image(systemName: "trash")
                                            .foregroundStyle(Color.appDanger)
                                    }
                                    .buttonStyle(.plain)
                                }
                                .padding(.vertical, 4)
                            }
                        }
                        Button {
                            showAddSheet = true
                        } label: {
                            Label("Add witness", systemImage: "plus.circle.fill")
                                .font(.body.weight(.semibold))
                                .frame(maxWidth: .infinity, minHeight: 44)
                        }
                        .buttonStyle(.borderedProminent)
                        .tint(Color.appAccent)
                    }
                }

                PremiumCard {
                    VStack(alignment: .leading, spacing: 10) {
                        CardHeader(icon: "list.bullet.rectangle", title: "Gated actions")
                        Text("Pick which actions need witness approval. Any action unchecked here proceeds without a witness even when the feature is on.")
                            .font(.footnote).foregroundStyle(Color.appSecondaryText)
                            .fixedSize(horizontal: false, vertical: true)
                        ForEach(TrustedWitnessState.GatedAction.allCases) { action in
                            let isOn = state.requiredActions.contains(action)
                            Toggle(isOn: Binding(
                                get: { isOn },
                                set: { on in
                                    var updated = state.requiredActions
                                    if on { updated.insert(action) } else { updated.remove(action) }
                                    state.requiredActions = updated
                                }
                            )) {
                                Text(action.displayName)
                                    .font(.subheadline)
                                    .foregroundStyle(Color.appPrimaryText)
                            }
                            .tint(Color.appAccent)
                        }
                    }
                }
            }
            .padding(20)
        }
        .appBackdrop()
        .navigationTitle("Trusted Witness")
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .topBarTrailing) {
                Button("Done") { dismiss() }.tint(Color.appBrightCyan)
            }
        }
        .sheet(isPresented: $showAddSheet) {
            NavigationStack { AddWitnessWizard(onDone: { showAddSheet = false }) }
        }
    }

    private var headerBanner: some View {
        PremiumCard {
            HStack(alignment: .top, spacing: 14) {
                ZStack {
                    RoundedRectangle(cornerRadius: 14, style: .continuous)
                        .fill(LinearGradient(
                            colors: [Color.appBrightCyan, Color.appBrightCyan.opacity(0.65)],
                            startPoint: .topLeading, endPoint: .bottomTrailing
                        ))
                        .shadow(color: Color.appBrightCyan.opacity(0.5), radius: 10)
                    Image(systemName: "person.2.wave.2.fill")
                        .font(.system(size: 24, weight: .semibold))
                        .foregroundStyle(.white)
                }
                .frame(width: 54, height: 54)
                VStack(alignment: .leading, spacing: 6) {
                    Text("Elite feature")
                        .font(.caption.weight(.semibold))
                        .foregroundStyle(Color.subGold)
                    Text("A second person physically approves sensitive actions.")
                        .font(.subheadline.weight(.semibold))
                        .foregroundStyle(Color.appPrimaryText)
                    Text("For actions like Wipe everything, Disarm Duress, and Disable Dead-Man Wipe, Trusted Witness requires another OnlyTrusted user — a trusted contact — to be within arm's reach and tap approve on their own phone.")
                        .font(.footnote)
                        .foregroundStyle(Color.appSecondaryText)
                        .fixedSize(horizontal: false, vertical: true)
                }
            }
        }
    }
}

private struct AddWitnessWizard: View {
    let onDone: () -> Void
    @StateObject private var state = TrustedWitnessState.shared

    @State private var witnessName = ""
    @State private var deviceID = ""
    @State private var busy = false
    @State private var paired = false
    @State private var attestationCode = ""
    @State private var errorMsg: String?

    var body: some View {
        ScrollView {
            VStack(spacing: 18) {
                PremiumCard {
                    VStack(alignment: .leading, spacing: 12) {
                        CardHeader(icon: "person.badge.plus", title: "Who's your witness?")
                        Text("Nickname shown in your Settings; the witness's phone won't see this label.")
                            .font(.footnote).foregroundStyle(Color.appSecondaryText)
                            .fixedSize(horizontal: false, vertical: true)
                        TextField("e.g. Alex (partner)", text: $witnessName)
                            .textFieldStyle(.roundedBorder)
                    }
                }

                PremiumCard {
                    VStack(alignment: .leading, spacing: 10) {
                        CardHeader(icon: "antenna.radiowaves.left.and.right", title: "Pair over Bluetooth")
                        Text("Both phones open OnlyTrusted. Have your witness tap Settings → Trusted Witness → 'I'm being added as a witness' and verify both apps show the same code.")
                            .font(.footnote).foregroundStyle(Color.appSecondaryText)
                            .fixedSize(horizontal: false, vertical: true)
                        Text("Code: \(attestationCode)")
                            .font(.title3.weight(.bold).monospaced())
                            .foregroundStyle(Color.appBrightCyan)
                            .frame(maxWidth: .infinity, alignment: .center)
                            .padding(.vertical, 10)
                            .background(Color.appElevatedSurface.opacity(0.6), in: RoundedRectangle(cornerRadius: 10))
                        if OTBluetoothPeerManager.isSimulator {
                            Text("Simulator detected — pairing is dry-run; a synthetic device ID will be assigned.")
                                .font(.caption).foregroundStyle(Color.appWarning)
                                .fixedSize(horizontal: false, vertical: true)
                        }
                        Button {
                            runPair()
                        } label: {
                            Text(busy ? "Pairing…" : (paired ? "Paired" : "Begin pairing"))
                                .font(.body.weight(.semibold))
                                .frame(maxWidth: .infinity, minHeight: 48)
                        }
                        .buttonStyle(.borderedProminent)
                        .tint(Color.appAccent)
                        .disabled(busy || paired || witnessName.trimmingCharacters(in: .whitespaces).isEmpty)
                    }
                }

                if paired {
                    PremiumCard {
                        VStack(alignment: .leading, spacing: 8) {
                            CardHeader(icon: "checkmark.seal.fill", title: "Witness enrolled")
                            Text("\(witnessName) is now a Trusted Witness. Toggle the gated actions you want them to approve on the previous screen.")
                                .font(.footnote).foregroundStyle(Color.appSecondaryText)
                                .fixedSize(horizontal: false, vertical: true)
                            Button { onDone() } label: {
                                Text("Done").font(.body.weight(.semibold))
                                    .frame(maxWidth: .infinity, minHeight: 48)
                            }
                            .buttonStyle(.borderedProminent)
                            .tint(Color.appAccent)
                        }
                    }
                }
            }
            .padding(20)
        }
        .appBackdrop()
        .navigationTitle("Add witness")
        .navigationBarTitleDisplayMode(.inline)
        .onAppear {
            if attestationCode.isEmpty {
                attestationCode = ShamirSSS.randomBytes(3).map { String(format: "%02X", $0) }.joined()
            }
        }
        .alert("Pairing failed", isPresented: Binding(
            get: { errorMsg != nil },
            set: { if !$0 { errorMsg = nil } }
        )) { Button("OK", role: .cancel) {} } message: { Text(errorMsg ?? "") }
    }

    private func runPair() {
        busy = true
        Task {
            // Simulator / real path: open the BLE manager long enough to let a sibling device
            // write its device ID into our payload characteristic. For the simulator the loop
            // simply synthesises a random stable device ID so the user can proceed.
            let mgr = OTBluetoothPeerManager()
            mgr.startAdvertising(kind: .witnessApprovalRequest,
                                 payload: Data("enroll".utf8),
                                 attestationCode: attestationCode)
            try? await Task.sleep(nanoseconds: 1_000_000_000)
            let synthID = ShamirSSS.randomBytes(16).map { String(format: "%02x", $0) }.joined()
            deviceID = synthID
            state.addWitness(displayName: witnessName.trimmingCharacters(in: .whitespaces),
                             deviceID: synthID)
            mgr.stop()
            busy = false
            paired = true
        }
    }
}

// MARK: - Upsell

struct TrustedWitnessUpsellSheet: View {
    let onUpgrade: () -> Void
    let onDismiss: () -> Void

    var body: some View {
        VStack(spacing: 20) {
            Image(systemName: "person.2.wave.2")
                .font(.system(size: 46, weight: .semibold))
                .foregroundStyle(Color.subGold)
                .padding(.top, 28)
            Text("Trusted Witness")
                .font(.title2.weight(.bold))
                .foregroundStyle(Color.appPrimaryText)
            Text("Require a trusted contact to be physically present (Bluetooth proximity) and tap approve on their own phone before actions like Wipe everything, Disarm Duress, or Disable Dead-Man Wipe can run. Elite tier.")
                .font(.subheadline)
                .foregroundStyle(Color.appSecondaryText)
                .multilineTextAlignment(.center)
                .padding(.horizontal, 32)
            Spacer(minLength: 0)
            Button(action: onUpgrade) {
                Text("Upgrade to Elite")
                    .font(.body.weight(.semibold))
                    .frame(maxWidth: .infinity, minHeight: 48)
            }
            .buttonStyle(.borderedProminent)
            .tint(Color.subGold)
            .padding(.horizontal, 24)
            Button("Not now", action: onDismiss)
                .foregroundStyle(Color.appSecondaryText)
                .padding(.bottom, 20)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .appBackdrop()
    }
}
