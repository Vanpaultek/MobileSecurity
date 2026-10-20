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

    var body: some View {
        ScrollView(.vertical, showsIndicators: false) {
            VStack(spacing: 16) {
                HeaderCard(onPanic: { gate.panic() })
                QuickSetupCard(
                    hasVaultEntry: !vault.contacts.isEmpty,
                    appLockOn: gate.appLockEnabled
                )
                MicCameraHoldersCard()
                HonestLimitsCard()
            }
            .padding(.horizontal, 16)
            .padding(.vertical, 16)
        }
        .background(Color(.systemGroupedBackground).ignoresSafeArea())
        .navigationTitle("Shield")
        .navigationBarTitleDisplayMode(.inline)
        .task { await vault.reload() }
    }
}

// MARK: - Cards

private struct HeaderCard: View {
    let onPanic: () -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 14) {
            HStack(spacing: 10) {
                Image(systemName: "shield.lefthalf.filled")
                    .font(.system(size: 18, weight: .semibold))
                    .foregroundStyle(Color.appAccent)
                    .decorativeAccessibility()
                Text("You're protected")
                    .font(.appSectionTitle)
            }

            Text("The microphone and camera indicator in your iOS status bar is Apple's authoritative source. This app handles your trusted-contact vault, call and SMS screening, and the Panic lock.")
                .font(.subheadline)
                .foregroundStyle(Color.appSecondaryText)
                .fixedSize(horizontal: false, vertical: true)

            Button(role: .destructive, action: onPanic) {
                Label("Panic lock", systemImage: "lock.fill")
                    .font(.body.weight(.semibold))
                    .frame(maxWidth: .infinity, minHeight: 32)
            }
            .buttonStyle(.borderedProminent)
            .tint(Color.appDanger)
            .controlSize(.large)
            .accessibilityLabel("Panic lock the app")
        }
        .padding(20)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(cardBackground)
    }
}

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
        VStack(alignment: .leading, spacing: 14) {
            HStack(spacing: 10) {
                Image(systemName: "checklist")
                    .font(.system(size: 18, weight: .semibold))
                    .foregroundStyle(Color.appAccent)
                    .decorativeAccessibility()
                Text("Quick setup")
                    .font(.appSectionTitle)
            }
            VStack(spacing: 10) {
                ForEach(Array(steps.enumerated()), id: \.offset) { _, step in
                    HStack(spacing: 12) {
                        Image(systemName: step.1 ? "checkmark.circle.fill" : "circle")
                            .foregroundStyle(step.1 ? Color.appSuccess : Color.appTertiaryText)
                            .decorativeAccessibility()
                        Text(step.0)
                            .font(.subheadline)
                            .foregroundStyle(Color.appPrimaryText)
                        Spacer()
                    }
                }
            }
        }
        .padding(20)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(cardBackground)
    }
}

private struct MicCameraHoldersCard: View {
    var body: some View {
        VStack(alignment: .leading, spacing: 14) {
            HStack(spacing: 10) {
                Image(systemName: "mic.badge.plus")
                    .font(.system(size: 18, weight: .semibold))
                    .foregroundStyle(Color.appAccent)
                    .decorativeAccessibility()
                Text("Apps using mic or camera")
                    .font(.appSectionTitle)
            }

            Text("iOS does not expose this list to third-party apps. The orange and green dot in your status bar is Apple's answer. To see the full list and revoke access per app, open the two system pages below.")
                .font(.subheadline)
                .foregroundStyle(Color.appSecondaryText)
                .fixedSize(horizontal: false, vertical: true)

            VStack(spacing: 10) {
                Button {
                    openURL("App-prefs:Privacy&path=MICROPHONE")
                } label: {
                    Label("Open Microphone settings", systemImage: "mic.fill")
                        .frame(maxWidth: .infinity, minHeight: 24)
                }
                .buttonStyle(.bordered)
                .tint(Color.appAccent)

                Button {
                    openURL("App-prefs:Privacy&path=CAMERA")
                } label: {
                    Label("Open Camera settings", systemImage: "camera.fill")
                        .frame(maxWidth: .infinity, minHeight: 24)
                }
                .buttonStyle(.bordered)
                .tint(Color.appAccent)
            }
        }
        .padding(20)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(cardBackground)
    }

    private func openURL(_ raw: String) {
        guard let url = URL(string: raw) else { return }
        UIApplication.shared.open(url)
    }
}

private struct HonestLimitsCard: View {
    var body: some View {
        VStack(alignment: .leading, spacing: 14) {
            HStack(spacing: 10) {
                Image(systemName: "info.circle.fill")
                    .font(.system(size: 18, weight: .semibold))
                    .foregroundStyle(Color.appWarning)
                    .decorativeAccessibility()
                Text("Honest limits")
                    .font(.appSectionTitle)
            }

            Text("What this app does on iOS")
                .font(.subheadline.weight(.semibold))
                .foregroundStyle(Color.appPrimaryText)
            BulletList(items: [
                "Keychain and CryptoKit encrypted family vault, biometric-gated.",
                "Call Directory for a static block list.",
                "SMS filter for unknown-sender messages.",
                "Panic lock and Face ID or Touch ID app lock."
            ])

            Text("What iOS does not let this app do (unlike Android)")
                .font(.subheadline.weight(.semibold))
                .foregroundStyle(Color.appPrimaryText)
                .padding(.top, 6)
            BulletList(items: [
                "Monitor other apps' mic or camera use. iOS reserves that for its own status bar indicator.",
                "Draw a watermark overlay on top of other apps. Not possible on iOS.",
                "Enumerate other apps' permissions. Not possible on iOS."
            ])
        }
        .padding(20)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(cardBackground)
    }
}

// MARK: - Primitives

private struct BulletList: View {
    let items: [String]
    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            ForEach(Array(items.enumerated()), id: \.offset) { _, item in
                HStack(alignment: .firstTextBaseline, spacing: 10) {
                    Circle()
                        .fill(Color.appAccent)
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

private var cardBackground: some View {
    RoundedRectangle(cornerRadius: 16, style: .continuous)
        .fill(Color(.secondarySystemGroupedBackground))
}
