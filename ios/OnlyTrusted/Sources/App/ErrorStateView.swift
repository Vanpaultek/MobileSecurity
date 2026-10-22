// SPDX-FileCopyrightText: 2026 VanPaulTek Inc
// SPDX-License-Identifier: AGPL-3.0-or-later
import SwiftUI

/// Full-screen error state. Large neon sad-cloud illustration with angry eyebrows + frown
/// + two "falling star" shapes beneath, then title + message, then a bright teal "Try Again"
/// pill with heavy outer glow and an outlined cyan "Go Back" pill below. Matches the new
/// neon mockup.
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
                colors: [
                    Color(red: 0.05, green: 0.14, blue: 0.20),
                    Color.appScreenBackground
                ],
                center: .center,
                startRadius: 20,
                endRadius: 560
            )
            .ignoresSafeArea()

            VStack(spacing: 28) {
                Spacer(minLength: 16)

                SadCloudIllustration()
                    .frame(width: 220, height: 220)

                VStack(spacing: 10) {
                    Text(title)
                        .font(.system(size: 28, weight: .bold, design: .rounded))
                        .foregroundStyle(Color.appPrimaryText)
                        .multilineTextAlignment(.center)
                    Text(message)
                        .font(.system(size: 16))
                        .foregroundStyle(Color.appSecondaryText)
                        .multilineTextAlignment(.center)
                        .fixedSize(horizontal: false, vertical: true)
                }
                .padding(.horizontal, 32)

                Spacer(minLength: 16)

                VStack(spacing: 14) {
                    if let onTryAgain {
                        Button(action: onTryAgain) {
                            Text("Try Again")
                                .font(.system(size: 18, weight: .bold, design: .rounded))
                                .foregroundStyle(Color.appScreenBackground)
                                .frame(maxWidth: .infinity, minHeight: 56)
                                .background(
                                    Capsule(style: .continuous)
                                        .fill(
                                            LinearGradient(
                                                colors: [Color.appBrightCyan, Color.appAccent],
                                                startPoint: .leading, endPoint: .trailing
                                            )
                                        )
                                        .shadow(color: Color.appBrightCyan.opacity(0.65),
                                                radius: 24, x: 0, y: 0)
                                        .shadow(color: Color.appBrightCyan.opacity(0.35),
                                                radius: 48, x: 0, y: 6)
                                )
                        }
                        .buttonStyle(.plain)
                        .accessibilityLabel("Try again")
                    }
                    Button(action: onGoBack) {
                        Text("Go Back")
                            .font(.system(size: 18, weight: .bold, design: .rounded))
                            .foregroundStyle(Color.appBrightCyan)
                            .frame(maxWidth: .infinity, minHeight: 56)
                            .background(
                                Capsule(style: .continuous)
                                    .fill(Color.clear)
                                    .overlay(
                                        Capsule(style: .continuous)
                                            .stroke(Color.appBrightCyan.opacity(0.7), lineWidth: 1.5)
                                    )
                                    .shadow(color: Color.appBrightCyan.opacity(0.25),
                                            radius: 10, x: 0, y: 0)
                            )
                    }
                    .buttonStyle(.plain)
                    .accessibilityLabel("Go back")
                }
                .padding(.horizontal, 24)
                .padding(.bottom, 32)
            }
        }
    }
}

// MARK: - Sad cloud illustration (SwiftUI shapes with teal glow)

private struct SadCloudIllustration: View {
    var body: some View {
        GeometryReader { proxy in
            let w = proxy.size.width
            let h = proxy.size.height
            let cx = w / 2
            let cloudCy = h * 0.42

            ZStack {
                // Cloud silhouette (overlapping circles)
                let cloudColor = LinearGradient(
                    colors: [Color.appBrightCyan.opacity(0.7),
                             Color.appPrimaryTeal.opacity(0.75)],
                    startPoint: .top, endPoint: .bottom
                )

                ZStack {
                    // Base ellipse (bottom)
                    Capsule()
                        .frame(width: w * 0.78, height: h * 0.26)
                        .position(x: cx, y: cloudCy + h * 0.06)
                    // Left bump
                    Circle()
                        .frame(width: w * 0.36, height: w * 0.36)
                        .position(x: cx - w * 0.22, y: cloudCy - h * 0.02)
                    // Center bump
                    Circle()
                        .frame(width: w * 0.46, height: w * 0.46)
                        .position(x: cx, y: cloudCy - h * 0.10)
                    // Right bump
                    Circle()
                        .frame(width: w * 0.34, height: w * 0.34)
                        .position(x: cx + w * 0.22, y: cloudCy - h * 0.03)
                }
                .foregroundStyle(cloudColor)
                .shadow(color: Color.appBrightCyan.opacity(0.6), radius: 24, x: 0, y: 0)
                .shadow(color: Color.appBrightCyan.opacity(0.35), radius: 48, x: 0, y: 10)

                // Angry eyebrows + eyes
                let eyeY = cloudCy + h * 0.00
                let eyebrowY = eyeY - h * 0.045
                let eyeOffset = w * 0.095
                // Left eyebrow (angry diagonal)
                Path { p in
                    p.move(to: CGPoint(x: cx - eyeOffset - w * 0.035, y: eyebrowY + h * 0.012))
                    p.addLine(to: CGPoint(x: cx - eyeOffset + w * 0.025, y: eyebrowY - h * 0.015))
                }
                .stroke(Color.white, style: StrokeStyle(lineWidth: 3.5, lineCap: .round))
                .shadow(color: Color.appBrightCyan.opacity(0.5), radius: 4)
                // Right eyebrow (angry diagonal mirrored)
                Path { p in
                    p.move(to: CGPoint(x: cx + eyeOffset + w * 0.035, y: eyebrowY + h * 0.012))
                    p.addLine(to: CGPoint(x: cx + eyeOffset - w * 0.025, y: eyebrowY - h * 0.015))
                }
                .stroke(Color.white, style: StrokeStyle(lineWidth: 3.5, lineCap: .round))
                .shadow(color: Color.appBrightCyan.opacity(0.5), radius: 4)
                // Eyes (vertical stripes)
                RoundedRectangle(cornerRadius: 2)
                    .fill(Color.white)
                    .frame(width: 4, height: 14)
                    .position(x: cx - eyeOffset, y: eyeY + h * 0.025)
                RoundedRectangle(cornerRadius: 2)
                    .fill(Color.white)
                    .frame(width: 4, height: 14)
                    .position(x: cx + eyeOffset, y: eyeY + h * 0.025)
                // Frown
                Path { p in
                    p.addArc(
                        center: CGPoint(x: cx, y: cloudCy + h * 0.11),
                        radius: w * 0.08,
                        startAngle: .degrees(200),
                        endAngle: .degrees(340),
                        clockwise: true
                    )
                }
                .stroke(Color.white, style: StrokeStyle(lineWidth: 3, lineCap: .round))
                .shadow(color: Color.appBrightCyan.opacity(0.5), radius: 4)

                // Two "falling stars" below the cloud
                NeonStar(size: w * 0.11)
                    .position(x: cx - w * 0.12, y: h * 0.84)
                NeonStar(size: w * 0.11)
                    .position(x: cx + w * 0.12, y: h * 0.84)
            }
        }
        .decorativeAccessibility()
    }
}

/// Simple 5-point star shape with cyan fill + glow.
private struct NeonStar: View {
    let size: CGFloat

    var body: some View {
        StarShape()
            .fill(Color.appBrightCyan)
            .frame(width: size, height: size)
            .shadow(color: Color.appBrightCyan.opacity(0.7), radius: 8, x: 0, y: 0)
    }
}

private struct StarShape: Shape {
    func path(in rect: CGRect) -> Path {
        let center = CGPoint(x: rect.midX, y: rect.midY)
        let outer = min(rect.width, rect.height) / 2
        let inner = outer * 0.42
        let pointCount = 5
        var p = Path()
        for i in 0..<(pointCount * 2) {
            let radius = i.isMultiple(of: 2) ? outer : inner
            let angle = -CGFloat.pi / 2 + CGFloat(i) * .pi / CGFloat(pointCount)
            let x = center.x + radius * cos(angle)
            let y = center.y + radius * sin(angle)
            if i == 0 { p.move(to: CGPoint(x: x, y: y)) }
            else { p.addLine(to: CGPoint(x: x, y: y)) }
        }
        p.closeSubpath()
        return p
    }
}
