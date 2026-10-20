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
            ScrollView {
                VStack(alignment: .leading, spacing: 32) {
                    hero
                    featuresCard
                    limitsCard
                    consentSection
                    legalFootnote
                }
                .padding(.horizontal, 24)
                .padding(.top, 48)
                .padding(.bottom, 24)
            }

            actionBar
        }
        .background(Color(.systemGroupedBackground).ignoresSafeArea())
    }

    // MARK: - Hero

    private var hero: some View {
        VStack(alignment: .leading, spacing: 12) {
            Image(systemName: "shield.lefthalf.filled")
                .font(.system(size: 36, weight: .semibold))
                .foregroundStyle(.tint)
                .padding(.bottom, 4)

            Text("OnlyTrusted")
                .font(.system(.largeTitle, design: .default, weight: .bold))

            Text("A serious privacy tool for you and your family — not an entertainment app. Please read and confirm the following before you continue.")
                .font(.body)
                .foregroundStyle(.secondary)
                .fixedSize(horizontal: false, vertical: true)
        }
    }

    // MARK: - Cards

    private var featuresCard: some View {
        InfoCard(
            icon: "lock.shield.fill",
            iconTint: .green,
            title: "What this app does on iOS",
            rows: [
                "Encrypted family vault, unlocked with Face ID or Touch ID.",
                "Call Directory Extension-based blocking (Phase 2).",
                "SMS filter for unknown-sender messages (Phase 2).",
                "Everything stays on this device. No cloud. No ads. No analytics."
            ]
        )
    }

    private var limitsCard: some View {
        InfoCard(
            icon: "exclamationmark.shield.fill",
            iconTint: .orange,
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
                .foregroundStyle(.secondary)
                .textCase(.uppercase)
                .padding(.leading, 4)

            VStack(spacing: 0) {
                ConsentRow(
                    title: "I'm an adult",
                    detail: "At least 18 — or the legal age of digital consent in my country.",
                    isOn: $isAdult
                )
                Divider().padding(.leading, 20)
                ConsentRow(
                    title: "This is my phone",
                    detail: "I own or administer this device. I'll only block calls on phones I'm authorised to manage.",
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
        Text("By continuing you also accept the Privacy Policy and Terms of Use shipped inside the app. Review them any time in Settings → Privacy, terms & honest claims.")
            .font(.footnote)
            .foregroundStyle(.tertiary)
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
                Text(title)
                    .font(.headline)
            }

            VStack(alignment: .leading, spacing: 12) {
                ForEach(Array(rows.enumerated()), id: \.offset) { _, row in
                    HStack(alignment: .firstTextBaseline, spacing: 10) {
                        Circle()
                            .fill(Color(.tertiaryLabel))
                            .frame(width: 4, height: 4)
                            .offset(y: -3)
                        Text(row)
                            .font(.subheadline)
                            .foregroundStyle(.primary)
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
                    .foregroundStyle(.secondary)
                    .fixedSize(horizontal: false, vertical: true)
            }
            .padding(.trailing, 8)
        }
        .padding(.horizontal, 20)
        .padding(.vertical, 14)
    }
}
