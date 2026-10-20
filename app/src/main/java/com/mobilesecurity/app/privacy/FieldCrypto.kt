package com.mobilesecurity.app.privacy

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * AES/GCM encryption with the key held in the Android Keystore.
 *
 * Used for secondary at-rest encryption of free-text fields (address, notes) on top of the
 * SQLCipher-encrypted database. That way, even if a Room export leaks, these fields remain
 * unreadable without the device's Keystore.
 */
object FieldCrypto {

    private const val KEYSTORE = "AndroidKeyStore"
    private const val KEY_ALIAS = "mobilesecurity-field-key"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val GCM_TAG_BITS = 128
    private const val IV_SIZE_BYTES = 12

    fun encrypt(plaintext: String?): String? {
        if (plaintext.isNullOrEmpty()) return null
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val iv = cipher.iv
        val ciphertext = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        val combined = ByteArray(iv.size + ciphertext.size).also {
            System.arraycopy(iv, 0, it, 0, iv.size)
            System.arraycopy(ciphertext, 0, it, iv.size, ciphertext.size)
        }
        return Base64.encodeToString(combined, Base64.NO_WRAP)
    }

    fun decrypt(encoded: String?): String? {
        if (encoded.isNullOrEmpty()) return null
        val combined = runCatching { Base64.decode(encoded, Base64.NO_WRAP) }.getOrNull() ?: return null
        if (combined.size <= IV_SIZE_BYTES) return null
        val iv = combined.copyOfRange(0, IV_SIZE_BYTES)
        val ciphertext = combined.copyOfRange(IV_SIZE_BYTES, combined.size)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(GCM_TAG_BITS, iv))
        return cipher.doFinal(ciphertext).toString(Charsets.UTF_8)
    }

    private fun getOrCreateKey(): SecretKey {
        val keystore = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (keystore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
        val spec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setRandomizedEncryptionRequired(true)
            .build()
        generator.init(spec)
        return generator.generateKey()
    }
}
