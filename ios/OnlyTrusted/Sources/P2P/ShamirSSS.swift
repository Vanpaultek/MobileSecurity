// SPDX-FileCopyrightText: 2026 VanPaulTek Inc
// SPDX-License-Identifier: AGPL-3.0-or-later
import Foundation
import CryptoKit

/// Shamir Secret Sharing over GF(2^8) — Elite feature.
///
/// The master key is treated as a vector of bytes. Each byte becomes an independent
/// polynomial of degree K-1 whose constant term is the secret byte. Evaluating at
/// `x = 1..N` yields N shares; any K of them recover every polynomial (and hence the
/// secret) via Lagrange interpolation over GF(2^8) with the AES irreducible polynomial
/// `x^8 + x^4 + x^3 + x + 1` (0x11B).
///
/// Reference test vectors (byte-level) cross-checked against the Kotlin twin
/// `ShamirSSS.kt` so re-splitting on either platform recovers to the same bytes.
///
/// Each distributed share is wrapped with:
///   - 16-byte random `salt` (unique per split).
///   - HMAC-SHA256 proof `tag = HMAC(key = SHA256(masterKey || salt), msg = index || share_bytes)`
///     so a recipient cannot swap or forge their share without the owner noticing on recovery.
///   - `splitID = SHA256(masterKey || salt)` — stable identifier used by the recipient to find
///     the right stash when the owner comes back for recovery.
enum ShamirSSS {

    /// A split share. `bytes` are the Shamir evaluations at `index` for each secret byte.
    struct Share: Equatable {
        let index: UInt8          // Shamir x-coordinate (1..N)
        let bytes: Data           // byte-for-byte y-coordinates (same length as secret)
    }

    /// A tamper-evident envelope mailed to one recipient device. `splitID` lets the
    /// recipient's local store find this share again on recovery.
    struct Envelope: Codable, Equatable {
        let splitID: String       // hex of SHA256(masterKey || salt), 64 chars
        let index: UInt8          // 1..N
        let threshold: UInt8      // K
        let total: UInt8          // N
        let salt: Data            // 16 bytes
        let shareBytes: Data      // = Share.bytes
        let proof: Data           // HMAC-SHA256(key = sha256(secret||salt), msg = [index] || shareBytes)
        let createdAt: Date
    }

    enum SplitError: Error {
        case invalidParameters
        case secretTooShort
        case notEnoughShares
        case indexMismatch
        case proofMismatch
    }

    /// Split `secret` into `n` shares with threshold `k`.
    static func split(secret: Data, threshold k: Int, total n: Int) throws -> [Share] {
        guard k >= 2, k <= n, n <= 255, !secret.isEmpty else {
            throw SplitError.invalidParameters
        }

        var out: [[UInt8]] = Array(repeating: [UInt8](repeating: 0, count: secret.count),
                                   count: n)
        // For every byte of the secret, pick k-1 random coefficients and evaluate at x=1..n.
        for (byteIdx, secretByte) in secret.enumerated() {
            var coeffs = [UInt8](repeating: 0, count: k)
            coeffs[0] = secretByte
            for i in 1..<k { coeffs[i] = Self.randomByte() }
            for x in 1...n {
                out[x - 1][byteIdx] = Self.eval(coeffs: coeffs, at: UInt8(x))
            }
        }
        return (1...n).map { x in Share(index: UInt8(x), bytes: Data(out[x - 1])) }
    }

    /// Combine `shares` (at least K of them) to recover the original secret.
    static func combine(shares: [Share]) throws -> Data {
        guard shares.count >= 2 else { throw SplitError.notEnoughShares }
        let secretLength = shares[0].bytes.count
        guard shares.allSatisfy({ $0.bytes.count == secretLength }) else {
            throw SplitError.indexMismatch
        }
        var out = [UInt8](repeating: 0, count: secretLength)
        for byteIdx in 0..<secretLength {
            var sum: UInt8 = 0
            for i in 0..<shares.count {
                var num: UInt8 = 1
                var den: UInt8 = 1
                let xi = shares[i].index
                let yi = shares[i].bytes[byteIdx]
                for j in 0..<shares.count where j != i {
                    let xj = shares[j].index
                    num = gfMul(num, xj)                       // product of (0 - xj) = xj in GF(2^8)
                    den = gfMul(den, xi ^ xj)                  // product of (xi - xj) = xi XOR xj
                }
                sum ^= gfMul(yi, gfMul(num, gfInverse(den)))
            }
            out[byteIdx] = sum
        }
        return Data(out)
    }

    // MARK: - Envelope helpers (adds HMAC proof + splitID per the brief)

    /// Compute the stable split identifier for a given secret + salt. Hex of SHA-256.
    static func splitID(secret: Data, salt: Data) -> String {
        let h = SHA256.hash(data: secret + salt)
        return h.map { String(format: "%02x", $0) }.joined()
    }

    /// Build the proof HMAC bound to this (index, shareBytes).
    static func proof(secret: Data, salt: Data, index: UInt8, shareBytes: Data) -> Data {
        let keyData = Data(SHA256.hash(data: secret + salt))
        let key = SymmetricKey(data: keyData)
        var msg = Data()
        msg.append(index)
        msg.append(shareBytes)
        let mac = HMAC<SHA256>.authenticationCode(for: msg, using: key)
        return Data(mac)
    }

    /// Wrap raw shares into envelopes with proof and metadata, ready to send to peers.
    static func makeEnvelopes(secret: Data,
                              threshold k: Int,
                              total n: Int) throws -> (envelopes: [Envelope], salt: Data, splitID: String) {
        let salt = Self.randomBytes(16)
        let id = splitID(secret: secret, salt: salt)
        let shares = try split(secret: secret, threshold: k, total: n)
        let envs = shares.map { share -> Envelope in
            Envelope(
                splitID: id,
                index: share.index,
                threshold: UInt8(k),
                total: UInt8(n),
                salt: salt,
                shareBytes: share.bytes,
                proof: proof(secret: secret,
                             salt: salt,
                             index: share.index,
                             shareBytes: share.bytes),
                createdAt: Date()
            )
        }
        return (envs, salt, id)
    }

    /// Validate an envelope against the reconstructed secret. The owner uses this on recovery
    /// to make sure a returned share wasn't tampered with.
    static func verify(envelope: Envelope, againstSecret secret: Data) -> Bool {
        let expected = proof(secret: secret,
                             salt: envelope.salt,
                             index: envelope.index,
                             shareBytes: envelope.shareBytes)
        return expected == envelope.proof
    }

    // MARK: - GF(2^8) arithmetic (AES irreducible polynomial 0x11B)

    /// Multiplication in GF(2^8). Russian-peasant style — avoids the need for log/antilog tables.
    static func gfMul(_ a: UInt8, _ b: UInt8) -> UInt8 {
        var result: UInt8 = 0
        var x: UInt8 = a
        var y: UInt8 = b
        while y != 0 {
            if y & 1 == 1 { result ^= x }
            let hi = (x & 0x80) != 0
            x = x &<< 1
            if hi { x ^= 0x1B }  // reduction by x^8 + x^4 + x^3 + x + 1
            y &>>= 1
        }
        return result
    }

    /// Multiplicative inverse in GF(2^8). Uses Fermat: a^(2^8 - 2) = a^254.
    static func gfInverse(_ a: UInt8) -> UInt8 {
        precondition(a != 0, "GF(2^8): no inverse for zero")
        // a^254 = a^(128+64+32+16+8+4+2) — six multiplications after squares.
        var result: UInt8 = 1
        var base = a
        var exp: UInt8 = 254
        while exp != 0 {
            if exp & 1 == 1 { result = gfMul(result, base) }
            base = gfMul(base, base)
            exp >>= 1
        }
        return result
    }

    /// Evaluate polynomial with coefficients `[c0, c1, c2, …]` at `x` using Horner's rule.
    static func eval(coeffs: [UInt8], at x: UInt8) -> UInt8 {
        var acc: UInt8 = 0
        for c in coeffs.reversed() { acc = gfMul(acc, x) ^ c }
        return acc
    }

    // MARK: - RNG

    static func randomBytes(_ count: Int) -> Data {
        var out = [UInt8](repeating: 0, count: count)
        _ = SecRandomCopyBytes(kSecRandomDefault, count, &out)
        return Data(out)
    }

    private static func randomByte() -> UInt8 {
        var b: UInt8 = 0
        _ = SecRandomCopyBytes(kSecRandomDefault, 1, &b)
        return b
    }
}
