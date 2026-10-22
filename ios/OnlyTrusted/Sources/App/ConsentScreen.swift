// SPDX-FileCopyrightText: 2026 VanPaulTek Inc
// SPDX-License-Identifier: AGPL-3.0-or-later
import SwiftUI

/// Mandatory consent screen. User must flip all three toggles ON before the
/// "I confirm and continue" button becomes enabled. Hero uses the splash
/// lakescape image + overlaid OnlyTrusted wordmark + title block.
struct ConsentScreen: View {
    let onConfirmed: () -> Void

    @State private var isAdult = false
    @State private var ownsDevice = false
    @State private var understandsLimits = false
    @State private var legalSheetSection: String?

    private var canContinue: Bool { isAdult && ownsDevice && understandsLimits }

    var body: some View {
        VStack(spacing: 0) {
            ScrollView(.vertical, showsIndicators: false) {
                VStack(spacing: 22) {
                    cinematicHeader
                    consentCard
                }
                .padding(.horizontal, 18)
                .padding(.top, 0)
                .padding(.bottom, 20)
            }

            actionBar
        }
        .background(Color.appScreenBackground.ignoresSafeArea())
        .sheet(item: Binding(
            get: { legalSheetSection.map(LegalSheetTarget.init) },
            set: { legalSheetSection = $0?.title }
        )) { target in
            NavigationStack {
                LegalScreen(initialSectionTitle: target.title)
                    .toolbar {
                        ToolbarItem(placement: .topBarTrailing) {
                            Button("Done") { legalSheetSection = nil }
                                .tint(Color.appAccent)
                        }
                    }
            }
        }
    }

    // MARK: - Cinematic header: hero image + wordmark + title + subtitle

    private var cinematicHeader: some View {
        VStack(spacing: 14) {
            // Hero (splash lakescape) — show ONLY the shield + mountain band at the top of
            // the SplashHero PNG. The baked "OnlyTrusted" wordmark + tagline live further
            // down in the asset and are deliberately cropped off so the native "Please
            // confirm" title + subtitle below take their place.
            ZStack(alignment: .bottom) {
                GeometryReader { proxy in
                    Image("SplashHero")
                        .resizable()
                        .aspectRatio(contentMode: .fill)
                        .frame(width: proxy.size.width,
                               height: proxy.size.width * (1672.0 / 941.0),
                               alignment: .top)
                        .frame(width: proxy.size.width, height: proxy.size.height, alignment: .top)
                        .clipped()
                        .overlay(alignment: .bottom) {
                            LinearGradient(
                                colors: [
                                    .clear,
                                    Color.appScreenBackground.opacity(0.6),
                                    Color.appScreenBackground
                                ],
                                startPoint: .top,
                                endPoint: .bottom
                            )
                            .frame(height: 90)
                        }
                }
                .frame(height: 280)
            }

            // "Please confirm" title + subtitle
            VStack(spacing: 10) {
                Text("Please confirm")
                    .font(.system(size: 30, weight: .bold, design: .rounded))
                    .foregroundStyle(Color.appPrimaryText)
                    .multilineTextAlignment(.center)
                Text("To use OnlyTrusted, you must review and confirm the following.")
                    .font(.subheadline)
                    .foregroundStyle(Color.appSecondaryText)
                    .multilineTextAlignment(.center)
                    .fixedSize(horizontal: false, vertical: true)
                    .padding(.horizontal, 10)
            }
        }
    }

    // MARK: - Three toggle cards

    private var consentCard: some View {
        VStack(spacing: 12) {
            ConsentToggleCard(
                symbol: "person.fill",
                title: "I am an adult",
                detail: "I am 18 years or older.",
                isOn: $isAdult
            )
            ConsentToggleCard(
                symbol: "iphone.gen3",
                title: "I own this device or am authorized to use it",
                detail: "I have the right to use and configure this device.",
                isOn: $ownsDevice
            )
            ConsentToggleCard(
                symbol: "shield.lefthalf.filled",
                title: "I have read and understood the honest limits",
                detail: "I understand what OnlyTrusted can do, what it cannot do, and any limitations on iOS.",
                isOn: $understandsLimits
            )
        }
    }

    // MARK: - Legal footnote (links underlined with teal)

    private var legalFootnote: some View {
        HStack(spacing: 0) {
            legalLink("Privacy Policy", section: "Privacy policy")
            legalDivider
            legalLink("Terms of Use", section: "Terms of use")
            legalDivider
            legalLink("Honest Claims", section: "Honest claims")
        }
        .frame(maxWidth: .infinity)
    }

    private var legalDivider: some View {
        Text("|")
            .foregroundStyle(Color.appTertiaryText)
            .font(.footnote)
            .padding(.horizontal, 10)
    }

    private func legalLink(_ label: String, section: String) -> some View {
        Button(action: { legalSheetSection = section }) {
            Text(label)
                .font(.footnote.weight(.semibold))
                .underline()
                .foregroundStyle(Color.appAccent)
        }
        .buttonStyle(.plain)
    }

    // MARK: - Sticky action bar

    private var actionBar: some View {
        VStack(spacing: 10) {
            Button(action: onConfirmed) {
                HStack(spacing: 8) {
                    Spacer()
                    Text("I confirm and continue")
                        .font(.system(size: 17, weight: .bold, design: .rounded))
                        .foregroundStyle(canContinue
                                          ? Color.appScreenBackground
                                          : Color.appTertiaryText)
                    Image(systemName: "chevron.right")
                        .font(.system(size: 14, weight: .bold))
                        .foregroundStyle(canContinue
                                          ? Color.appScreenBackground
                                          : Color.appTertiaryText)
                    Spacer()
                }
                .frame(maxWidth: .infinity, minHeight: 54)
                .background(
                    RoundedRectangle(cornerRadius: 28, style: .continuous)
                        .fill(
                            canContinue
                                ? AnyShapeStyle(LinearGradient(
                                    colors: [Color.appBrightCyan, Color.appAccent],
                                    startPoint: .topLeading, endPoint: .bottomTrailing))
                                : AnyShapeStyle(Color.appElevatedSurface.opacity(0.8))
                        )
                )
                .shadow(color: canContinue ? Color.appBrightCyan.opacity(0.45) : .clear,
                        radius: 14, y: 4)
            }
            .buttonStyle(.plain)
            .disabled(!canContinue)
            .padding(.horizontal, 18)

            legalFootnote
                .padding(.bottom, 8)
        }
        .padding(.top, 8)
        .background(Color.appScreenBackground)
    }
}

// MARK: - Consent toggle card with icon chip + title + detail + toggle

private struct ConsentToggleCard: View {
    let symbol: String
    let title: String
    let detail: String
    @Binding var isOn: Bool

    var body: some View {
        HStack(alignment: .top, spacing: 14) {
            // Teal icon chip
            Image(systemName: symbol)
                .font(.system(size: 22, weight: .semibold))
                .foregroundStyle(Color.appBrightCyan)
                .frame(width: 48, height: 48)
                .background(
                    Color.appBrightCyan.opacity(0.14),
                    in: RoundedRectangle(cornerRadius: 12, style: .continuous)
                )
                .accessibilityHidden(true)

            // Title + detail stack
            VStack(alignment: .leading, spacing: 4) {
                Text(title)
                    .font(.system(size: 16, weight: .semibold))
                    .foregroundStyle(Color.appPrimaryText)
                    .fixedSize(horizontal: false, vertical: true)
                Text(detail)
                    .font(.system(size: 13))
                    .foregroundStyle(Color.appSecondaryText)
                    .fixedSize(horizontal: false, vertical: true)
            }

            Spacer(minLength: 4)

            Toggle("", isOn: $isOn)
                .labelsHidden()
                .tint(Color.appAccent)
        }
        .padding(.horizontal, 14)
        .padding(.vertical, 14)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(
            Color.appElevatedSurface,
            in: RoundedRectangle(cornerRadius: 16, style: .continuous)
        )
        .overlay {
            RoundedRectangle(cornerRadius: 16, style: .continuous)
                .strokeBorder(Color.appBrightCyan.opacity(0.14), lineWidth: 1)
        }
    }
}

private struct LegalSheetTarget: Identifiable {
    let title: String
    var id: String { title }
}
