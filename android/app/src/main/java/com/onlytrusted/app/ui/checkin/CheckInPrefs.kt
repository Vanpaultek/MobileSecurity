// SPDX-FileCopyrightText: 2026 VanPaulTek Inc
// SPDX-License-Identifier: AGPL-3.0-or-later
package com.onlytrusted.app.ui.checkin

import android.content.Context
import android.content.SharedPreferences

/**
 * Persisted state for Safety Check-In (Pro).
 *
 * All stateless — the UI reads values on composition, writes on toggle. The scheduled
 * nag is driven by [CheckInScheduler] using these keys as its source of truth.
 */
object CheckInPrefs {
    private const val PREFS = "checkin_prefs_v1"
    private const val KEY_LAST_AT = "checkin.v1.last_at_millis"
    private const val KEY_SCHEDULED = "checkin.v1.scheduled_enabled"
    private const val KEY_HOUR = "checkin.v1.scheduled_hour"
    private const val KEY_MINUTE = "checkin.v1.scheduled_minute"

    private fun prefs(ctx: Context): SharedPreferences =
        ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun lastCheckInMillis(ctx: Context): Long =
        prefs(ctx).getLong(KEY_LAST_AT, 0L)

    fun setLastCheckIn(ctx: Context, millis: Long) {
        prefs(ctx).edit().putLong(KEY_LAST_AT, millis).apply()
    }

    fun scheduledEnabled(ctx: Context): Boolean =
        prefs(ctx).getBoolean(KEY_SCHEDULED, false)

    fun setScheduledEnabled(ctx: Context, enabled: Boolean) {
        prefs(ctx).edit().putBoolean(KEY_SCHEDULED, enabled).apply()
    }

    fun scheduledHour(ctx: Context): Int =
        prefs(ctx).getInt(KEY_HOUR, 21)

    fun scheduledMinute(ctx: Context): Int =
        prefs(ctx).getInt(KEY_MINUTE, 0)

    fun setScheduledTime(ctx: Context, hour: Int, minute: Int) {
        prefs(ctx).edit()
            .putInt(KEY_HOUR, hour)
            .putInt(KEY_MINUTE, minute)
            .apply()
    }
}
