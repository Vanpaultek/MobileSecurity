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
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 16) {
                    Text("OnlyTrusted")
                        .font(.largeTitle.weight(.bold))

                    Text("This app is a serious privacy tool for the phone owner and their immediate family. It is not an entertainment or novelty app. Before you use it, please read and confirm the following.")
                        .font(.body)

                    GroupBox("What this app does on iOS") {
                        VStack(alignment: .leading, spacing: 6) {
                            Text("• Encrypted family vault with Face ID / Touch ID unlock.")
                            Text("• Call Directory Extension-based blocking (Phase 2).")
                            Text("• SMS filter for unknown-sender messages (Phase 2).")
                            Text("• Keeps every piece of data on this device only. No cloud. No ads. No analytics.")
                        }.font(.footnote)
                    }

                    GroupBox("Honest limits iOS imposes") {
                        VStack(alignment: .leading, spacing: 6) {
                            Text("• Apps on iOS cannot monitor mic/camera use by other apps — iOS reserves that for its own status bar indicator.")
                            Text("• You cannot stop the other side of a video call from recording their own device.")
                            Text("• Full screen-overlay watermarks are not possible on iOS at all.")
                            Text("• Carrier/network tracking is outside any app's control.")
                        }.font(.footnote)
                    }

                    Toggle(isOn: $isAdult) {
                        Text("I am at least 18 years old (or the legal age of digital consent in my country).")
                            .font(.footnote)
                    }
                    Toggle(isOn: $ownsDevice) {
                        Text("I own or administer this phone and will only block numbers and intercept calls on devices I am authorised to manage.")
                            .font(.footnote)
                    }
                    Toggle(isOn: $understandsLimits) {
                        Text("I have read the honest limits above and understand this is a privacy aid, not a guarantee against determined attackers.")
                            .font(.footnote)
                    }

                    Button {
                        onAccepted()
                    } label: {
                        Text("I confirm and continue").frame(maxWidth: .infinity)
                    }
                    .buttonStyle(.borderedProminent)
                    .disabled(!canContinue)
                    .padding(.top, 8)

                    Text("By continuing you also accept the Privacy Policy and Terms of Use shipped inside the app. They can be reviewed any time from Settings → Privacy, terms & honest claims.")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }
                .padding(20)
            }
            .navigationBarHidden(true)
        }
    }
}
