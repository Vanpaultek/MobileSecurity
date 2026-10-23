// SPDX-FileCopyrightText: 2026 VanPaulTek Inc
// SPDX-License-Identifier: AGPL-3.0-or-later
import Foundation
#if canImport(FamilyControls)
import FamilyControls
import ManagedSettings
#endif

/// Thin wrapper around Family Controls authorization + the `ManagedSettingsStore`.
///
/// Separation of concerns:
///   * `AppShieldState` holds the user-visible state (selection, toggle, counts).
///   * `AppShieldManager` performs the side-effects (authorization, apply/clear the shield).
///
/// All methods bail out gracefully when `AppShieldState.isFamilyControlsAvailable` is
/// `false` (simulator / missing entitlement). The error strings surfaced here are the
/// same strings the setup screen renders — no technical jargon.
@MainActor
enum AppShieldManager {

    #if canImport(FamilyControls)
    /// Shared Managed Settings store. "OnlyTrusted" is a plain string, not a reserved name;
    /// it namespaces the applied restrictions so that clearing them later doesn't disturb
    /// other apps that happen to use their own stores.
    private static let store = ManagedSettingsStore(named: .init("OnlyTrustedAppShield"))
    #endif

    /// Request Family Controls authorization. Idempotent — if the user already approved we
    /// simply return the current status without re-prompting.
    static func requestAuthorization() async -> AppShieldState.Authorization {
        #if canImport(FamilyControls)
        guard AppShieldState.isFamilyControlsAvailable else {
            AppShieldState.shared.authorization = .denied
            return .denied
        }
        do {
            try await AuthorizationCenter.shared.requestAuthorization(for: .individual)
            let status = currentStatus()
            AppShieldState.shared.authorization = status
            return status
        } catch {
            AppShieldState.shared.authorization = .denied
            return .denied
        }
        #else
        AppShieldState.shared.authorization = .denied
        return .denied
        #endif
    }

    /// Read-only snapshot of the current authorization. Called at app launch so the UI
    /// can show "Approved" / "Needs authorization" without re-prompting.
    static func refreshAuthorization() {
        #if canImport(FamilyControls)
        guard AppShieldState.isFamilyControlsAvailable else {
            AppShieldState.shared.authorization = .denied
            return
        }
        AppShieldState.shared.authorization = currentStatus()
        #else
        AppShieldState.shared.authorization = .denied
        #endif
    }

    #if canImport(FamilyControls)
    private static func currentStatus() -> AppShieldState.Authorization {
        switch AuthorizationCenter.shared.authorizationStatus {
        case .notDetermined: return .notDetermined
        case .denied:        return .denied
        case .approved:      return .approved
        @unknown default:    return .denied
        }
    }
    #endif

    /// Apply the current selection to the Managed Settings store. Returns `true` when the
    /// system accepted the shield. On simulator / unauthorised states this is a no-op and
    /// returns `false` so the UI can revert the toggle.
    @discardableResult
    static func applyShield() -> Bool {
        #if canImport(FamilyControls)
        guard AppShieldState.isFamilyControlsAvailable else { return false }
        guard AppShieldState.shared.authorization == .approved else { return false }
        let selection = AppShieldState.shared.selection
        guard !selection.applicationTokens.isEmpty else {
            // Nothing to shield — treat as "cleared" so the UI doesn't claim coverage.
            clearShield()
            return false
        }
        store.shield.applications = selection.applicationTokens
        AppShieldState.shared.persistEnabled(true)
        return true
        #else
        return false
        #endif
    }

    /// Clear the shield. Safe to call even if nothing is currently applied.
    static func clearShield() {
        #if canImport(FamilyControls)
        store.shield.applications = nil
        #endif
        AppShieldState.shared.persistEnabled(false)
    }

    /// Load the persisted selection + authorization status. Call once at app launch.
    static func rehydrate() {
        #if canImport(FamilyControls)
        AppShieldState.shared.reloadSelection()
        refreshAuthorization()
        // If the user previously enabled the shield, re-apply on launch so the OS-level
        // restriction survives a device restart. If authorization has since been revoked
        // or the entitlement is missing we silently clear the stored "enabled" bit so the
        // UI doesn't lie.
        if AppShieldState.shared.enabled {
            let ok = applyShield()
            if !ok {
                AppShieldState.shared.persistEnabled(false)
            }
        }
        #else
        AppShieldState.shared.authorization = .denied
        AppShieldState.shared.persistEnabled(false)
        #endif
    }
}
