// SPDX-FileCopyrightText: 2026 VanPaulTek Inc
// SPDX-License-Identifier: AGPL-3.0-or-later
package com.onlytrusted.app.ui.duress

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * Mirrors iOS `DuressState`. Holds setup flags, hashes the duress PIN with a per-install
 * salt, and exposes `isDuressSession` as a process-wide StateFlow so screens that read from
 * the vault (via `TrustedContactsRepository`) know to show the decoy data.
 *
 * All persisted fields live in `EncryptedSharedPreferences` so the salt / hash / alert
 * contact id are not readable from a plain filesystem copy.
 */
object DuressState {

    private const val PREFS = "duress_prefs_v1"
    private const val KEY_ENABLED = "enabled"
    private const val KEY_SALT = "pin_salt"
    private const val KEY_HASH = "pin_hash"
    private const val KEY_ALERT_ID = "alert_contact_id"
    private const val KEY_OWNER = "owner_name"
    private const val KEY_REAL_HASH = "real_pin_hash"

    private val _isDuressSession = MutableStateFlow(false)
    val isDuressSession: StateFlow<Boolean> = _isDuressSession.asStateFlow()

    fun setDuressSession(active: Boolean) {
        _isDuressSession.value = active
    }

    private fun prefs(ctx: Context): SharedPreferences {
        val masterKey = MasterKey.Builder(ctx)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        return EncryptedSharedPreferences.create(
            ctx.applicationContext,
            PREFS,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    fun isEnabled(ctx: Context): Boolean = prefs(ctx).getBoolean(KEY_ENABLED, false)

    fun setEnabled(ctx: Context, value: Boolean) {
        prefs(ctx).edit().putBoolean(KEY_ENABLED, value).apply()
    }

    fun alertContactId(ctx: Context): Long? =
        prefs(ctx).getLong(KEY_ALERT_ID, -1L).takeIf { it > 0 }

    fun setAlertContactId(ctx: Context, id: Long?) {
        prefs(ctx).edit().apply {
            if (id == null) remove(KEY_ALERT_ID) else putLong(KEY_ALERT_ID, id)
        }.apply()
    }

    fun ownerName(ctx: Context): String = prefs(ctx).getString(KEY_OWNER, null).orEmpty()

    fun setOwnerName(ctx: Context, name: String) {
        prefs(ctx).edit().putString(KEY_OWNER, name).apply()
    }

    /** Sets a new duress PIN. Overwrites any existing one. */
    fun setDuressPin(ctx: Context, pin: String) {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val hash = hash(pin, salt)
        prefs(ctx).edit()
            .putString(KEY_SALT, Base64.encodeToString(salt, Base64.NO_WRAP))
            .putString(KEY_HASH, Base64.encodeToString(hash, Base64.NO_WRAP))
            .apply()
    }

    fun matches(ctx: Context, candidate: String): Boolean {
        if (!isEnabled(ctx)) return false
        val p = prefs(ctx)
        val saltB64 = p.getString(KEY_SALT, null) ?: return false
        val hashB64 = p.getString(KEY_HASH, null) ?: return false
        val salt = runCatching { Base64.decode(saltB64, Base64.NO_WRAP) }.getOrNull() ?: return false
        val expected = runCatching { Base64.decode(hashB64, Base64.NO_WRAP) }.getOrNull() ?: return false
        val got = hash(candidate, salt)
        return MessageDigest.isEqual(got, expected)
    }

    /** Reserved for a future app-owned real PIN. */
    fun collidesWithRealPin(ctx: Context, candidate: String): Boolean {
        val p = prefs(ctx)
        val saltB64 = p.getString(KEY_SALT, null) ?: return false
        val realHashB64 = p.getString(KEY_REAL_HASH, null) ?: return false
        val salt = runCatching { Base64.decode(saltB64, Base64.NO_WRAP) }.getOrNull() ?: return false
        val expected = runCatching { Base64.decode(realHashB64, Base64.NO_WRAP) }.getOrNull() ?: return false
        return MessageDigest.isEqual(hash(candidate, salt), expected)
    }

    private fun hash(pin: String, salt: ByteArray): ByteArray {
        val md = MessageDigest.getInstance("SHA-256")
        md.update(salt)
        md.update(pin.toByteArray(Charsets.UTF_8))
        return md.digest()
    }

    fun clearAll(ctx: Context) {
        prefs(ctx).edit().clear().apply()
        _isDuressSession.value = false
    }
}
