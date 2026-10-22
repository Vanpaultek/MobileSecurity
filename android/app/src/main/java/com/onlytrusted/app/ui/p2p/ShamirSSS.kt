// SPDX-FileCopyrightText: 2026 VanPaulTek Inc
// SPDX-License-Identifier: AGPL-3.0-or-later
package com.onlytrusted.app.ui.p2p

import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Shamir Secret Sharing over GF(2^8) — Elite feature. Mirrors the iOS [ShamirSSS.swift]
 * implementation byte-for-byte so a share generated on one platform can be reconstructed
 * on the other.
 *
 * Each byte of the master key becomes an independent polynomial of degree K-1 whose
 * constant term is the secret byte. Evaluating at `x = 1..N` yields the N shares.
 * Recovery uses Lagrange interpolation in GF(2^8) with the AES irreducible polynomial
 * `x^8 + x^4 + x^3 + x + 1` (0x11B).
 *
 * Each wire envelope adds a 16-byte random `salt`, a `splitID = SHA256(secret || salt)`,
 * and an HMAC-SHA256 `proof` keyed with the same hash so a recipient cannot tamper with
 * or swap shares without detection on recovery.
 */
object ShamirSSS {

    /** A raw split share. [bytes] holds one Shamir y-coord per secret byte. */
    data class Share(val index: Int, val bytes: ByteArray) {
        override fun equals(other: Any?): Boolean =
            other is Share && index == other.index && bytes.contentEquals(other.bytes)
        override fun hashCode(): Int = 31 * index + bytes.contentHashCode()
    }

    /** Tamper-evident envelope delivered to one recipient device. */
    data class Envelope(
        val splitID: String,
        val index: Int,
        val threshold: Int,
        val total: Int,
        val salt: ByteArray,
        val shareBytes: ByteArray,
        val proof: ByteArray,
        val createdAtMillis: Long
    ) {
        override fun equals(other: Any?): Boolean {
            if (other !is Envelope) return false
            return splitID == other.splitID &&
                index == other.index &&
                threshold == other.threshold &&
                total == other.total &&
                salt.contentEquals(other.salt) &&
                shareBytes.contentEquals(other.shareBytes) &&
                proof.contentEquals(other.proof)
        }
        override fun hashCode(): Int {
            var r = splitID.hashCode()
            r = 31 * r + index
            r = 31 * r + threshold
            r = 31 * r + total
            r = 31 * r + salt.contentHashCode()
            r = 31 * r + shareBytes.contentHashCode()
            r = 31 * r + proof.contentHashCode()
            return r
        }
    }

    class SplitError(msg: String) : RuntimeException(msg)

    private val rng = SecureRandom()

    /** Split [secret] into [total] shares with threshold [threshold]. */
    fun split(secret: ByteArray, threshold: Int, total: Int): List<Share> {
        require(threshold >= 2 && threshold <= total && total <= 255 && secret.isNotEmpty()) {
            throw SplitError("Invalid Shamir parameters.")
        }
        val out = Array(total) { ByteArray(secret.size) }
        val coeffs = ByteArray(threshold)
        for (byteIdx in secret.indices) {
            coeffs[0] = secret[byteIdx]
            for (i in 1 until threshold) {
                coeffs[i] = randomByte()
            }
            for (x in 1..total) {
                out[x - 1][byteIdx] = eval(coeffs, x.toByte())
            }
        }
        return (1..total).map { x -> Share(x, out[x - 1]) }
    }

    /** Combine [shares] (at least K of them) to recover the secret. */
    fun combine(shares: List<Share>): ByteArray {
        require(shares.size >= 2) { "need at least 2 shares" }
        val length = shares[0].bytes.size
        require(shares.all { it.bytes.size == length }) { "share length mismatch" }

        val out = ByteArray(length)
        for (byteIdx in 0 until length) {
            var sum: Byte = 0
            for (i in shares.indices) {
                var num: Byte = 1
                var den: Byte = 1
                val xi = shares[i].index.toByte()
                val yi = shares[i].bytes[byteIdx]
                for (j in shares.indices) {
                    if (j == i) continue
                    val xj = shares[j].index.toByte()
                    num = gfMul(num, xj)                          // product of (0 - xj) = xj
                    den = gfMul(den, (xi.toInt() xor xj.toInt()).toByte())
                }
                sum = (sum.toInt() xor gfMul(yi, gfMul(num, gfInverse(den))).toInt()).toByte()
            }
            out[byteIdx] = sum
        }
        return out
    }

    // --- Envelope helpers -----------------------------------------------------------------

    /** Hex-encoded SHA-256(secret || salt) — stable id for a given split. */
    fun splitID(secret: ByteArray, salt: ByteArray): String {
        val md = MessageDigest.getInstance("SHA-256")
        md.update(secret); md.update(salt)
        return md.digest().toHex()
    }

    /** HMAC-SHA256 proof bound to this share. See file header for keying. */
    fun proof(secret: ByteArray, salt: ByteArray, index: Int, shareBytes: ByteArray): ByteArray {
        val md = MessageDigest.getInstance("SHA-256")
        md.update(secret); md.update(salt)
        val keyData = md.digest()
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(keyData, "HmacSHA256"))
        mac.update(byteArrayOf(index.toByte()))
        mac.update(shareBytes)
        return mac.doFinal()
    }

    /** Build the tamper-evident envelopes ready to send to peers. */
    fun makeEnvelopes(secret: ByteArray, threshold: Int, total: Int): Triple<List<Envelope>, ByteArray, String> {
        val salt = randomBytes(16)
        val id = splitID(secret, salt)
        val shares = split(secret, threshold, total)
        val envelopes = shares.map { s ->
            Envelope(
                splitID = id,
                index = s.index,
                threshold = threshold,
                total = total,
                salt = salt,
                shareBytes = s.bytes,
                proof = proof(secret, salt, s.index, s.bytes),
                createdAtMillis = System.currentTimeMillis()
            )
        }
        return Triple(envelopes, salt, id)
    }

    /** Verify an envelope's proof against the reconstructed secret. */
    fun verify(envelope: Envelope, secret: ByteArray): Boolean {
        val expected = proof(secret, envelope.salt, envelope.index, envelope.shareBytes)
        return expected.contentEquals(envelope.proof)
    }

    // --- GF(2^8) arithmetic (AES irreducible polynomial 0x11B) ---------------------------

    /** Russian-peasant GF(2^8) multiplication. */
    fun gfMul(a: Byte, b: Byte): Byte {
        var result = 0
        var x = a.toInt() and 0xff
        var y = b.toInt() and 0xff
        while (y != 0) {
            if (y and 1 == 1) result = result xor x
            val hi = (x and 0x80) != 0
            x = (x shl 1) and 0xff
            if (hi) x = x xor 0x1B
            y = y ushr 1
        }
        return result.toByte()
    }

    /** Multiplicative inverse via Fermat's little theorem: a^254. */
    fun gfInverse(a: Byte): Byte {
        require(a.toInt() and 0xff != 0) { "no inverse for zero" }
        var result: Byte = 1
        var base = a
        var exp = 254
        while (exp != 0) {
            if (exp and 1 == 1) result = gfMul(result, base)
            base = gfMul(base, base)
            exp = exp ushr 1
        }
        return result
    }

    /** Horner evaluation of a polynomial at `x`. Coefficients are `[c0, c1, c2, …]`. */
    fun eval(coeffs: ByteArray, x: Byte): Byte {
        var acc: Byte = 0
        for (i in coeffs.indices.reversed()) {
            acc = (gfMul(acc, x).toInt() xor coeffs[i].toInt()).toByte()
        }
        return acc
    }

    // --- RNG ------------------------------------------------------------------------------

    fun randomBytes(count: Int): ByteArray {
        val out = ByteArray(count)
        rng.nextBytes(out)
        return out
    }

    private fun randomByte(): Byte {
        val b = ByteArray(1)
        rng.nextBytes(b)
        return b[0]
    }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
}
