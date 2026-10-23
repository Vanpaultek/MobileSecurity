// SPDX-FileCopyrightText: 2026 VanPaulTek Inc
// SPDX-License-Identifier: AGPL-3.0-or-later
package com.onlytrusted.app.ui.advancedblocking

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.util.Calendar

/**
 * Premium-only Advanced Blocking rules. Mirrors iOS `AdvancedBlocking`.
 *  * Quiet Hours — a daily window during which non-trusted-contact calls are treated as
 *    blocked. Stored as minutes since midnight (0..1439).
 *  * Area-code allowlist — 3-digit NANP codes whose callers are never flagged as
 *    unknown-caller spam even if they aren't in the vault.
 *
 * Query the helpers from CallBlockerService / SMS filter paths to apply the rules at
 * routing time; the state is read fresh on each query so edits take effect without a
 * service restart.
 */
object AdvancedBlocking {

    private const val PREFS = "advanced_blocking_prefs_v1"
    private const val KEY_QUIET_ENABLED = "quiet_enabled"
    private const val KEY_QUIET_START = "quiet_start_minutes"
    private const val KEY_QUIET_END = "quiet_end_minutes"
    private const val KEY_AREA_CODES = "area_codes_csv"

    @Volatile private var cachedMasterKey: MasterKey? = null

    private fun prefs(ctx: Context): SharedPreferences {
        val master = cachedMasterKey ?: synchronized(this) {
            cachedMasterKey ?: MasterKey.Builder(ctx.applicationContext)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
                .also { cachedMasterKey = it }
        }
        return EncryptedSharedPreferences.create(
            ctx.applicationContext, PREFS, master,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    fun quietHoursEnabled(ctx: Context): Boolean =
        prefs(ctx).getBoolean(KEY_QUIET_ENABLED, false)

    fun setQuietHoursEnabled(ctx: Context, enabled: Boolean) {
        prefs(ctx).edit().putBoolean(KEY_QUIET_ENABLED, enabled).apply()
    }

    fun quietStartMinutes(ctx: Context): Int =
        prefs(ctx).getInt(KEY_QUIET_START, 22 * 60) // default 22:00

    fun setQuietStartMinutes(ctx: Context, m: Int) {
        prefs(ctx).edit().putInt(KEY_QUIET_START, m.coerceIn(0, 1439)).apply()
    }

    fun quietEndMinutes(ctx: Context): Int =
        prefs(ctx).getInt(KEY_QUIET_END, 7 * 60) // default 07:00

    fun setQuietEndMinutes(ctx: Context, m: Int) {
        prefs(ctx).edit().putInt(KEY_QUIET_END, m.coerceIn(0, 1439)).apply()
    }

    fun areaCodeAllowlist(ctx: Context): List<String> {
        val raw = prefs(ctx).getString(KEY_AREA_CODES, "") ?: ""
        if (raw.isEmpty()) return emptyList()
        return raw.split(",").filter { it.length == 3 && it.all(Char::isDigit) }
    }

    fun setAreaCodeAllowlist(ctx: Context, codes: List<String>) {
        val cleaned = codes
            .map { it.filter(Char::isDigit) }
            .filter { it.length == 3 }
            .distinct()
            .sorted()
        prefs(ctx).edit().putString(KEY_AREA_CODES, cleaned.joinToString(",")).apply()
    }

    /** True if the current clock time falls inside the quiet-hours window. Cross-midnight supported. */
    fun isQuietHoursNow(ctx: Context, cal: Calendar = Calendar.getInstance()): Boolean {
        if (!quietHoursEnabled(ctx)) return false
        val now = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
        val start = quietStartMinutes(ctx)
        val end = quietEndMinutes(ctx)
        if (start == end) return false
        return if (start < end) now in start until end
        else now >= start || now < end
    }

    /** True if the number's NANP area code is on the user's allowlist. */
    fun isAreaCodeAllowed(ctx: Context, phoneNumber: String): Boolean {
        val digits = phoneNumber.filter(Char::isDigit)
        if (digits.length < 10) return false
        val noCountry = if (digits.length == 11 && digits.startsWith("1")) digits.drop(1) else digits
        val code = noCountry.take(3)
        return areaCodeAllowlist(ctx).contains(code)
    }

    fun minutesLabel(m: Int): String {
        val h = (m / 60) % 24
        val mm = m % 60
        return "%02d:%02d".format(h, mm)
    }
}
