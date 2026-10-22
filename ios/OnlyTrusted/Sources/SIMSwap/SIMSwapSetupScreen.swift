// SPDX-FileCopyrightText: 2026 VanPaulTek Inc
// SPDX-License-Identifier: AGPL-3.0-or-later
import SwiftUI

/// Setup + management screen for the SIM-Swap Alarm (Pro feature). Reached from
/// Settings → Security & data → "SIM-Swap Alarm (Pro)". The Settings row gates on
/// `SubscriptionState.hasHighPremium`; this screen assumes the user is on Pro.
struct SIMSwapSetupScreen: View {
    @Environment(\.dismiss) private var dismiss
    @StateObject private var state = SIMSwapState.shared

    @State private var showResetConfirm = false
    @State private var carrierNumberDraft: String = ""
    @State private var showCaptureToast = false

    private static let dateFormatter: DateFormatter = {
        let f = DateFormatter()
        f.dateStyle = .medium
        f.timeStyle = .short
        return f
    }()

    var body: some View {
        ScrollView(.vertical, showsIndicators: false) {
            VStack(alignment: .leading, spacing: 18) {
                introCard
                baselineCard
                toggleCard
                carrierCard
                limitationsNote
            }
            .padding(.horizontal, 18)
            .padding(.top, 10)
            .padding(.bottom, 32)
        }
        .appBackdrop()
        .navigationTitle("SIM-Swap Alarm")
        .navigationBarTitleDisplayMode(.inline)
        .onAppear {
            carrierNumberDraft = state.carrierNumber
        }
        .alert("Reset the baseline?", isPresented: $showResetConfirm) {
            Button("Reset", role: .destructive) {
                state.resetBaseline()
            }
            Button("Cancel", role: .cancel) {}
        } message: {
            Text("The alarm will stay disabled until you capture a new baseline.")
        }
        .overlay(alignment: .bottom) {
            if showCaptureToast {
                Text("Baseline captured")
                    .font(.footnote.weight(.semibold))
                    .foregroundStyle(Color.appScreenBackground)
                    .padding(.horizontal, 16)
                    .padding(.vertical, 10)
                    .background(Capsule().fill(Color.appAccent))
                    .padding(.bottom, 24)
                    .transition(.opacity)
            }
        }
    }

    // MARK: - Cards

    private var introCard: some View {
        PremiumCard {
            VStack(alignment: .leading, spacing: 10) {
                HStack(spacing: 10) {
                    Image(systemName: "simcard.fill")
                        .font(.title3)
                        .foregroundStyle(Color.appAccent)
                    Text("What this does")
                        .font(.title3.weight(.bold))
                        .foregroundStyle(Color.appPrimaryText)
                }
                Text("A SIM swap is when an attacker tricks your carrier into moving your number to their SIM so they can intercept your bank 2FA codes. OnlyTrusted fingerprints your carrier identity when you enable this, then alerts you if it ever changes.")
                    .font(.footnote)
                    .foregroundStyle(Color.appSecondaryText)
                    .fixedSize(horizontal: false, vertical: true)
            }
        }
    }

    private var baselineCard: some View {
        PremiumCard {
            VStack(alignment: .leading, spacing: 12) {
                CardHeader(icon: "checkmark.seal.fill", title: "SIM baseline")
                let current = SIMSwapIdentity.current()
                HStack(spacing: 10) {
                    Image(systemName: "antenna.radiowaves.left.and.right")
                        .foregroundStyle(Color.appBrightCyan)
                    VStack(alignment: .leading, spacing: 2) {
                        Text("Current SIM")
                            .font(.footnote.weight(.semibold))
                            .foregroundStyle(Color.appSecondaryText)
                        Text(current.displayString)
                            .font(.subheadline.weight(.semibold))
                            .foregroundStyle(Color.appPrimaryText)
                            .fixedSize(horizontal: false, vertical: true)
                    }
                    Spacer()
                }
                if let b = state.baseline {
                    SettingsDivider()
                    HStack(spacing: 10) {
                        Image(systemName: "clock.fill")
                            .foregroundStyle(Color.appBrightCyan)
                        VStack(alignment: .leading, spacing: 2) {
                            Text("Last baseline update")
                                .font(.footnote.weight(.semibold))
                                .foregroundStyle(Color.appSecondaryText)
                            Text(Self.dateFormatter.string(from: b.capturedAt))
                                .font(.subheadline)
                                .foregroundStyle(Color.appPrimaryText)
                            Text(b.display)
                                .font(.caption)
                                .foregroundStyle(Color.appTertiaryText)
                                .fixedSize(horizontal: false, vertical: true)
                        }
                        Spacer()
                    }
                } else {
                    Text("No baseline captured yet.")
                        .font(.caption)
                        .foregroundStyle(Color.appWarning)
                }
                HStack(spacing: 10) {
                    Button {
                        state.captureBaseline()
                        state.isEnabled = true
                        withAnimation { showCaptureToast = true }
                        DispatchQueue.main.asyncAfter(deadline: .now() + 1.8) {
                            withAnimation { showCaptureToast = false }
                        }
                    } label: {
                        Text(state.baseline == nil ? "Capture baseline" : "Re-capture baseline")
                            .font(.body.weight(.semibold))
                            .frame(maxWidth: .infinity, minHeight: 44)
                    }
                    .buttonStyle(.borderedProminent)
                    .tint(Color.appAccent)

                    if state.baseline != nil {
                        Button(role: .destructive) {
                            showResetConfirm = true
                        } label: {
                            Text("Reset")
                                .font(.body.weight(.semibold))
                                .frame(minHeight: 44)
                                .padding(.horizontal, 12)
                        }
                        .buttonStyle(.bordered)
                    }
                }
                .padding(.top, 4)
            }
        }
    }

    private var toggleCard: some View {
        PremiumCard {
            Toggle(isOn: $state.isEnabled) {
                VStack(alignment: .leading, spacing: 2) {
                    Text("Alarm enabled")
                        .font(.subheadline.weight(.semibold))
                        .foregroundStyle(Color.appPrimaryText)
                    Text(state.isEnabled
                         ? "You'll see a full-screen alert on next app open if the SIM changes."
                         : "Capture a baseline above, then enable to arm the alarm.")
                        .font(.caption)
                        .foregroundStyle(Color.appSecondaryText)
                        .fixedSize(horizontal: false, vertical: true)
                }
            }
            .tint(Color.appAccent)
            .disabled(state.baseline == nil)
        }
    }

    private var carrierCard: some View {
        PremiumCard {
            VStack(alignment: .leading, spacing: 10) {
                CardHeader(icon: "phone.fill", title: "Carrier phone number")
                Text("Used by the \"Call my carrier\" button on the alarm screen. Defaults to 611 (US carriers). Set your carrier's fraud / account-services line here.")
                    .font(.caption)
                    .foregroundStyle(Color.appSecondaryText)
                    .fixedSize(horizontal: false, vertical: true)
                TextField("Carrier number (e.g. 611 or +1-800-...)",
                          text: $carrierNumberDraft)
                    .keyboardType(.phonePad)
                    .textFieldStyle(.roundedBorder)
                    .onSubmit { commitCarrierNumber() }
                Button {
                    commitCarrierNumber()
                } label: {
                    Text("Save")
                        .font(.body.weight(.semibold))
                        .frame(maxWidth: .infinity, minHeight: 40)
                }
                .buttonStyle(.bordered)
                .tint(Color.appAccent)
            }
        }
    }

    private var limitationsNote: some View {
        PremiumCard {
            VStack(alignment: .leading, spacing: 6) {
                HStack(spacing: 8) {
                    Image(systemName: "info.circle.fill").foregroundStyle(Color.appWarning)
                    Text("iOS 16+ limitation")
                        .font(.subheadline.weight(.semibold))
                        .foregroundStyle(Color.appPrimaryText)
                }
                Text("Apple deprecated the `CTCarrier` carrier-name / MCC / MNC APIs in iOS 16. On recent iOS versions these return the sentinel value `--` / `65535`, which means the alarm can only reliably detect a swap when CoreTelephony still exposes real values (usually dual-SIM eSIM slots or older iOS). Run OnlyTrusted on Android for a stricter SIM-swap guarantee via SIM serial + IMSI.")
                    .font(.caption)
                    .foregroundStyle(Color.appSecondaryText)
                    .fixedSize(horizontal: false, vertical: true)
            }
        }
    }

    private func commitCarrierNumber() {
        let trimmed = carrierNumberDraft.trimmingCharacters(in: .whitespacesAndNewlines)
        state.carrierNumber = trimmed.isEmpty ? "611" : trimmed
    }
}
