// SPDX-FileCopyrightText: 2026 VanPaulTek Inc
// SPDX-License-Identifier: AGPL-3.0-or-later
package com.onlytrusted.app.privacy

import android.content.Context

object OnboardingState {
    private const val PREFS = "onboarding_prefs"
    private const val KEY_ONBOARDED = "onboarded_v1"
    private const val KEY_CONSENT_ACCEPTED = "consent_accepted_v1"
    private const val KEY_CONSENT_AT = "consent_accepted_at"
    private const val KEY_IS_ADULT = "is_adult"
    private const val KEY_OWNS_DEVICE = "owns_device"
    private const val KEY_UNDERSTOOD_LIMITS = "understood_limits"

    fun hasOnboarded(ctx: Context): Boolean =
        prefs(ctx).getBoolean(KEY_ONBOARDED, false)

    fun markOnboarded(ctx: Context) {
        prefs(ctx).edit().putBoolean(KEY_ONBOARDED, true).apply()
    }

    fun hasAccepted(ctx: Context): Boolean =
        prefs(ctx).getBoolean(KEY_CONSENT_ACCEPTED, false)

    fun accept(
        ctx: Context,
        isAdult: Boolean,
        ownsDevice: Boolean,
        understandsLimits: Boolean
    ) {
        require(isAdult && ownsDevice && understandsLimits) {
            "All three confirmations are required to enter the app."
        }
        prefs(ctx).edit()
            .putBoolean(KEY_CONSENT_ACCEPTED, true)
            .putLong(KEY_CONSENT_AT, System.currentTimeMillis())
            .putBoolean(KEY_IS_ADULT, isAdult)
            .putBoolean(KEY_OWNS_DEVICE, ownsDevice)
            .putBoolean(KEY_UNDERSTOOD_LIMITS, understandsLimits)
            .apply()
    }

    fun reset(ctx: Context) {
        prefs(ctx).edit().clear().apply()
    }

    private fun prefs(ctx: Context) =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
