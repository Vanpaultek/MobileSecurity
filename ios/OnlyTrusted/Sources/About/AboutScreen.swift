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
                    .foregroundStyle(.secondary)
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
            }

            Section("Changelog") {
                Text("0.1.0 — Initial iOS release (Phase 1):\n• Keychain + CryptoKit encrypted family vault.\n• Biometric app lock.\n• Onboarding + legal + settings.\n\nPhase 2 (planned):\n• Call Directory Extension for a static block list.\n• IdentityLookup extension for SMS filtering.\n• Evidence pack export.")
                    .font(.footnote)
            }

            Section("Open-source attributions") {
                Attribution("Swift + Foundation", "Apache License 2.0", "https://swift.org/")
                Attribution("SwiftUI + CryptoKit + LocalAuthentication", "Apple SDK", "https://developer.apple.com/")
            }
        }
        .navigationTitle("About")
        .navigationBarTitleDisplayMode(.inline)
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
            Text("License: \(license) · \(url)").font(.caption).foregroundStyle(.secondary)
        }
    }
}
