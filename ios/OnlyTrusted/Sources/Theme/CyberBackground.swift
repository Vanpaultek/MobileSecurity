// SPDX-FileCopyrightText: 2026 VanPaulTek Inc
// SPDX-License-Identifier: AGPL-3.0-or-later
import SwiftUI

/// Subtle, non-interactive lighting. No animations or status claims.
struct CyberBackground: View {
    var body: some View {
        GeometryReader { proxy in
            ZStack(alignment: .topTrailing) {
                Color.appScreenBackground
                Circle()
                    .fill(Color.appBrightCyan.opacity(0.065))
                    .frame(width: 270, height: 270)
                    .blur(radius: 85)
                    .offset(x: 100, y: -120)
                Circle()
                    .fill(Color.appAccent.opacity(0.035))
                    .frame(width: 220, height: 220)
                    .blur(radius: 90)
                    .offset(x: -proxy.size.width * 0.60, y: proxy.size.height * 0.42)
            }
            .frame(width: proxy.size.width, height: proxy.size.height)
        }
        .ignoresSafeArea()
        .accessibilityHidden(true)
        .allowsHitTesting(false)
    }
}
