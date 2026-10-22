// SPDX-FileCopyrightText: 2026 VanPaulTek Inc
// SPDX-License-Identifier: AGPL-3.0-or-later
package com.onlytrusted.app.ui.p2p

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * Elite-tier Trusted Witness local state (Android twin of iOS TrustedWitnessState).
 *
 * Stores enrolled witness device IDs + per-action requirement flags in
 * [EncryptedSharedPreferences] so a filesystem copy is unreadable without the device-bound
 * master key.
 */
object TrustedWitnessState {

    private const val PREFS = "trusted_witness_prefs_v1"
    private const val KEY_ENABLED = "trusted_witness.v1.enabled"
    private const val KEY_REQUIRED = "trusted_witness.v1.required"
    private const val KEY_WITNESSES = "trusted_witness.v1.witnesses"

    enum class GatedAction(val displayName: String, val approvalPrompt: String) {
        VAULT_EXPORT("Vault export", "export the encrypted vault"),
        WIPE_EVERYTHING("Wipe everything", "wipe everything on this device"),
        DISARM_DURESS("Disarm Duress Mode", "disarm Duress Mode"),
        DISABLE_DEADMAN_WIPE("Disable Dead-Man Wipe", "disable the Dead-Man Wipe"),
        CHANGE_MASTER_PIN("Change master PIN", "change the master PIN");

        companion object {
            fun fromRawOrNull(raw: String?): GatedAction? =
                entries.firstOrNull { it.name == raw }
        }
    }

    data class Witness(
        val id: String,
        val displayName: String,
        val deviceID: String,
        val enrolledAtMillis: Long
    )

    private fun prefs(ctx: Context) = EncryptedSharedPreferences.create(
        ctx.applicationContext,
        PREFS,
        MasterKey.Builder(ctx).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    fun isFeatureEnabled(ctx: Context): Boolean =
        prefs(ctx).getBoolean(KEY_ENABLED, false)

    fun setFeatureEnabled(ctx: Context, value: Boolean) {
        prefs(ctx).edit().putBoolean(KEY_ENABLED, value).apply()
    }

    fun requiredActions(ctx: Context): Set<GatedAction> {
        val saved = prefs(ctx).getStringSet(KEY_REQUIRED, null)
        return if (saved == null) {
            // default: every action is required when the feature is on
            GatedAction.entries.toSet()
        } else {
            saved.mapNotNull { GatedAction.fromRawOrNull(it) }.toSet()
        }
    }

    fun setRequiredActions(ctx: Context, actions: Set<GatedAction>) {
        prefs(ctx).edit().putStringSet(KEY_REQUIRED, actions.map { it.name }.toSet()).apply()
    }

    fun requiresWitness(ctx: Context, action: GatedAction): Boolean {
        if (!isFeatureEnabled(ctx)) return false
        if (witnesses(ctx).isEmpty()) return false
        return action in requiredActions(ctx)
    }

    // --- Witness roster ---

    fun witnesses(ctx: Context): List<Witness> {
        val raw = prefs(ctx).getString(KEY_WITNESSES, null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                Witness(
                    id = o.getString("id"),
                    displayName = o.getString("displayName"),
                    deviceID = o.getString("deviceID"),
                    enrolledAtMillis = o.optLong("enrolledAt")
                )
            }
        }.getOrDefault(emptyList())
    }

    fun addWitness(ctx: Context, displayName: String, deviceID: String): Witness {
        val list = witnesses(ctx).toMutableList()
        val w = Witness(
            id = UUID.randomUUID().toString(),
            displayName = displayName,
            deviceID = deviceID,
            enrolledAtMillis = System.currentTimeMillis()
        )
        list.add(w)
        persist(ctx, list)
        return w
    }

    fun removeWitness(ctx: Context, id: String) {
        persist(ctx, witnesses(ctx).filterNot { it.id == id })
    }

    fun clearAll(ctx: Context) {
        prefs(ctx).edit().remove(KEY_WITNESSES).apply()
    }

    private fun persist(ctx: Context, list: List<Witness>) {
        val arr = JSONArray()
        list.forEach { w ->
            arr.put(JSONObject().apply {
                put("id", w.id)
                put("displayName", w.displayName)
                put("deviceID", w.deviceID)
                put("enrolledAt", w.enrolledAtMillis)
            })
        }
        prefs(ctx).edit().putString(KEY_WITNESSES, arr.toString()).apply()
    }
}
