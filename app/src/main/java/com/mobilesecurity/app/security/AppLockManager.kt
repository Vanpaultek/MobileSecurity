/*
 * Copyright (C) 2026 57Million LLC
 *
 * This file is part of MobileSecurity.
 *
 * MobileSecurity is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * MobileSecurity is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */
package com.mobilesecurity.app.security

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
