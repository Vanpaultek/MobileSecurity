// SPDX-FileCopyrightText: 2026 VanPaulTek Inc
// SPDX-License-Identifier: AGPL-3.0-or-later
package com.onlytrusted.app.ui.dataremoval

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import org.json.JSONArray
import java.util.Locale

/**
 * Catalog + template renderer for the Data Removal Scan (FREE tier, visibility-only).
 * Reads `assets/threatintel/data_broker_sites.v1.json` once per process. Mirrors the
 * iOS `DataRemovalCatalog`.
 *
 * We DO NOT scrape or probe these sites. The feature only generates deep-link search
 * URLs the user can open in their default browser. UI copy makes it clear nothing is
 * removed on the user's behalf — removal is a manual, per-site flow.
 */
data class DataRemovalSite(
    val name: String,
    val searchUrl: String
)

object DataRemovalCatalog {

    @Volatile
    private var cached: List<DataRemovalSite>? = null

    fun sites(ctx: Context): List<DataRemovalSite> {
        val existing = cached
        if (existing != null) return existing
        synchronized(this) {
            val again = cached
            if (again != null) return again
            val raw = runCatching {
                ctx.applicationContext.assets.open("threatintel/data_broker_sites.v1.json")
                    .bufferedReader().use { it.readText() }
            }.getOrNull() ?: return emptyList<DataRemovalSite>().also { cached = it }
            val built = runCatching {
                val arr = JSONArray(raw)
                buildList {
                    for (i in 0 until arr.length()) {
                        val o = arr.getJSONObject(i)
                        add(
                            DataRemovalSite(
                                name = o.optString("name"),
                                searchUrl = o.optString("search_url")
                            )
                        )
                    }
                }
            }.getOrDefault(emptyList())
            cached = built
            return built
        }
    }

    /**
     * Expand `{first}` / `{last}` / `{First}` / `{Last}` placeholders. Values are
     * percent-encoded. Returns `null` when either name is blank after trimming.
     */
    fun renderSearchUrl(template: String, firstName: String, lastName: String): Uri? {
        val fTrim = firstName.trim()
        val lTrim = lastName.trim()
        if (fTrim.isEmpty() || lTrim.isEmpty()) return null

        val fLow = fTrim.lowercase(Locale.ROOT)
        val lLow = lTrim.lowercase(Locale.ROOT)
        val fTitle = fTrim.replaceFirstChar { it.uppercase(Locale.ROOT) }
            .let { it.first() + it.drop(1).lowercase(Locale.ROOT) }
        val lTitle = lTrim.replaceFirstChar { it.uppercase(Locale.ROOT) }
            .let { it.first() + it.drop(1).lowercase(Locale.ROOT) }

        fun enc(s: String): String = Uri.encode(s, "")

        val expanded = template
            .replace("{first}", enc(fLow))
            .replace("{last}", enc(lLow))
            .replace("{First}", enc(fTitle))
            .replace("{Last}", enc(lTitle))

        return runCatching { Uri.parse(expanded) }.getOrNull()
    }
}

/**
 * Encrypted-prefs persistence for the user's last-entered scan parameters. Mirrors the
 * iOS `DataRemovalState` Keychain-backed storage. Convenience only — the feature works
 * without any of these values being persisted.
 */
object DataRemovalState {

    private const val PREFS = "data_removal_prefs_v1"
    private const val KEY_FIRST = "first_name"
    private const val KEY_LAST = "last_name"
    private const val KEY_STATE = "state_code"

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

    fun firstName(ctx: Context): String = prefs(ctx).getString(KEY_FIRST, "").orEmpty()
    fun lastName(ctx: Context): String = prefs(ctx).getString(KEY_LAST, "").orEmpty()
    fun stateCode(ctx: Context): String = prefs(ctx).getString(KEY_STATE, "").orEmpty()

    fun setFirstName(ctx: Context, value: String) {
        prefs(ctx).edit().putString(KEY_FIRST, value).apply()
    }

    fun setLastName(ctx: Context, value: String) {
        prefs(ctx).edit().putString(KEY_LAST, value).apply()
    }

    fun setStateCode(ctx: Context, value: String) {
        prefs(ctx).edit().putString(KEY_STATE, value).apply()
    }

    fun clearAll(ctx: Context) {
        prefs(ctx).edit().clear().apply()
    }
}

/** 50 US states + DC. Used for the optional state picker. */
object USStates {
    val all: List<String> = listOf(
        "AL","AK","AZ","AR","CA","CO","CT","DE","DC","FL","GA","HI","ID","IL","IN",
        "IA","KS","KY","LA","ME","MD","MA","MI","MN","MS","MO","MT","NE","NV","NH",
        "NJ","NM","NY","NC","ND","OH","OK","OR","PA","RI","SC","SD","TN","TX","UT",
        "VT","VA","WA","WV","WI","WY"
    )
}
