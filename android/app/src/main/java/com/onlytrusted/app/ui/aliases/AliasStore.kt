// SPDX-FileCopyrightText: 2026 VanPaulTek Inc
// SPDX-License-Identifier: AGPL-3.0-or-later
package com.onlytrusted.app.ui.aliases

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import org.json.JSONArray
import org.json.JSONObject
import java.security.SecureRandom
import java.util.UUID

/**
 * EncryptedSharedPreferences-backed store for Private Email Aliases (FREE tier,
 * unlimited count). Mirrors iOS `AliasStore`.
 *
 * Honest claim: OnlyTrusted does not operate a mail server. We generate random alias
 * local-parts and store user bookkeeping (label, enabled flag, chosen relay). Forwarding
 * is done by the user's chosen relay provider — iCloud+, DuckDuckGo, SimpleLogin,
 * Firefox Relay, or a custom domain.
 */
object AliasStore {

    private const val PREFS = "aliases_prefs_v1"
    private const val KEY_LIST = "list"
    private const val KEY_RELAY = "relay"
    private const val KEY_CUSTOM_DOMAIN = "custom_domain"

    data class EmailAlias(
        val id: String,
        val prefix: String,
        val label: String,
        val enabled: Boolean,
        val createdAtMillis: Long
    )

    // Cache the MasterKey after the first build to avoid the Keystore round-trip on every
    // prefs() call (every read / write / toggle). The key material lives in the Android
    // Keystore; the MasterKey instance is just a thin handle, safe to retain for the
    // process lifetime.
    @Volatile private var cachedMasterKey: MasterKey? = null

    private fun prefs(ctx: Context): SharedPreferences {
        val master = cachedMasterKey ?: synchronized(this) {
            cachedMasterKey ?: MasterKey.Builder(ctx.applicationContext)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
                .also { cachedMasterKey = it }
        }
        return EncryptedSharedPreferences.create(
            ctx.applicationContext,
            PREFS,
            master,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    // MARK: - List ops

    fun load(ctx: Context): List<EmailAlias> {
        val raw = prefs(ctx).getString(KEY_LIST, null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                EmailAlias(
                    id = o.getString("id"),
                    prefix = o.getString("prefix"),
                    label = o.optString("label", ""),
                    enabled = o.optBoolean("enabled", true),
                    createdAtMillis = o.optLong("createdAt", 0L)
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun save(ctx: Context, aliases: List<EmailAlias>) {
        val arr = JSONArray()
        aliases.forEach { a ->
            arr.put(JSONObject().apply {
                put("id", a.id)
                put("prefix", a.prefix)
                put("label", a.label)
                put("enabled", a.enabled)
                put("createdAt", a.createdAtMillis)
            })
        }
        prefs(ctx).edit().putString(KEY_LIST, arr.toString()).apply()
    }

    fun generate(ctx: Context, label: String = ""): EmailAlias {
        val a = EmailAlias(
            id = UUID.randomUUID().toString(),
            prefix = generatePrefix(),
            label = label.trim(),
            enabled = true,
            createdAtMillis = System.currentTimeMillis()
        )
        val current = load(ctx).toMutableList()
        current.add(0, a)
        save(ctx, current)
        return a
    }

    fun toggle(ctx: Context, id: String) {
        val updated = load(ctx).map {
            if (it.id == id) it.copy(enabled = !it.enabled) else it
        }
        save(ctx, updated)
    }

    fun rename(ctx: Context, id: String, label: String) {
        val updated = load(ctx).map {
            if (it.id == id) it.copy(label = label.trim()) else it
        }
        save(ctx, updated)
    }

    fun delete(ctx: Context, id: String) {
        save(ctx, load(ctx).filterNot { it.id == id })
    }

    fun clearAll(ctx: Context) {
        prefs(ctx).edit().remove(KEY_LIST).apply()
    }

    // MARK: - Premium: bulk + CSV

    /** Premium: flip every alias to disabled. */
    fun disableAll(ctx: Context) {
        save(ctx, load(ctx).map { it.copy(enabled = false) })
    }

    /** Premium: delete every alias currently marked disabled. */
    fun deleteAllDisabled(ctx: Context) {
        save(ctx, load(ctx).filter { it.enabled })
    }

    /** Premium: CSV export (prefix,label,enabled,created_at_iso). */
    fun exportCsv(ctx: Context): String {
        val iso = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", java.util.Locale.US)
        val sb = StringBuilder("prefix,label,enabled,created_at\n")
        load(ctx).forEach { a ->
            sb.append(a.prefix).append(',')
                .append(csvEscape(a.label)).append(',')
                .append(a.enabled).append(',')
                .append(iso.format(java.util.Date(a.createdAtMillis)))
                .append('\n')
        }
        return sb.toString()
    }

    /** Premium: import CSV; returns number of new aliases added (dupes skipped). */
    fun importCsv(ctx: Context, raw: String): Int {
        val iso = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", java.util.Locale.US)
        val existing = load(ctx).toMutableList()
        val existingPrefixes = existing.map { it.prefix }.toMutableSet()
        var added = 0
        raw.lines().forEachIndexed { idx, line ->
            if (idx == 0 && line.startsWith("prefix,")) return@forEachIndexed
            if (line.isBlank()) return@forEachIndexed
            val cols = csvSplit(line)
            if (cols.size < 4) return@forEachIndexed
            val prefix = cols[0]
            if (prefix.isEmpty() || prefix in existingPrefixes) return@forEachIndexed
            val label = cols[1]
            val enabled = cols[2].equals("true", ignoreCase = true)
            val createdAt = runCatching { iso.parse(cols[3])?.time }.getOrNull()
                ?: System.currentTimeMillis()
            existing.add(0, EmailAlias(
                id = UUID.randomUUID().toString(),
                prefix = prefix,
                label = label,
                enabled = enabled,
                createdAtMillis = createdAt
            ))
            existingPrefixes.add(prefix)
            added++
        }
        save(ctx, existing)
        return added
    }

    private fun csvEscape(s: String): String {
        return if (s.contains(',') || s.contains('"') || s.contains('\n')) {
            "\"" + s.replace("\"", "\"\"") + "\""
        } else s
    }

    private fun csvSplit(line: String): List<String> {
        val cols = mutableListOf<String>()
        val cur = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < line.length) {
            val c = line[i]
            if (inQuotes) {
                if (c == '"') {
                    if (i + 1 < line.length && line[i + 1] == '"') {
                        cur.append('"'); i++
                    } else {
                        inQuotes = false
                    }
                } else {
                    cur.append(c)
                }
            } else {
                when (c) {
                    ',' -> { cols.add(cur.toString()); cur.setLength(0) }
                    '"' -> inQuotes = true
                    else -> cur.append(c)
                }
            }
            i++
        }
        cols.add(cur.toString())
        return cols
    }

    // MARK: - Relay

    fun relay(ctx: Context): RelayChoice {
        val raw = prefs(ctx).getString(KEY_RELAY, null) ?: return RelayChoice.ICLOUD
        return RelayChoice.fromKey(raw) ?: RelayChoice.ICLOUD
    }

    fun setRelay(ctx: Context, choice: RelayChoice) {
        prefs(ctx).edit().putString(KEY_RELAY, choice.key).apply()
    }

    fun customDomain(ctx: Context): String =
        prefs(ctx).getString(KEY_CUSTOM_DOMAIN, "") ?: ""

    fun setCustomDomain(ctx: Context, domain: String) {
        prefs(ctx).edit().putString(KEY_CUSTOM_DOMAIN, domain.trim().lowercase()).apply()
    }

    fun address(ctx: Context, alias: EmailAlias): String {
        val relay = relay(ctx)
        val domain = when (relay) {
            RelayChoice.CUSTOM -> {
                val d = customDomain(ctx)
                if (d.isEmpty()) "yourdomain.example" else d
            }
            else -> relay.domain
        }
        return "${alias.prefix}@$domain"
    }

    // MARK: - Prefix generator

    private const val PREFIX_ALPHABET = "abcdefghjkmnpqrstuvwxyz23456789"

    // SecureRandom (not kotlin.random.Random) — the alias prefix becomes a quasi-secret
    // identifier that lets third parties bill or spam the user, so prefix predictability
    // is a privacy concern even though it isn't a crypto key.
    private val secureRandom = SecureRandom()

    private fun generatePrefix(): String {
        // 3-3-4 split with hyphens, alphabet avoids visually ambiguous chars.
        // Entropy: 31^10 ≈ 8.2×10¹⁴.
        fun chunk(n: Int): String =
            (1..n).map { PREFIX_ALPHABET[secureRandom.nextInt(PREFIX_ALPHABET.length)] }
                .joinToString("")
        return "${chunk(3)}-${chunk(3)}-${chunk(4)}"
    }
}

enum class RelayChoice(
    val key: String,
    val displayName: String,
    val subtitle: String,
    val domain: String,
    val portalUrl: String?
) {
    ICLOUD(
        "icloud",
        "iCloud+ Hide My Email",
        "Included with any paid iCloud+. Create aliases in Settings → Mail → Hide My Email.",
        "icloud.com",
        "https://www.icloud.com/settings/hidemyemail"
    ),
    DUCKDUCKGO(
        "ddg",
        "DuckDuckGo Email Protection",
        "Free. Enroll at duckduckgo.com/email.",
        "duck.com",
        "https://duckduckgo.com/email/"
    ),
    SIMPLELOGIN(
        "simplelogin",
        "SimpleLogin",
        "Free tier up to 15 aliases. Open simplelogin.io.",
        "aleeas.com",
        "https://simplelogin.io/"
    ),
    FIREFOX_RELAY(
        "firefox",
        "Firefox Relay",
        "Free tier up to 5 masks. Open relay.firefox.com.",
        "mozmail.com",
        "https://relay.firefox.com/"
    ),
    CUSTOM(
        "custom",
        "Custom domain",
        "Your own domain with a catch-all mailbox you already own.",
        "",
        null
    );

    companion object {
        fun fromKey(k: String): RelayChoice? = values().firstOrNull { it.key == k }
    }
}
