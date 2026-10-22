// SPDX-FileCopyrightText: 2026 VanPaulTek Inc
// SPDX-License-Identifier: AGPL-3.0-or-later
import SwiftUI
import UIKit

/// Full-screen, non-dismissable alarm shown when the SIM identity differs from the stored
/// baseline on app launch. Three CTAs:
///   1. "Lock app now" — fires `AppGate.panic()` so the user has to re-authenticate.
///   2. "Call my carrier" — opens `tel:` to the user-configured carrier number (default 611).
///   3. "I did this swap on purpose" — overwrites the baseline and dismisses the alarm.
struct SIMSwapAlarmSheet: View {
    let alarm: SIMSwapState.Alarm
    let onLock: () -> Void
    let onAcknowledge: () -> Void

    @StateObject private var state = SIMSwapState.shared

    var body: some View {
        VStack(spacing: 24) {
            Spacer(minLength: 24)

            ZStack {
                Circle()
                    .fill(Color.appDanger.opacity(0.18))
                    .frame(width: 110, height: 110)
                Image(systemName: "simcard.fill")
                    .font(.system(size: 48, weight: .semibold))
                    .foregroundStyle(Color.appDanger)
                    .accessibilityHidden(true)
                Image(systemName: "exclamationmark.triangle.fill")
                    .font(.system(size: 22, weight: .bold))
                    .foregroundStyle(Color.appDanger)
                    .offset(x: 34, y: 32)
                    .shadow(color: Color.appDanger.opacity(0.6), radius: 8)
                    .accessibilityHidden(true)
            }

            VStack(spacing: 10) {
                Text("SIM change detected")
                    .font(.title.weight(.bold))
                    .foregroundStyle(Color.appPrimaryText)
                    .multilineTextAlignment(.center)
                Text("If this wasn't you, lock the app, call your carrier, and check your bank + email accounts for intrusion. SIM-swap attacks happen within minutes.")
                    .font(.callout)
                    .foregroundStyle(Color.appSecondaryText)
                    .multilineTextAlignment(.center)
                    .padding(.horizontal, 20)
                    .fixedSize(horizontal: false, vertical: true)
            }

            PremiumCard {
                VStack(alignment: .leading, spacing: 6) {
                    HStack(spacing: 8) {
                        Text("Previous:").font(.caption).foregroundStyle(Color.appSecondaryText)
                        Text(alarm.oldHashShort + "…")
                            .font(.caption.monospaced())
                            .foregroundStyle(Color.appPrimaryText)
                    }
                    HStack(spacing: 8) {
                        Text("Current: ").font(.caption).foregroundStyle(Color.appSecondaryText)
                        Text(alarm.newHashShort + "…")
                            .font(.caption.monospaced())
                            .foregroundStyle(Color.appDanger)
                    }
                    Text("Fingerprints are SHA-256 truncated. Full carrier identity is kept only in your Keychain.")
                        .font(.caption2)
                        .foregroundStyle(Color.appTertiaryText)
                        .padding(.top, 2)
                }
            }
            .padding(.horizontal, 20)

            Spacer(minLength: 0)

            VStack(spacing: 10) {
                Button(action: onLock) {
                    Label("Lock app now", systemImage: "lock.fill")
                        .font(.body.weight(.bold))
                        .frame(maxWidth: .infinity, minHeight: 50)
                }
                .buttonStyle(.borderedProminent)
                .tint(Color.appDanger)

                Button(action: callCarrier) {
                    Label("Call my carrier (\(displayCarrierNumber))",
                          systemImage: "phone.fill")
                        .font(.body.weight(.semibold))
                        .frame(maxWidth: .infinity, minHeight: 46)
                }
                .buttonStyle(.borderedProminent)
                .tint(Color.appAccent)

                Button(action: onAcknowledge) {
                    Text("I did this swap on purpose")
                        .font(.body.weight(.medium))
                        .frame(maxWidth: .infinity, minHeight: 42)
                }
                .buttonStyle(.bordered)
                .tint(Color.appSecondaryText)
            }
            .padding(.horizontal, 20)
            .padding(.bottom, 24)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .appBackdrop()
        .interactiveDismissDisabled(true)
    }

    private var displayCarrierNumber: String {
        state.carrierNumber.isEmpty ? "611" : state.carrierNumber
    }

    private func callCarrier() {
        let digits = displayCarrierNumber.filter { "+0123456789".contains($0) }
        guard let url = URL(string: "tel:\(digits)") else { return }
        UIApplication.shared.open(url)
    }
}
