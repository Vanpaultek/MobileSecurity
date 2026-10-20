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
package com.mobilesecurity.app.privacy

import android.content.Context

object OnboardingState {
    private const val PREFS = "onboarding_prefs"
    private const val KEY_ACCEPTED = "accepted_v1"
    private const val KEY_ACCEPTED_AT = "accepted_at"
    private const val KEY_IS_ADULT = "is_adult"
    private const val KEY_OWNS_DEVICE = "owns_device"
    private const val KEY_UNDERSTOOD_LIMITS = "understood_limits"

    fun hasAccepted(ctx: Context): Boolean =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_ACCEPTED, false)

    fun accept(
        ctx: Context,
        isAdult: Boolean,
        ownsDevice: Boolean,
        understandsLimits: Boolean
    ) {
        require(isAdult && ownsDevice && understandsLimits) {
            "All three confirmations are required to enter the app."
        }
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putBoolean(KEY_ACCEPTED, true)
            .putLong(KEY_ACCEPTED_AT, System.currentTimeMillis())
            .putBoolean(KEY_IS_ADULT, isAdult)
            .putBoolean(KEY_OWNS_DEVICE, ownsDevice)
            .putBoolean(KEY_UNDERSTOOD_LIMITS, understandsLimits)
            .apply()
    }

    fun reset(ctx: Context) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply()
    }
}
