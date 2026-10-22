// SPDX-FileCopyrightText: 2026 VanPaulTek Inc
// SPDX-License-Identifier: AGPL-3.0-or-later
package com.onlytrusted.app.ui.facelock

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import org.json.JSONArray
import kotlin.math.sqrt

/**
 * Elite-tier (`SubscriptionState.hasElite`) feature: Face-Change Shoulder-Surfing Lock.
 *
 * Stores the enrolled owner-face embedding (128-float L2-normalised vector) in
 * `EncryptedSharedPreferences` — the same AES-256-GCM scheme used by the Hardware Security
 * Key and Permission Watchdog features. A filesystem copy of the prefs file is unreadable
 * without the device-bound master key.
 *
 * The embedding is NEVER a raw image. It is derived from the face's 2D landmark geometry
 * (left/right eye, nose, mouth) normalised by inter-ocular distance, padded + L2-normalised
 * to 128 floats. See `FaceLockEmbedder` for the extraction, and `FaceLockMonitor` for the
 * runtime decision loop.
 */
object FaceLockState {

    private const val PREFS = "facelock_prefs_v1"
    private const val KEY_EMBEDDING = "facelock.v1.embedding"
    private const val KEY_LOCK_WHEN_OWNER_LEAVES = "facelock.v1.lockWhenOwnerLeaves"
    private const val KEY_LOCK_WHEN_STRANGER_JOINS = "facelock.v1.lockWhenStrangerJoins"
    private const val KEY_BLUR_ON_EXIT = "facelock.v1.blurOnExit"

    /** Minimum cosine similarity required to accept the live face as the enrolled owner. */
    const val OWNER_SIMILARITY_THRESHOLD: Float = 0.70f

    /** Walk-away grace window (ms) before 0-face frames trigger a blur. */
    const val NO_FACE_COUNTDOWN_MS: Long = 3_000L

    /** Sampling cadence for the runtime monitor (ms). Battery-friendly per the brief. */
    const val SAMPLE_INTERVAL_MS: Long = 2_000L

    /** Target embedding length. Mirrors the iOS implementation. */
    const val EMBEDDING_DIM: Int = 128

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

    // ---- Enrollment --------------------------------------------------------

    fun isEnrolled(ctx: Context): Boolean = embedding(ctx) != null

    fun embedding(ctx: Context): FloatArray? {
        val raw = prefs(ctx).getString(KEY_EMBEDDING, null) ?: return null
        return runCatching {
            val arr = JSONArray(raw)
            FloatArray(arr.length()) { i -> arr.getDouble(i).toFloat() }
        }.getOrNull()
    }

    /** Store the three-sample average embedding (straight / slight-left / slight-right). */
    fun setEnrollment(ctx: Context, samples: List<FloatArray>) {
        val averaged = averageEmbedding(samples) ?: return
        val arr = JSONArray()
        averaged.forEach { arr.put(it.toDouble()) }
        prefs(ctx).edit().putString(KEY_EMBEDDING, arr.toString()).apply()
    }

    fun clearEnrollment(ctx: Context) {
        prefs(ctx).edit().remove(KEY_EMBEDDING).apply()
    }

    // ---- Toggles -----------------------------------------------------------

    fun lockWhenOwnerLeaves(ctx: Context): Boolean =
        prefs(ctx).getBoolean(KEY_LOCK_WHEN_OWNER_LEAVES, true)
    fun setLockWhenOwnerLeaves(ctx: Context, value: Boolean) {
        prefs(ctx).edit().putBoolean(KEY_LOCK_WHEN_OWNER_LEAVES, value).apply()
    }

    fun lockWhenStrangerJoins(ctx: Context): Boolean =
        prefs(ctx).getBoolean(KEY_LOCK_WHEN_STRANGER_JOINS, true)
    fun setLockWhenStrangerJoins(ctx: Context, value: Boolean) {
        prefs(ctx).edit().putBoolean(KEY_LOCK_WHEN_STRANGER_JOINS, value).apply()
    }

    fun blurOnExit(ctx: Context): Boolean =
        prefs(ctx).getBoolean(KEY_BLUR_ON_EXIT, true)
    fun setBlurOnExit(ctx: Context, value: Boolean) {
        prefs(ctx).edit().putBoolean(KEY_BLUR_ON_EXIT, value).apply()
    }

    // ---- Math helpers ------------------------------------------------------

    fun averageEmbedding(samples: List<FloatArray>): FloatArray? {
        val first = samples.firstOrNull() ?: return null
        val dim = first.size
        if (dim == 0) return null
        val acc = FloatArray(dim)
        var kept = 0
        for (s in samples) {
            if (s.size != dim) continue
            for (i in 0 until dim) acc[i] += s[i]
            kept += 1
        }
        if (kept == 0) return null
        for (i in 0 until dim) acc[i] /= kept
        return l2Normalised(acc)
    }

    fun l2Normalised(v: FloatArray): FloatArray {
        var sumSq = 0.0f
        for (x in v) sumSq += x * x
        val n = sqrt(sumSq.coerceAtLeast(1e-9f))
        if (n == 0f) return v.copyOf()
        return FloatArray(v.size) { i -> v[i] / n }
    }

    fun cosineSimilarity(a: FloatArray, b: FloatArray): Float {
        if (a.size != b.size) return 0f
        var dot = 0f
        var na = 0f
        var nb = 0f
        for (i in a.indices) {
            dot += a[i] * b[i]
            na += a[i] * a[i]
            nb += b[i] * b[i]
        }
        val denom = sqrt(na.coerceAtLeast(1e-9f)) * sqrt(nb.coerceAtLeast(1e-9f))
        return if (denom == 0f) 0f else dot / denom
    }

    fun matchesOwner(ctx: Context, candidate: FloatArray): Boolean {
        val owner = embedding(ctx) ?: return false
        return cosineSimilarity(owner, candidate) >= OWNER_SIMILARITY_THRESHOLD
    }
}
