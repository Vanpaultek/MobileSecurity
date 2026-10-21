// SPDX-FileCopyrightText: 2026 57Million LLC
// SPDX-License-Identifier: AGPL-3.0-or-later
import SwiftUI

struct SplashScreen: View {
    let onDone: () -> Void

    @State private var progress: CGFloat = 0.0
    @State private var glowPulse = false

    private let duration: Double = 1.4

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

            VStack(spacing: 28) {
                Spacer()

                ZStack {
                    Circle()
                        .fill(Color.appAccent.opacity(glowPulse ? 0.22 : 0.10))
                        .frame(width: 220, height: 220)
                        .blur(radius: 24)
                        .animation(.easeInOut(duration: 1.4).repeatForever(autoreverses: true),
                                   value: glowPulse)

                    Image("BrandLogo")
                        .resizable()
                        .scaledToFit()
                        .frame(width: 140, height: 140)
                        .clipShape(RoundedRectangle(cornerRadius: 32, style: .continuous))
                        .shadow(color: Color.appBrightCyan.opacity(0.35), radius: 18, x: 0, y: 0)
                        .accessibilityLabel("OnlyTrusted")
                }

                VStack(spacing: 10) {
                    Text("OnlyTrusted")
                        .font(.system(size: 32, weight: .bold, design: .rounded))
                        .foregroundStyle(Color.appPrimaryText)

                    Text("A quieter, safer phone\nfor the people who matter.")
                        .font(.subheadline)
                        .foregroundStyle(Color.appSecondaryText)
                        .multilineTextAlignment(.center)
                        .fixedSize(horizontal: false, vertical: true)
                }

                Spacer()

                VStack(spacing: 10) {
                    GeometryReader { geo in
                        ZStack(alignment: .leading) {
                            Capsule()
                                .fill(Color.appAccent.opacity(0.15))
                            Capsule()
                                .fill(Color.appAccent)
                                .frame(width: geo.size.width * progress)
                        }
                    }
                    .frame(height: 4)
                    .frame(maxWidth: 180)

                    Text("Loading…")
                        .font(.footnote)
                        .foregroundStyle(Color.appTertiaryText)
                }
                .padding(.bottom, 56)
            }
            .padding(.horizontal, 24)
        }
        .onAppear {
            glowPulse = true
            withAnimation(.linear(duration: duration)) {
                progress = 1.0
            }
            DispatchQueue.main.asyncAfter(deadline: .now() + duration) {
                onDone()
            }
        }
    }
}
