// SPDX-FileCopyrightText: 2026 57Million LLC
// SPDX-License-Identifier: AGPL-3.0-or-later
import SwiftUI

struct OnboardingScreen: View {
    let onAccepted: () -> Void

    @State private var isAdult = false
    @State private var ownsDevice = false
    @State private var understandsLimits = false

    private var canContinue: Bool { isAdult && ownsDevice && understandsLimits }

    var body: some View {
        VStack(spacing: 0) {
            ScrollView(.vertical, showsIndicators: false) {
                VStack(alignment: .leading, spacing: 28) {
                    hero
                    featuresCard
                    limitsCard
                    consentSection
                    legalFootnote
                }
                .padding(.horizontal, 24)
                .padding(.top, 24)
                .padding(.bottom, 24)
            }
            // Scroll content starts BELOW the safe area so the top status bar
            // (Dynamic Island + clock + WiFi) is not under the scroll surface.
            .safeAreaPadding(.top)

            actionBar
        }
        .background(Color(.systemGroupedBackground).ignoresSafeArea())
    }

    // MARK: - Hero

    private var hero: some View {
        VStack(alignment: .leading, spacing: 12) {
            Image(systemName: "shield.lefthalf.filled")
                .font(.system(size: 36, weight: .semibold))
                .foregroundStyle(Color.appAccent)
                .padding(.bottom, 4)
                .decorativeAccessibility()

            Text("OnlyTrusted")
                .font(.appLargeTitle)

            Text("A serious privacy tool for you and your family. This is not an entertainment app. Please read and confirm the following before you continue.")
                .font(.body)
                .foregroundStyle(Color.appSecondaryText)
                .fixedSize(horizontal: false, vertical: true)
        }
    }

    // MARK: - Cards

    private var featuresCard: some View {
        InfoCard(
            icon: "lock.shield.fill",
            iconTint: Color.appSuccess,
            title: "What this app does on iOS",
            rows: [
                "Encrypted family vault, unlocked with Face ID or Touch ID.",
                "Blocks calls and texts from numbers on your block list.",
                "Filters SMS from unknown senders.",
                "Keeps every piece of data on this device only. No cloud. No ads. No analytics."
            ]
        )
    }

    private var limitsCard: some View {
        InfoCard(
            icon: "exclamationmark.shield.fill",
            iconTint: Color.appWarning,
            title: "Honest limits iOS imposes",
            rows: [
                "iOS does not let any third-party app monitor other apps' mic or camera. Apple's status-bar indicator is the only source.",
                "You cannot stop the other side of a video call from recording their own device.",
                "Full screen-overlay watermarks are not possible on iOS at all.",
                "Carrier and network-level tracking is outside any app's control."
            ]
        )
    }

    // MARK: - Consent

    private var consentSection: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("Please confirm")
                .font(.footnote.weight(.semibold))
                .foregroundStyle(Color.appSecondaryText)
                .textCase(.uppercase)
                .padding(.leading, 4)

            VStack(spacing: 0) {
                ConsentRow(
                    title: "I'm an adult",
                    detail: "At least 18, or the legal age of digital consent in my country.",
                    isOn: $isAdult
                )
                Divider().padding(.leading, 20)
                ConsentRow(
                    title: "This is my phone",
                    detail: "I own or administer this device. I will only block calls on phones I am authorised to manage.",
                    isOn: $ownsDevice
                )
                Divider().padding(.leading, 20)
                ConsentRow(
                    title: "I understand the limits",
                    detail: "This is a privacy aid, not a guarantee against determined attackers.",
                    isOn: $understandsLimits
                )
            }
            .background(
                RoundedRectangle(cornerRadius: 14, style: .continuous)
                    .fill(Color(.secondarySystemGroupedBackground))
            )
        }
    }

    private var legalFootnote: some View {
        Text("By continuing you also accept the Privacy Policy and Terms of Use shipped inside the app. Review them any time in Settings, under Privacy, terms and honest claims.")
            .font(.footnote)
            .foregroundStyle(Color.appTertiaryText)
            .fixedSize(horizontal: false, vertical: true)
    }

    // MARK: - Sticky action bar

    private var actionBar: some View {
        VStack(spacing: 0) {
            Divider()
            Button(action: onAccepted) {
                Text("I confirm and continue")
                    .font(.body.weight(.semibold))
                    .frame(maxWidth: .infinity, minHeight: 32)
            }
            .buttonStyle(.borderedProminent)
            .tint(Color.appAccent)
            .controlSize(.large)
            .disabled(!canContinue)
            .padding(.horizontal, 20)
            .padding(.top, 16)
            .padding(.bottom, 8)
        }
        .background(.ultraThinMaterial)
    }
}

// MARK: - Reusable row components

private struct InfoCard: View {
    let icon: String
    let iconTint: Color
    let title: String
    let rows: [String]

    var body: some View {
        VStack(alignment: .leading, spacing: 16) {
            HStack(spacing: 10) {
                Image(systemName: icon)
                    .font(.system(size: 16, weight: .semibold))
                    .foregroundStyle(iconTint)
                    .decorativeAccessibility()
                Text(title)
                    .font(.appSectionTitle)
            }

            VStack(alignment: .leading, spacing: 12) {
                ForEach(Array(rows.enumerated()), id: \.offset) { _, row in
                    HStack(alignment: .firstTextBaseline, spacing: 10) {
                        Circle()
                            .fill(Color.appAccent)
                            .frame(width: 5, height: 5)
                            .offset(y: -3)
                            .decorativeAccessibility()
                        Text(row)
                            .font(.subheadline)
                            .foregroundStyle(Color.appPrimaryText)
                            .fixedSize(horizontal: false, vertical: true)
                    }
                }
            }
        }
        .padding(20)
        .background(
            RoundedRectangle(cornerRadius: 14, style: .continuous)
                .fill(Color(.secondarySystemGroupedBackground))
        )
    }
}

private struct ConsentRow: View {
    let title: String
    let detail: String
    @Binding var isOn: Bool

    var body: some View {
        Toggle(isOn: $isOn) {
            VStack(alignment: .leading, spacing: 4) {
                Text(title)
                    .font(.subheadline.weight(.semibold))
                Text(detail)
                    .font(.caption)
                    .foregroundStyle(Color.appSecondaryText)
                    .fixedSize(horizontal: false, vertical: true)
            }
            .padding(.trailing, 8)
        }
        .tint(Color.appAccent)
        .padding(.horizontal, 20)
        .padding(.vertical, 14)
    }
}
