// SPDX-FileCopyrightText: 2026 VanPaulTek Inc
// SPDX-License-Identifier: AGPL-3.0-or-later
import Foundation
import Security
import SwiftUI

/// Panic Lockdown Mode (Elite). The "I'm in trouble RIGHT NOW" counterpart to Border Mode's
/// "I'm about to be in trouble" preset.
///
/// On [activate] the state object:
///   1. Snapshots every setting it will mutate (`previousState`).
///   2. Arms Duress Mode so the very next unlock drops into the decoy vault + silent SOS.
///   3. Forces aggressive Sensor Monitor flagging on (iOS: the dashboard tone flag the
///      existing BorderMode toggle also reads).
///   4. Disables the Smart Link Guard share extension via an app-group flag the extension
///      reads on `viewDidLoad` — if set, it refuses the share and shows a lockdown notice.
///   5. Disables biometric unlock (PIN-only, Fifth-Amendment advantage in US courts).
///   6. Logs a `PANIC_LOCKDOWN_ACTIVATED` SecurityEvent.
///   7. Locks the app via `AppGate.panic()` (caller does this; see
///      `PanicLockdownScreen.activate`).
///
/// On [disarm] the snapshot is restored. Disarm requires biometric + PIN; if a hardware key
/// is enrolled, a key tap is also required (via `HardwareKeyGate.requireAssertion`).
///
/// Storage: Keychain under service `com.onlytrusted.app.panic-lockdown` with device-only
/// accessibility. The armed flag, timestamp, and previous-state JSON blob all live in a
/// single Keychain-held payload so a filesystem copy cannot reveal them.
@MainActor
final class PanicLockdownState: ObservableObject {
    static let shared = PanicLockdownState()

    @Published private(set) var isArmed: Bool = false
    @Published private(set) var armedAt: Date? = nil

    private static let keychainService = "com.onlytrusted.app.panic-lockdown"
    private static let keychainAccount = "state.v1"

    /// App-group UserDefaults key the Smart Link Guard share extension reads to short-circuit
    /// its share-sheet UI while Panic Lockdown is armed.
    static let shareExtensionDisabledKey = "panic_lockdown.share_disabled"

    private init() {
        if let payload = loadPayload() {
            isArmed = payload.armed
            armedAt = payload.armedAt
        }
    }

    // MARK: - Models

    /// Snapshot of every setting we touch so [disarm] can put them back. Mirrors the Android
    /// `PanicLockdownState.PreviousState`.
    private struct PreviousState: Codable {
        var duressEnabled: Bool
        var sensorForcedOn: Bool
        var appLockBiometricAllowed: Bool
        var shareExtensionDisabled: Bool
    }

    /// Combined on-disk payload. Easier to roundtrip through the Keychain than three
    /// separate `SecItem` entries, and it stays small (< 1 KB).
    private struct Payload: Codable {
        var armed: Bool
        var armedAt: Date?
        var previousState: PreviousState?
    }

    // MARK: - Activate

    /// Snapshot current state, force the lockdown settings, and log the event. Caller is
    /// responsible for having gated this on `SubscriptionState.hasElite`.
    func activate() {
        let d = UserDefaults.standard
        let duress = DuressState.shared

        let snapshot = PreviousState(
            duressEnabled: duress.isEnabled,
            sensorForcedOn: d.bool(forKey: "settings.sensor.forced_on"),
            appLockBiometricAllowed: d.object(forKey: "settings.applock.biometric") as? Bool ?? true,
            shareExtensionDisabled: appGroupDefaults().bool(forKey: Self.shareExtensionDisabledKey)
        )

        // 2 — Arm Duress Mode so the next unlock triggers the decoy vault + silent SOS.
        //     Whether the user enters the real PIN or the duress PIN, the duress session
        //     flag will be set by `AppLockGate` based on the already-on `isEnabled` flag.
        duress.isEnabled = true

        // 3 — Force Sensor Monitor to its most aggressive flagging posture (same flag
        //     Border Mode uses — the dashboard reads it and escalates copy / notifications).
        d.set(true, forKey: "settings.sensor.forced_on")

        // 4 — Disable Smart Link Guard share extension via an app-group flag.
        appGroupDefaults().set(true, forKey: Self.shareExtensionDisabledKey)

        // 5 — Biometric unlock OFF. PIN-only. Same flag Border Mode flips, so the lock
        //     screen's `attemptAuth()` branch skips LocalAuthentication and goes straight
        //     to the PIN sheet.
        d.set(false, forKey: "settings.applock.biometric")

        // 6 — Log the panic activation.
        EventStore.shared.append(
            type: "PANIC_LOCKDOWN_ACTIVATED",
            detail: "Panic Lockdown armed."
        )

        // Flip the armed flag last so observers see the orchestrated settings in place.
        let now = Date()
        isArmed = true
        armedAt = now
        savePayload(Payload(armed: true, armedAt: now, previousState: snapshot))
    }

    // MARK: - Disarm

    /// Restore the previous state. Caller is responsible for the biometric + PIN double-factor
    /// check (and the hardware-key tap when an Elite key is enrolled).
    func disarm() {
        let payload = loadPayload()
        let snapshot = payload?.previousState
        let d = UserDefaults.standard
        let duress = DuressState.shared

        if let snapshot {
            duress.isEnabled = snapshot.duressEnabled
            // Clear any lingering duress session so the owner isn't stuck in the decoy vault.
            duress.isDuressSession = false
            d.set(snapshot.sensorForcedOn, forKey: "settings.sensor.forced_on")
            d.set(snapshot.appLockBiometricAllowed, forKey: "settings.applock.biometric")
            appGroupDefaults().set(
                snapshot.shareExtensionDisabled,
                forKey: Self.shareExtensionDisabledKey
            )
        } else {
            // No snapshot (shouldn't happen) — fall back to sane defaults.
            duress.isDuressSession = false
            d.set(true, forKey: "settings.applock.biometric")
            d.set(false, forKey: "settings.sensor.forced_on")
            appGroupDefaults().set(false, forKey: Self.shareExtensionDisabledKey)
        }

        EventStore.shared.append(type: "PANIC_LOCKDOWN_DISARMED", detail: nil)

        isArmed = false
        armedAt = nil
        clearPayload()
    }

    // MARK: - Keychain roundtrip

    private func savePayload(_ payload: Payload) {
        guard let data = try? JSONEncoder().encode(payload) else { return }
        let base: [CFString: Any] = [
            kSecClass: kSecClassGenericPassword,
            kSecAttrService: Self.keychainService,
            kSecAttrAccount: Self.keychainAccount
        ]
        SecItemDelete(base as CFDictionary)
        var insert = base
        insert[kSecValueData] = data
        insert[kSecAttrAccessible] = kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
        SecItemAdd(insert as CFDictionary, nil)
    }

    private func loadPayload() -> Payload? {
        let query: [CFString: Any] = [
            kSecClass: kSecClassGenericPassword,
            kSecAttrService: Self.keychainService,
            kSecAttrAccount: Self.keychainAccount,
            kSecReturnData: true,
            kSecMatchLimit: kSecMatchLimitOne
        ]
        var out: CFTypeRef?
        guard SecItemCopyMatching(query as CFDictionary, &out) == errSecSuccess,
              let data = out as? Data else {
            return nil
        }
        return try? JSONDecoder().decode(Payload.self, from: data)
    }

    private func clearPayload() {
        let base: [CFString: Any] = [
            kSecClass: kSecClassGenericPassword,
            kSecAttrService: Self.keychainService,
            kSecAttrAccount: Self.keychainAccount
        ]
        SecItemDelete(base as CFDictionary)
    }

    /// Shared app-group UserDefaults suite — used so the Smart Link Guard share extension
    /// can read the lockdown flag.
    private func appGroupDefaults() -> UserDefaults {
        UserDefaults(suiteName: SharedContainer.appGroupIdentifier) ?? .standard
    }
}
