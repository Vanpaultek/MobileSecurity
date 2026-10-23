// SPDX-FileCopyrightText: 2026 VanPaulTek Inc
// SPDX-License-Identifier: AGPL-3.0-or-later
package com.onlytrusted.app.ui.breachwatch

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import org.json.JSONArray
import org.json.JSONObject

/**
 * EncryptedSharedPreferences-backed state for Breach Watch (FREE tier).
 *
 * Mirrors iOS `BreachWatchState`. Monitors exactly one email on FREE. All fields live
 * under the `breach_watch_prefs_v1` prefs file encrypted with AES-256-GCM via the
 * Android Keystore master key, following the same pattern as HP1 Duress.
 */
object BreachWatchState {

    private const val PREFS = "breach_watch_prefs_v1"
    private const val KEY_EMAIL = "email"
    private const val KEY_LAST_CHECK = "last_check"
    private const val KEY_HITS_JSON = "hits_json"
    private const val KEY_USER_NAME = "user_name"

    data class BreachHit(
        val source: String,
        val firstSeenMillis: Long
    )

    private fun prefs(ctx: Context): SharedPreferences {
        val master = MasterKey.Builder(ctx)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        return EncryptedSharedPreferences.create(
            ctx.applicationContext,
            PREFS,
            master,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    fun email(ctx: Context): String = prefs(ctx).getString(KEY_EMAIL, "").orEmpty()

    fun userName(ctx: Context): String = prefs(ctx).getString(KEY_USER_NAME, "").orEmpty()

    fun lastCheckMillis(ctx: Context): Long? =
        prefs(ctx).getLong(KEY_LAST_CHECK, 0L).takeIf { it > 0 }

    fun hits(ctx: Context): List<BreachHit> {
        val raw = prefs(ctx).getString(KEY_HITS_JSON, null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    add(
                        BreachHit(
                            source = o.optString("source"),
                            firstSeenMillis = o.optLong("first_seen", System.currentTimeMillis())
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    fun saveEmail(ctx: Context, raw: String) {
        val normalised = BreachLookup.normalise(raw)
        prefs(ctx).edit()
            .putString(KEY_EMAIL, normalised)
            // Changing the email invalidates the previous hits cache.
            .remove(KEY_HITS_JSON)
            .apply()
    }

    fun setUserName(ctx: Context, name: String) {
        prefs(ctx).edit().putString(KEY_USER_NAME, name.trim()).apply()
    }

    /**
     * Runs the local breach lookup and returns the (possibly empty) hits list. Updates
     * [lastCheckMillis] + the hits cache. Returns the hits so callers can post an
     * immediate notification when a new hit appears.
     */
    fun runCheck(ctx: Context, nowMillis: Long = System.currentTimeMillis()): List<BreachHit> {
        val address = email(ctx)
        if (address.isEmpty()) {
            prefs(ctx).edit().putLong(KEY_LAST_CHECK, nowMillis).apply()
            return emptyList()
        }
        val isHit = BreachLookup.isEmailBreached(ctx, address)
        val currentHits = hits(ctx)
        val updated: List<BreachHit> = if (isHit) {
            val existing = currentHits.firstOrNull { it.source == "A known breach list" }
            if (existing != null) currentHits else listOf(
                BreachHit(source = "A known breach list", firstSeenMillis = nowMillis)
            )
        } else {
            emptyList()
        }
        persistHits(ctx, updated)
        prefs(ctx).edit().putLong(KEY_LAST_CHECK, nowMillis).apply()
        return updated
    }

    fun clearAll(ctx: Context) {
        prefs(ctx).edit().clear().apply()
    }

    private fun persistHits(ctx: Context, hits: List<BreachHit>) {
        val arr = JSONArray()
        hits.forEach {
            arr.put(
                JSONObject()
                    .put("source", it.source)
                    .put("first_seen", it.firstSeenMillis)
            )
        }
        prefs(ctx).edit().putString(KEY_HITS_JSON, arr.toString()).apply()
    }
}
