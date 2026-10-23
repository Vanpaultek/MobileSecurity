// SPDX-FileCopyrightText: 2026 VanPaulTek Inc
// SPDX-License-Identifier: AGPL-3.0-or-later
package com.onlytrusted.app.ui.dataremoval

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

/**
 * Catalog + per-mode template renderer for the Data Removal Scan (FREE tier,
 * visibility-only). Reads `assets/threatintel/data_broker_sites.v1.json` once per
 * process. Mirrors the iOS `DataRemovalCatalog`.
 *
 * Each site may expose up to three deep-link search URLs — Name / Phone / Address —
 * via the `search_urls` JSON object. Sites that only support one or two of these omit
 * the other keys; the UI filters sites out of the visible list when they can't fulfil
 * the selected `SearchMode`.
 *
 * We DO NOT scrape or probe these sites. The feature only generates deep-link URLs
 * the user can open in their default browser. Removal remains manual, per-site.
 */
enum class SearchMode(val label: String, val key: String) {
    NAME("Name", "name"),
    PHONE("Phone", "phone"),
    ADDRESS("Address", "address");

    companion object {
        fun fromKey(key: String?): SearchMode =
            entries.firstOrNull { it.key == key } ?: NAME
    }
}

data class DataRemovalSite(
    val name: String,
    val searchUrls: Map<String, String>
) {
    fun template(mode: SearchMode): String? = searchUrls[mode.key]
}

data class DataRemovalForm(
    val firstName: String = "",
    val lastName: String = "",
    val state: String = "",
    val phone: String = "",
    val street: String = "",
    val city: String = ""
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
                        val urls = urlsFromJson(o.optJSONObject("search_urls"))
                        add(DataRemovalSite(name = o.optString("name"), searchUrls = urls))
                    }
                }
            }.getOrDefault(emptyList())
            cached = built
            return built
        }
    }

    private fun urlsFromJson(obj: JSONObject?): Map<String, String> {
        if (obj == null) return emptyMap()
        val out = mutableMapOf<String, String>()
        for (key in listOf("name", "phone", "address")) {
            val v = obj.optString(key, "")
            if (v.isNotEmpty()) out[key] = v
        }
        return out
    }

    /** Sites that support the given search mode, preserving catalog order. */
    fun sites(ctx: Context, mode: SearchMode): List<DataRemovalSite> =
        sites(ctx).filter { it.template(mode) != null }

    /** Builds the deep-link URL for a site + mode + form inputs. */
    fun renderUrl(site: DataRemovalSite, mode: SearchMode, form: DataRemovalForm): Uri? {
        val template = site.template(mode) ?: return null
        return when (mode) {
            SearchMode.NAME    -> renderName(template, form)
            SearchMode.PHONE   -> renderPhone(template, form)
            SearchMode.ADDRESS -> renderAddress(template, form)
        }
    }

    private fun renderName(template: String, form: DataRemovalForm): Uri? {
        val fTrim = form.firstName.trim()
        val lTrim = form.lastName.trim()
        if (fTrim.isEmpty() || lTrim.isEmpty()) return null

        val fLow = fTrim.lowercase(Locale.ROOT)
        val lLow = lTrim.lowercase(Locale.ROOT)
        val fTitle = fTrim.replaceFirstChar { it.uppercase(Locale.ROOT) }
            .let { it.first() + it.drop(1).lowercase(Locale.ROOT) }
        val lTitle = lTrim.replaceFirstChar { it.uppercase(Locale.ROOT) }
            .let { it.first() + it.drop(1).lowercase(Locale.ROOT) }

        val expanded = template
            .replace("{first}", enc(fLow))
            .replace("{last}", enc(lLow))
            .replace("{First}", enc(fTitle))
            .replace("{Last}", enc(lTitle))
        return runCatching { Uri.parse(expanded) }.getOrNull()
    }

    private fun renderPhone(template: String, form: DataRemovalForm): Uri? {
        val digits = form.phone.filter { it.isDigit() }
        if (digits.length < 10) return null
        val expanded = template.replace("{phone}", digits)
        return runCatching { Uri.parse(expanded) }.getOrNull()
    }

    private fun renderAddress(template: String, form: DataRemovalForm): Uri? {
        val street = form.street.trim()
        val city = form.city.trim()
        val state = form.state.trim()
        if (street.isEmpty() || city.isEmpty() || state.isEmpty()) return null

        val expanded = template
            .replace("{street}", enc(street.lowercase(Locale.ROOT).replace(" ", "-")))
            .replace("{city}", enc(city.lowercase(Locale.ROOT).replace(" ", "-")))
            .replace("{state}", enc(state.lowercase(Locale.ROOT)))
        return runCatching { Uri.parse(expanded) }.getOrNull()
    }

    private fun enc(s: String): String = Uri.encode(s, "")
}

/**
 * Encrypted-prefs persistence for the user's last-entered scan parameters. Mirrors the
 * iOS `DataRemovalState` Keychain-backed storage. Convenience only — the feature works
 * without any of these values being persisted.
 */
object DataRemovalState {

    private const val PREFS = "data_removal_prefs_v2"
    private const val KEY_MODE = "search_mode"
    private const val KEY_FIRST = "first_name"
    private const val KEY_LAST = "last_name"
    private const val KEY_STATE = "state_code"
    private const val KEY_PHONE = "phone"
    private const val KEY_STREET = "street"
    private const val KEY_CITY = "city"

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

    fun mode(ctx: Context): SearchMode = SearchMode.fromKey(prefs(ctx).getString(KEY_MODE, null))
    fun firstName(ctx: Context): String = prefs(ctx).getString(KEY_FIRST, "").orEmpty()
    fun lastName(ctx: Context): String = prefs(ctx).getString(KEY_LAST, "").orEmpty()
    fun stateCode(ctx: Context): String = prefs(ctx).getString(KEY_STATE, "").orEmpty()
    fun phone(ctx: Context): String = prefs(ctx).getString(KEY_PHONE, "").orEmpty()
    fun street(ctx: Context): String = prefs(ctx).getString(KEY_STREET, "").orEmpty()
    fun city(ctx: Context): String = prefs(ctx).getString(KEY_CITY, "").orEmpty()

    fun setMode(ctx: Context, value: SearchMode) {
        prefs(ctx).edit().putString(KEY_MODE, value.key).apply()
    }
    fun setFirstName(ctx: Context, value: String) {
        prefs(ctx).edit().putString(KEY_FIRST, value).apply()
    }
    fun setLastName(ctx: Context, value: String) {
        prefs(ctx).edit().putString(KEY_LAST, value).apply()
    }
    fun setStateCode(ctx: Context, value: String) {
        prefs(ctx).edit().putString(KEY_STATE, value).apply()
    }
    fun setPhone(ctx: Context, value: String) {
        prefs(ctx).edit().putString(KEY_PHONE, value).apply()
    }
    fun setStreet(ctx: Context, value: String) {
        prefs(ctx).edit().putString(KEY_STREET, value).apply()
    }
    fun setCity(ctx: Context, value: String) {
        prefs(ctx).edit().putString(KEY_CITY, value).apply()
    }

    fun formFrom(ctx: Context) = DataRemovalForm(
        firstName = firstName(ctx),
        lastName = lastName(ctx),
        state = stateCode(ctx),
        phone = phone(ctx),
        street = street(ctx),
        city = city(ctx)
    )

    fun clearAll(ctx: Context) {
        prefs(ctx).edit().clear().apply()
    }
}

/** 50 US states + DC. Used for the state picker. */
object USStates {
    val all: List<String> = listOf(
        "AL","AK","AZ","AR","CA","CO","CT","DE","DC","FL","GA","HI","ID","IL","IN",
        "IA","KS","KY","LA","ME","MD","MA","MI","MN","MS","MO","MT","NE","NV","NH",
        "NJ","NM","NY","NC","ND","OH","OK","OR","PA","RI","SC","SD","TN","TX","UT",
        "VT","VA","WA","WV","WI","WY"
    )
}
