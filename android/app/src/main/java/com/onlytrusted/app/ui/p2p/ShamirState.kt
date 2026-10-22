// SPDX-FileCopyrightText: 2026 VanPaulTek Inc
// SPDX-License-Identifier: AGPL-3.0-or-later
package com.onlytrusted.app.ui.p2p

import android.content.Context
import android.util.Base64
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import java.util.UUID

/**
 * Local-only state for the Shamir Split (Elite) feature on Android.
 *
 * Backed by [EncryptedSharedPreferences] with the same AES-256-GCM master-key scheme used by
 * the other Elite features. A filesystem copy of the prefs is unreadable without the device
 * key material.
 *
 * Two separate stashes:
 *   - `mySplit`     — metadata for the split the OWNER made from this device.
 *   - `heldShares`  — Shamir envelopes received from other owners that this device holds in
 *                     escrow until they come back for recovery.
 */
object ShamirState {

    private const val PREFS = "shamir_state_prefs_v1"
    private const val KEY_MY_SPLIT = "shamir.v1.my_split"
    private const val KEY_HELD = "shamir.v1.held_shares"
    private const val KEY_LAST_RECOVERY_TEST = "shamir.v1.last_recovery_test"

    private fun prefs(ctx: Context) = EncryptedSharedPreferences.create(
        ctx.applicationContext,
        PREFS,
        MasterKey.Builder(ctx).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    data class Distribution(
        val id: String,
        val shareIndex: Int,
        val contactId: String?,
        val contactDisplayName: String,
        val distributedAtMillis: Long?,
        val confirmed: Boolean
    )

    data class MySplit(
        val splitID: String,
        val threshold: Int,
        val total: Int,
        val createdAtMillis: Long,
        val distributions: List<Distribution>
    )

    data class HeldShare(
        val id: String,
        val envelope: ShamirSSS.Envelope,
        val ownerLabel: String,
        val receivedAtMillis: Long
    )

    // --- MySplit ---

    fun mySplit(ctx: Context): MySplit? {
        val raw = prefs(ctx).getString(KEY_MY_SPLIT, null) ?: return null
        return runCatching { decodeSplit(JSONObject(raw)) }.getOrNull()
    }

    fun beginSplit(
        ctx: Context,
        threshold: Int,
        total: Int,
        splitID: String,
        assignments: List<Triple<Int, String?, String>>
    ) {
        val now = System.currentTimeMillis()
        val dists = assignments.map { (idx, cId, cName) ->
            Distribution(
                id = UUID.randomUUID().toString(),
                shareIndex = idx,
                contactId = cId,
                contactDisplayName = cName,
                distributedAtMillis = null,
                confirmed = false
            )
        }
        val split = MySplit(
            splitID = splitID,
            threshold = threshold,
            total = total,
            createdAtMillis = now,
            distributions = dists
        )
        prefs(ctx).edit().putString(KEY_MY_SPLIT, encodeSplit(split).toString()).apply()
    }

    fun markDistributed(ctx: Context, shareIndex: Int) {
        val current = mySplit(ctx) ?: return
        val updated = current.copy(
            distributions = current.distributions.map { d ->
                if (d.shareIndex == shareIndex) d.copy(
                    distributedAtMillis = System.currentTimeMillis(),
                    confirmed = true
                ) else d
            }
        )
        prefs(ctx).edit().putString(KEY_MY_SPLIT, encodeSplit(updated).toString()).apply()
    }

    fun clearSplit(ctx: Context) {
        prefs(ctx).edit().remove(KEY_MY_SPLIT).apply()
    }

    fun lastRecoveryTestMillis(ctx: Context): Long = prefs(ctx).getLong(KEY_LAST_RECOVERY_TEST, 0L)

    fun markRecoveryTest(ctx: Context) {
        prefs(ctx).edit().putLong(KEY_LAST_RECOVERY_TEST, System.currentTimeMillis()).apply()
    }

    // --- Held shares ---

    fun heldShares(ctx: Context): List<HeldShare> {
        val raw = prefs(ctx).getString(KEY_HELD, null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i -> decodeHeld(arr.getJSONObject(i)) }
        }.getOrDefault(emptyList())
    }

    fun addHeldShare(ctx: Context, envelope: ShamirSSS.Envelope, ownerLabel: String) {
        val list = heldShares(ctx).toMutableList()
        list.removeAll {
            it.envelope.splitID == envelope.splitID && it.envelope.index == envelope.index
        }
        list.add(
            HeldShare(
                id = UUID.randomUUID().toString(),
                envelope = envelope,
                ownerLabel = ownerLabel,
                receivedAtMillis = System.currentTimeMillis()
            )
        )
        persistHeld(ctx, list)
    }

    fun removeHeldShare(ctx: Context, id: String) {
        persistHeld(ctx, heldShares(ctx).filterNot { it.id == id })
    }

    // --- JSON ---

    private fun encodeSplit(s: MySplit): JSONObject = JSONObject().apply {
        put("splitID", s.splitID)
        put("threshold", s.threshold)
        put("total", s.total)
        put("createdAt", s.createdAtMillis)
        put("distributions", JSONArray().apply {
            s.distributions.forEach { d ->
                put(JSONObject().apply {
                    put("id", d.id)
                    put("shareIndex", d.shareIndex)
                    put("contactId", d.contactId ?: "")
                    put("contactDisplayName", d.contactDisplayName)
                    put("distributedAt", d.distributedAtMillis ?: 0L)
                    put("confirmed", d.confirmed)
                })
            }
        })
    }

    private fun decodeSplit(obj: JSONObject): MySplit {
        val dists = obj.optJSONArray("distributions") ?: JSONArray()
        return MySplit(
            splitID = obj.getString("splitID"),
            threshold = obj.getInt("threshold"),
            total = obj.getInt("total"),
            createdAtMillis = obj.optLong("createdAt"),
            distributions = (0 until dists.length()).map { i ->
                val d = dists.getJSONObject(i)
                Distribution(
                    id = d.getString("id"),
                    shareIndex = d.getInt("shareIndex"),
                    contactId = d.optString("contactId").ifEmpty { null },
                    contactDisplayName = d.getString("contactDisplayName"),
                    distributedAtMillis = d.optLong("distributedAt").takeIf { it > 0 },
                    confirmed = d.optBoolean("confirmed")
                )
            }
        )
    }

    private fun persistHeld(ctx: Context, list: List<HeldShare>) {
        val arr = JSONArray()
        list.forEach { h ->
            arr.put(JSONObject().apply {
                put("id", h.id)
                put("ownerLabel", h.ownerLabel)
                put("receivedAt", h.receivedAtMillis)
                put("envelope", JSONObject().apply {
                    put("splitID", h.envelope.splitID)
                    put("index", h.envelope.index)
                    put("threshold", h.envelope.threshold)
                    put("total", h.envelope.total)
                    put("salt", Base64.encodeToString(h.envelope.salt, Base64.NO_WRAP))
                    put("shareBytes", Base64.encodeToString(h.envelope.shareBytes, Base64.NO_WRAP))
                    put("proof", Base64.encodeToString(h.envelope.proof, Base64.NO_WRAP))
                    put("createdAt", h.envelope.createdAtMillis)
                })
            })
        }
        prefs(ctx).edit().putString(KEY_HELD, arr.toString()).apply()
    }

    private fun decodeHeld(obj: JSONObject): HeldShare {
        val env = obj.getJSONObject("envelope")
        return HeldShare(
            id = obj.getString("id"),
            ownerLabel = obj.getString("ownerLabel"),
            receivedAtMillis = obj.optLong("receivedAt"),
            envelope = ShamirSSS.Envelope(
                splitID = env.getString("splitID"),
                index = env.getInt("index"),
                threshold = env.getInt("threshold"),
                total = env.getInt("total"),
                salt = Base64.decode(env.getString("salt"), Base64.NO_WRAP),
                shareBytes = Base64.decode(env.getString("shareBytes"), Base64.NO_WRAP),
                proof = Base64.decode(env.getString("proof"), Base64.NO_WRAP),
                createdAtMillis = env.optLong("createdAt")
            )
        )
    }
}

/**
 * Supplies the "master key" the Shamir Split distributes. For now we derive a stable 32-byte
 * secret from a device-local anchor so split/recover round-trips on the same device yield the
 * same bytes. A production build would plug the real vault key here.
 */
object ShamirMasterKey {
    fun current(ctx: Context): ByteArray {
        val anchor = ("ot-shamir-master-v1:" + android.provider.Settings.Secure.getString(
            ctx.contentResolver, android.provider.Settings.Secure.ANDROID_ID
        ).orEmpty()).toByteArray()
        return MessageDigest.getInstance("SHA-256").digest(anchor)
    }
}
