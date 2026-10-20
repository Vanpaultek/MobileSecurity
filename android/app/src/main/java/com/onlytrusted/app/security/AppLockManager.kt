// SPDX-FileCopyrightText: 2026 57Million LLC
// SPDX-License-Identifier: AGPL-3.0-or-later
package com.onlytrusted.app.security

import android.content.Context
import android.content.SharedPreferences

object AppLockManager {

    private const val PREFS = "app_lock_prefs"
    private const val KEY_ENABLED = "enabled"
    private const val KEY_GRACE_SEC = "grace_seconds"
    private const val KEY_LAST_UNLOCKED_AT = "last_unlocked_at"
    private const val DEFAULT_GRACE_SECONDS = 60

    private fun prefs(ctx: Context): SharedPreferences =
        ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun isEnabled(ctx: Context): Boolean = prefs(ctx).getBoolean(KEY_ENABLED, false)

    fun setEnabled(ctx: Context, enabled: Boolean) {
        prefs(ctx).edit().putBoolean(KEY_ENABLED, enabled).apply()
    }

    fun graceSeconds(ctx: Context): Int =
        prefs(ctx).getInt(KEY_GRACE_SEC, DEFAULT_GRACE_SECONDS)

    fun setGraceSeconds(ctx: Context, seconds: Int) {
        prefs(ctx).edit().putInt(KEY_GRACE_SEC, seconds.coerceIn(0, 600)).apply()
    }

    fun markUnlocked(ctx: Context) {
        prefs(ctx).edit().putLong(KEY_LAST_UNLOCKED_AT, System.currentTimeMillis()).apply()
    }

    fun resetUnlock(ctx: Context) {
        prefs(ctx).edit().remove(KEY_LAST_UNLOCKED_AT).apply()
    }

    fun shouldPrompt(ctx: Context): Boolean {
        if (!isEnabled(ctx)) return false
        val last = prefs(ctx).getLong(KEY_LAST_UNLOCKED_AT, 0L)
        if (last == 0L) return true
        val elapsed = System.currentTimeMillis() - last
        return elapsed > graceSeconds(ctx) * 1000L
    }
}
