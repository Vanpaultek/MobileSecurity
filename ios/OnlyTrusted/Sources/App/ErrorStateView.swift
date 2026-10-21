// SPDX-FileCopyrightText: 2026 57Million LLC
// SPDX-License-Identifier: AGPL-3.0-or-later
import SwiftUI

struct ErrorStateView: View {
    let title: String
    let message: String
    let onTryAgain: (() -> Void)?
    let onGoBack: () -> Void

    init(
        title: String = "Something went wrong",
        message: String = "We couldn't complete this action. Please try again.",
        onTryAgain: (() -> Void)? = nil,
        onGoBack: @escaping () -> Void
    ) {
        self.title = title
        self.message = message
        self.onTryAgain = onTryAgain
        self.onGoBack = onGoBack
    }

    var body: some View {
        ZStack {
            RadialGradient(
                colors: [Color(red: 0.07, green: 0.19, blue: 0.23),
                         Color.appScreenBackground],
                center: .center,
                startRadius: 20,
                endRadius: 520
            )
            .ignoresSafeArea()

            VStack(spacing: 24) {
                Spacer()

                ZStack {
                    Circle()
                        .fill(Color.appDanger.opacity(0.12))
                        .frame(width: 160, height: 160)
                    Image(systemName: "cloud.rain.fill")
                        .font(.system(size: 72, weight: .regular))
                        .foregroundStyle(Color.appAccent.opacity(0.9))
                        .accessibilityHidden(true)
                }

                VStack(spacing: 10) {
                    Text(title)
                        .font(.system(size: 24, weight: .bold, design: .rounded))
                        .foregroundStyle(Color.appPrimaryText)
                        .multilineTextAlignment(.center)
                    Text(message)
                        .font(.subheadline)
                        .foregroundStyle(Color.appSecondaryText)
                        .multilineTextAlignment(.center)
                        .fixedSize(horizontal: false, vertical: true)
                }
                .padding(.horizontal, 32)

                Spacer()

                VStack(spacing: 12) {
                    if let onTryAgain {
                        Button(action: onTryAgain) {
                            Text("Try Again")
                                .font(.body.weight(.semibold))
                                .frame(maxWidth: .infinity, minHeight: 32)
                        }
                        .buttonStyle(.borderedProminent)
                        .tint(Color.appAccent)
                        .controlSize(.large)
                    }
                    Button(action: onGoBack) {
                        Text("Go Back")
                            .font(.body.weight(.semibold))
                            .frame(maxWidth: .infinity, minHeight: 32)
                    }
                    .buttonStyle(.bordered)
                    .tint(Color.appAccent)
                    .controlSize(.large)
                }
                .padding(.horizontal, 24)
                .padding(.bottom, 36)
            }
        }
    }
}
