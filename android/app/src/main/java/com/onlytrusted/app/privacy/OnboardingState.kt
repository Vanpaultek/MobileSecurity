// SPDX-FileCopyrightText: 2026 57Million LLC
// SPDX-License-Identifier: AGPL-3.0-or-later
package com.onlytrusted.app.privacy

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
