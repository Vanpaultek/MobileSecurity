// SPDX-FileCopyrightText: 2026 VanPaulTek Inc
// SPDX-License-Identifier: AGPL-3.0-or-later
import Foundation
import CryptoKit
import CommonCrypto
import Security

/// AES-256-GCM encrypted backup blob with PBKDF2-SHA256 key derivation.
///
/// File layout:
///   [MAGIC "OT2BAK\0" (7 bytes)]
///   [u32 version (big-endian) = 1]
///   [16-byte salt]
///   [12-byte nonce]
///   [ciphertext ...]
///   [16-byte GCM tag]
///
/// PBKDF2 parameters: 200,000 iterations SHA-256 → 32-byte key.
///
/// This file is also used by the restore path. The passphrase is never persisted; the user
/// re-enters it on restore.
enum BackupCrypto {

    static let magic: [UInt8] = [0x4F, 0x54, 0x32, 0x42, 0x41, 0x4B, 0x00] // "OT2BAK\0"
    static let version: UInt32 = 1
    static let saltBytes = 16
    static let nonceBytes = 12
    static let tagBytes = 16
    static let keyBytes = 32
    static let iterations: UInt32 = 200_000

    enum CryptoError: Error {
        case shortFile
        case badMagic
        case unsupportedVersion
        case keyDerivationFailed(Int32)
        case decryptionFailed
    }

    // MARK: - Encrypt

    /// Returns the full file bytes (header + ciphertext + tag) ready to be written to disk.
    static func encrypt(plaintext: Data, passphrase: String) throws -> Data {
        let salt = randomBytes(count: saltBytes)
        let key = try deriveKey(passphrase: passphrase, salt: salt)
        let nonceData = randomBytes(count: nonceBytes)
        let nonce = try AES.GCM.Nonce(data: nonceData)

        let sealed = try AES.GCM.seal(plaintext, using: SymmetricKey(data: key), nonce: nonce)

        var out = Data()
        out.append(contentsOf: magic)
        out.append(contentsOf: beU32(version))
        out.append(salt)
        out.append(nonceData)
        out.append(sealed.ciphertext)
        out.append(sealed.tag)
        return out
    }

    // MARK: - Decrypt

    static func decrypt(fileBytes: Data, passphrase: String) throws -> Data {
        let headerLen = magic.count + 4 + saltBytes + nonceBytes
        guard fileBytes.count > headerLen + tagBytes else { throw CryptoError.shortFile }

        for (i, b) in magic.enumerated() where fileBytes[i] != b {
            throw CryptoError.badMagic
        }
        let ver = beU32(fileBytes.subdata(in: magic.count..<(magic.count + 4)))
        guard ver == version else { throw CryptoError.unsupportedVersion }

        let saltRange = (magic.count + 4)..<(magic.count + 4 + saltBytes)
        let nonceRange = saltRange.upperBound..<(saltRange.upperBound + nonceBytes)
        let tagRange = (fileBytes.count - tagBytes)..<fileBytes.count
        let ciphertextRange = nonceRange.upperBound..<tagRange.lowerBound

        let salt = fileBytes.subdata(in: saltRange)
        let nonceData = fileBytes.subdata(in: nonceRange)
        let ciphertext = fileBytes.subdata(in: ciphertextRange)
        let tag = fileBytes.subdata(in: tagRange)

        let key = try deriveKey(passphrase: passphrase, salt: salt)
        let nonce = try AES.GCM.Nonce(data: nonceData)
        let box = try AES.GCM.SealedBox(nonce: nonce, ciphertext: ciphertext, tag: tag)
        do {
            return try AES.GCM.open(box, using: SymmetricKey(data: key))
        } catch {
            throw CryptoError.decryptionFailed
        }
    }

    // MARK: - PBKDF2

    static func deriveKey(passphrase: String, salt: Data) throws -> Data {
        var out = Data(count: keyBytes)
        let pwBytes = Array(passphrase.utf8)
        let status = out.withUnsafeMutableBytes { outPtr -> Int32 in
            salt.withUnsafeBytes { saltPtr -> Int32 in
                CCKeyDerivationPBKDF(
                    CCPBKDFAlgorithm(kCCPBKDF2),
                    pwBytes, pwBytes.count,
                    saltPtr.bindMemory(to: UInt8.self).baseAddress, salt.count,
                    CCPseudoRandomAlgorithm(kCCPRFHmacAlgSHA256),
                    iterations,
                    outPtr.bindMemory(to: UInt8.self).baseAddress, keyBytes
                )
            }
        }
        guard status == kCCSuccess else { throw CryptoError.keyDerivationFailed(status) }
        return out
    }

    // MARK: - Random / helpers

    static func randomBytes(count: Int) -> Data {
        var bytes = [UInt8](repeating: 0, count: count)
        _ = SecRandomCopyBytes(kSecRandomDefault, count, &bytes)
        return Data(bytes)
    }

    private static func beU32(_ value: UInt32) -> [UInt8] {
        [
            UInt8((value >> 24) & 0xFF),
            UInt8((value >> 16) & 0xFF),
            UInt8((value >> 8) & 0xFF),
            UInt8(value & 0xFF)
        ]
    }

    private static func beU32(_ data: Data) -> UInt32 {
        (UInt32(data[data.startIndex]) << 24) |
            (UInt32(data[data.startIndex + 1]) << 16) |
            (UInt32(data[data.startIndex + 2]) << 8) |
            UInt32(data[data.startIndex + 3])
    }

    // MARK: - Passphrase strength

    /// Returns nil if the passphrase passes policy, otherwise a user-facing reason.
    static func validate(passphrase: String) -> String? {
        if passphrase.count < 12 { return "Use at least 12 characters." }
        let hasLetter = passphrase.rangeOfCharacter(from: .letters) != nil
        let hasDigit = passphrase.rangeOfCharacter(from: .decimalDigits) != nil
        let symbols = CharacterSet.alphanumerics.inverted
        let hasSymbol = passphrase.rangeOfCharacter(from: symbols) != nil
        if !hasLetter { return "Include at least one letter." }
        if !hasDigit { return "Include at least one digit." }
        if !hasSymbol { return "Include at least one symbol." }
        return nil
    }
}
