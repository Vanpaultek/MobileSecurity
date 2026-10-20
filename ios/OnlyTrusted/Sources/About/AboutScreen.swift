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
        Form {
            Section {
                Text("OnlyTrusted \(version) (build \(build))")
                    .font(.headline)
                Text("A product of 57Million LLC. Licensed under the GNU Affero General Public License v3.0.")
                    .font(.footnote)
                    .foregroundStyle(Color.appSecondaryText)
            }

            Section("Support") {
                LabeledContent("Email", value: "reddy@vanpaultek.com")
                Button {
                    if let url = URL(string: "mailto:reddy@vanpaultek.com?subject=OnlyTrusted%20\(version)%20support") {
                        UIApplication.shared.open(url)
                    }
                } label: {
                    Label("Open email client", systemImage: "envelope")
                }
                .tint(Color.appAccent)
            }

            Section("Changelog") {
                VStack(alignment: .leading, spacing: 8) {
                    Text("Version \(version)")
                        .font(.subheadline.weight(.semibold))
                        .foregroundStyle(Color.appPrimaryText)
                    bulletLine("Encrypted family vault with Keychain and CryptoKit.")
                    bulletLine("Biometric app lock, with safe fallback if no passcode is set.")
                    bulletLine("Call Directory for a static block list.")
                    bulletLine("SMS filter for unknown senders.")
                    bulletLine("Evidence pack export for carrier, bank, or law enforcement.")
                }
                .padding(.vertical, 4)
            }

            Section("Open-source attributions") {
                Attribution("Swift + Foundation", "Apache License 2.0", "https://swift.org/")
                Attribution("SwiftUI + CryptoKit + LocalAuthentication", "Apple SDK", "https://developer.apple.com/")
            }
        }
        .navigationTitle("About")
        .navigationBarTitleDisplayMode(.inline)
    }

    @ViewBuilder
    private func bulletLine(_ text: String) -> some View {
        HStack(alignment: .firstTextBaseline, spacing: 8) {
            Circle()
                .fill(Color.appAccent)
                .frame(width: 4, height: 4)
                .offset(y: -2)
                .accessibilityHidden(true)
            Text(text)
                .font(.footnote)
                .foregroundStyle(Color.appPrimaryText)
                .fixedSize(horizontal: false, vertical: true)
        }
    }
}

private struct Attribution: View {
    let name: String
    let license: String
    let url: String

    init(_ name: String, _ license: String, _ url: String) {
        self.name = name
        self.license = license
        self.url = url
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(name).font(.subheadline).fontWeight(.medium)
            Text("License: \(license) · \(url)").font(.caption).foregroundStyle(Color.appSecondaryText)
        }
    }
}
