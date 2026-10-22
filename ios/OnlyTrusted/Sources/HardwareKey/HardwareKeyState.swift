// SPDX-FileCopyrightText: 2026 VanPaulTek Inc
// SPDX-License-Identifier: AGPL-3.0-or-later
import Foundation
import Security
import SwiftUI

/// Elite-tier (`SubscriptionState.hasElite`) feature: FIDO2 / WebAuthn hardware security key
/// (YubiKey, Google Titan Key, SoloKey). On iOS 15+ we use `ASAuthorizationController` with the
/// security-key credential provider for both registration and assertion.
///
/// This file owns the local record of enrolled keys. Each enrollment is a tuple of
/// (credentialId, publicKey, friendlyName, dateAdded). Credentials live in the Keychain under
/// `com.onlytrusted.app.security-key` with device-only accessibility — a filesystem copy
/// is not useful without the Keychain.
///
/// Nothing in this file performs the WebAuthn ceremony itself; see `HardwareKeyGate` for the
/// `AuthenticationServices` wiring that actually prompts the user to tap their key.
@MainActor
final class HardwareKeyState: ObservableObject {
    static let shared = HardwareKeyState()

    /// WebAuthn relying-party ID. Fixed string — on-device only, never transmitted. The RP ID
    /// is baked into the credentialId so the hardware key only replays assertions for this app.
    static let relyingPartyID = "onlytrusted.local"

    /// Keychain service/account used to persist the enrolled-credentials blob.
    private static let keychainService = "com.onlytrusted.app.security-key"
    private static let keychainAccount = "enrolled-credentials.v1"

    /// UserDefaults flag that short-circuits the assertion flow on the iOS Simulator. The
    /// simulator cannot talk to a real USB / Lightning / NFC key, so without this toggle the
    /// gated flows would be impossible to exercise there. Does NOT bypass real-device checks:
    /// `HardwareKeyGate` only honors it when `ProcessInfo` reports a simulator target.
    private static let skipFlagKey = "hardware_key.simulator_skip.v1"

    @Published private(set) var enrolledKeys: [EnrolledKey] = []
    @Published var simulatorSkipEnabled: Bool {
        didSet { UserDefaults.standard.set(simulatorSkipEnabled, forKey: Self.skipFlagKey) }
    }

    private init() {
        simulatorSkipEnabled = UserDefaults.standard.bool(forKey: Self.skipFlagKey)
        enrolledKeys = loadFromKeychain() ?? []
    }

    /// Convenience: `true` when at least one key is enrolled. Features that call
    /// `HardwareKeyGate.requireAssertion` short-circuit to "no gating" when this is false
    /// (opt-in feature).
    var hasEnrolledKey: Bool { !enrolledKeys.isEmpty }

    /// Append a freshly-enrolled key. Does not fail; a duplicate credentialId overwrites.
    func addKey(_ key: EnrolledKey) {
        if let idx = enrolledKeys.firstIndex(where: { $0.credentialId == key.credentialId }) {
            enrolledKeys[idx] = key
        } else {
            enrolledKeys.append(key)
        }
        persist()
    }

    /// Remove a key by its stable identifier.
    func removeKey(id: UUID) {
        enrolledKeys.removeAll { $0.id == id }
        persist()
    }

    /// Clears every enrolled credential. Used by Settings → Wipe everything.
    func clearAll() {
        enrolledKeys = []
        persist()
    }

    /// Returns `true` when the running binary is targeting the iOS Simulator. We check this
    /// at runtime rather than build time so a release build cannot sneak through a simulator
    /// skip in production.
    static var isRunningOnSimulator: Bool {
        #if targetEnvironment(simulator)
        return true
        #else
        return false
        #endif
    }

    // MARK: - Keychain persistence

    private func persist() {
        guard let data = try? JSONEncoder().encode(enrolledKeys) else { return }
        let base: [CFString: Any] = [
            kSecClass: kSecClassGenericPassword,
            kSecAttrService: Self.keychainService,
            kSecAttrAccount: Self.keychainAccount
        ]
        // Delete-then-add is simpler than SecItemUpdate for a small blob.
        SecItemDelete(base as CFDictionary)
        var insert = base
        insert[kSecValueData] = data
        insert[kSecAttrAccessible] = kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
        SecItemAdd(insert as CFDictionary, nil)
    }

    private func loadFromKeychain() -> [EnrolledKey]? {
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
        return try? JSONDecoder().decode([EnrolledKey].self, from: data)
    }

    // MARK: - Model

    /// Local record of a single enrolled hardware key. We keep the raw credentialId + public
    /// key bytes so a future server-backed WebAuthn flow can replay the registration without
    /// re-prompting the user to tap the key.
    struct EnrolledKey: Identifiable, Codable, Hashable {
        let id: UUID
        /// Opaque credential handle returned by the authenticator.
        let credentialId: Data
        /// COSE-encoded public key material. Kept so we can verify assertions if we ever
        /// move the challenge/response off-device.
        let publicKey: Data?
        /// Nickname the user gave the key during enrollment ("YubiKey 5C", "Backup key…").
        var displayName: String
        /// When the key was first enrolled on this device.
        let dateAdded: Date

        func formattedDateAdded() -> String {
            let f = DateFormatter()
            f.dateFormat = "MMM d, yyyy"
            return f.string(from: dateAdded)
        }
    }
}
