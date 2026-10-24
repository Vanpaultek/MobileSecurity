// SPDX-FileCopyrightText: 2026 VanPaulTek Inc
// SPDX-License-Identifier: AGPL-3.0-or-later
import Foundation
import CryptoKit
import Security
import SwiftUI

/// Observable store for the mandatory first-launch profile setup. Three fields:
///   * `email`           — user-provided address. Stored locally only. Never transmitted.
///                         Used to pre-fill Breach Watch and personalise copy.
///   * `pinHash`         — PBKDF2-HMAC-SHA256 hash of the 4-6 digit app PIN (100k iters).
///   * `biometricEnabled`— whether the user opted in to Face ID / Touch ID unlock.
///
/// All three live in the Keychain under `kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly`
/// so they survive reboot but are per-device and never iCloud-synced.
///
/// Honest claim preserved: nothing contacts a server. Email is stored on-device only;
/// PIN hash never leaves the device; biometric authentication uses LocalAuthentication
/// which goes through Secure Enclave.
@MainActor
final class ProfileStore: ObservableObject {

    static let shared = ProfileStore()

    @Published private(set) var email: String
    @Published private(set) var biometricEnabled: Bool
    @Published private(set) var isSetupComplete: Bool

    private var pinHashHex: String
    private var pinSaltHex: String

    private enum Keys {
        static let email            = "profile.v1.email"
        static let pinHash          = "profile.v1.pin_hash"
        static let pinSalt          = "profile.v1.pin_salt"
        static let biometric        = "profile.v1.biometric_enabled"
        static let completed        = "profile.v1.setup_completed_at"
    }

    private init() {
        self.email = Keychain.get(Keys.email) ?? ""
        self.pinHashHex = Keychain.get(Keys.pinHash) ?? ""
        self.pinSaltHex = Keychain.get(Keys.pinSalt) ?? ""
        self.biometricEnabled = (Keychain.get(Keys.biometric) == "1")
        self.isSetupComplete = (Keychain.get(Keys.completed) != nil)
    }

    // MARK: - Setters

    func setEmail(_ raw: String) {
        let normalised = raw.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
        self.email = normalised
        Keychain.set(normalised, for: Keys.email)
    }

    /// Hashes the PIN with PBKDF2-HMAC-SHA256 (100k iterations) + a fresh random salt
    /// before storing. The raw PIN is never persisted.
    ///
    /// `async` so the 100k-iter HMAC loop runs on a detached background task — on an
    /// iPhone 11-class device the hash takes ~250–600 ms and would freeze the setup
    /// screen if run on the MainActor.
    func setPIN(_ pin: String) async {
        let salt = Data((0..<16).map { _ in UInt8.random(in: 0...255) })
        let hash = await Task.detached(priority: .userInitiated) {
            Self.pbkdf2(pin: pin, salt: salt)
        }.value
        self.pinSaltHex = salt.map { String(format: "%02x", $0) }.joined()
        self.pinHashHex = hash.map { String(format: "%02x", $0) }.joined()
        Keychain.set(pinSaltHex, for: Keys.pinSalt)
        Keychain.set(pinHashHex, for: Keys.pinHash)
    }

    func setBiometricEnabled(_ enabled: Bool) {
        self.biometricEnabled = enabled
        Keychain.set(enabled ? "1" : "0", for: Keys.biometric)
    }

    /// Marks setup done. The RootView checks `isSetupComplete` to decide whether to
    /// render ProfileSetupScreen or the main app.
    func markSetupComplete() {
        let ts = ISO8601DateFormatter().string(from: Date())
        Keychain.set(ts, for: Keys.completed)
        self.isSetupComplete = true
    }

    // MARK: - Verification

    /// Returns true if the given PIN matches the stored hash. Used by the unlock gate.
    func verifyPIN(_ pin: String) -> Bool {
        guard !pinHashHex.isEmpty, !pinSaltHex.isEmpty else { return false }
        guard let salt = Data(hex: pinSaltHex) else { return false }
        let computed = Self.pbkdf2(pin: pin, salt: salt).map { String(format: "%02x", $0) }.joined()
        return constantTimeEqual(computed, pinHashHex)
    }

    func clearAll() {
        email = ""
        pinHashHex = ""
        pinSaltHex = ""
        biometricEnabled = false
        isSetupComplete = false
        Keychain.delete(Keys.email)
        Keychain.delete(Keys.pinHash)
        Keychain.delete(Keys.pinSalt)
        Keychain.delete(Keys.biometric)
        Keychain.delete(Keys.completed)
    }

    // MARK: - PBKDF2 (CommonCrypto-free, via CryptoKit HMAC loop)

    nonisolated private static let pbkdf2Iterations = 100_000

    nonisolated private static func pbkdf2(pin: String, salt: Data) -> Data {
        // One-block PBKDF2 is sufficient — SHA-256 produces 32 bytes = 256 bits. For a
        // 6-digit PIN the entropy ceiling is 10^6 (~20 bits) regardless; 100k iterations
        // raise the brute-force cost to seconds-per-guess on commodity hardware.
        let password = Data(pin.utf8)
        var block = salt
        block.append(0x00); block.append(0x00); block.append(0x00); block.append(0x01) // INT(1)

        var u = Data(HMAC<SHA256>.authenticationCode(for: block, using: SymmetricKey(data: password)))
        var out = u
        for _ in 1..<pbkdf2Iterations {
            u = Data(HMAC<SHA256>.authenticationCode(for: u, using: SymmetricKey(data: password)))
            for i in 0..<out.count { out[i] ^= u[i] }
        }
        return out
    }

    private func constantTimeEqual(_ a: String, _ b: String) -> Bool {
        guard a.count == b.count else { return false }
        var diff: UInt8 = 0
        for (x, y) in zip(a.utf8, b.utf8) { diff |= (x ^ y) }
        return diff == 0
    }
}

// MARK: - Hex helpers

private extension Data {
    init?(hex: String) {
        guard hex.count.isMultiple(of: 2) else { return nil }
        var data = Data(capacity: hex.count / 2)
        var i = hex.startIndex
        while i < hex.endIndex {
            let next = hex.index(i, offsetBy: 2)
            guard let b = UInt8(hex[i..<next], radix: 16) else { return nil }
            data.append(b)
            i = next
        }
        self = data
    }
}

// MARK: - Keychain shim

private enum Keychain {
    static func set(_ value: String, for key: String) {
        let data = Data(value.utf8)
        let q: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrAccount as String: key
        ]
        let attrs: [String: Any] = [
            kSecValueData as String: data,
            kSecAttrAccessible as String: kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
        ]
        if SecItemUpdate(q as CFDictionary, attrs as CFDictionary) == errSecItemNotFound {
            var insert = q
            insert[kSecValueData as String] = data
            insert[kSecAttrAccessible as String] = kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
            SecItemAdd(insert as CFDictionary, nil)
        }
    }
    static func get(_ key: String) -> String? {
        let q: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrAccount as String: key,
            kSecReturnData as String: true,
            kSecMatchLimit as String: kSecMatchLimitOne
        ]
        var out: AnyObject?
        guard SecItemCopyMatching(q as CFDictionary, &out) == errSecSuccess,
              let d = out as? Data else { return nil }
        return String(data: d, encoding: .utf8)
    }
    static func delete(_ key: String) {
        let q: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrAccount as String: key
        ]
        SecItemDelete(q as CFDictionary)
    }
}
