// SPDX-FileCopyrightText: 2026 VanPaulTek Inc
// SPDX-License-Identifier: AGPL-3.0-or-later
import SwiftUI

/// Full-screen cinematic backdrop (neon shield over alpine lake) with a dark scrim on top
/// so page content stays readable. Non-interactive, no animations.
struct AppBackdrop: View {
    var body: some View {
        ZStack {
            Color.appScreenBackground
            Image("AppBackdrop")
                .resizable()
                .aspectRatio(contentMode: .fill)
                .opacity(0.55)
            LinearGradient(
                stops: [
                    .init(color: Color.appScreenBackground.opacity(0.35), location: 0.0),
                    .init(color: Color.appScreenBackground.opacity(0.55), location: 0.4),
                    .init(color: Color.appScreenBackground.opacity(0.75), location: 0.75),
                    .init(color: Color.appScreenBackground.opacity(0.92), location: 1.0)
                ],
                startPoint: .top,
                endPoint: .bottom
            )
        }
        .ignoresSafeArea()
        .accessibilityHidden(true)
        .allowsHitTesting(false)
    }
}

extension View {
    /// Replaces the plain `appScreenBackground` with the cinematic AppBackdrop.
    func appBackdrop() -> some View {
        self.background(AppBackdrop())
    }
}
