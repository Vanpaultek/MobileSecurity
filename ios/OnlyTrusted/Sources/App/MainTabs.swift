// SPDX-FileCopyrightText: 2026 57Million LLC
// SPDX-License-Identifier: AGPL-3.0-or-later
import SwiftUI

struct MainTabs: View {
    @EnvironmentObject private var gate: AppGate

    var body: some View {
        TabView {
            NavigationStack { DashboardScreen() }
                .tabItem { Label("Shield", systemImage: "shield.lefthalf.filled") }

            NavigationStack { VaultScreen() }
                .tabItem { Label("Family", systemImage: "person.2.fill") }

            NavigationStack { EventsScreen() }
                .tabItem { Label("Events", systemImage: "clock.arrow.circlepath") }

            NavigationStack { BlockListScreen() }
                .tabItem { Label("Blocked", systemImage: "nosign") }

            NavigationStack { SettingsScreen() }
                .tabItem { Label("Settings", systemImage: "gearshape.fill") }
        }
        .tint(Color.appAccent)
    }
}

struct DashboardScreen: View {
    @EnvironmentObject private var gate: AppGate
    @StateObject private var vault = VaultStore.shared

    private var setupProgress: Double {
        let total = 2.0
        var done = 0.0
        if !vault.contacts.isEmpty { done += 1 }
        if gate.appLockEnabled { done += 1 }
        return done / total
    }

    var body: some View {
        ScrollView(.vertical, showsIndicators: false) {
            VStack(spacing: 20) {
                HeroCard(
                    percent: Int(setupProgress * 100),
                    allDone: setupProgress >= 1.0,
                    onPanic: { gate.panic() }
                )

                if setupProgress < 1.0 {
                    QuickSetupCard(
                        hasVaultEntry: !vault.contacts.isEmpty,
                        appLockOn: gate.appLockEnabled
                    )
                }

                MicCameraCard()
                HonestLimitsCard()
            }
            .padding(.horizontal, 20)
            .padding(.top, 8)
            .padding(.bottom, 32)
        }
        .background(Color.appScreenBackground.ignoresSafeArea())
        .navigationTitle("Shield")
        .navigationBarTitleDisplayMode(.inline)
        .task { await vault.reload() }
    }
}

// MARK: - Hero gradient card

private struct HeroCard: View {
    let percent: Int
    let allDone: Bool
    let onPanic: () -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 24) {
            // Status + big percent
            HStack(alignment: .top) {
                VStack(alignment: .leading, spacing: 6) {
                    Text(allDone ? "You're protected" : "Finish setup")
                        .font(.title3.weight(.semibold))
                        .foregroundStyle(.white)
                    Text(allDone
                         ? "Trusted-only is on. Audit chain intact."
                         : "Complete the steps below to switch on full protection.")
                        .font(.subheadline)
                        .foregroundStyle(Color.white.opacity(0.85))
                        .fixedSize(horizontal: false, vertical: true)
                }
                Spacer(minLength: 12)
                ProgressRing(percent: percent)
            }

            Button(role: .destructive, action: onPanic) {
                Label("Panic lock", systemImage: "lock.fill")
                    .font(.body.weight(.semibold))
                    .frame(maxWidth: .infinity, minHeight: 36)
            }
            .buttonStyle(.borderedProminent)
            .tint(.white.opacity(0.18))
            .foregroundStyle(.white)
            .controlSize(.large)
            .accessibilityLabel("Panic lock the app")
        }
        .padding(24)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(
            RoundedRectangle(cornerRadius: 24, style: .continuous)
                .fill(
                    LinearGradient(
                        colors: [Color.appAccent, Color.appAccent.opacity(0.75)],
                        startPoint: .topLeading,
                        endPoint: .bottomTrailing
                    )
                )
        )
    }
}

private struct ProgressRing: View {
    let percent: Int
    private let size: CGFloat = 76

    var body: some View {
        ZStack {
            Circle()
                .stroke(Color.white.opacity(0.25), lineWidth: 6)
            Circle()
                .trim(from: 0, to: Double(percent) / 100)
                .stroke(Color.white, style: StrokeStyle(lineWidth: 6, lineCap: .round))
                .rotationEffect(.degrees(-90))
            VStack(spacing: 0) {
                Text("\(percent)")
                    .font(.title3.weight(.bold))
                    .foregroundStyle(.white)
                Text("%")
                    .font(.caption2.weight(.semibold))
                    .foregroundStyle(Color.white.opacity(0.85))
            }
        }
        .frame(width: size, height: size)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel("Setup \(percent) percent complete")
    }
}

// MARK: - Quick setup

private struct QuickSetupCard: View {
    let hasVaultEntry: Bool
    let appLockOn: Bool

    private var steps: [(String, Bool)] {
        [
            ("Add yourself and family to the vault", hasVaultEntry),
            ("Enable App Lock in Settings", appLockOn)
        ]
    }

    var body: some View {
        PremiumCard {
            VStack(alignment: .leading, spacing: 18) {
                CardHeader(icon: "checklist", title: "Quick setup")
                VStack(spacing: 14) {
                    ForEach(Array(steps.enumerated()), id: \.offset) { _, step in
                        HStack(spacing: 14) {
                            Image(systemName: step.1 ? "checkmark.circle.fill" : "circle")
                                .font(.title3)
                                .foregroundStyle(step.1 ? Color.appSuccess : Color.appTertiaryText)
                                .decorativeAccessibility()
                            Text(step.0)
                                .font(.subheadline)
                                .foregroundStyle(Color.appPrimaryText)
                                .strikethrough(step.1, color: Color.appSecondaryText)
                            Spacer()
                        }
                    }
                }
            }
        }
    }
}

// MARK: - Mic & camera

private struct MicCameraCard: View {
    var body: some View {
        PremiumCard {
            VStack(alignment: .leading, spacing: 18) {
                CardHeader(icon: "mic.badge.plus", title: "Apps using mic or camera")

                Text("iOS does not expose this list to third-party apps. The orange and green dot in your status bar is Apple's answer. Open the system pages below to see every app and revoke per-app access.")
                    .font(.subheadline)
                    .foregroundStyle(Color.appSecondaryText)
                    .fixedSize(horizontal: false, vertical: true)

                VStack(spacing: 12) {
                    SystemLinkRow(icon: "mic.fill", label: "Microphone settings") {
                        openURL("App-prefs:Privacy&path=MICROPHONE")
                    }
                    SystemLinkRow(icon: "camera.fill", label: "Camera settings") {
                        openURL("App-prefs:Privacy&path=CAMERA")
                    }
                }
            }
        }
    }

    private func openURL(_ raw: String) {
        guard let url = URL(string: raw) else { return }
        UIApplication.shared.open(url)
    }
}

// MARK: - Honest limits

private struct HonestLimitsCard: View {
    var body: some View {
        PremiumCard {
            VStack(alignment: .leading, spacing: 18) {
                CardHeader(icon: "info.circle.fill", title: "Honest limits", tint: Color.appWarning)

                HonestColumn(
                    heading: "What this app does on iOS",
                    headingTint: Color.appSuccess,
                    items: [
                        "Keychain and CryptoKit encrypted family vault.",
                        "Call Directory for a static block list.",
                        "SMS filter for unknown senders.",
                        "Panic lock plus biometric app lock."
                    ]
                )

                HonestColumn(
                    heading: "What iOS does not let this app do",
                    headingTint: Color.appWarning,
                    items: [
                        "Monitor other apps' mic or camera use.",
                        "Draw a watermark on top of other apps.",
                        "Enumerate other apps' permissions."
                    ]
                )
            }
        }
    }
}

private struct HonestColumn: View {
    let heading: String
    let headingTint: Color
    let items: [String]

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            Text(heading)
                .font(.footnote.weight(.semibold))
                .textCase(.uppercase)
                .foregroundStyle(headingTint)
            VStack(alignment: .leading, spacing: 10) {
                ForEach(Array(items.enumerated()), id: \.offset) { _, item in
                    HStack(alignment: .firstTextBaseline, spacing: 12) {
                        Circle()
                            .fill(headingTint.opacity(0.5))
                            .frame(width: 5, height: 5)
                            .offset(y: -3)
                            .decorativeAccessibility()
                        Text(item)
                            .font(.subheadline)
                            .foregroundStyle(Color.appPrimaryText)
                            .fixedSize(horizontal: false, vertical: true)
                    }
                }
            }
        }
    }
}

