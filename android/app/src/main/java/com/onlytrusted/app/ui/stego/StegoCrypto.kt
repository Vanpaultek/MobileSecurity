// SPDX-FileCopyrightText: 2026 VanPaulTek Inc
// SPDX-License-Identifier: AGPL-3.0-or-later
package com.onlytrusted.app.ui.stego

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Steganographic Vault (Elite) — hides an AES-256-GCM encrypted vault blob inside an
 * innocuous carrier (PNG or PDF). Mirrors the iOS `StegoCrypto` byte-for-byte so a stego
 * file produced on one platform decrypts on the other.
 *
 * Container header (`OT3SG | u32 length | salt | nonce | ciphertext | tag`) is identical to
 * iOS. The PNG format uses LSB bit-spreading across RGB channels; PDF appends the payload
 * after `%%EOF` since the PDF spec tolerates trailing garbage.
 */
object StegoCrypto {

    val MAGIC: ByteArray = byteArrayOf(0x4F, 0x54, 0x33, 0x53, 0x47) // "OT3SG"
    const val SALT_BYTES = 16
    const val NONCE_BYTES = 12
    const val TAG_BITS = 128
    const val TAG_BYTES = 16
    const val KEY_BITS = 256
    const val KEY_BYTES = KEY_BITS / 8
    const val ITERATIONS = 200_000
    /** Pepper mixed into PBKDF2 salt so a stego passphrase never derives the same key as a
     *  `.ot2bak` passphrase even when the user reuses the string. */
    const val PEPPER = "onlytrusted.stego.v1"

    enum class CarrierFormat { PNG, PDF }

    class CarrierTooSmallException(val haveBytes: Int, val needBytes: Int) :
        Exception("Carrier is too small — need $needBytes bytes capacity, have $haveBytes.")

    class CarrierUnreadableException : Exception("Carrier file could not be read as an image.")
    class NotPNGException : Exception("This file is not a PNG.")
    class NotPDFException : Exception("This file is not a PDF.")
    class NoVaultFoundException :
        Exception("No vault found in this file. Check your passphrase or try a different carrier.")

    // --------------------------------------------------------------------- OT3SG container

    fun makeOT3SGPayload(plaintext: ByteArray, passphrase: CharArray): ByteArray {
        val salt = randomBytes(SALT_BYTES)
        val key = deriveKey(passphrase, salt)
        val nonce = randomBytes(NONCE_BYTES)

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_BITS, nonce))
        val sealed = cipher.doFinal(plaintext) // ciphertext + tag

        val body = salt + nonce + sealed // length is deterministic
        val header = ByteBuffer.allocate(MAGIC.size + 4).order(ByteOrder.BIG_ENDIAN).apply {
            put(MAGIC); putInt(body.size)
        }.array()
        return header + body
    }

    fun decryptOT3SGPayload(payload: ByteArray, passphrase: CharArray): ByteArray {
        if (payload.size < MAGIC.size + 4 + SALT_BYTES + NONCE_BYTES + TAG_BYTES) {
            throw NoVaultFoundException()
        }
        for (i in MAGIC.indices) if (payload[i] != MAGIC[i]) throw NoVaultFoundException()
        val bodyLen = ByteBuffer.wrap(payload, MAGIC.size, 4).order(ByteOrder.BIG_ENDIAN).int
        if (payload.size < MAGIC.size + 4 + bodyLen) throw NoVaultFoundException()
        val bodyStart = MAGIC.size + 4
        val salt = payload.copyOfRange(bodyStart, bodyStart + SALT_BYTES)
        val nonce = payload.copyOfRange(bodyStart + SALT_BYTES, bodyStart + SALT_BYTES + NONCE_BYTES)
        val ciphertextAndTag = payload.copyOfRange(bodyStart + SALT_BYTES + NONCE_BYTES,
                                                   bodyStart + bodyLen)

        val key = deriveKey(passphrase, salt)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_BITS, nonce))
        return try {
            cipher.doFinal(ciphertextAndTag)
        } catch (t: Throwable) {
            throw NoVaultFoundException()
        }
    }

    // --------------------------------------------------------------------- PNG LSB

    /** Embeds `payload` into a copy of `carrierPNG`. Returns PNG bytes. */
    fun embedIntoPNG(carrierPNG: ByteArray, payload: ByteArray): ByteArray {
        val carrier = BitmapFactory.decodeByteArray(carrierPNG, 0, carrierPNG.size)
            ?: throw CarrierUnreadableException()
        val width = carrier.width
        val height = carrier.height
        val bitsNeeded = payload.size * 8
        val availableBits = width * height * 3
        if (availableBits < bitsNeeded) {
            throw CarrierTooSmallException(availableBits / 8, (bitsNeeded + 7) / 8)
        }

        val pixels = IntArray(width * height)
        carrier.getPixels(pixels, 0, width, 0, 0, width, height)

        // ARGB_8888: A=24..31, R=16..23, G=8..15, B=0..7. iOS reads channels in R→G→B order
        // so we need to match that exactly: encode into R low-bit first, then G, then B.
        var bitCursor = 0
        val totalBits = payload.size * 8
        var pixelIdx = 0
        while (bitCursor < totalBits && pixelIdx < pixels.size) {
            var argb = pixels[pixelIdx]
            for (channel in 0 until 3) {
                if (bitCursor >= totalBits) break
                val byteIdx = bitCursor / 8
                val bitIdx = 7 - (bitCursor % 8)
                val bit = ((payload[byteIdx].toInt() ushr bitIdx) and 1)
                argb = when (channel) {
                    0 -> (argb and 0xFFFEFFFF.toInt()) or (bit shl 16) // R low-bit
                    1 -> (argb and 0xFFFFFEFF.toInt()) or (bit shl 8)  // G low-bit
                    else -> (argb and 0xFFFFFFFE.toInt()) or bit        // B low-bit
                }
                bitCursor++
            }
            pixels[pixelIdx] = argb
            pixelIdx++
        }

        val out = Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
        val stream = ByteArrayOutputStream()
        if (!out.compress(Bitmap.CompressFormat.PNG, 100, stream)) {
            throw CarrierUnreadableException()
        }
        return stream.toByteArray()
    }

    /** Extracts an OT3SG payload from a stego-PNG. */
    fun extractFromPNG(stegoPNG: ByteArray): ByteArray {
        val carrier = BitmapFactory.decodeByteArray(stegoPNG, 0, stegoPNG.size)
            ?: throw NotPNGException()
        val width = carrier.width
        val height = carrier.height
        val pixels = IntArray(width * height)
        carrier.getPixels(pixels, 0, width, 0, 0, width, height)

        val headerBits = (MAGIC.size + 4) * 8
        val header = ByteArray(MAGIC.size + 4)
        readBits(pixels, 0, headerBits, header)
        for (i in MAGIC.indices) if (header[i] != MAGIC[i]) throw NoVaultFoundException()
        val payloadLen = ByteBuffer.wrap(header, MAGIC.size, 4).order(ByteOrder.BIG_ENDIAN).int
        val total = MAGIC.size + 4 + payloadLen
        if (total * 8 > width * height * 3) throw NoVaultFoundException()
        val out = ByteArray(total)
        readBits(pixels, 0, total * 8, out)
        return out
    }

    private fun readBits(pixels: IntArray, bitStart: Int, bitCount: Int, out: ByteArray) {
        for (i in out.indices) out[i] = 0
        var pixelIdx = bitStart / 3
        var channel = bitStart % 3
        for (cursor in 0 until bitCount) {
            if (pixelIdx >= pixels.size) throw NoVaultFoundException()
            val argb = pixels[pixelIdx]
            val bit = when (channel) {
                0 -> (argb ushr 16) and 1
                1 -> (argb ushr 8) and 1
                else -> argb and 1
            }
            val byteIdx = cursor / 8
            val bitIdx = 7 - (cursor % 8)
            out[byteIdx] = (out[byteIdx].toInt() or (bit shl bitIdx)).toByte()
            channel++
            if (channel == 3) { channel = 0; pixelIdx++ }
        }
    }

    // --------------------------------------------------------------------- PDF append

    fun embedIntoPDF(carrierPDF: ByteArray, payload: ByteArray): ByteArray {
        if (carrierPDF.size < 4 || carrierPDF[0] != 0x25.toByte() || carrierPDF[1] != 0x50.toByte()
            || carrierPDF[2] != 0x44.toByte() || carrierPDF[3] != 0x46.toByte()) {
            throw NotPDFException()
        }
        val out = ByteArrayOutputStream(carrierPDF.size + payload.size + 1)
        out.write(carrierPDF)
        out.write(0x0A)
        out.write(payload)
        return out.toByteArray()
    }

    fun extractFromPDF(stegoPDF: ByteArray): ByteArray {
        if (stegoPDF.size < 4 || stegoPDF[0] != 0x25.toByte() || stegoPDF[1] != 0x50.toByte()
            || stegoPDF[2] != 0x44.toByte() || stegoPDF[3] != 0x46.toByte()) {
            throw NotPDFException()
        }
        // Scan backwards for the magic.
        var start = -1
        outer@ for (i in stegoPDF.size - (MAGIC.size + 4) downTo 0) {
            for (m in MAGIC.indices) {
                if (stegoPDF[i + m] != MAGIC[m]) continue@outer
            }
            start = i; break
        }
        if (start < 0) throw NoVaultFoundException()
        if (start + MAGIC.size + 4 > stegoPDF.size) throw NoVaultFoundException()
        val len = ByteBuffer.wrap(stegoPDF, start + MAGIC.size, 4)
            .order(ByteOrder.BIG_ENDIAN).int
        val total = MAGIC.size + 4 + len
        if (start + total > stegoPDF.size) throw NoVaultFoundException()
        return stegoPDF.copyOfRange(start, start + total)
    }

    // --------------------------------------------------------------------- crypto plumbing

    fun deriveKey(passphrase: CharArray, salt: ByteArray): ByteArray {
        // Prepend pepper to salt so stego keys are distinct from .ot2bak keys even with the
        // same passphrase.
        val effectiveSalt = PEPPER.toByteArray(Charsets.UTF_8) + salt
        val spec = PBEKeySpec(passphrase, effectiveSalt, ITERATIONS, KEY_BITS)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        return factory.generateSecret(spec).encoded
    }

    fun randomBytes(count: Int): ByteArray =
        ByteArray(count).also { SecureRandom().nextBytes(it) }

    /** Returns null if passphrase passes policy, otherwise human-readable reason. */
    fun validatePassphrase(p: String): String? {
        if (p.length < 12) return "Use at least 12 characters."
        if (p.none { it.isLetter() }) return "Include at least one letter."
        if (p.none { it.isDigit() }) return "Include at least one digit."
        if (p.none { !it.isLetterOrDigit() }) return "Include at least one symbol."
        return null
    }
}
