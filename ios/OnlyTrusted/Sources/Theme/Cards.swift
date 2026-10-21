// SPDX-FileCopyrightText: 2026 57Million LLC
// SPDX-License-Identifier: AGPL-3.0-or-later
import SwiftUI

/// A standard "premium" card used across every screen: soft rounded corners (20pt radius),
/// generous padding (22pt), subtle shadow, pinned to the secondarySystemGroupedBackground so
/// cards float cleanly over the primary systemGroupedBackground surface.
///
/// Rules:
///  - One or more PremiumCards per screen, stacked in a VStack with 20pt spacing.
///  - Each card starts with a `CardHeader` (icon chip + title) for consistency.
///  - Content inside uses `.appPrimaryText` + `.appSecondaryText` only (never system .secondary).
struct PremiumCard<Content: View>: View {
    @ViewBuilder let content: Content

    var body: some View {
        content
            .padding(22)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(
                RoundedRectangle(cornerRadius: 20, style: .continuous)
                    .fill(Color(.secondarySystemGroupedBackground))
            )
            .shadow(color: Color.black.opacity(0.04), radius: 8, x: 0, y: 2)
    }
}

/// Icon-chip + title row that every PremiumCard opens with. The icon sits in a tinted rounded
/// square so the eye finds section boundaries quickly.
struct CardHeader: View {
    let icon: String
    let title: String
    var tint: Color = .appAccent

    var body: some View {
        HStack(spacing: 12) {
            ZStack {
                RoundedRectangle(cornerRadius: 10, style: .continuous)
                    .fill(tint.opacity(0.14))
                Image(systemName: icon)
                    .font(.body.weight(.semibold))
                    .foregroundStyle(tint)
            }
            .frame(width: 36, height: 36)
            .decorativeAccessibility()

            Text(title)
                .font(.title3.weight(.semibold))
                .foregroundStyle(Color.appPrimaryText)
            Spacer(minLength: 0)
        }
    }
}

/// Standard inner-row inside a PremiumCard that opens an external destination (iOS Settings,
/// email client, etc). Looks like a labeled pill with a leading colored icon and a trailing
/// out-arrow.
struct SystemLinkRow: View {
    let icon: String
    let label: String
    let tint: Color
    let onTap: () -> Void

    init(icon: String, label: String, tint: Color = .appAccent, onTap: @escaping () -> Void) {
        self.icon = icon
        self.label = label
        self.tint = tint
        self.onTap = onTap
    }

    var body: some View {
        Button(action: onTap) {
            HStack(spacing: 14) {
                Image(systemName: icon)
                    .font(.body.weight(.semibold))
                    .foregroundStyle(tint)
                    .frame(width: 28, height: 28)
                    .decorativeAccessibility()
                Text(label)
                    .font(.subheadline.weight(.medium))
                    .foregroundStyle(Color.appPrimaryText)
                Spacer()
                Image(systemName: "arrow.up.forward")
                    .font(.footnote.weight(.semibold))
                    .foregroundStyle(Color.appTertiaryText)
                    .decorativeAccessibility()
            }
            .padding(.horizontal, 14)
            .padding(.vertical, 12)
            .background(
                RoundedRectangle(cornerRadius: 12, style: .continuous)
                    .fill(Color(.systemGroupedBackground))
            )
        }
        .buttonStyle(.plain)
        .accessibilityLabel(label)
    }
}

/// Centered empty-state used on Vault / Events / Blocked when there is no data. Big icon,
/// headline, supporting copy, and an optional CTA. Mirrors Apple's ContentUnavailableView
/// but with our card chrome.
struct PremiumEmptyState: View {
    let icon: String
    let title: String
    let message: String
    let ctaTitle: String?
    let ctaAction: (() -> Void)?

    init(
        icon: String,
        title: String,
        message: String,
        ctaTitle: String? = nil,
        ctaAction: (() -> Void)? = nil
    ) {
        self.icon = icon
        self.title = title
        self.message = message
        self.ctaTitle = ctaTitle
        self.ctaAction = ctaAction
    }

    var body: some View {
        VStack(spacing: 18) {
            ZStack {
                Circle()
                    .fill(Color.appAccent.opacity(0.12))
                Image(systemName: icon)
                    .font(.system(size: 44, weight: .light))
                    .foregroundStyle(Color.appAccent)
            }
            .frame(width: 108, height: 108)
            .decorativeAccessibility()

            Text(title)
                .font(.title2.weight(.semibold))
                .foregroundStyle(Color.appPrimaryText)
                .multilineTextAlignment(.center)

            Text(message)
                .font(.subheadline)
                .foregroundStyle(Color.appSecondaryText)
                .multilineTextAlignment(.center)
                .fixedSize(horizontal: false, vertical: true)

            if let ctaTitle, let ctaAction {
                Button(action: ctaAction) {
                    Text(ctaTitle)
                        .font(.body.weight(.semibold))
                        .frame(maxWidth: .infinity, minHeight: 32)
                }
                .buttonStyle(.borderedProminent)
                .tint(Color.appAccent)
                .controlSize(.large)
                .padding(.top, 6)
            }
        }
        .padding(.horizontal, 32)
        .padding(.top, 64)
        .padding(.bottom, 32)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }
}
