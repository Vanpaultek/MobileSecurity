// SPDX-FileCopyrightText: 2026 VanPaulTek Inc
// SPDX-License-Identifier: AGPL-3.0-or-later
import Foundation
import CoreGraphics
import ImageIO
import UIKit
import CryptoKit
import CommonCrypto
import Security

/// Steganographic Vault (Elite) — hides an AES-256-GCM encrypted vault blob inside an
/// innocuous carrier file. Two carrier formats are supported:
///
///   1. PNG least-significant-bit (LSB) encoding — each byte of ciphertext is spread across
///      eight consecutive pixel RGB channels, flipping the least-significant bit to encode
///      the next bit. Visually indistinguishable from the carrier.
///   2. PDF trailing-bytes encoding — PDFs tolerate arbitrary trailing garbage after `%%EOF`.
///      We append `OT3SG | u32 length | ciphertext` directly to the raw PDF bytes.
///
/// Both formats share the same ciphertext layout as the Elite B1 encrypted backup, minus the
/// `OT2BAK\0` magic — this file injects its own container header (`OT3SG`) around a raw
/// AES-GCM blob of `salt | nonce | ciphertext | tag`.
///
/// Design goals:
///  - Forensic-plausible: the carrier remains a valid PNG / PDF that any file browser renders.
///  - Zero local state: no sidecar file. The output *is* the vault.
///  - Symmetric with iOS/Android: the Android implementation uses the same `OT3SG` header +
///    layout so a stego file created on one platform decrypts on the other.
enum StegoCrypto {

    /// Container magic, prepended to the encrypted payload inside the carrier. Four ASCII
    /// bytes "OT3S" + a single 'G' so a hex-dump reader can spot the marker at a glance.
    static let magic: [UInt8] = [0x4F, 0x54, 0x33, 0x53, 0x47] // "OT3SG"
    static let saltBytes = 16
    static let nonceBytes = 12
    static let tagBytes = 16
    static let keyBytes = 32
    static let iterations: UInt32 = 200_000
    /// PBKDF2 pepper hashed into the derived key so a stego-vault passphrase cannot be
    /// reused against a `.ot2bak` backup even if the user picks the same passphrase.
    static let pepper = "onlytrusted.stego.v1"

    enum CarrierFormat: String, CaseIterable, Identifiable {
        case png
        case pdf
        var id: String { rawValue }
        var displayName: String {
            switch self {
            case .png: return "PNG image"
            case .pdf: return "PDF document"
            }
        }
    }

    enum StegoError: Error, LocalizedError {
        case carrierTooSmall(Int, Int)
        case carrierUnreadable
        case notPNG
        case notPDF
        case noVaultFound
        case decryptionFailed
        case writeFailed(String)
        case passphraseWeak(String)

        var errorDescription: String? {
            switch self {
            case .carrierTooSmall(let have, let need):
                return "Carrier is too small to hide the vault — need \(need) pixels, have \(have). Pick a larger image."
            case .carrierUnreadable: return "Carrier file could not be read as an image."
            case .notPNG: return "This file is not a PNG."
            case .notPDF: return "This file is not a PDF."
            case .noVaultFound:
                return "No vault found in this file. Check your passphrase or try a different carrier."
            case .decryptionFailed:
                return "No vault found in this file. Check your passphrase or try a different carrier."
            case .writeFailed(let msg): return msg
            case .passphraseWeak(let msg): return msg
            }
        }
    }

    // MARK: - Encrypt (produces OT3SG container)

    /// Encrypts `plaintext` with `passphrase` and wraps it in an `OT3SG` container of the shape:
    ///
    ///   [MAGIC (5)] [u32 payload length] [16 salt] [12 nonce] [ciphertext] [16 tag]
    ///
    /// The returned bytes are what gets embedded into the carrier.
    static func makeOT3SGPayload(plaintext: Data, passphrase: String) throws -> Data {
        if let bad = BackupCrypto.validate(passphrase: passphrase) {
            throw StegoError.passphraseWeak(bad)
        }
        let salt = randomBytes(count: saltBytes)
        let key = try deriveKey(passphrase: passphrase, salt: salt)
        let nonceData = randomBytes(count: nonceBytes)
        let nonce = try AES.GCM.Nonce(data: nonceData)
        let sealed = try AES.GCM.seal(plaintext, using: SymmetricKey(data: key), nonce: nonce)

        var body = Data()
        body.append(salt)
        body.append(nonceData)
        body.append(sealed.ciphertext)
        body.append(sealed.tag)

        var out = Data()
        out.append(contentsOf: magic)
        out.append(contentsOf: beU32(UInt32(body.count)))
        out.append(body)
        return out
    }

    /// Reverse of `makeOT3SGPayload`. Caller provides the raw `OT3SG` byte sequence previously
    /// extracted from the carrier. Returns the plaintext vault blob.
    static func decryptOT3SGPayload(payload: Data, passphrase: String) throws -> Data {
        guard payload.count > magic.count + 4 + saltBytes + nonceBytes + tagBytes else {
            throw StegoError.noVaultFound
        }
        for (i, b) in magic.enumerated() where payload[payload.startIndex + i] != b {
            throw StegoError.noVaultFound
        }
        let lenRange = (payload.startIndex + magic.count)..<(payload.startIndex + magic.count + 4)
        let bodyLength = Int(beU32(payload.subdata(in: lenRange)))
        guard payload.count >= magic.count + 4 + bodyLength else {
            throw StegoError.noVaultFound
        }
        let bodyStart = payload.startIndex + magic.count + 4
        let body = payload.subdata(in: bodyStart..<(bodyStart + bodyLength))

        let salt = body.subdata(in: 0..<saltBytes)
        let nonceData = body.subdata(in: saltBytes..<(saltBytes + nonceBytes))
        let tag = body.subdata(in: (body.count - tagBytes)..<body.count)
        let ciphertext = body.subdata(in: (saltBytes + nonceBytes)..<(body.count - tagBytes))

        let key = try deriveKey(passphrase: passphrase, salt: salt)
        let nonce = try AES.GCM.Nonce(data: nonceData)
        let box = try AES.GCM.SealedBox(nonce: nonce, ciphertext: ciphertext, tag: tag)
        do {
            return try AES.GCM.open(box, using: SymmetricKey(data: key))
        } catch {
            throw StegoError.decryptionFailed
        }
    }

    // MARK: - PNG LSB

    /// Encodes `payload` (an OT3SG container) into a copy of the carrier PNG by flipping the
    /// least-significant bit of consecutive RGB channel bytes. Returns the newly-encoded PNG
    /// bytes.
    static func embedIntoPNG(carrierPNG: Data, payload: Data) throws -> Data {
        guard let image = UIImage(data: carrierPNG), let cg = image.cgImage else {
            throw StegoError.carrierUnreadable
        }

        let width = cg.width
        let height = cg.height
        let bitsNeeded = payload.count * 8
        let availableBits = width * height * 3 // 3 channels per pixel
        if availableBits < bitsNeeded {
            throw StegoError.carrierTooSmall(availableBits / 8, (bitsNeeded + 7) / 8)
        }

        // Normalize to RGBA8 so we can touch each byte directly.
        let bytesPerPixel = 4
        let bytesPerRow = width * bytesPerPixel
        var pixelBytes = [UInt8](repeating: 0, count: height * bytesPerRow)

        let colorSpace = CGColorSpaceCreateDeviceRGB()
        let bitmapInfo: UInt32 = CGImageAlphaInfo.premultipliedLast.rawValue
        guard let ctx = CGContext(
            data: &pixelBytes,
            width: width, height: height,
            bitsPerComponent: 8, bytesPerRow: bytesPerRow,
            space: colorSpace, bitmapInfo: bitmapInfo
        ) else {
            throw StegoError.carrierUnreadable
        }
        ctx.draw(cg, in: CGRect(x: 0, y: 0, width: width, height: height))

        // Walk through payload bit-by-bit, flipping the LSB of each successive RGB byte.
        // We skip the alpha channel to preserve transparency and avoid dithering artifacts.
        var bitCursor = 0
        let totalBits = payload.count * 8
        for pixel in 0..<(width * height) where bitCursor < totalBits {
            let base = pixel * bytesPerPixel
            for channel in 0..<3 where bitCursor < totalBits {
                let byteIdx = bitCursor / 8
                let bitIdx = 7 - (bitCursor % 8)
                let bit = (payload[payload.startIndex + byteIdx] >> bitIdx) & 1
                pixelBytes[base + channel] = (pixelBytes[base + channel] & 0xFE) | bit
                bitCursor += 1
            }
        }

        guard let outCG = ctx.makeImage() else { throw StegoError.carrierUnreadable }
        return try pngData(from: outCG) ?? { throw StegoError.carrierUnreadable }()
    }

    /// Reverse of `embedIntoPNG`: scans the LSBs for the `OT3SG` magic and length-prefixed
    /// payload, returns the extracted OT3SG container.
    static func extractFromPNG(stegoPNG: Data) throws -> Data {
        guard let image = UIImage(data: stegoPNG), let cg = image.cgImage else {
            throw StegoError.notPNG
        }
        let width = cg.width
        let height = cg.height
        let bytesPerPixel = 4
        let bytesPerRow = width * bytesPerPixel
        var pixelBytes = [UInt8](repeating: 0, count: height * bytesPerRow)

        let colorSpace = CGColorSpaceCreateDeviceRGB()
        let bitmapInfo: UInt32 = CGImageAlphaInfo.premultipliedLast.rawValue
        guard let ctx = CGContext(
            data: &pixelBytes, width: width, height: height,
            bitsPerComponent: 8, bytesPerRow: bytesPerRow,
            space: colorSpace, bitmapInfo: bitmapInfo
        ) else {
            throw StegoError.notPNG
        }
        ctx.draw(cg, in: CGRect(x: 0, y: 0, width: width, height: height))

        // Read enough bits to cover magic + length first; only then do we know how long
        // the full payload is.
        let headerBits = (magic.count + 4) * 8
        var header = Data(count: magic.count + 4)
        try readBits(from: pixelBytes, bitStart: 0, bitCount: headerBits, into: &header,
                     bytesPerPixel: bytesPerPixel)
        for (i, b) in magic.enumerated() where header[i] != b {
            throw StegoError.noVaultFound
        }
        let payloadLength = Int(beU32(header.subdata(in: magic.count..<(magic.count + 4))))
        let totalBits = (magic.count + 4 + payloadLength) * 8
        if totalBits > width * height * 3 {
            throw StegoError.noVaultFound
        }
        var payload = Data(count: magic.count + 4 + payloadLength)
        try readBits(from: pixelBytes, bitStart: 0, bitCount: totalBits, into: &payload,
                     bytesPerPixel: bytesPerPixel)
        return payload
    }

    // MARK: - PDF trailing-bytes

    /// Appends an OT3SG payload to the end of a PDF. The PDF spec treats bytes after `%%EOF`
    /// as inert; Preview / Acrobat ignore them when rendering.
    static func embedIntoPDF(carrierPDF: Data, payload: Data) throws -> Data {
        guard carrierPDF.starts(with: [0x25, 0x50, 0x44, 0x46]) else {
            throw StegoError.notPDF // "%PDF"
        }
        var out = Data()
        out.append(carrierPDF)
        out.append(contentsOf: [0x0A]) // newline so our marker starts on its own line
        out.append(payload)
        return out
    }

    /// Scans a PDF for the trailing OT3SG payload. Searches from the end backwards for the
    /// magic bytes so a stego-pdf with later edits still resolves correctly.
    static func extractFromPDF(stegoPDF: Data) throws -> Data {
        guard stegoPDF.starts(with: [0x25, 0x50, 0x44, 0x46]) else {
            throw StegoError.notPDF
        }
        // Find the last occurrence of the magic sequence in the data.
        let magicBytes = Data(magic)
        guard let magicRange = stegoPDF.range(of: magicBytes, options: .backwards) else {
            throw StegoError.noVaultFound
        }
        let afterMagic = magicRange.upperBound
        guard afterMagic + 4 <= stegoPDF.count else { throw StegoError.noVaultFound }
        let lenBytes = stegoPDF.subdata(in: afterMagic..<(afterMagic + 4))
        let payloadLength = Int(beU32(lenBytes))
        let totalLength = magic.count + 4 + payloadLength
        guard magicRange.lowerBound + totalLength <= stegoPDF.count else {
            throw StegoError.noVaultFound
        }
        return stegoPDF.subdata(in: magicRange.lowerBound..<(magicRange.lowerBound + totalLength))
    }

    // MARK: - Helpers

    private static func pngData(from cg: CGImage) throws -> Data? {
        let data = NSMutableData()
        guard let dest = CGImageDestinationCreateWithData(data, "public.png" as CFString, 1, nil) else {
            return nil
        }
        CGImageDestinationAddImage(dest, cg, nil)
        guard CGImageDestinationFinalize(dest) else { return nil }
        return data as Data
    }

    private static func readBits(from pixels: [UInt8], bitStart: Int, bitCount: Int,
                                 into out: inout Data, bytesPerPixel: Int) throws {
        for i in 0..<out.count { out[i] = 0 }
        var pixelIndex = bitStart / 3
        var channelIndex = bitStart % 3
        for bitCursor in 0..<bitCount {
            let byteIdx = bitCursor / 8
            let bitIdx = 7 - (bitCursor % 8)
            let base = pixelIndex * bytesPerPixel + channelIndex
            if base >= pixels.count { throw StegoError.noVaultFound }
            let bit = pixels[base] & 1
            out[byteIdx] |= UInt8(bit << bitIdx)
            channelIndex += 1
            if channelIndex == 3 {
                channelIndex = 0
                pixelIndex += 1
            }
        }
    }

    // MARK: - Crypto plumbing

    private static func deriveKey(passphrase: String, salt: Data) throws -> Data {
        // Mix pepper into salt so the derived key is isolated from the backup domain.
        var effectiveSalt = Data(pepper.utf8)
        effectiveSalt.append(salt)

        var out = Data(count: keyBytes)
        let pwBytes = Array(passphrase.utf8)
        let saltData = effectiveSalt
        let status = out.withUnsafeMutableBytes { outPtr -> Int32 in
            saltData.withUnsafeBytes { saltPtr -> Int32 in
                CCKeyDerivationPBKDF(
                    CCPBKDFAlgorithm(kCCPBKDF2),
                    pwBytes, pwBytes.count,
                    saltPtr.bindMemory(to: UInt8.self).baseAddress, saltData.count,
                    CCPseudoRandomAlgorithm(kCCPRFHmacAlgSHA256),
                    iterations,
                    outPtr.bindMemory(to: UInt8.self).baseAddress, keyBytes
                )
            }
        }
        guard status == kCCSuccess else { throw StegoError.decryptionFailed }
        return out
    }

    private static func randomBytes(count: Int) -> Data {
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
}
