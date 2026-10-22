// SPDX-FileCopyrightText: 2026 VanPaulTek Inc
// SPDX-License-Identifier: AGPL-3.0-or-later
import Foundation
import CryptoKit
import Security

/// Keychain-backed AES-GCM encryption for free-text fields (address, notes).
/// Equivalent of Android's FieldCrypto; uses Keychain to store a 256-bit symmetric key.
enum FieldCrypto {

    private static let service = "com.onlytrusted.app"
    private static let account = "field-key.v1"

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

    // MARK: - Key management

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
