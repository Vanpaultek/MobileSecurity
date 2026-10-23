// SPDX-FileCopyrightText: 2026 VanPaulTek Inc
// SPDX-License-Identifier: AGPL-3.0-or-later
import SwiftUI
import UIKit

/// Auto Privacy Shield dashboard. Three CLEARLY-SEPARATED cards:
///
///   1. App Recording Shutdown — the real, enforced bit. OnlyTrusted stops its own
///      camera/mic sessions on background/lock. Toggles + a Stop-now button + last-10
///      events from `CaptureRegistry.history`.
///   2. Selected Apps Restriction — not configured in Phase 1 (lands in Elite Phase 2
///      via FamilyControls). We show an "Available on Elite" deep-link to Upgrade.
///   3. System Sensor Toggles — iOS does not expose system-wide sensor toggles to
///      third-party apps, so we honestly say so and deep-link to the two Apple-provided
///      surfaces that help: Control Center recording indicators + the App Privacy Report.
///
/// We deliberately do NOT collapse these into a single green "Protected" badge. Doing
/// so would misrepresent scope: only card #1 is enforced by this app.
struct PrivacyShieldDashboard: View {
    @StateObject private var state = PrivacyShieldState.shared
    @StateObject private var registry = CaptureRegistry.shared
    @State private var showUpgrade = false

    var body: some View {
        ScrollView(.vertical, showsIndicators: false) {
            VStack(alignment: .leading, spacing: 20) {
                heroCard
                appRecordingShutdownCard
                selectedAppsCard
                systemSensorTogglesCard
                hardeningAssistantButton
                limitationNote
            }
            .padding(.horizontal, 18)
            .padding(.top, 10)
            .padding(.bottom, 32)
        }
        .appBackdrop()
        .navigationTitle("Privacy Shield")
        .navigationBarTitleDisplayMode(.inline)
        .sheet(isPresented: $showUpgrade) {
            NavigationStack { UpgradeToPremiumScreen() }
        }
    }

    // MARK: - Hero

    private var heroCard: some View {
        PremiumCard {
            HStack(spacing: 14) {
                ZStack {
                    RoundedRectangle(cornerRadius: 12, style: .continuous)
                        .fill(Color.appAccent.opacity(0.18))
                    Image(systemName: "shield.lefthalf.filled.badge.checkmark")
                        .font(.system(size: 24, weight: .semibold))
                        .foregroundStyle(Color.appAccent)
                }
                .frame(width: 48, height: 48)
                .decorativeAccessibility()
                VStack(alignment: .leading, spacing: 4) {
                    Text("Three layers, honestly scoped")
                        .font(.subheadline.weight(.semibold))
                        .foregroundStyle(Color.appPrimaryText)
                    Text("Apple reserves global camera/mic toggles to supervised MDM, so no consumer app can do that. Here's what we can — and can't — do on your behalf.")
                        .font(.caption)
                        .foregroundStyle(Color.appSecondaryText)
                        .fixedSize(horizontal: false, vertical: true)
                }
            }
        }
    }

    // MARK: - Card 1: App Recording Shutdown (ENFORCED)

    private var appRecordingShutdownCard: some View {
        PremiumCard {
            VStack(alignment: .leading, spacing: 12) {
                CardHeader(icon: "shield.lefthalf.filled", title: "App Recording Shutdown")
                statusChip(color: Color.appSuccess, label: "Enforced")
                Text("OnlyTrusted's own camera and microphone sessions stop automatically when the app goes to background or the screen locks. We never auto-restart on return — you re-open the feature yourself.")
                    .font(.footnote)
                    .foregroundStyle(Color.appSecondaryText)
                    .fixedSize(horizontal: false, vertical: true)

                Toggle(isOn: $state.autoStopEnabled) {
                    Text("Enable auto-stop")
                        .font(.subheadline.weight(.semibold))
                        .foregroundStyle(Color.appPrimaryText)
                }
                .tint(Color.appAccent)

                Toggle(isOn: $state.requireManualRestart) {
                    Text("Require manual restart after unlock")
                        .font(.subheadline.weight(.semibold))
                        .foregroundStyle(Color.appPrimaryText)
                }
                .tint(Color.appAccent)

                // Live state read-out.
                HStack {
                    Image(systemName: registry.active.isEmpty ? "mic.slash" : "mic.fill")
                        .foregroundStyle(registry.active.isEmpty ? Color.appSecondaryText : Color.appWarning)
                    Text(registry.active.isEmpty
                         ? "No sessions active."
                         : "\(registry.active.count) active: \(activeFeatureList)")
                        .font(.caption)
                        .foregroundStyle(Color.appSecondaryText)
                        .fixedSize(horizontal: false, vertical: true)
                }

                if !registry.active.isEmpty {
                    Button {
                        _ = CaptureRegistry.shared.stopAll(reason: .manualStopAll)
                    } label: {
                        HStack {
                            Image(systemName: "stop.circle.fill")
                            Text("Stop recording now")
                                .font(.body.weight(.semibold))
                        }
                        .frame(maxWidth: .infinity, minHeight: 44)
                    }
                    .buttonStyle(.borderedProminent)
                    .tint(Color.appWarning)
                }

                if let latest = registry.history.first {
                    HStack(alignment: .top, spacing: 8) {
                        Image(systemName: "checkmark.circle.fill")
                            .foregroundStyle(Color.appSuccess)
                        Text("Last event: stopped \(latest.feature) \(latest.kind.rawValue) at \(Self.timeFormatter.string(from: latest.timestamp)) (\(latest.reason.rawValue)).")
                            .font(.caption)
                            .foregroundStyle(Color.appSecondaryText)
                            .fixedSize(horizontal: false, vertical: true)
                    }
                }

                if !registry.history.isEmpty {
                    DisclosureGroup("History (last \(registry.history.count))") {
                        VStack(alignment: .leading, spacing: 6) {
                            ForEach(registry.history) { ev in
                                HStack {
                                    Image(systemName: ev.kind == .camera ? "camera.fill" : "mic.fill")
                                        .foregroundStyle(Color.appSecondaryText)
                                        .font(.caption)
                                    Text("\(Self.timeFormatter.string(from: ev.timestamp)) · \(ev.feature) · \(ev.reason.rawValue)")
                                        .font(.caption2)
                                        .foregroundStyle(Color.appTertiaryText)
                                    Spacer()
                                }
                            }
                        }
                        .padding(.top, 4)
                    }
                    .tint(Color.appAccent)
                    .font(.footnote)
                }
            }
        }
    }

    private var activeFeatureList: String {
        registry.active.map { $0.feature }.joined(separator: ", ")
    }

    // MARK: - Card 2: Selected Apps Restriction (ELITE PHASE 2)

    private var selectedAppsCard: some View {
        PremiumCard {
            VStack(alignment: .leading, spacing: 12) {
                CardHeader(icon: "app.badge", title: "Selected Apps Restriction")
                statusChip(color: Color.appBrightCyan, label: "Available on Elite")
                Text("Phase 2 ships a FamilyControls-based picker so you can require Face ID / passcode before chosen apps can launch after unlock. iOS keeps global sensor toggles reserved to MDM, so we restrict launches instead of mic/camera directly.")
                    .font(.footnote)
                    .foregroundStyle(Color.appSecondaryText)
                    .fixedSize(horizontal: false, vertical: true)
                Button {
                    showUpgrade = true
                } label: {
                    HStack {
                        Image(systemName: "crown.fill")
                        Text("Upgrade to Elite")
                            .font(.body.weight(.semibold))
                    }
                    .frame(maxWidth: .infinity, minHeight: 44)
                }
                .buttonStyle(.borderedProminent)
                .tint(Color.subGold)
            }
        }
    }

    // MARK: - Card 3: System Sensor Toggles (OS-LEVEL)

    private var systemSensorTogglesCard: some View {
        PremiumCard {
            VStack(alignment: .leading, spacing: 12) {
                CardHeader(icon: "switch.2", title: "System Sensor Toggles")
                statusChip(color: Color.appSecondaryText, label: "Owned by iOS")
                Text("iOS doesn't expose system-wide camera/mic toggles to third-party apps. Use Control Center's recording indicators (orange = mic, green = camera) to spot in-the-moment use, and the App Privacy Report for a 7-day summary.")
                    .font(.footnote)
                    .foregroundStyle(Color.appSecondaryText)
                    .fixedSize(horizontal: false, vertical: true)
                Button {
                    openSettingsURL()
                } label: {
                    HStack {
                        Image(systemName: "arrow.up.right.square.fill")
                        Text("Open iOS Settings")
                            .font(.body.weight(.semibold))
                    }
                    .frame(maxWidth: .infinity, minHeight: 44)
                }
                .buttonStyle(.borderedProminent)
                .tint(Color.appAccent)
                Text("Settings → Privacy & Security → App Privacy Report. If you've never turned it on, tap Record App Activity.")
                    .font(.caption)
                    .foregroundStyle(Color.appTertiaryText)
                    .fixedSize(horizontal: false, vertical: true)
            }
        }
    }

    // MARK: - Hardening button

    private var hardeningAssistantButton: some View {
        PremiumCard {
            NavigationLink { PermissionHardeningScreen() } label: {
                HStack(spacing: 14) {
                    ZStack {
                        RoundedRectangle(cornerRadius: 11, style: .continuous)
                            .fill(
                                LinearGradient(
                                    colors: [Color.appAccent, Color.appAccent.opacity(0.75)],
                                    startPoint: .topLeading, endPoint: .bottomTrailing
                                )
                            )
                        Image(systemName: "slider.horizontal.3")
                            .font(.system(size: 20, weight: .semibold))
                            .foregroundStyle(.white)
                    }
                    .frame(width: 44, height: 44)
                    .decorativeAccessibility()
                    VStack(alignment: .leading, spacing: 2) {
                        Text("Hardening Assistant")
                            .font(.subheadline.weight(.semibold))
                            .foregroundStyle(Color.appPrimaryText)
                        Text("Review OnlyTrusted's own permissions and jump into iOS Settings.")
                            .font(.caption)
                            .foregroundStyle(Color.appSecondaryText)
                            .fixedSize(horizontal: false, vertical: true)
                    }
                    Spacer()
                    Image(systemName: "chevron.right")
                        .font(.footnote.weight(.semibold))
                        .foregroundStyle(Color.appTertiaryText)
                        .decorativeAccessibility()
                }
                .padding(.vertical, 4)
            }
            .buttonStyle(.plain)
        }
    }

    // MARK: - Limitation footer

    private var limitationNote: some View {
        Text("Why not a single green 'Protected' badge? Only card #1 is enforced by OnlyTrusted. Cards #2 and #3 depend on an Elite upgrade and on iOS Settings respectively. Collapsing them would misrepresent what this app actually does.")
            .font(.caption)
            .foregroundStyle(Color.appTertiaryText)
            .fixedSize(horizontal: false, vertical: true)
            .padding(.horizontal, 4)
    }

    // MARK: - Helpers

    private func statusChip(color: Color, label: String) -> some View {
        HStack(spacing: 6) {
            Circle().fill(color).frame(width: 8, height: 8)
            Text(label)
                .font(.caption2.weight(.semibold))
                .foregroundStyle(color)
        }
        .padding(.horizontal, 10)
        .padding(.vertical, 5)
        .background(color.opacity(0.14), in: Capsule())
    }

    private func openSettingsURL() {
        if let url = URL(string: UIApplication.openSettingsURLString) {
            UIApplication.shared.open(url)
        }
    }

    private static let timeFormatter: DateFormatter = {
        let f = DateFormatter()
        f.dateStyle = .none
        f.timeStyle = .short
        return f
    }()
}
