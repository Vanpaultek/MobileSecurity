// SPDX-FileCopyrightText: 2026 VanPaulTek Inc
// SPDX-License-Identifier: AGPL-3.0-or-later
package com.onlytrusted.app.ui.hardwarekey

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

/**
 * Elite-tier (`SubscriptionState.hasElite`) feature: FIDO2 / WebAuthn hardware security key
 * (YubiKey, Google Titan Key, SoloKey). This file owns the local record of enrolled keys and
 * the dev-only emulator skip flag.
 *
 * Storage: `EncryptedSharedPreferences` with the AES-256-GCM scheme used across the Elite
 * batch (Duress state, Permission Watchdog, Tracker Finder). A filesystem copy of the prefs
 * file is unreadable without the device-bound master key.
 *
 * The ceremonial WebAuthn work is handled in [HardwareKeyGate] using Android's unified
 * `CredentialManager` API (API 28+). This store is intentionally dumb — it only persists what
 * the authenticator produced and exposes a tiny list of records to the setup screen.
 */
object HardwareKeyState {

    /** WebAuthn relying-party identifier. Fixed string, on-device only. */
    const val RELYING_PARTY_ID: String = "onlytrusted.local"

    private const val PREFS = "hardware_key_prefs_v1"
    private const val KEY_ENROLLED = "hardware_key.enrolled.v1"
    private const val KEY_SIMULATOR_SKIP = "hardware_key.emulator_skip.v1"

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

    /** Enrolled hardware keys as surfaced to the Setup screen. */
    data class EnrolledKey(
        val id: String,
        val credentialId: String,
        val publicKey: String?,
        val displayName: String,
        val addedAtMillis: Long
    ) {
        fun formattedDateAdded(): String =
            SimpleDateFormat("MMM d, yyyy", Locale.US).format(Date(addedAtMillis))
    }

    fun enrolledKeys(ctx: Context): List<EnrolledKey> {
        val raw = prefs(ctx).getString(KEY_ENROLLED, null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                EnrolledKey(
                    id = o.getString("id"),
                    credentialId = o.getString("credentialId"),
                    publicKey = o.optString("publicKey").takeIf { it.isNotEmpty() },
                    displayName = o.getString("displayName"),
                    addedAtMillis = o.getLong("addedAtMillis")
                )
            }
        }.getOrDefault(emptyList())
    }

    fun hasEnrolledKey(ctx: Context): Boolean = enrolledKeys(ctx).isNotEmpty()

    fun addKey(
        ctx: Context,
        credentialId: String,
        publicKey: String?,
        displayName: String
    ): EnrolledKey {
        val list = enrolledKeys(ctx).toMutableList()
        // Duplicate credentialId → overwrite in place so a re-enrollment doesn't duplicate.
        val existing = list.indexOfFirst { it.credentialId == credentialId }
        val record = EnrolledKey(
            id = UUID.randomUUID().toString(),
            credentialId = credentialId,
            publicKey = publicKey,
            displayName = displayName,
            addedAtMillis = System.currentTimeMillis()
        )
        if (existing >= 0) list[existing] = record else list.add(record)
        persist(ctx, list)
        return record
    }

    fun removeKey(ctx: Context, id: String) {
        val list = enrolledKeys(ctx).filterNot { it.id == id }
        persist(ctx, list)
    }

    fun clearAll(ctx: Context) {
        prefs(ctx).edit().remove(KEY_ENROLLED).apply()
    }

    private fun persist(ctx: Context, list: List<EnrolledKey>) {
        val arr = JSONArray()
        list.forEach { k ->
            arr.put(
                JSONObject().apply {
                    put("id", k.id)
                    put("credentialId", k.credentialId)
                    put("publicKey", k.publicKey ?: "")
                    put("displayName", k.displayName)
                    put("addedAtMillis", k.addedAtMillis)
                }
            )
        }
        prefs(ctx).edit().putString(KEY_ENROLLED, arr.toString()).apply()
    }

    // Dev-only skip toggle (emulator). Honored by [HardwareKeyGate] only when the running
    // process is detected as an emulator — a release build on a real device ignores it.
    fun isEmulatorSkipEnabled(ctx: Context): Boolean =
        prefs(ctx).getBoolean(KEY_SIMULATOR_SKIP, false)

    fun setEmulatorSkipEnabled(ctx: Context, enabled: Boolean) {
        prefs(ctx).edit().putBoolean(KEY_SIMULATOR_SKIP, enabled).apply()
    }

    /**
     * Heuristic emulator detection — matches Android's own emulator fingerprints (Google
     * generic / emulator / sdk_gphone family). Keeps the dev skip usable in CI / local dev
     * while blocking it on anything that even remotely looks like a real device.
     */
    val isRunningOnEmulator: Boolean by lazy {
        val fp = Build.FINGERPRINT.orEmpty().lowercase(Locale.US)
        val model = Build.MODEL.orEmpty().lowercase(Locale.US)
        val product = Build.PRODUCT.orEmpty().lowercase(Locale.US)
        val hardware = Build.HARDWARE.orEmpty().lowercase(Locale.US)
        fp.startsWith("generic") ||
            fp.contains("vbox") ||
            fp.contains("emulator") ||
            model.contains("google_sdk") ||
            model.contains("emulator") ||
            model.contains("android sdk built for") ||
            product.contains("sdk_gphone") ||
            product.contains("emulator") ||
            hardware.contains("goldfish") ||
            hardware.contains("ranchu")
    }
}
