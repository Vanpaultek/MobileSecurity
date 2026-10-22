// SPDX-FileCopyrightText: 2026 VanPaulTek Inc
// SPDX-License-Identifier: AGPL-3.0-or-later
import SwiftUI

/// Consistent editorial header for OnlyTrusted's native iOS screens.
/// Decorative indicators carry no fabricated protection status.
struct CyberPageHeader: View {
    let eyebrow: String
    let title: String
    let detail: String
    let symbol: String

    var body: some View {
        HStack(alignment: .center, spacing: 14) {
            VStack(alignment: .leading, spacing: 7) {
                Text(eyebrow.uppercased())
                    .font(.system(.caption2, design: .monospaced, weight: .bold))
                    .tracking(2)
                    .foregroundStyle(Color.appAccent)
                Text(title)
                    .font(.system(.title, design: .rounded, weight: .bold))
                    .foregroundStyle(Color.appPrimaryText)
                    .fixedSize(horizontal: false, vertical: true)
                Text(detail)
                    .font(.subheadline)
                    .foregroundStyle(Color.appSecondaryText)
                    .fixedSize(horizontal: false, vertical: true)
            }
            Spacer(minLength: 4)
            Image(systemName: symbol)
                .font(.system(size: 29, weight: .light))
                .foregroundStyle(Color.appAccent)
                .frame(width: 62, height: 62)
                .background(Color.appAccent.opacity(0.10), in: RoundedRectangle(cornerRadius: 20))
                .overlay { RoundedRectangle(cornerRadius: 20).strokeBorder(Color.appAccent.opacity(0.24)) }
                .accessibilityHidden(true)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(.vertical, 9)
    }
}

/// Visual location cue, not a claim that phone protections have been enabled.
struct LocalOnlyStrip: View {
    var body: some View {
        HStack(spacing: 9) {
            Image(systemName: "iphone.gen3")
                .foregroundStyle(Color.appAccent)
                .accessibilityHidden(true)
            Text("LOCAL TO THIS DEVICE")
                .font(.system(.caption2, design: .monospaced, weight: .semibold))
                .tracking(1)
                .foregroundStyle(Color.appSecondaryText)
            Spacer()
            Image(systemName: "lock.shield")
                .foregroundStyle(Color.appAccent)
                .accessibilityHidden(true)
        }
        .padding(.horizontal, 15)
        .padding(.vertical, 12)
        .background(Color.appElevatedSurface, in: RoundedRectangle(cornerRadius: 12))
        .accessibilityElement(children: .combine)
    }
}
