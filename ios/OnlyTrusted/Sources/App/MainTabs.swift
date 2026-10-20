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
    }
}

struct DashboardScreen: View {
    @EnvironmentObject private var gate: AppGate
    @StateObject private var vault = VaultStore.shared

    var body: some View {
        ScrollView {
            VStack(spacing: 12) {
                HeaderCard(onPanic: { gate.panic() })
                QuickSetupCard(
                    hasVaultEntry: !vault.contacts.isEmpty,
                    appLockOn: gate.appLockEnabled
                )
                MicCameraHoldersCard()
                HonestLimitsCard()
            }
            .padding(16)
        }
        .navigationTitle("Shield")
        .navigationBarTitleDisplayMode(.inline)
        .task { await vault.reload() }
    }
}

private struct HeaderCard: View {
    let onPanic: () -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            Label("You're protected", systemImage: "shield.lefthalf.filled")
                .font(.title2.weight(.semibold))

            Text("Mic & camera monitoring is managed by iOS itself. This app handles your trusted-contact vault, call & SMS screening, and the Panic lock.")
                .font(.subheadline)
                .foregroundStyle(Color.appSecondaryText)

            Button(role: .destructive, action: onPanic) {
                Label("Panic lock", systemImage: "lock.fill")
                    .frame(maxWidth: .infinity)
            }
            .buttonStyle(.borderedProminent)
            .tint(.red)
        }
        .padding(16)
        .background(RoundedRectangle(cornerRadius: 16).fill(Color(.secondarySystemBackground)))
    }
}

private struct QuickSetupCard: View {
    let hasVaultEntry: Bool
    let appLockOn: Bool

    private var steps: [(String, Bool)] {
        [
            ("Add yourself + family to the vault", hasVaultEntry),
            ("Enable App Lock in Settings", appLockOn)
        ]
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("Quick setup").font(.headline)
            ForEach(Array(steps.enumerated()), id: \.offset) { _, step in
                HStack {
                    Image(systemName: step.1 ? "checkmark.circle.fill" : "circle")
                        .foregroundStyle(step.1 ? .green : .secondary)
                    Text(step.0).font(.subheadline)
                    Spacer()
                }
            }
        }
        .padding(16)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(RoundedRectangle(cornerRadius: 16).fill(Color(.secondarySystemBackground)))
    }
}

private struct MicCameraHoldersCard: View {
    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            Label("Apps that can use mic / camera", systemImage: "mic.badge.plus")
                .font(.headline)

            Text("iOS does not expose this list to third-party apps. The orange/green dot in your status bar is Apple's answer. For the full list and the ability to revoke access per app, open the two system pages below.")
                .font(.footnote)
                .foregroundStyle(Color.appSecondaryText)

            Button {
                openURL("App-prefs:Privacy&path=MICROPHONE")
            } label: {
                Label("Open Privacy → Microphone", systemImage: "mic.fill")
                    .frame(maxWidth: .infinity)
            }
            .buttonStyle(.bordered)

            Button {
                openURL("App-prefs:Privacy&path=CAMERA")
            } label: {
                Label("Open Privacy → Camera", systemImage: "camera.fill")
                    .frame(maxWidth: .infinity)
            }
            .buttonStyle(.bordered)
        }
        .padding(16)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(RoundedRectangle(cornerRadius: 16).fill(Color(.secondarySystemBackground)))
    }

    private func openURL(_ raw: String) {
        guard let url = URL(string: raw) else { return }
        UIApplication.shared.open(url)
    }
}

private struct HonestLimitsCard: View {
    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            Text("What iOS lets this app do").font(.headline)
            Text("• Keychain + CryptoKit-encrypted family vault, biometric-gated.")
            Text("• Call Directory Extension for a static block list (Phase 2).")
            Text("• SMS filter for unknown-sender messages only (Phase 2).")
            Text("• Panic lock + Face ID / Touch ID app lock.")
            Text("")
            Text("What iOS does NOT let this app do (unlike Android)").font(.headline)
            Text("• Monitor other apps' mic/camera use — iOS reserves that for its own status bar indicator.")
            Text("• Draw a watermark overlay on top of other apps — not possible on iOS.")
            Text("• Enumerate other apps' permissions — not possible on iOS.")
        }
        .font(.footnote)
        .padding(16)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(RoundedRectangle(cornerRadius: 16).fill(Color(.secondarySystemBackground)))
    }
}

