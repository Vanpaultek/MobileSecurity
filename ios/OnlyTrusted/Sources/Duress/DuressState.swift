// SPDX-FileCopyrightText: 2026 VanPaulTek Inc
// SPDX-License-Identifier: AGPL-3.0-or-later
import Foundation
import CryptoKit
import Security
import SwiftUI

/// Owner-facing mode flag for the vault store. "real" is the normal path; "duress" swaps in
/// the decoy store so other screens read fake data under coercion.
enum VaultMode: String { case real, duress }

/// Session + persisted state for Duress Mode (High Premium / Pro tier feature).
///
/// Three concerns live here:
/// 1. Setup state — `isConfigured`, `isEnabled`, `pinHashBase64`, `alertContactId`, `ownerName`.
/// 2. Session state — `isDuressSession` (in-memory) tells `VaultStore` / UI which blob to read.
/// 3. Verification — `matches(pin:)` checks a candidate PIN against the stored hash.
///
/// PINs are never kept in plaintext. We salt + SHA-256 the PIN and keep the salt + hash in
/// UserDefaults (hash is useless without the salt, salt is useless without the hash).
@MainActor
final class DuressState: ObservableObject {
    static let shared = DuressState()

    // Persisted flags
    @Published var isEnabled: Bool {
        didSet { d.set(isEnabled, forKey: Keys.enabled) }
    }
    @Published var alertContactId: UUID? {
        didSet {
            d.set(alertContactId?.uuidString, forKey: Keys.alertContactId)
        }
    }
    @Published var ownerName: String {
        didSet { d.set(ownerName, forKey: Keys.ownerName) }
    }

    /// In-memory flag flipped by `AppLockGate` when the duress PIN unlocks.
    /// Other screens (VaultStore, dashboards) read this to decide which store to show.
    @Published var isDuressSession: Bool = false {
        didSet { VaultStore.shared.activeMode = isDuressSession ? .duress : .real }
    }

    private let d = UserDefaults.standard

    private enum Keys {
        static let enabled = "duress.v1.enabled"
        static let pinSalt = "duress.v1.pin_salt"
        static let pinHash = "duress.v1.pin_hash"
        static let alertContactId = "duress.v1.alert_contact_id"
        static let ownerName = "duress.v1.owner_name"
        static let realPinHash = "duress.v1.real_pin_hash" // echo of the real-PIN hash for cross-check
    }

    private init() {
        isEnabled = d.bool(forKey: Keys.enabled)
        alertContactId = d.string(forKey: Keys.alertContactId).flatMap(UUID.init(uuidString:))
        ownerName = d.string(forKey: Keys.ownerName) ?? ""
    }

    // MARK: - PIN management

    /// Store a new duress PIN. Overwrites any existing one.
    func setDuressPin(_ pin: String) {
        let salt = randomSalt()
        let hash = hash(pin: pin, salt: salt)
        d.set(salt.base64EncodedString(), forKey: Keys.pinSalt)
        d.set(hash.base64EncodedString(), forKey: Keys.pinHash)
    }

    /// Returns true if `candidate` matches the saved duress PIN. Returns false when no PIN
    /// has been set or the entry is empty.
    func matches(pin candidate: String) -> Bool {
        guard isEnabled else { return false }
        guard
            let saltB64 = d.string(forKey: Keys.pinSalt),
            let hashB64 = d.string(forKey: Keys.pinHash),
            let salt = Data(base64Encoded: saltB64),
            let expected = Data(base64Encoded: hashB64)
        else { return false }
        let got = hash(pin: candidate, salt: salt)
        // Constant-time compare
        return constantTimeEquals(got, expected)
    }

    /// Returns true if `candidate` equals any previously-set "real" PIN we have hashed. Used
    /// to prevent the owner reusing their lock-screen PIN as the duress PIN. We do not know the
    /// device passcode; this only guards against reuse of a PIN *saved by this app*, which is
    /// unused today — kept for future when the app owns its own PIN.
    func collidesWithRealPin(_ candidate: String) -> Bool {
        guard
            let saltB64 = d.string(forKey: Keys.pinSalt),
            let realHashB64 = d.string(forKey: Keys.realPinHash),
            let salt = Data(base64Encoded: saltB64),
            let expected = Data(base64Encoded: realHashB64)
        else { return false }
        let got = hash(pin: candidate, salt: salt)
        return constantTimeEquals(got, expected)
    }

    func clearPin() {
        d.removeObject(forKey: Keys.pinSalt)
        d.removeObject(forKey: Keys.pinHash)
    }

    // MARK: - Hashing

    private func randomSalt() -> Data {
        var bytes = [UInt8](repeating: 0, count: 16)
        _ = SecRandomCopyBytes(kSecRandomDefault, bytes.count, &bytes)
        return Data(bytes)
    }

    private func hash(pin: String, salt: Data) -> Data {
        var hasher = SHA256()
        hasher.update(data: salt)
        hasher.update(data: Data(pin.utf8))
        return Data(hasher.finalize())
    }

    private func constantTimeEquals(_ a: Data, _ b: Data) -> Bool {
        guard a.count == b.count else { return false }
        var diff: UInt8 = 0
        for i in 0..<a.count { diff |= a[i] ^ b[i] }
        return diff == 0
    }
}
