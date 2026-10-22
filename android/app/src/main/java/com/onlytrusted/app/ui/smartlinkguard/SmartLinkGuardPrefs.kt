// SPDX-FileCopyrightText: 2026 VanPaulTek Inc
// SPDX-License-Identifier: AGPL-3.0-or-later
package com.onlytrusted.app.ui.smartlinkguard

import android.content.Context
import android.content.SharedPreferences

/**
 * Persisted state for Smart Link Guard (Pro).
 *
 *  - [isSmsScanEnabled] gates whether the manifest-registered SMS receiver actually
 *    runs the rules engine. We leave the receiver registered either way so Android
 *    doesn't have to re-register when the toggle flips.
 *  - [recentScans] is a short rolling log (max 20) of URLs the owner inspected or
 *    the SMS scanner flagged. ASCII unit-separator encoding keeps the schema cheap.
 */
object SmartLinkGuardPrefs {

    private const val PREFS = "smartlinkguard_prefs_v1"
    private const val KEY_SMS_ENABLED = "slg.v1.sms_enabled"
    private const val KEY_RECENT = "slg.v1.recent"

    // ASCII control chars avoid collisions with any realistic URL / reason string.
    private const val RECORD_SEP = "" // RS — between records
    private const val FIELD_SEP = "" // US — between fields in one record

    private fun prefs(ctx: Context): SharedPreferences =
        ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun isSmsScanEnabled(ctx: Context): Boolean =
        prefs(ctx).getBoolean(KEY_SMS_ENABLED, true)

    fun setSmsScanEnabled(ctx: Context, enabled: Boolean) {
        prefs(ctx).edit().putBoolean(KEY_SMS_ENABLED, enabled).apply()
    }

    fun recentScans(ctx: Context): List<RecentScan> {
        val raw = prefs(ctx).getString(KEY_RECENT, null) ?: return emptyList()
        return raw.split(RECORD_SEP).mapNotNull { RecentScan.decode(it) }
    }

    fun addScan(ctx: Context, scan: RecentScan) {
        val existing = recentScans(ctx)
        val next = (listOf(scan) + existing).take(20)
        val encoded = next.joinToString(RECORD_SEP) { it.encode() }
        prefs(ctx).edit().putString(KEY_RECENT, encoded).apply()
    }

    fun clearScans(ctx: Context) {
        prefs(ctx).edit().remove(KEY_RECENT).apply()
    }

    internal fun fieldSep(): String = FIELD_SEP
}

/**
 * One entry in the recent-scans log. Deliberately flat so we can serialise with a cheap
 * joiner — JSON would be overkill for a 20-row rolling list.
 */
data class RecentScan(
    val timestampMillis: Long,
    val url: String,
    val verdict: URLVerdict,
    val source: Source,
    val topReason: String
) {
    enum class Source { Manual, QR, SMS }

    fun encode(): String {
        val sep = SmartLinkGuardPrefs.fieldSep()
        val safeUrl = url.replace(sep, " ").replace("", " ")
        val safeReason = topReason.replace(sep, " ").replace("", " ")
        return listOf(
            timestampMillis.toString(),
            verdict.name,
            source.name,
            safeUrl,
            safeReason
        ).joinToString(sep)
    }

    companion object {
        fun decode(raw: String): RecentScan? {
            val sep = SmartLinkGuardPrefs.fieldSep()
            val parts = raw.split(sep)
            if (parts.size < 5) return null
            val ts = parts[0].toLongOrNull() ?: return null
            val verdict = runCatching { URLVerdict.valueOf(parts[1]) }.getOrNull() ?: return null
            val source = runCatching { Source.valueOf(parts[2]) }.getOrNull() ?: return null
            return RecentScan(ts, parts[3], verdict, source, parts[4])
        }
    }
}
