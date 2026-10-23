// SPDX-FileCopyrightText: 2026 VanPaulTek Inc
// SPDX-License-Identifier: AGPL-3.0-or-later
package com.onlytrusted.app.ui.threatintel

import android.content.Context
import org.json.JSONObject
import java.security.MessageDigest
import java.util.Date
import java.util.Locale

/**
 * Elite-tier (`SubscriptionState.hasElite`) offline threat-intelligence database.
 *
 * Loads the four bundled feed files from `assets/threatintel/` into in-memory lookup
 * structures the moment [get] is first called. All lookups run 100% on-device.
 * Partner Swift impl lives at
 * `ios/.../Sources/ThreatIntel/ThreatIntelDatabase.swift` and must stay in sync on
 * data-file names + parsing conventions (`#` = comment, blank lines ignored).
 */
class ThreatIntelDatabase private constructor(appContext: Context) {

    data class PegasusIoCs(
        val filenames: List<String>,
        val plistEntries: List<String>,
        val dylibSha256: List<String>,
        val processNames: List<String>,
        val domains: List<String>,
        val updatedAtMillis: Long?
    )

    data class VaultScanHit(
        val contactName: String,
        val reason: String,
        val value: String
    )

    data class DeviceScanHit(
        val kind: String,
        val value: String
    )

    val scamNumbers: Set<String>
    val phishingPatterns: List<String>
    val breachHashPrefixes: Set<String>
    val pegasus: PegasusIoCs?

    val totalEntries: Int
        get() = scamNumbers.size + phishingPatterns.size + breachHashPrefixes.size +
            (pegasus?.let {
                it.filenames.size + it.plistEntries.size + it.dylibSha256.size +
                    it.processNames.size + it.domains.size
            } ?: 0)

    val lastUpdatedMillis: Long? = null

    init {
        scamNumbers = loadLines(appContext, "threatintel/scam_numbers.v1.txt").toSet()
        phishingPatterns = loadLines(appContext, "threatintel/phishing_urls.v1.txt")
            .map { it.lowercase(Locale.ROOT) }
        breachHashPrefixes = loadLines(appContext, "threatintel/breach_hashes.v1.txt")
            .map { it.lowercase(Locale.ROOT) }
            .toSet()
        pegasus = runCatching {
            appContext.applicationContext.assets.open("threatintel/pegasus_iocs.v1.json")
                .bufferedReader().use { it.readText() }
        }.map { parsePegasus(it) }.getOrNull()
    }

    private val lastUpdated: Date? = pegasus?.updatedAtMillis?.let { Date(it) }

    fun lastUpdatedDate(): Date? = lastUpdated

    private fun loadLines(ctx: Context, path: String): List<String> =
        runCatching {
            ctx.applicationContext.assets.open(path).bufferedReader().use { r ->
                r.lineSequence()
                    .map { it.trim() }
                    .filter { it.isNotEmpty() && !it.startsWith("#") }
                    .toList()
            }
        }.getOrElse { emptyList() }

    private fun parsePegasus(raw: String): PegasusIoCs {
        val obj = JSONObject(raw)
        fun arr(name: String): List<String> {
            val a = obj.optJSONArray(name) ?: return emptyList()
            return (0 until a.length()).map { a.getString(it) }
        }
        val rawUpdated: String? = if (obj.has("updated_at")) obj.optString("updated_at").takeIf { it.isNotEmpty() } else null
        val updatedAt = rawUpdated?.let {
            runCatching {
                val fmt = java.text.SimpleDateFormat("yyyy-MM-dd", Locale.US)
                fmt.timeZone = java.util.TimeZone.getTimeZone("UTC")
                fmt.parse(it)?.time
            }.getOrNull()
        }
        return PegasusIoCs(
            filenames = arr("filenames"),
            plistEntries = arr("plist_entries"),
            dylibSha256 = arr("dylib_sha256"),
            processNames = arr("process_names"),
            domains = arr("domains"),
            updatedAtMillis = updatedAt
        )
    }

    // MARK: - Lookups

    fun isScamNumber(raw: String): Boolean {
        if (raw.isBlank()) return false
        val digits = raw.filter { it.isDigit() || it == '+' }
        val normalised = if (digits.startsWith("+")) digits else "+$digits"
        if (normalised in scamNumbers) return true
        if (digits in scamNumbers) return true
        return false
    }

    fun isPhishingHost(host: String): Boolean {
        if (host.isBlank()) return false
        val needle = host.lowercase(Locale.ROOT)
        return phishingPatterns.any { pattern ->
            needle.contains(pattern) || pattern.contains(needle)
        }
    }

    fun isBreachedEmail(email: String): Boolean {
        if (email.isBlank()) return false
        val lowered = email.trim().lowercase(Locale.ROOT)
        val digest = MessageDigest.getInstance("SHA-256").digest(lowered.toByteArray())
        val hex = digest.joinToString("") { "%02x".format(it) }
        val prefix = hex.take(10)
        return prefix in breachHashPrefixes
    }

    companion object {
        @Volatile private var instance: ThreatIntelDatabase? = null

        fun get(ctx: Context): ThreatIntelDatabase =
            instance ?: synchronized(this) {
                instance ?: ThreatIntelDatabase(ctx.applicationContext).also { instance = it }
            }
    }
}
