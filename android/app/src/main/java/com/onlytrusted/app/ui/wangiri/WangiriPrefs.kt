// SPDX-FileCopyrightText: 2026 VanPaulTek Inc
// SPDX-License-Identifier: AGPL-3.0-or-later
package com.onlytrusted.app.ui.wangiri

import android.content.Context
import android.content.SharedPreferences
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Persisted state for Wangiri Protection (Pro).
 *
 * Backed by SharedPreferences so we can keep the feature stateless from the service
 * perspective — the watcher reads [isEnabled] on every ring and writes blocks via
 * [addBlock] which [WangiriSettingsScreen] observes.
 */
object WangiriPrefs {

    private const val PREFS = "wangiri_prefs_v1"
    private const val KEY_ENABLED = "wangiri.v1.enabled"
    private const val KEY_MONTHLY_COUNT = "wangiri.v1.monthly_count"
    private const val KEY_MONTHLY_KEY = "wangiri.v1.monthly_key"
    private const val KEY_RECENT = "wangiri.v1.recent_json"
    /** Default ON — the brief says "default ON". */
    private const val DEFAULT_ENABLED = true

    private fun prefs(ctx: Context): SharedPreferences =
        ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun isEnabled(ctx: Context): Boolean =
        prefs(ctx).getBoolean(KEY_ENABLED, DEFAULT_ENABLED)

    fun setEnabled(ctx: Context, enabled: Boolean) {
        prefs(ctx).edit().putBoolean(KEY_ENABLED, enabled).apply()
    }

    fun blockedThisMonth(ctx: Context): Int {
        val current = currentMonthKey()
        val stored = prefs(ctx).getString(KEY_MONTHLY_KEY, null)
        return if (current == stored) {
            prefs(ctx).getInt(KEY_MONTHLY_COUNT, 0)
        } else {
            // Different month — reset.
            prefs(ctx).edit()
                .putString(KEY_MONTHLY_KEY, current)
                .putInt(KEY_MONTHLY_COUNT, 0)
                .apply()
            0
        }
    }

    private fun incrementMonthlyCount(ctx: Context) {
        val now = blockedThisMonth(ctx) // triggers monthly reset
        prefs(ctx).edit().putInt(KEY_MONTHLY_COUNT, now + 1).apply()
    }

    fun recentBlocks(ctx: Context): List<WangiriBlock> {
        val raw = prefs(ctx).getString(KEY_RECENT, null) ?: return emptyList()
        return raw.split('|')
            .mapNotNull { WangiriBlock.decode(it) }
    }

    fun addBlock(ctx: Context, block: WangiriBlock) {
        val existing = recentBlocks(ctx)
        val next = (listOf(block) + existing).take(20)
        val encoded = next.joinToString("|") { it.encode() }
        prefs(ctx).edit().putString(KEY_RECENT, encoded).apply()
        incrementMonthlyCount(ctx)
    }

    private fun currentMonthKey(): String =
        SimpleDateFormat("yyyy-MM", Locale.US).format(Date())
}

/**
 * A single wangiri detection. Stored as a `|`-joined CSV in SharedPreferences — simpler
 * than wiring a JSON lib for a 20-entry rolling log.
 */
data class WangiriBlock(
    val timestampMillis: Long,
    val number: String?,
    val elapsedMillis: Long
) {
    fun encode(): String = buildString {
        append(timestampMillis); append(',')
        append(elapsedMillis); append(',')
        append(number?.replace(",", "")?.replace("|", "") ?: "")
    }

    companion object {
        fun decode(raw: String): WangiriBlock? {
            val parts = raw.split(',', limit = 3)
            if (parts.size < 3) return null
            val ts = parts[0].toLongOrNull() ?: return null
            val elapsed = parts[1].toLongOrNull() ?: return null
            val number = parts[2].ifBlank { null }
            return WangiriBlock(ts, number, elapsed)
        }
    }
}
