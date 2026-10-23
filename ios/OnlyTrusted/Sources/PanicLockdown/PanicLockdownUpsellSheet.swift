// SPDX-FileCopyrightText: 2026 VanPaulTek Inc
// SPDX-License-Identifier: AGPL-3.0-or-later
import SwiftUI

/// Shown to non-Elite tiers (free / Premium / Family / Pro) when they tap the
/// "Panic Lockdown (Elite)" settings row. Routes to the Upgrade screen.
struct PanicLockdownUpsellSheet: View {
    let onUpgrade: () -> Void
    let onDismiss: () -> Void

    var body: some View {
        VStack(spacing: 20) {
            Image(systemName: "exclamationmark.octagon.fill")
                .font(.system(size: 46, weight: .bold))
                .foregroundStyle(Color.appDanger)
                .padding(.top, 28)
            Text("Panic Lockdown")
                .font(.title2.weight(.bold))
                .foregroundStyle(Color.appPrimaryText)
            Text("The RIGHT-NOW emergency button. One tap locks the app, arms Duress, forces aggressive sensor monitoring, disables biometric unlock and the share extension, and logs the moment with a timestamp. Elite tier.")
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
