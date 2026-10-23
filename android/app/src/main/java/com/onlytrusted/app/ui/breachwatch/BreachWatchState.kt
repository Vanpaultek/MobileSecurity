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
    private const val KEY_ADDITIONAL_EMAILS = "additional_emails"
    private const val KEY_LAST_CHECK = "last_check"
    private const val KEY_HITS_JSON = "hits_json"
    private const val KEY_USER_NAME = "user_name"

    /** Total email cap across Free (1) + Premium (up to 5). Shared with iOS. */
    const val MAX_EMAILS_PREMIUM = 5

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

    /** Premium-only additional emails (0..4 entries). Primary email stays in KEY_EMAIL. */
    fun additionalEmails(ctx: Context): List<String> {
        val raw = prefs(ctx).getString(KEY_ADDITIONAL_EMAILS, null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { arr.getString(it) }
        }.getOrDefault(emptyList())
    }

    /** All monitored emails, primary first, additional after. */
    fun allEmails(ctx: Context): List<String> {
        val primary = email(ctx)
        val extra = additionalEmails(ctx)
        return (if (primary.isNotEmpty()) listOf(primary) else emptyList()) + extra
    }

    /** Returns true if added. Caller should check hasPremium before invoking. */
    fun addAdditionalEmail(ctx: Context, raw: String): Boolean {
        val normalised = BreachLookup.normalise(raw)
        if (normalised.isEmpty()) return false
        if (normalised == email(ctx)) return false
        val current = additionalEmails(ctx).toMutableList()
        if (current.contains(normalised)) return false
        if (1 + current.size >= MAX_EMAILS_PREMIUM) return false
        current.add(normalised)
        persistAdditional(ctx, current)
        return true
    }

    fun removeAdditionalEmail(ctx: Context, raw: String) {
        val updated = additionalEmails(ctx).filterNot { it == raw }
        persistAdditional(ctx, updated)
    }

    private fun persistAdditional(ctx: Context, list: List<String>) {
        val arr = JSONArray().apply { list.forEach { put(it) } }
        prefs(ctx).edit().putString(KEY_ADDITIONAL_EMAILS, arr.toString()).apply()
    }

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
        val addresses = allEmails(ctx)
        if (addresses.isEmpty()) {
            prefs(ctx).edit().putLong(KEY_LAST_CHECK, nowMillis).apply()
            return emptyList()
        }
        val currentHits = hits(ctx)
        // One BreachHit per matched address — source == email address. First-seen preserved
        // across repeated checks so we don't reset the "when it first appeared" clock.
        val updated = buildList {
            for (addr in addresses) {
                if (BreachLookup.isEmailBreached(ctx, addr)) {
                    val existing = currentHits.firstOrNull { it.source == addr }
                    add(
                        BreachHit(
                            source = addr,
                            firstSeenMillis = existing?.firstSeenMillis ?: nowMillis
                        )
                    )
                }
            }
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
