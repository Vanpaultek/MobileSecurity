// SPDX-FileCopyrightText: 2026 VanPaulTek Inc
// SPDX-License-Identifier: AGPL-3.0-or-later
import SwiftUI

/// Dead-Man Wipe (Elite) setup screen.
///
/// Reached from Settings → Security & data → "Dead-Man Wipe (Elite)". The parent row is already
/// gated on `SubscriptionState.hasElite`; this screen assumes the user is on Elite.
///
/// The flow is intentionally scary: a big destructive warning sits above the enable toggle,
/// and enabling it immediately resets the countdown so the user has a full `intervalDays`
/// grace period before anything bad can happen. The 24h-before notification is scheduled
/// automatically.
struct DeadmanWipeSetupScreen: View {
    @Environment(\.dismiss) private var dismiss
    @StateObject private var state = DeadmanWipeState.shared

    @State private var confirmEnable = false
    @State private var confirmReset = false

    var body: some View {
        ScrollView {
            VStack(spacing: 18) {
                headerCard

                PremiumCard {
                    VStack(alignment: .leading, spacing: 14) {
                        Toggle(isOn: Binding(
                            get: { state.isEnabled },
                            set: { newValue in
                                if newValue && !state.isEnabled {
                                    confirmEnable = true
                                } else if !newValue {
                                    // Elite Trusted Witness gate — disabling the Dead-Man Wipe
                                    // is on the gated-action list. No witness armed → no-op.
                                    TrustedWitnessGate.require(
                                        action: .disableDeadmanWipe,
                                        reason: "Have your witness approve disabling Dead-Man Wipe."
                                    ) { tw in
                                        if case .success = tw { state.disable() }
                                    }
                                }
                            }
                        )) {
                            HStack(spacing: 12) {
                                Image(systemName: state.isEnabled ? "clock.badge.exclamationmark.fill" : "clock.badge.exclamationmark")
                                    .foregroundStyle(state.isEnabled ? Color.appDanger : Color.subGold)
                                VStack(alignment: .leading, spacing: 2) {
                                    Text("Enabled")
                                        .font(.subheadline.weight(.semibold))
                                        .foregroundStyle(Color.appPrimaryText)
                                    Text(state.isEnabled
                                         ? "Timer is live. Open the app before the deadline to reset it."
                                         : "Off. Nothing will be wiped automatically.")
                                        .font(.caption)
                                        .foregroundStyle(Color.appSecondaryText)
                                        .fixedSize(horizontal: false, vertical: true)
                                }
                            }
                        }
                        .tint(Color.appDanger)
                    }
                }

                PremiumCard {
                    VStack(alignment: .leading, spacing: 12) {
                        CardHeader(icon: "timer", title: "Wipe interval")
                        Text("How long OnlyTrusted will wait without an unlock before it wipes everything.")
                            .font(.footnote)
                            .foregroundStyle(Color.appSecondaryText)
                            .fixedSize(horizontal: false, vertical: true)
                        ForEach(DeadmanWipeState.allowedIntervals, id: \.self) { d in
                            RadioRow(label: "\(d) days",
                                     isSelected: state.intervalDays == d) {
                                state.intervalDays = d
                            }
                        }
                    }
                }

                if state.isEnabled {
                    PremiumCard {
                        VStack(alignment: .leading, spacing: 10) {
                            CardHeader(icon: "calendar.badge.clock", title: "Status")
                            labelRow("Last unlock", dateFmt(state.lastUnlock))
                            labelRow("Will wipe at", dateFmt(state.wipeDeadline), tint: Color.appDanger)
                        }
                    }

                    PremiumCard {
                        VStack(alignment: .leading, spacing: 10) {
                            Text("Warning")
                                .font(.subheadline.weight(.bold))
                                .foregroundStyle(Color.appDanger)
                            Text("The app will delete every trusted contact, every blocked number, every event, every setting, and every encrypted vault entry on \(dateFmt(state.wipeDeadline)) if you don't unlock the app before then. This cannot be undone.")
                                .font(.footnote)
                                .foregroundStyle(Color.appPrimaryText)
                                .fixedSize(horizontal: false, vertical: true)
                        }
                    }

                    Button { confirmReset = true } label: {
                        Text("Reset timer")
                            .font(.body.weight(.semibold))
                            .frame(maxWidth: .infinity, minHeight: 48)
                    }
                    .buttonStyle(.borderedProminent)
                    .tint(Color.appAccent)
                    .padding(.horizontal, 4)
                }
            }
            .padding(20)
        }
        .appBackdrop()
        .navigationTitle("Dead-Man Wipe")
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .topBarTrailing) {
                Button("Done") { dismiss() }.tint(Color.appBrightCyan)
            }
        }
        .confirmationDialog(
            "Enable Dead-Man Wipe?",
            isPresented: $confirmEnable,
            titleVisibility: .visible
        ) {
            Button("Enable", role: .destructive) {
                state.enable(intervalDays: state.intervalDays)
            }
            Button("Cancel", role: .cancel) {}
        } message: {
            Text("If you don't open OnlyTrusted for \(state.intervalDays) days, every trusted contact, every blocked number, every event, every setting, and every encrypted vault entry will be deleted. This cannot be undone.")
        }
        .confirmationDialog(
            "Reset timer?",
            isPresented: $confirmReset,
            titleVisibility: .visible
        ) {
            Button("Reset", role: .destructive) {
                state.markUnlockNow()
            }
            Button("Cancel", role: .cancel) {}
        } message: {
            Text("Resets the countdown to \(state.intervalDays) days from now.")
        }
    }

    private var headerCard: some View {
        PremiumCard {
            HStack(alignment: .top, spacing: 14) {
                ZStack {
                    RoundedRectangle(cornerRadius: 14, style: .continuous)
                        .fill(
                            LinearGradient(
                                colors: [Color.appDanger, Color.appDanger.opacity(0.7)],
                                startPoint: .topLeading,
                                endPoint: .bottomTrailing
                            )
                        )
                        .shadow(color: Color.appDanger.opacity(0.6), radius: 10)
                    Image(systemName: "clock.badge.exclamationmark.fill")
                        .font(.system(size: 24, weight: .semibold))
                        .foregroundStyle(.white)
                }
                .frame(width: 54, height: 54)
                VStack(alignment: .leading, spacing: 6) {
                    Text("Elite feature")
                        .font(.caption.weight(.semibold))
                        .foregroundStyle(Color.subGold)
                    Text("A trip-wire for lost or seized devices.")
                        .font(.subheadline.weight(.semibold))
                        .foregroundStyle(Color.appPrimaryText)
                    Text("If you can't unlock the app for a long time, it wipes itself. All data is local; there is no server fallback.")
                        .font(.footnote)
                        .foregroundStyle(Color.appSecondaryText)
                        .fixedSize(horizontal: false, vertical: true)
                }
            }
        }
    }

    private func labelRow(_ label: String, _ value: String, tint: Color = Color.appPrimaryText) -> some View {
        HStack {
            Text(label)
                .font(.footnote)
                .foregroundStyle(Color.appSecondaryText)
            Spacer()
            Text(value)
                .font(.subheadline.weight(.semibold))
                .foregroundStyle(tint)
        }
    }

    private func dateFmt(_ date: Date) -> String {
        let f = DateFormatter()
        f.dateFormat = "MMM d, yyyy · h:mm a"
        return f.string(from: date)
    }
}

/// Elite upsell sheet shown when a non-Elite user taps the Dead-Man Wipe row in Settings.
struct DeadmanWipeUpsellSheet: View {
    let onUpgrade: () -> Void
    let onDismiss: () -> Void

    var body: some View {
        VStack(spacing: 20) {
            Image(systemName: "clock.badge.exclamationmark.fill")
                .font(.system(size: 46, weight: .semibold))
                .foregroundStyle(Color.subGold)
                .padding(.top, 28)
            Text("Dead-Man Wipe")
                .font(.title2.weight(.bold))
                .foregroundStyle(Color.appPrimaryText)
            Text("A trip-wire for lost or seized devices. If OnlyTrusted hasn't been unlocked for a long time, it wipes every contact, blocked number, event, and setting on its own. Elite tier.")
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
