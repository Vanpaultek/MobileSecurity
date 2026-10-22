// SPDX-FileCopyrightText: 2026 VanPaulTek Inc
// SPDX-License-Identifier: AGPL-3.0-or-later
package com.onlytrusted.app.ui.signedlog

import android.content.Context
import android.util.Base64
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Elite-tier (`SubscriptionState.hasElite`) feature: hardware-signed, Merkle-anchored audit
 * log. Mirrors the iOS `SignedLogStore`.
 *
 *   - Signatures are stored in a sidecar file (`signed_log_prefs_v1`) keyed by the row's
 *     `rowHash` — the Room schema stays untouched so extensions and existing callers aren't
 *     affected.
 *   - Merkle commits roll up batches of events (24 h cadence per the brief). Each commit
 *     stores its full leaf list so a verifier can reproduce the root locally.
 *
 * Storage uses `EncryptedSharedPreferences` with the AES-256-GCM scheme used across the rest
 * of the Elite batch (Duress state, Permission Watchdog, Hardware Keys).
 */
object SignedLogState {

    private const val PREFS = "signed_log_prefs_v1"
    private const val KEY_SIGN_EVERY = "signedlog.sign_every.v1"
    private const val KEY_SIGNATURES = "signedlog.signatures.v1"
    private const val KEY_MERKLE = "signedlog.merkle.v1"
    /** 24 h in milliseconds — the Merkle commit cadence per the brief. */
    const val MERKLE_WINDOW_MILLIS: Long = 24L * 60L * 60L * 1000L

    private fun prefs(ctx: Context) = EncryptedSharedPreferences.create(
        ctx.applicationContext,
        PREFS,
        MasterKey.Builder(ctx).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    // --------------------------------------------------------------------- Toggle

    fun isSignEveryEntryEnabled(ctx: Context): Boolean =
        prefs(ctx).getBoolean(KEY_SIGN_EVERY, false)

    fun setSignEveryEntryEnabled(ctx: Context, enabled: Boolean) {
        prefs(ctx).edit().putBoolean(KEY_SIGN_EVERY, enabled).apply()
    }

    // --------------------------------------------------------------------- Signature records

    data class SignedEntry(
        val rowHash: String,
        val signatureB64: String,
        val signatureKeyIdB64: String,
        val signedAtMillis: Long
    )

    fun signatures(ctx: Context): List<SignedEntry> {
        val raw = prefs(ctx).getString(KEY_SIGNATURES, null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                SignedEntry(
                    rowHash = o.getString("rowHash"),
                    signatureB64 = o.getString("signatureB64"),
                    signatureKeyIdB64 = o.getString("signatureKeyIdB64"),
                    signedAtMillis = o.getLong("signedAtMillis")
                )
            }
        }.getOrDefault(emptyList())
    }

    fun recordSignature(
        ctx: Context,
        rowHash: String,
        signature: ByteArray,
        credentialIdB64: String
    ) {
        val entries = signatures(ctx).toMutableList()
        val entry = SignedEntry(
            rowHash = rowHash,
            signatureB64 = Base64.encodeToString(signature, Base64.NO_WRAP),
            signatureKeyIdB64 = credentialIdB64,
            signedAtMillis = System.currentTimeMillis()
        )
        val idx = entries.indexOfFirst { it.rowHash == rowHash }
        if (idx >= 0) entries[idx] = entry else entries.add(entry)
        persistSignatures(ctx, entries)
    }

    private fun persistSignatures(ctx: Context, entries: List<SignedEntry>) {
        val arr = JSONArray()
        entries.forEach { e ->
            arr.put(JSONObject().apply {
                put("rowHash", e.rowHash)
                put("signatureB64", e.signatureB64)
                put("signatureKeyIdB64", e.signatureKeyIdB64)
                put("signedAtMillis", e.signedAtMillis)
            })
        }
        prefs(ctx).edit().putString(KEY_SIGNATURES, arr.toString()).apply()
    }

    // --------------------------------------------------------------------- Merkle commits

    data class MerkleCommit(
        val commitAtMillis: Long,
        val fromRowHash: String?,
        val toRowHash: String,
        val count: Int,
        val rootHex: String,
        val leaves: List<String>
    )

    fun merkleCommits(ctx: Context): List<MerkleCommit> {
        val raw = prefs(ctx).getString(KEY_MERKLE, null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                val leavesArr = o.getJSONArray("leaves")
                val leaves = (0 until leavesArr.length()).map { leavesArr.getString(it) }
                MerkleCommit(
                    commitAtMillis = o.getLong("commitAtMillis"),
                    fromRowHash = o.optString("fromRowHash").takeIf { it.isNotEmpty() },
                    toRowHash = o.getString("toRowHash"),
                    count = o.getInt("count"),
                    rootHex = o.getString("rootHex"),
                    leaves = leaves
                )
            }
        }.getOrDefault(emptyList())
    }

    fun persistMerkleCommits(ctx: Context, commits: List<MerkleCommit>) {
        val arr = JSONArray()
        commits.forEach { c ->
            arr.put(JSONObject().apply {
                put("commitAtMillis", c.commitAtMillis)
                put("fromRowHash", c.fromRowHash ?: "")
                put("toRowHash", c.toRowHash)
                put("count", c.count)
                put("rootHex", c.rootHex)
                put("leaves", JSONArray(c.leaves))
            })
        }
        prefs(ctx).edit().putString(KEY_MERKLE, arr.toString()).apply()
    }

    /**
     * Returns the SHA-256 Merkle root over `leaves`. Duplicates the last leaf when needed to
     * keep the tree balanced. Must match the iOS implementation byte-for-byte so a stego/
     * evidence pack created on one platform verifies on the other.
     */
    fun merkleRoot(leaves: List<String>): String {
        if (leaves.isEmpty()) return "0".repeat(64)
        var level: List<ByteArray> = leaves.map { hexToBytes(it) }
        while (level.size > 1) {
            val padded = if (level.size % 2 == 1) level + listOf(level.last()) else level
            val next = mutableListOf<ByteArray>()
            var i = 0
            while (i < padded.size) {
                val combined = padded[i] + padded[i + 1]
                next.add(MessageDigest.getInstance("SHA-256").digest(combined))
                i += 2
            }
            level = next
        }
        return bytesToHex(level[0])
    }

    fun lastCommitDescription(ctx: Context): String {
        val last = merkleCommits(ctx).lastOrNull() ?: return "Never"
        return SimpleDateFormat("MMM d, yyyy · h:mm a", Locale.US).format(Date(last.commitAtMillis))
    }

    private fun hexToBytes(hex: String): ByteArray {
        val out = ByteArray(hex.length / 2)
        for (i in out.indices) {
            out[i] = ((Character.digit(hex[i * 2], 16) shl 4) or
                Character.digit(hex[i * 2 + 1], 16)).toByte()
        }
        return out
    }

    fun bytesToHex(bytes: ByteArray): String =
        bytes.joinToString("") { "%02x".format(it) }
}
