// SPDX-FileCopyrightText: 2026 VanPaulTek Inc
// SPDX-License-Identifier: AGPL-3.0-or-later
import Foundation
import CryptoKit
import Security

/// Keychain-held key for the decoy vault, kept in a separate service bundle so a leak of the
/// real vault's key cannot touch the decoy and vice versa. The decoy key is still per-device,
/// AES-256; a future hardening pass can derive it from the duress PIN with HKDF so the decoy
/// is unreadable unless the duress PIN is in memory.
enum DuressFieldCrypto {

    private static let service = "com.onlytrusted.app.duress-vault"
    private static let account = "duress-field-key.v1"

    enum CryptoError: Error {
        case keychainStoreFailed(OSStatus)
        case keychainLoadFailed(OSStatus)
        case invalidCiphertext
    }

    static func encrypt(_ plaintext: String?) throws -> String? {
        guard let plaintext, !plaintext.isEmpty else { return nil }
        let key = try getOrCreateKey()
        let box = try AES.GCM.seal(Data(plaintext.utf8), using: key)
        guard let combined = box.combined else { throw CryptoError.invalidCiphertext }
        return combined.base64EncodedString()
    }

    static func decrypt(_ encoded: String?) throws -> String? {
        guard let encoded, !encoded.isEmpty else { return nil }
        guard let data = Data(base64Encoded: encoded) else { throw CryptoError.invalidCiphertext }
        let key = try getOrCreateKey()
        let box = try AES.GCM.SealedBox(combined: data)
        let plain = try AES.GCM.open(box, using: key)
        return String(data: plain, encoding: .utf8)
    }

    private static let keyLock = NSLock()
    nonisolated(unsafe) private static var cached: SymmetricKey?

    private static func getOrCreateKey() throws -> SymmetricKey {
        keyLock.lock()
        defer { keyLock.unlock() }
        if let cached { return cached }
        if let keyData = try loadKeyData() {
            let key = SymmetricKey(data: keyData)
            cached = key
            return key
        }
        let key = SymmetricKey(size: .bits256)
        try storeKeyData(key.withUnsafeBytes { Data($0) })
        cached = key
        return key
    }

    private static func baseQuery() -> [CFString: Any] {
        [
            kSecClass: kSecClassGenericPassword,
            kSecAttrService: service,
            kSecAttrAccount: account,
            kSecAttrAccessible: kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
        ]
    }

    private static func loadKeyData() throws -> Data? {
        var query = baseQuery()
        query[kSecReturnData] = true
        query[kSecMatchLimit] = kSecMatchLimitOne
        var out: CFTypeRef?
        let status = SecItemCopyMatching(query as CFDictionary, &out)
        switch status {
        case errSecSuccess:
            return out as? Data
        case errSecItemNotFound:
            return nil
        default:
            throw CryptoError.keychainLoadFailed(status)
        }
    }

    private static func storeKeyData(_ data: Data) throws {
        var query = baseQuery()
        query[kSecValueData] = data
        let status = SecItemAdd(query as CFDictionary, nil)
        if status != errSecSuccess && status != errSecDuplicateItem {
            throw CryptoError.keychainStoreFailed(status)
        }
    }
}

/// Decoy vault — same persistence shape as `VaultStore` but routed through its own file +
/// Keychain service. Deliberately kept dumb and self-contained so a reader of the real
/// vault can't accidentally pick this one up.
///
/// Not an `ObservableObject`; the main `VaultStore` publishes the right list depending on
/// `activeMode` so the UI only needs one subscription.
@MainActor
final class DuressVaultStore {
    static let shared = DuressVaultStore()

    private let storageURL: URL

    init(storageURL: URL? = nil) {
        if let storageURL {
            self.storageURL = storageURL
        } else {
            let dir = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0]
                .appendingPathComponent("OnlyTrusted", isDirectory: true)
            try? FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
            self.storageURL = dir.appendingPathComponent("duress-vault.v1.json")
        }
    }

    func loadMasked() -> [MaskedContact] {
        loadRecords().map { $0.toMasked() }
    }

    func reveal(id: UUID) throws -> RevealedContact? {
        guard let record = loadRecords().first(where: { $0.id == id }) else { return nil }
        return RevealedContact(
            id: record.id,
            displayName: record.displayName,
            relation: record.relation,
            isOwner: record.isOwner,
            addressPlain: try DuressFieldCrypto.decrypt(record.addressEncrypted),
            notesPlain: try DuressFieldCrypto.decrypt(record.notesEncrypted),
            numbers: record.numbers
        )
    }

    func save(_ draft: TrustedContactDraft) throws {
        var records = loadRecords()
        let now = Date()
        let id = draft.id ?? UUID()
        let numbers: [TrustedPhone] = draft.phoneNumbers
            .map { (raw, label) -> (String, String?) in
                (raw.trimmingCharacters(in: .whitespacesAndNewlines),
                 label?.trimmingCharacters(in: .whitespacesAndNewlines))
            }
            .filter { !$0.0.isEmpty }
            .map { TrustedPhone(label: $0.1?.nilIfEmptyDuress, raw: $0.0) }

        let record = TrustedContactRecord(
            id: id,
            displayName: draft.displayName.trimmingCharacters(in: .whitespacesAndNewlines),
            relation: draft.relation?.trimmingCharacters(in: .whitespacesAndNewlines).nilIfEmptyDuress,
            isOwner: draft.isOwner,
            maskedEmail: nil,
            addressEncrypted: try DuressFieldCrypto.encrypt(draft.address?.trimmingCharacters(in: .whitespacesAndNewlines)),
            notesEncrypted: try DuressFieldCrypto.encrypt(draft.notes?.trimmingCharacters(in: .whitespacesAndNewlines)),
            numbers: numbers,
            createdAt: records.first(where: { $0.id == id })?.createdAt ?? now,
            updatedAt: now
        )
        if let idx = records.firstIndex(where: { $0.id == id }) {
            records[idx] = record
        } else {
            records.append(record)
        }
        try persist(records)
    }

    func delete(id: UUID) throws {
        var records = loadRecords()
        records.removeAll { $0.id == id }
        try persist(records)
    }

    func seedPlaceholdersIfEmpty(_ placeholders: [TrustedContactDraft]) throws {
        guard loadRecords().isEmpty else { return }
        for draft in placeholders { try save(draft) }
    }

    func wipe() {
        try? FileManager.default.removeItem(at: storageURL)
    }

    // MARK: - Persistence

    private func loadRecords() -> [TrustedContactRecord] {
        guard let data = try? Data(contentsOf: storageURL) else { return [] }
        return (try? JSONDecoder.duress.decode([TrustedContactRecord].self, from: data)) ?? []
    }

    private func persist(_ records: [TrustedContactRecord]) throws {
        let sorted = records.sorted { lhs, rhs in
            if lhs.isOwner != rhs.isOwner { return lhs.isOwner }
            return lhs.displayName.lowercased() < rhs.displayName.lowercased()
        }
        let data = try JSONEncoder.duress.encode(sorted)
        try data.write(to: storageURL, options: [.atomic, .completeFileProtection])
    }
}

private extension TrustedContactRecord {
    func toMasked() -> MaskedContact {
        MaskedContact(
            id: id,
            displayName: displayName,
            relation: relation,
            isOwner: isOwner,
            maskedEmail: maskedEmail,
            maskedNumbers: numbers.map { $0.maskedForDisplay },
            normalisedNumbers: numbers.map { $0.normalisedNumber }
        )
    }
}

private extension JSONEncoder {
    static let duress: JSONEncoder = {
        let e = JSONEncoder()
        e.dateEncodingStrategy = .iso8601
        e.outputFormatting = [.sortedKeys]
        return e
    }()
}

private extension JSONDecoder {
    static let duress: JSONDecoder = {
        let d = JSONDecoder()
        d.dateDecodingStrategy = .iso8601
        return d
    }()
}

private extension String {
    var nilIfEmptyDuress: String? { isEmpty ? nil : self }
}
