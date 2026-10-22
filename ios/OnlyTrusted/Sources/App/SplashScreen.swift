// SPDX-FileCopyrightText: 2026 VanPaulTek Inc
// SPDX-License-Identifier: AGPL-3.0-or-later
import SwiftUI

/// Full-screen cinematic splash. The entire scene (shield, lakescape, wordmark, tagline,
/// "Loading…" bar) is baked into the user-supplied `SplashHero` illustration — no native
/// overlays, no shape duplication. We just render the image at aspect-fill for the fixed
/// splash duration, then hand off via onDone.
struct SplashScreen: View {
    let onDone: () -> Void

    private let duration: Double = 1.4

    var body: some View {
        ZStack {
            Color.appScreenBackground.ignoresSafeArea()

            GeometryReader { proxy in
                Image("SplashHero")
                    .resizable()
                    .aspectRatio(contentMode: .fill)
                    .frame(width: proxy.size.width, height: proxy.size.height, alignment: .center)
                    .clipped()
            }
            .ignoresSafeArea()
            .accessibilityHidden(true)
        }
        .accessibilityLabel("OnlyTrusted. A quieter, safer phone for the people who matter.")
        .onAppear {
            DispatchQueue.main.asyncAfter(deadline: .now() + duration) {
                onDone()
            }
        }
    }
}
