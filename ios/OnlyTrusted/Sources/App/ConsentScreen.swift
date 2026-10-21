// SPDX-FileCopyrightText: 2026 57Million LLC
// SPDX-License-Identifier: AGPL-3.0-or-later
import SwiftUI

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
                VStack(spacing: 20) {
                    header
                    consentCard
                    legalFootnote
                }
                .padding(.horizontal, 24)
                .padding(.top, 24)
                .padding(.bottom, 24)
            }
            .safeAreaPadding(.top)

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

    private var header: some View {
        VStack(spacing: 10) {
            ZStack {
                Circle()
                    .fill(Color.appAccent.opacity(0.14))
                Image(systemName: "checkmark.shield.fill")
                    .font(.system(size: 34, weight: .semibold))
                    .foregroundStyle(Color.appAccent)
            }
            .frame(width: 80, height: 80)
            .decorativeAccessibility()

            Text("Please confirm")
                .font(.system(size: 28, weight: .bold, design: .rounded))
                .foregroundStyle(Color.appPrimaryText)
                .multilineTextAlignment(.center)

            Text("This app is for people who need strong privacy and safety.")
                .font(.subheadline)
                .foregroundStyle(Color.appSecondaryText)
                .multilineTextAlignment(.center)
                .fixedSize(horizontal: false, vertical: true)
        }
        .frame(maxWidth: .infinity)
    }

    private var consentCard: some View {
        PremiumCard {
            VStack(spacing: 0) {
                ConsentToggleRow(
                    title: "I am an adult",
                    detail: "I am 18 years or older.",
                    isOn: $isAdult
                )
                Divider().padding(.leading, 20)
                ConsentToggleRow(
                    title: "I am the device owner",
                    detail: "This is my personal device.",
                    isOn: $ownsDevice
                )
                Divider().padding(.leading, 20)
                ConsentToggleRow(
                    title: "I have read and understood the honest limits",
                    detail: "I understand what this app can and cannot do.",
                    isOn: $understandsLimits
                )
            }
        }
    }

    private var legalFootnote: some View {
        HStack(spacing: 18) {
            legalLink("Privacy Policy", section: "Privacy policy")
            Text("|").foregroundStyle(Color.appTertiaryText).font(.footnote)
            legalLink("Terms of Use", section: "Terms of use")
            Text("|").foregroundStyle(Color.appTertiaryText).font(.footnote)
            legalLink("Honest Claims", section: "Honest claims")
        }
        .frame(maxWidth: .infinity)
    }

    private func legalLink(_ label: String, section: String) -> some View {
        Button(label) { legalSheetSection = section }
            .font(.footnote.weight(.semibold))
            .tint(Color.appAccent)
    }

    private var actionBar: some View {
        VStack(spacing: 0) {
            Divider()
            Button(action: onConfirmed) {
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

private struct ConsentToggleRow: View {
    let title: String
    let detail: String
    @Binding var isOn: Bool

    var body: some View {
        Toggle(isOn: $isOn) {
            VStack(alignment: .leading, spacing: 4) {
                Text(title)
                    .font(.subheadline.weight(.semibold))
                    .foregroundStyle(Color.appPrimaryText)
                Text(detail)
                    .font(.caption)
                    .foregroundStyle(Color.appSecondaryText)
                    .fixedSize(horizontal: false, vertical: true)
            }
            .padding(.trailing, 8)
        }
        .tint(Color.appAccent)
        .padding(.vertical, 12)
    }
}

private struct LegalSheetTarget: Identifiable {
    let title: String
    var id: String { title }
}
