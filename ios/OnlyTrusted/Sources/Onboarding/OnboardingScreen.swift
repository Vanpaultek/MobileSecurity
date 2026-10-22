// SPDX-FileCopyrightText: 2026 VanPaulTek Inc
// SPDX-License-Identifier: AGPL-3.0-or-later
import SwiftUI

/// iOS 17+ — cinematic onboarding for OnlyTrusted.
/// Explains the product. Consent lives in the separate ConsentScreen.
struct OnboardingScreen: View {
    let onContinue: () -> Void
    @Environment(\.dynamicTypeSize) private var dynamicTypeSize

    var body: some View {
        VStack(spacing: 0) {
            ScrollView(.vertical, showsIndicators: false) {
                VStack(spacing: 16) {
                    cinematicHero
                    headline
                    benefits
                    pageIndicator
                }
                .padding(.horizontal, 18)
                .padding(.bottom, 12)
            }
            .accessibilityIdentifier("onlytrusted.onboarding.content")
            continueBar
        }
        .background(Color.appScreenBackground.ignoresSafeArea())
        .preferredColorScheme(.dark)
    }

    // MARK: - Hero (full-width image with wordmark overlay)

    private var cinematicHero: some View {
        let heroHeight: CGFloat = dynamicTypeSize.isAccessibilitySize ? 420 : 380

        return ZStack(alignment: .top) {
            GeometryReader { proxy in
                Image("OnboardingHero")
                    .resizable()
                    .aspectRatio(contentMode: .fill)
                    .frame(width: proxy.size.width,
                           height: proxy.size.width * (1536.0 / 1024.0),
                           alignment: .top)
                    .frame(width: proxy.size.width, height: proxy.size.height, alignment: .top)
                    .clipped()
            }
            .frame(height: heroHeight)

            // "OnlyTrusted" wordmark overlaid on top of hero (white "Only" + teal "Trusted")
            HStack(spacing: 0) {
                Text("Only")
                    .foregroundStyle(.white)
                Text("Trusted")
                    .foregroundStyle(Color.appAccent)
            }
            .font(.system(size: 36, weight: .bold, design: .rounded))
            .shadow(color: .black.opacity(0.55), radius: 10, x: 0, y: 2)
            .padding(.top, 10)
        }
        .frame(height: heroHeight)
        .clipShape(RoundedRectangle(cornerRadius: 22, style: .continuous))
        .overlay {
            RoundedRectangle(cornerRadius: 22, style: .continuous)
                .strokeBorder(Color.appBrightCyan.opacity(0.36), lineWidth: 1)
        }
        .accessibilityElement(children: .combine)
        .accessibilityLabel("OnlyTrusted. A safer, quieter phone starts here.")
    }

    // MARK: - Headline

    private var headline: some View {
        Text("A safer, quieter phone\nstarts here.")
            .font(.system(size: 26, weight: .bold, design: .rounded))
            .multilineTextAlignment(.center)
            .foregroundStyle(Color.appPrimaryText)
            .fixedSize(horizontal: false, vertical: true)
            .padding(.top, 4)
            .padding(.horizontal, 20)
    }

    // MARK: - 5 benefit cards (icon + title + subtitle + chevron)

    private var benefits: some View {
        VStack(spacing: 10) {
            BenefitTile(
                symbol: "person.3.fill",
                title: "Allow only trusted contacts",
                subtitle: "Keep your conversations with people you trust."
            )
            BenefitTile(
                symbol: "bell.slash.fill",
                title: "Silence or block everyone else",
                subtitle: "Reduce unwanted calls and messages."
            )
            BenefitTile(
                symbol: "doc.text.magnifyingglass",
                title: "Keep a local audit log",
                subtitle: "Review recent calls and messages on your device."
            )
            BenefitTile(
                symbol: "shield.lefthalf.filled",
                title: "Your data never leaves this device",
                subtitle: "Everything stays private and on your iPhone."
            )
            BenefitTile(
                symbol: "info.circle.fill",
                title: "Important to know",
                subtitle: "iOS has system limitations. OnlyTrusted cannot monitor other apps, your microphone, or your camera. Call blocking and message filtering use Apple's supported features."
            )
        }
        .accessibilityElement(children: .contain)
    }

    // MARK: - Page indicator (3 dots, middle active per mockup)

    private var pageIndicator: some View {
        HStack(spacing: 8) {
            PageDot(active: false)
            PageDot(active: true)
            PageDot(active: false)
        }
        .padding(.top, 4)
        .accessibilityHidden(true)
    }

    // MARK: - Sticky Get Started button

    private var continueBar: some View {
        VStack(spacing: 0) {
            Button(action: onContinue) {
                HStack(spacing: 8) {
                    Spacer()
                    Text("Get Started")
                        .font(.system(size: 18, weight: .bold, design: .rounded))
                        .foregroundStyle(Color.appScreenBackground)
                    Image(systemName: "chevron.right")
                        .font(.system(size: 15, weight: .bold))
                        .foregroundStyle(Color.appScreenBackground)
                    Spacer()
                }
                .frame(maxWidth: .infinity, minHeight: 56)
                .background(
                    LinearGradient(colors: [Color.appBrightCyan, Color.appAccent],
                                   startPoint: .topLeading, endPoint: .bottomTrailing),
                    in: RoundedRectangle(cornerRadius: 28, style: .continuous)
                )
                .shadow(color: Color.appBrightCyan.opacity(0.45), radius: 14, y: 4)
            }
            .buttonStyle(.plain)
            .accessibilityHint("Opens the required consent confirmations before using OnlyTrusted")
            .accessibilityIdentifier("onlytrusted.onboarding.getStarted")
            .padding(.horizontal, 18)
            .padding(.top, 8)
            .padding(.bottom, 10)
        }
        .background(Color.appScreenBackground)
    }
}

// MARK: - Row + indicator helpers

private struct BenefitTile: View {
    let symbol: String
    let title: String
    let subtitle: String

    var body: some View {
        HStack(alignment: .top, spacing: 14) {
            // Icon chip (teal tinted square)
            Image(systemName: symbol)
                .font(.system(size: 20, weight: .semibold))
                .foregroundStyle(Color.appBrightCyan)
                .frame(width: 46, height: 46)
                .background(
                    Color.appBrightCyan.opacity(0.14),
                    in: RoundedRectangle(cornerRadius: 12, style: .continuous)
                )
                .accessibilityHidden(true)

            // Title + subtitle stack
            VStack(alignment: .leading, spacing: 3) {
                Text(title)
                    .font(.system(size: 16, weight: .semibold))
                    .foregroundStyle(Color.appPrimaryText)
                    .fixedSize(horizontal: false, vertical: true)
                Text(subtitle)
                    .font(.system(size: 13))
                    .foregroundStyle(Color.appSecondaryText)
                    .fixedSize(horizontal: false, vertical: true)
            }

            Spacer(minLength: 4)

            Image(systemName: "chevron.right")
                .font(.footnote.weight(.semibold))
                .foregroundStyle(Color.appTertiaryText)
                .accessibilityHidden(true)
        }
        .padding(.horizontal, 14)
        .padding(.vertical, 12)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(
            Color.appElevatedSurface,
            in: RoundedRectangle(cornerRadius: 16, style: .continuous)
        )
        .overlay {
            RoundedRectangle(cornerRadius: 16, style: .continuous)
                .strokeBorder(Color.appBrightCyan.opacity(0.14), lineWidth: 1)
        }
        .accessibilityElement(children: .combine)
    }
}

private struct PageDot: View {
    let active: Bool
    var body: some View {
        Circle()
            .fill(active ? Color.appAccent : Color.appAccent.opacity(0.3))
            .frame(width: active ? 9 : 7, height: active ? 9 : 7)
    }
}
