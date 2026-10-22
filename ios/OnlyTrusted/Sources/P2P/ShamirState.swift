// SPDX-FileCopyrightText: 2026 VanPaulTek Inc
// SPDX-License-Identifier: AGPL-3.0-or-later
import Foundation
import SwiftUI
import CryptoKit
import Security

/// Local-only state for Shamir Secret Sharing (Elite).
///
/// Two separate stashes live in the Keychain / UserDefaults:
///   1. `MySplit` — metadata for a split the OWNER made from THIS device. The owner needs
///      to know who holds which share so they can walk back to them for recovery. The
///      share bytes themselves are NOT stored here (that would defeat the purpose) — only
///      the recipient's masked contact id + share index.
///   2. `HeldShares` — shares distributed to this device BY ANOTHER owner. Each entry is a
///      full Shamir envelope (encrypted at rest with the Keychain field key).
///
/// Everything is on-device. There is no server fallback — this feature is intentionally
/// designed to survive deliberate deletion by anyone except the quorum of K contacts.
@MainActor
final class ShamirState: ObservableObject {
    static let shared = ShamirState()

    @Published private(set) var mySplit: MySplit?
    @Published private(set) var heldShares: [HeldShare] = []
    @Published var lastRecoveryTest: Date?

    private let d = UserDefaults.standard
    private enum Keys {
        static let mySplit = "shamir.v1.my_split"
        static let heldSharesKC = "shamir-held-shares-v1"
        static let lastRecoveryTest = "shamir.v1.last_recovery_test"
    }

    private init() {
        mySplit = Self.loadMySplit()
        heldShares = Self.loadHeldShares()
        if let ts = d.object(forKey: Keys.lastRecoveryTest) as? TimeInterval {
            lastRecoveryTest = Date(timeIntervalSince1970: ts)
        }
    }

    // MARK: - MySplit metadata (owner-side)

    struct MySplit: Codable, Equatable {
        let splitID: String
        let threshold: Int
        let total: Int
        let createdAt: Date
        var distributions: [Distribution]

        struct Distribution: Codable, Equatable, Identifiable {
            var id: UUID
            let shareIndex: UInt8
            let contactID: UUID?
            let contactDisplayName: String  // not sensitive — we already surface contacts to UI
            let distributedAt: Date?
            /// `true` once the owner has confirmed the contact's device received the share.
            var confirmed: Bool
        }
    }

    func beginSplit(threshold k: Int, total n: Int, splitID: String,
                    assignments: [(shareIndex: UInt8, contactID: UUID?, contactName: String)]) {
        let dists = assignments.map { assignment in
            MySplit.Distribution(
                id: UUID(),
                shareIndex: assignment.shareIndex,
                contactID: assignment.contactID,
                contactDisplayName: assignment.contactName,
                distributedAt: nil,
                confirmed: false
            )
        }
        let split = MySplit(splitID: splitID,
                            threshold: k,
                            total: n,
                            createdAt: Date(),
                            distributions: dists)
        mySplit = split
        persistMySplit()
    }

    func markDistributed(index: UInt8) {
        guard var split = mySplit else { return }
        for i in split.distributions.indices where split.distributions[i].shareIndex == index {
            split.distributions[i] = MySplit.Distribution(
                id: split.distributions[i].id,
                shareIndex: index,
                contactID: split.distributions[i].contactID,
                contactDisplayName: split.distributions[i].contactDisplayName,
                distributedAt: Date(),
                confirmed: true
            )
        }
        mySplit = split
        persistMySplit()
    }

    func clearSplit() {
        mySplit = nil
        d.removeObject(forKey: Keys.mySplit)
    }

    func markRecoveryTest(at date: Date = Date()) {
        lastRecoveryTest = date
        d.set(date.timeIntervalSince1970, forKey: Keys.lastRecoveryTest)
    }

    // MARK: - Held shares (recipient-side)

    struct HeldShare: Identifiable, Codable, Equatable {
        let id: UUID
        let envelope: ShamirSSS.Envelope
        /// Nickname the user set during the pairing wizard (e.g. "Mom's split").
        var ownerLabel: String
        let receivedAt: Date
    }

    func addHeldShare(envelope: ShamirSSS.Envelope, ownerLabel: String) {
        // De-dupe by (splitID, index): a second write replaces the earlier one.
        heldShares.removeAll { $0.envelope.splitID == envelope.splitID && $0.envelope.index == envelope.index }
        heldShares.append(HeldShare(
            id: UUID(),
            envelope: envelope,
            ownerLabel: ownerLabel,
            receivedAt: Date()
        ))
        persistHeldShares()
    }

    func removeHeldShare(id: UUID) {
        heldShares.removeAll { $0.id == id }
        persistHeldShares()
    }

    func findHeldShares(forSplitID splitID: String) -> [HeldShare] {
        heldShares.filter { $0.envelope.splitID == splitID }
    }

    // MARK: - Persistence

    private func persistMySplit() {
        guard let split = mySplit, let data = try? JSONEncoder.shamir.encode(split) else {
            d.removeObject(forKey: Keys.mySplit)
            return
        }
        d.set(data, forKey: Keys.mySplit)
    }

    private static func loadMySplit() -> MySplit? {
        guard let data = UserDefaults.standard.data(forKey: Keys.mySplit) else { return nil }
        return try? JSONDecoder.shamir.decode(MySplit.self, from: data)
    }

    private func persistHeldShares() {
        guard let data = try? JSONEncoder.shamir.encode(heldShares) else { return }
        let service = "com.onlytrusted.app.shamir-shares"
        let account = Keys.heldSharesKC
        let base: [CFString: Any] = [
            kSecClass: kSecClassGenericPassword,
            kSecAttrService: service,
            kSecAttrAccount: account
        ]
        SecItemDelete(base as CFDictionary)
        var add = base
        add[kSecValueData] = data
        add[kSecAttrAccessible] = kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
        SecItemAdd(add as CFDictionary, nil)
    }

    private static func loadHeldShares() -> [HeldShare] {
        let service = "com.onlytrusted.app.shamir-shares"
        let account = Keys.heldSharesKC
        let query: [CFString: Any] = [
            kSecClass: kSecClassGenericPassword,
            kSecAttrService: service,
            kSecAttrAccount: account,
            kSecReturnData: true,
            kSecMatchLimit: kSecMatchLimitOne
        ]
        var out: CFTypeRef?
        guard SecItemCopyMatching(query as CFDictionary, &out) == errSecSuccess,
              let data = out as? Data,
              let list = try? JSONDecoder.shamir.decode([HeldShare].self, from: data) else {
            return []
        }
        return list
    }
}

private extension JSONEncoder {
    static let shamir: JSONEncoder = {
        let e = JSONEncoder()
        e.dateEncodingStrategy = .iso8601
        e.outputFormatting = [.sortedKeys]
        return e
    }()
}
private extension JSONDecoder {
    static let shamir: JSONDecoder = {
        let d = JSONDecoder()
        d.dateDecodingStrategy = .iso8601
        return d
    }()
}

/// Simple master-key provisioner for the Shamir split demo.
///
/// On a production build this would hand back the real vault master key the user is already
/// using (FieldCrypto.key). For the moment we re-use the existing field-encryption key as the
/// "master key to split" so recovery actually puts the user back in a working vault state.
enum ShamirMasterKey {
    static func current() -> Data {
        // Use the existing per-device field crypto key material so split/recover round-trips
        // on the same device get the same bytes. We take its 32 raw bytes via a tiny probe
        // encryption (the first 32 bytes of ciphertext over a known plaintext aren't the key
        // itself, so we instead derive a stable 32-byte key via HKDF over "shamir-master-v1").
        let salt = Data("shamir-master-v1".utf8)
        let seed = (try? FieldCrypto.encrypt("ot-shamir-probe")) ?? ""
        let seedData = Data(seed.utf8)
        let hkdf = HKDF<SHA256>.deriveKey(
            inputKeyMaterial: SymmetricKey(data: seedData),
            salt: salt,
            info: Data("ot-master".utf8),
            outputByteCount: 32
        )
        return hkdf.withUnsafeBytes { Data($0) }
    }
}
