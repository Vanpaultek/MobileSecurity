// SPDX-FileCopyrightText: 2026 57Million LLC
// SPDX-License-Identifier: AGPL-3.0-or-later
import SwiftUI

struct OnboardingScreen: View {
    let onContinue: () -> Void

    var body: some View {
        VStack(spacing: 0) {
            ScrollView(.vertical, showsIndicators: false) {
                VStack(spacing: 28) {
                    hero
                    benefitsCard
                    limitsCard
                }
                .padding(.horizontal, 24)
                .padding(.top, 20)
                .padding(.bottom, 24)
            }
            .safeAreaPadding(.top)

            actionBar
        }
        .background(Color.appScreenBackground.ignoresSafeArea())
    }

    private var hero: some View {
        VStack(spacing: 14) {
            ZStack {
                Circle()
                    .fill(Color.appAccent.opacity(0.12))
                    .frame(width: 136, height: 136)
                Image("BrandLogo")
                    .resizable()
                    .scaledToFit()
                    .frame(width: 92, height: 92)
                    .clipShape(RoundedRectangle(cornerRadius: 20, style: .continuous))
                    .accessibilityLabel("OnlyTrusted")
            }

            Text("OnlyTrusted")
                .font(.system(size: 30, weight: .bold, design: .rounded))
                .foregroundStyle(Color.appPrimaryText)

            Text("A safer, quieter phone starts here.")
                .font(.subheadline)
                .foregroundStyle(Color.appSecondaryText)
                .multilineTextAlignment(.center)
                .fixedSize(horizontal: false, vertical: true)
        }
        .frame(maxWidth: .infinity)
    }

    private var benefitsCard: some View {
        PremiumCard {
            VStack(alignment: .leading, spacing: 14) {
                BenefitRow(icon: "person.2.fill", tint: Color.appAccent,
                           title: "Allow only trusted contacts")
                Divider().padding(.leading, 44)
                BenefitRow(icon: "bell.slash.fill", tint: Color.appAccent,
                           title: "Silence or block everyone else")
                Divider().padding(.leading, 44)
                BenefitRow(icon: "doc.text.magnifyingglass", tint: Color.appAccent,
                           title: "Keep a local audit log")
                Divider().padding(.leading, 44)
                BenefitRow(icon: "lock.shield.fill", tint: Color.appAccent,
                           title: "Your data never leaves this device")
            }
        }
    }

    private var limitsCard: some View {
        PremiumCard {
            VStack(alignment: .leading, spacing: 14) {
                CardHeader(icon: "exclamationmark.shield.fill",
                           title: "Honest limits iOS imposes",
                           tint: Color.appWarning)
                VStack(alignment: .leading, spacing: 10) {
                    bullet("iOS does not let any third-party app monitor other apps' mic or camera. Apple's status-bar indicator is the only source.")
                    bullet("You cannot stop the other side of a video call from recording their own device.")
                    bullet("Full screen-overlay watermarks are not possible on iOS at all.")
                    bullet("Carrier and network-level tracking is outside any app's control.")
                }
            }
        }
    }

    private func bullet(_ text: String) -> some View {
        HStack(alignment: .firstTextBaseline, spacing: 10) {
            Circle()
                .fill(Color.appAccent)
                .frame(width: 5, height: 5)
                .offset(y: -3)
                .decorativeAccessibility()
            Text(text)
                .font(.subheadline)
                .foregroundStyle(Color.appPrimaryText)
                .fixedSize(horizontal: false, vertical: true)
        }
    }

    private var actionBar: some View {
        VStack(spacing: 0) {
            Divider()
            Button(action: onContinue) {
                Text("Get Started")
                    .font(.body.weight(.semibold))
                    .frame(maxWidth: .infinity, minHeight: 32)
            }
            .buttonStyle(.borderedProminent)
            .tint(Color.appAccent)
            .controlSize(.large)
            .padding(.horizontal, 20)
            .padding(.top, 16)
            .padding(.bottom, 8)
        }
        .background(.ultraThinMaterial)
    }
}

private struct BenefitRow: View {
    let icon: String
    let tint: Color
    let title: String

    var body: some View {
        HStack(spacing: 14) {
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
                .font(.subheadline.weight(.medium))
                .foregroundStyle(Color.appPrimaryText)
                .fixedSize(horizontal: false, vertical: true)

            Spacer()
        }
        .padding(.vertical, 4)
    }
}
