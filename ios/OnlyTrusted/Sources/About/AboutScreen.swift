// SPDX-FileCopyrightText: 2026 57Million LLC
// SPDX-License-Identifier: AGPL-3.0-or-later
import SwiftUI

struct AboutScreen: View {
    private var version: String {
        Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String ?? "?"
    }
    private var build: String {
        Bundle.main.infoDictionary?["CFBundleVersion"] as? String ?? "?"
    }

    var body: some View {
        ScrollView(.vertical, showsIndicators: false) {
            VStack(spacing: 18) {
                heroCard
                supportCard
                changelogCard
                attributionsCard
            }
            .padding(.horizontal, 20)
            .padding(.top, 12)
            .padding(.bottom, 32)
        }
        .background(Color(.systemGroupedBackground).ignoresSafeArea())
        .navigationTitle("About")
        .navigationBarTitleDisplayMode(.inline)
    }

    private var heroCard: some View {
        PremiumCard {
            VStack(alignment: .leading, spacing: 14) {
                HStack(spacing: 14) {
                    ZStack {
                        RoundedRectangle(cornerRadius: 16, style: .continuous)
                            .fill(
                                LinearGradient(
                                    colors: [Color.appAccent, Color.appAccent.opacity(0.65)],
                                    startPoint: .topLeading,
                                    endPoint: .bottomTrailing
                                )
                            )
                        Image(systemName: "shield.lefthalf.filled")
                            .font(.title.weight(.semibold))
                            .foregroundStyle(.white)
                    }
                    .frame(width: 56, height: 56)
                    .decorativeAccessibility()

                    VStack(alignment: .leading, spacing: 2) {
                        Text("OnlyTrusted")
                            .font(.title2.weight(.bold))
                            .foregroundStyle(Color.appPrimaryText)
                        Text("Version \(version) (build \(build))")
                            .font(.footnote)
                            .foregroundStyle(Color.appSecondaryText)
                    }
                    Spacer()
                }
                Text("A product of 57Million LLC. Licensed under the GNU Affero General Public License v3.0.")
                    .font(.footnote)
                    .foregroundStyle(Color.appSecondaryText)
                    .fixedSize(horizontal: false, vertical: true)
            }
        }
    }

    private var supportCard: some View {
        PremiumCard {
            VStack(alignment: .leading, spacing: 14) {
                CardHeader(icon: "envelope.fill", title: "Support")
                SystemLinkRow(icon: "paperplane.fill", label: "Email reddy@vanpaultek.com") {
                    if let url = URL(string: "mailto:reddy@vanpaultek.com?subject=OnlyTrusted%20\(version)%20support") {
                        UIApplication.shared.open(url)
                    }
                }
            }
        }
    }

    private var changelogCard: some View {
        PremiumCard {
            VStack(alignment: .leading, spacing: 14) {
                CardHeader(icon: "sparkles", title: "What's inside")
                VStack(alignment: .leading, spacing: 10) {
                    bulletLine("Encrypted family vault with Keychain and CryptoKit.")
                    bulletLine("Biometric app lock, with safe fallback if no passcode is set.")
                    bulletLine("Call Directory for a static block list.")
                    bulletLine("SMS filter for unknown senders.")
                    bulletLine("Evidence pack export for carrier, bank, or law enforcement.")
                }
            }
        }
    }

    private var attributionsCard: some View {
        PremiumCard {
            VStack(alignment: .leading, spacing: 14) {
                CardHeader(icon: "chevron.left.forwardslash.chevron.right", title: "Open-source attributions")
                VStack(alignment: .leading, spacing: 14) {
                    attribution("Swift and Foundation", "Apache License 2.0", "swift.org")
                    attribution("SwiftUI, CryptoKit, LocalAuthentication", "Apple SDK", "developer.apple.com")
                }
            }
        }
    }

    @ViewBuilder
    private func bulletLine(_ text: String) -> some View {
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

    @ViewBuilder
    private func attribution(_ name: String, _ license: String, _ url: String) -> some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(name).font(.subheadline.weight(.semibold)).foregroundStyle(Color.appPrimaryText)
            Text("\(license) · \(url)")
                .font(.caption)
                .foregroundStyle(Color.appSecondaryText)
        }
    }
}
