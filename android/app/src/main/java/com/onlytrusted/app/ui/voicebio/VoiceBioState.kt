// SPDX-FileCopyrightText: 2026 VanPaulTek Inc
// SPDX-License-Identifier: AGPL-3.0-or-later
package com.onlytrusted.app.ui.voicebio

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import org.json.JSONArray

/**
 * Elite-tier Voice Biometric store. Mirrors the iOS `VoiceBioState` but writes to
 * `EncryptedSharedPreferences` (AES-256-GCM via Jetpack Security) instead of the Keychain.
 *
 * One row per enrolled trusted contact. Key: `enrollment.<contactId>` → JSON array of
 * 20 floats (the mean MFCC descriptor). The enrolled-id index is kept in a separate key
 * so UI can list enrolled contacts without scanning every pref.
 *
 * The raw audio is NEVER stored — only the derived descriptor.
 */
object VoiceBioState {

    private const val PREFS = "voice_bio_prefs_v1"
    private const val KEY_ENROLLED_IDS = "enrolled_ids"
    private const val KEY_FEATURE_ENABLED = "feature_enabled"
    private const val ENROLLMENT_PREFIX = "enrollment."

    /** Minimum cosine similarity below which a candidate is flagged as a possible deepfake. */
    const val MATCH_THRESHOLD: Float = 0.6f

    /** Enrollment clip length (seconds). */
    const val ENROLLMENT_DURATION_SECONDS: Int = 10

    /** Live-call verification clip length (seconds). */
    const val VERIFICATION_DURATION_SECONDS: Int = 2

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

    // ---- Feature toggle ----------------------------------------------------

    fun isFeatureEnabled(ctx: Context): Boolean =
        prefs(ctx).getBoolean(KEY_FEATURE_ENABLED, true)

    fun setFeatureEnabled(ctx: Context, enabled: Boolean) {
        prefs(ctx).edit().putBoolean(KEY_FEATURE_ENABLED, enabled).apply()
    }

    // ---- Enrollment index --------------------------------------------------

    fun enrolledContactIds(ctx: Context): Set<Long> {
        val raw = prefs(ctx).getString(KEY_ENROLLED_IDS, null) ?: return emptySet()
        return runCatching {
            val arr = JSONArray(raw)
            buildSet(arr.length()) {
                for (i in 0 until arr.length()) add(arr.getLong(i))
            }
        }.getOrDefault(emptySet())
    }

    fun isEnrolled(ctx: Context, contactId: Long): Boolean =
        contactId in enrolledContactIds(ctx)

    private fun writeIndex(ctx: Context, ids: Set<Long>) {
        val arr = JSONArray()
        for (id in ids) arr.put(id)
        prefs(ctx).edit().putString(KEY_ENROLLED_IDS, arr.toString()).apply()
    }

    // ---- Per-contact descriptor --------------------------------------------

    private fun descriptorKey(contactId: Long): String = "$ENROLLMENT_PREFIX$contactId"

    fun descriptor(ctx: Context, contactId: Long): FloatArray? {
        val raw = prefs(ctx).getString(descriptorKey(contactId), null) ?: return null
        return runCatching {
            val arr = JSONArray(raw)
            FloatArray(arr.length()) { i -> arr.getDouble(i).toFloat() }
        }.getOrNull()
    }

    /** Persist a 20-dim MFCC descriptor for a contact. Overwrites any previous enrollment. */
    fun enroll(ctx: Context, contactId: Long, descriptor: FloatArray) {
        if (descriptor.size != MFCC.COEFFICIENT_COUNT) return
        val arr = JSONArray()
        for (v in descriptor) arr.put(v.toDouble())
        prefs(ctx).edit().putString(descriptorKey(contactId), arr.toString()).apply()
        val ids = enrolledContactIds(ctx).toMutableSet()
        ids.add(contactId)
        writeIndex(ctx, ids)
    }

    /** Remove the stored voice-print for a contact. Idempotent. */
    fun clear(ctx: Context, contactId: Long) {
        prefs(ctx).edit().remove(descriptorKey(contactId)).apply()
        val ids = enrolledContactIds(ctx).toMutableSet()
        ids.remove(contactId)
        writeIndex(ctx, ids)
    }

    /** Nuke every voice-print on disk. Called by Wipe Everything. */
    fun clearAll(ctx: Context) {
        prefs(ctx).edit().clear().apply()
    }
}
