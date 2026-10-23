// SPDX-FileCopyrightText: 2026 VanPaulTek Inc
// SPDX-License-Identifier: AGPL-3.0-or-later
import SwiftUI
import UIKit
#if canImport(FamilyControls)
import FamilyControls
#endif

/// Elite-tier setup wizard for "Auto Privacy Shield — Phase 2: Selected App Restriction".
///
/// Caller gates on `SubscriptionState.hasElite`; this screen assumes the user is Elite.
///
/// Flow:
///   1. Honest explainer card — what Family Controls does + the "selected apps restricted,
///      not sensor-disabled" caveat.
///   2. Authorization button (iOS asks the user; simulator path shows the honest fallback).
///   3. `FamilyActivityPicker` sheet → user picks apps.
///   4. Enable toggle → applies / clears the shield via `AppShieldManager`.
///   5. Status chip: "N apps shielded via Family Controls."
struct AppShieldSetupScreen: View {
    @Environment(\.dismiss) private var dismiss
    @StateObject private var state = AppShieldState.shared
    @State private var showPicker = false
    @State private var showAuthErrorAlert = false

    var body: some View {
        ScrollView {
            VStack(spacing: 18) {
                explainerCard
                authorizationCard
                selectionCard
                statusFooter
            }
            .padding(.horizontal, 18)
            .padding(.top, 12)
            .padding(.bottom, 32)
        }
        .appBackdrop()
        .navigationTitle("App Shield")
        .navigationBarTitleDisplayMode(.inline)
        .onAppear {
            AppShieldManager.rehydrate()
        }
        .alert("Family Controls unavailable", isPresented: $showAuthErrorAlert) {
            Button("Open Settings") {
                if let url = URL(string: UIApplication.openSettingsURLString) {
                    UIApplication.shared.open(url)
                }
            }
            Button("OK", role: .cancel) {}
        } message: {
            Text("Family Controls requires a real device and Apple's manual approval of the family-controls entitlement. Simulators and un-approved builds cannot apply the shield.")
        }
        #if canImport(FamilyControls)
        .sheet(isPresented: $showPicker) {
            FamilyActivityPickerSheet(onDone: { newSelection in
                state.persistSelection(newSelection)
                // If the shield is already on, re-apply with the new selection so the
                // user's edits take effect immediately.
                if state.enabled {
                    _ = AppShieldManager.applyShield()
                }
                showPicker = false
            }, onCancel: {
                showPicker = false
            })
        }
        #endif
    }

    // MARK: - Explainer

    private var explainerCard: some View {
        PremiumCard {
            VStack(alignment: .leading, spacing: 10) {
                CardHeader(icon: "app.badge.checkmark.fill", title: "What this actually does")
                Text("With your approval, iOS Family Controls lets OnlyTrusted shield the apps you pick: they become inaccessible at the OS level until you turn the shield off here.")
                    .font(.footnote)
                    .foregroundStyle(Color.appSecondaryText)
                    .fixedSize(horizontal: false, vertical: true)
                HStack(alignment: .top, spacing: 10) {
                    Image(systemName: "exclamationmark.triangle.fill")
                        .foregroundStyle(Color.appWarning)
                        .font(.footnote)
                    Text("This blocks app launches, not sensors. Apple reserves global camera / microphone toggles to supervised MDM, so no consumer app can disable them.")
                        .font(.caption)
                        .foregroundStyle(Color.appTertiaryText)
                        .fixedSize(horizontal: false, vertical: true)
                }
            }
        }
    }

    // MARK: - Authorization

    private var authorizationCard: some View {
        PremiumCard {
            VStack(alignment: .leading, spacing: 12) {
                CardHeader(icon: "person.badge.shield.checkmark.fill", title: "Authorization")

                if !AppShieldState.isFamilyControlsAvailable {
                    HStack(spacing: 8) {
                        Image(systemName: "exclamationmark.octagon.fill")
                            .foregroundStyle(Color.appDanger)
                        Text(simulatorMessage)
                            .font(.footnote)
                            .foregroundStyle(Color.appSecondaryText)
                            .fixedSize(horizontal: false, vertical: true)
                    }
                } else {
                    HStack(spacing: 8) {
                        Image(systemName: statusIcon)
                            .foregroundStyle(statusColor)
                        Text(statusLabel)
                            .font(.footnote.weight(.semibold))
                            .foregroundStyle(statusColor)
                    }
                    Text(authorizationCopy)
                        .font(.caption)
                        .foregroundStyle(Color.appSecondaryText)
                        .fixedSize(horizontal: false, vertical: true)

                    if state.authorization != .approved {
                        Button {
                            Task {
                                let result = await AppShieldManager.requestAuthorization()
                                if result == .denied {
                                    showAuthErrorAlert = true
                                }
                            }
                        } label: {
                            Text("Request authorization")
                                .font(.body.weight(.semibold))
                                .frame(maxWidth: .infinity, minHeight: 44)
                        }
                        .buttonStyle(.borderedProminent)
                        .tint(Color.appAccent)
                    }
                }
            }
        }
    }

    // MARK: - Selection + toggle

    private var selectionCard: some View {
        PremiumCard {
            VStack(alignment: .leading, spacing: 12) {
                CardHeader(icon: "square.grid.3x3.fill", title: "Shielded apps")

                #if canImport(FamilyControls)
                if AppShieldState.isFamilyControlsAvailable && state.authorization == .approved {
                    Button {
                        showPicker = true
                    } label: {
                        HStack {
                            Image(systemName: "square.grid.3x3.topleft.filled")
                            Text(state.shieldedCount == 0 ? "Choose apps to shield" : "Edit shielded apps")
                                .font(.body.weight(.semibold))
                        }
                        .frame(maxWidth: .infinity, minHeight: 44)
                    }
                    .buttonStyle(.borderedProminent)
                    .tint(Color.appAccent)

                    HStack(spacing: 6) {
                        Circle()
                            .fill(state.shieldedCount == 0 ? Color.appTertiaryText : Color.appAccent)
                            .frame(width: 8, height: 8)
                        Text(state.shieldedCount == 0
                             ? "No apps selected yet."
                             : "\(state.shieldedCount) app\(state.shieldedCount == 1 ? "" : "s") selected.")
                            .font(.caption)
                            .foregroundStyle(Color.appSecondaryText)
                    }

                    Toggle(isOn: Binding(
                        get: { state.enabled },
                        set: { newValue in
                            if newValue {
                                let applied = AppShieldManager.applyShield()
                                if !applied {
                                    showAuthErrorAlert = true
                                }
                            } else {
                                AppShieldManager.clearShield()
                            }
                        }
                    )) {
                        VStack(alignment: .leading, spacing: 2) {
                            Text("Enable App Shield")
                                .font(.subheadline.weight(.semibold))
                                .foregroundStyle(Color.appPrimaryText)
                            Text("Blocks launching the selected apps until you turn this off.")
                                .font(.caption)
                                .foregroundStyle(Color.appSecondaryText)
                                .fixedSize(horizontal: false, vertical: true)
                        }
                    }
                    .tint(Color.appAccent)
                    .disabled(state.shieldedCount == 0)
                } else {
                    Text("Grant authorization above before you can pick apps.")
                        .font(.footnote)
                        .foregroundStyle(Color.appTertiaryText)
                        .fixedSize(horizontal: false, vertical: true)
                }
                #else
                Text("Family Controls isn't available in this build.")
                    .font(.footnote)
                    .foregroundStyle(Color.appTertiaryText)
                #endif
            }
        }
    }

    // MARK: - Footer

    private var statusFooter: some View {
        PremiumCard {
            VStack(alignment: .leading, spacing: 8) {
                CardHeader(icon: "info.circle.fill", title: "Current status")
                if state.enabled && state.shieldedCount > 0 {
                    Text("\(state.shieldedCount) app\(state.shieldedCount == 1 ? "" : "s") shielded via Family Controls.")
                        .font(.footnote.weight(.semibold))
                        .foregroundStyle(Color.appSuccess)
                        .fixedSize(horizontal: false, vertical: true)
                } else {
                    Text("Not configured. Pick apps above and enable the shield to start the restriction.")
                        .font(.footnote)
                        .foregroundStyle(Color.appSecondaryText)
                        .fixedSize(horizontal: false, vertical: true)
                }
            }
        }
    }

    // MARK: - Copy helpers

    private var simulatorMessage: String {
        AppShieldState.isRunningOnSimulator
            ? "Family Controls isn't available in this build. The restriction requires a real device + Apple's manual approval of the family-controls entitlement."
            : "Family Controls isn't linked into this build. The restriction requires Apple's manual approval of the family-controls entitlement."
    }

    private var statusIcon: String {
        switch state.authorization {
        case .approved: return "checkmark.seal.fill"
        case .denied:   return "xmark.octagon.fill"
        case .notDetermined: return "questionmark.circle.fill"
        }
    }

    private var statusColor: Color {
        switch state.authorization {
        case .approved: return Color.appSuccess
        case .denied:   return Color.appDanger
        case .notDetermined: return Color.appWarning
        }
    }

    private var statusLabel: String {
        switch state.authorization {
        case .approved: return "Approved"
        case .denied:   return "Denied — open Settings to revisit"
        case .notDetermined: return "Not authorized yet"
        }
    }

    private var authorizationCopy: String {
        switch state.authorization {
        case .approved:
            return "iOS will enforce the shield for the apps you pick below."
        case .denied:
            return "Family Controls is disabled for OnlyTrusted. Open iOS Settings → Screen Time → OnlyTrusted and allow it to continue."
        case .notDetermined:
            return "iOS will ask you to allow OnlyTrusted to manage Screen Time for this device."
        }
    }
}

// MARK: - FamilyActivityPicker sheet wrapper

#if canImport(FamilyControls)
/// Hosts the system `FamilyActivityPicker` as a sheet so we can decorate it with a title +
/// Done / Cancel buttons. The picker drives a `@State` copy of the selection that is only
/// persisted when the user taps Done.
private struct FamilyActivityPickerSheet: View {
    let onDone: (FamilyActivitySelection) -> Void
    let onCancel: () -> Void

    @State private var draft: FamilyActivitySelection = AppShieldState.shared.selection

    var body: some View {
        NavigationStack {
            FamilyActivityPicker(selection: $draft)
                .navigationTitle("Pick apps to shield")
                .navigationBarTitleDisplayMode(.inline)
                .toolbar {
                    ToolbarItem(placement: .topBarLeading) {
                        Button("Cancel", role: .cancel) { onCancel() }
                            .tint(Color.appAccent)
                    }
                    ToolbarItem(placement: .topBarTrailing) {
                        Button("Done") { onDone(draft) }
                            .fontWeight(.semibold)
                            .tint(Color.appAccent)
                    }
                }
        }
    }
}
#endif
