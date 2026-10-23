// SPDX-FileCopyrightText: 2026 VanPaulTek Inc
// SPDX-License-Identifier: AGPL-3.0-or-later
import Foundation
#if canImport(FamilyControls)
import FamilyControls
import ManagedSettings
#endif

/// Observable state for Auto Privacy Shield — Phase 2: Selected App Restriction.
///
/// Elite-tier feature (`SubscriptionState.hasElite`).
///
/// What it owns:
///   * `authorizationStatus` — mirror of `AuthorizationCenter.shared.authorizationStatus`.
///   * `selection`           — the user's `FamilyActivitySelection` (persisted as JSON).
///   * `enabled`             — whether the shield is currently applied via `ManagedSettingsStore`.
///   * `shieldedCount`       — count of individual applications in the current selection.
///
/// Honest scoping:
///   * This is the strongest consumer-App-Store path. Shielded apps become inaccessible
///     at the OS level until the shield is lifted. It blocks LAUNCH — not microphone or
///     camera sensors — so the dashboard copy must say "selected apps restricted, not
///     sensor-disabled".
///   * iOS Simulator cannot complete `FamilyControls` authorization. `AppShieldManager`
///     detects simulator + unavailable framework and surfaces an honest "requires device +
///     Apple-approved entitlement" message.
///   * The `com.apple.developer.family-controls` entitlement is NOT auto-provisioned by
///     Apple. See `ios/Entitlements/OnlyTrusted.entitlements` for the manual-request note.
@MainActor
final class AppShieldState: ObservableObject {

    static let shared = AppShieldState()

    /// Mirror of `AuthorizationCenter.shared.authorizationStatus` serialised as a String so
    /// we never leak FamilyControls types into non-Family-Controls contexts.
    enum Authorization: String {
        case notDetermined
        case denied
        case approved
    }

    @Published var authorization: Authorization = .notDetermined
    @Published var enabled: Bool = false
    @Published var shieldedCount: Int = 0

    #if canImport(FamilyControls)
    /// Current `FamilyActivitySelection`. Updated by the picker; applied by `AppShieldManager`.
    /// Default is empty.
    @Published var selection: FamilyActivitySelection = FamilyActivitySelection()
    #endif

    private init() {
        self.enabled = UserDefaults.standard.bool(forKey: Keys.enabled)
    }

    // MARK: - Entitlement + simulator probe

    /// `true` when the host build environment can run Family Controls at full fidelity.
    /// Simulator always returns `false` because `AuthorizationCenter` request calls throw.
    static var isFamilyControlsAvailable: Bool {
        #if targetEnvironment(simulator)
        return false
        #else
        #if canImport(FamilyControls)
        return true
        #else
        return false
        #endif
        #endif
    }

    static var isRunningOnSimulator: Bool {
        #if targetEnvironment(simulator)
        return true
        #else
        return false
        #endif
    }

    // MARK: - Persistence

    enum Keys {
        static let enabled = "appshield.v1.enabled"
        static let selectionJSON = "appshield.v1.selection"
    }

    func persistEnabled(_ value: Bool) {
        UserDefaults.standard.set(value, forKey: Keys.enabled)
        enabled = value
    }

    #if canImport(FamilyControls)
    /// Encode the current selection to disk so it survives relaunch.
    func persistSelection(_ selection: FamilyActivitySelection) {
        let encoder = JSONEncoder()
        if let data = try? encoder.encode(selection) {
            UserDefaults.standard.set(data, forKey: Keys.selectionJSON)
        }
        self.selection = selection
        self.shieldedCount = selection.applicationTokens.count
    }

    /// Reload the persisted selection into `selection`. Called from `AppShieldManager` on launch.
    func reloadSelection() {
        guard let data = UserDefaults.standard.data(forKey: Keys.selectionJSON) else {
            self.selection = FamilyActivitySelection()
            self.shieldedCount = 0
            return
        }
        let decoder = JSONDecoder()
        if let decoded = try? decoder.decode(FamilyActivitySelection.self, from: data) {
            self.selection = decoded
            self.shieldedCount = decoded.applicationTokens.count
        }
    }
    #endif
}
