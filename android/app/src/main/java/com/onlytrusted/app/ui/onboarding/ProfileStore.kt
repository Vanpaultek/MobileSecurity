// SPDX-FileCopyrightText: 2026 VanPaulTek Inc
// SPDX-License-Identifier: AGPL-3.0-or-later
package com.onlytrusted.app.ui.onboarding

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * EncryptedSharedPreferences-backed profile store. Mirrors iOS `ProfileStore`.
 *
 *   * email             — stored on-device only; prefills Breach Watch, personalises copy.
 *   * pinHash + pinSalt — PBKDF2-HMAC-SHA256 of the 4–6 digit app PIN, 100k iterations.
 *   * biometricEnabled  — opt-in flag for BiometricPrompter fast-path.
 *
 * No field ever leaves the device. The honest claim stays intact.
 */
object ProfileStore {

    private const val PREFS = "profile_prefs_v1"
    private const val KEY_EMAIL = "email"
    private const val KEY_PIN_HASH = "pin_hash_hex"
    private const val KEY_PIN_SALT = "pin_salt_hex"
    private const val KEY_BIOMETRIC = "biometric_enabled"
    private const val KEY_COMPLETED_AT = "setup_completed_at"

    private const val PBKDF2_ITERATIONS = 100_000
    private const val PBKDF2_KEY_BITS = 256

    @Volatile private var cachedMasterKey: MasterKey? = null

    private fun prefs(ctx: Context): SharedPreferences {
        val master = cachedMasterKey ?: synchronized(this) {
            cachedMasterKey ?: MasterKey.Builder(ctx.applicationContext)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
                .also { cachedMasterKey = it }
        }
        return EncryptedSharedPreferences.create(
            ctx.applicationContext, PREFS, master,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    // --- Getters -----------------------------------------------------------------

    fun email(ctx: Context): String = prefs(ctx).getString(KEY_EMAIL, "") ?: ""
    fun biometricEnabled(ctx: Context): Boolean = prefs(ctx).getBoolean(KEY_BIOMETRIC, false)
    fun isSetupComplete(ctx: Context): Boolean = prefs(ctx).contains(KEY_COMPLETED_AT)

    // --- Setters -----------------------------------------------------------------

    fun setEmail(ctx: Context, raw: String) {
        prefs(ctx).edit().putString(KEY_EMAIL, raw.trim().lowercase()).apply()
    }

    fun setPIN(ctx: Context, pin: String) {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val hash = pbkdf2(pin, salt)
        prefs(ctx).edit()
            .putString(KEY_PIN_SALT, salt.toHex())
            .putString(KEY_PIN_HASH, hash.toHex())
            .apply()
    }

    fun setBiometricEnabled(ctx: Context, enabled: Boolean) {
        prefs(ctx).edit().putBoolean(KEY_BIOMETRIC, enabled).apply()
    }

    fun markSetupComplete(ctx: Context) {
        prefs(ctx).edit().putLong(KEY_COMPLETED_AT, System.currentTimeMillis()).apply()
    }

    fun clearAll(ctx: Context) {
        prefs(ctx).edit().clear().apply()
    }

    // --- Internals ---------------------------------------------------------------

    private fun pbkdf2(pin: String, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(pin.toCharArray(), salt, PBKDF2_ITERATIONS, PBKDF2_KEY_BITS)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        return factory.generateSecret(spec).encoded
    }

    private fun ByteArray.toHex(): String =
        joinToString("") { "%02x".format(it) }
}
