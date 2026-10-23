// SPDX-FileCopyrightText: 2026 VanPaulTek Inc
// SPDX-License-Identifier: AGPL-3.0-or-later
import SwiftUI

/// Wrap any Premium-gated screen's content in this view. If the user is on Free the
/// screen shows a single upsell card in place of the real content; on Premium the
/// `content` closure runs normally. Mirrors Android `PremiumGate.kt`.
///
/// Deep-link safety: Settings routes still allow Free users to open the destination so
/// they can see what they'd unlock. The gate kicks in as soon as the body composes.
struct PremiumGate<Content: View>: View {

    let featureName: String
    let description: String
    @ViewBuilder let content: () -> Content

    @StateObject private var sub = SubscriptionState.shared

    var body: some View {
        if sub.hasPremium {
            content()
        } else {
            upsellBody
        }
    }

    private var upsellBody: some View {
        VStack {
            Spacer()
            PremiumCard {
                VStack(alignment: .leading, spacing: 12) {
                    HStack(spacing: 10) {
                        Image(systemName: "crown.fill")
                            .foregroundStyle(Color.subGold)
                            .font(.title2)
                        Text("\(featureName) — Premium")
                            .font(.title3.weight(.bold))
                            .foregroundStyle(Color.appPrimaryText)
                    }
                    Text(description)
                        .font(.subheadline)
                        .foregroundStyle(Color.appSecondaryText)
                        .fixedSize(horizontal: false, vertical: true)
                    Text("This feature is included with the Premium plan ($4.99/yr). Open Settings → Upgrade to Premium to unlock it.")
                        .font(.caption)
                        .foregroundStyle(Color.appSecondaryText)
                        .fixedSize(horizontal: false, vertical: true)
                    Text("Your Free plan still includes trusted contacts, call & SMS handling, Blocking & Unknown, Data Removal Scan, and all device-level settings.")
                        .font(.caption2)
                        .foregroundStyle(Color.appTertiaryText)
                        .fixedSize(horizontal: false, vertical: true)
                }
            }
            .padding(.horizontal, 20)
            Spacer()
        }
        .appBackdrop()
    }
}
