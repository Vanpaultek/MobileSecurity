// SPDX-FileCopyrightText: 2026 57Million LLC
// SPDX-License-Identifier: AGPL-3.0-or-later
package com.onlytrusted.app.privacy

import android.content.Context
import android.content.SharedPreferences

/**
 * Settings that change the behaviour of the services at runtime.
 *
 * Kept in a plain SharedPreferences file (no sensitive content) so the services and the UI
 * read the same values without a dependency on Room or coroutines.
 */
object ProtectionSettings {

    private const val PREFS = "protection_prefs"
    private const val KEY_TRUSTED_ONLY_CALLS = "trusted_only_calls"
    private const val KEY_TRUSTED_ONLY_SMS = "trusted_only_sms"
    private const val KEY_SILENCE_UNTRUSTED_CALLS = "silence_untrusted_calls"
    private const val KEY_STRICT_MODE = "strict_mode"

    private fun prefs(ctx: Context): SharedPreferences =
        ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun trustedOnlyCalls(ctx: Context): Boolean =
        prefs(ctx).getBoolean(KEY_TRUSTED_ONLY_CALLS, false)

    fun setTrustedOnlyCalls(ctx: Context, enabled: Boolean) {
        prefs(ctx).edit().putBoolean(KEY_TRUSTED_ONLY_CALLS, enabled).apply()
    }

    fun trustedOnlySms(ctx: Context): Boolean =
        prefs(ctx).getBoolean(KEY_TRUSTED_ONLY_SMS, false)

    fun setTrustedOnlySms(ctx: Context, enabled: Boolean) {
        prefs(ctx).edit().putBoolean(KEY_TRUSTED_ONLY_SMS, enabled).apply()
    }

    /** If true, trusted-only calls go to voicemail silently; if false, we only log. */
    fun silenceUntrustedCalls(ctx: Context): Boolean =
        prefs(ctx).getBoolean(KEY_SILENCE_UNTRUSTED_CALLS, true)

    fun setSilenceUntrustedCalls(ctx: Context, enabled: Boolean) {
        prefs(ctx).edit().putBoolean(KEY_SILENCE_UNTRUSTED_CALLS, enabled).apply()
    }

    /** When ON, the dashboard shows mic/camera status with stronger copy and
     *  the vault add/edit screens re-prompt biometric on each open. */
    fun strictMode(ctx: Context): Boolean =
        prefs(ctx).getBoolean(KEY_STRICT_MODE, true)

    fun setStrictMode(ctx: Context, enabled: Boolean) {
        prefs(ctx).edit().putBoolean(KEY_STRICT_MODE, enabled).apply()
    }

    private const val KEY_OWNER_NUMBER = "owner_number"
    private const val KEY_HEURISTIC_SILENCE = "heuristic_silence_enabled"

    fun ownerNumber(ctx: Context): String? =
        prefs(ctx).getString(KEY_OWNER_NUMBER, null)?.takeIf { it.isNotBlank() }

    fun setOwnerNumber(ctx: Context, number: String?) {
        prefs(ctx).edit()
            .putString(KEY_OWNER_NUMBER, number?.trim()?.takeIf { it.isNotBlank() })
            .apply()
    }

    /** When ON, calls scoring at Recommendation.SILENCE are silenced automatically. */
    fun heuristicSilence(ctx: Context): Boolean =
        prefs(ctx).getBoolean(KEY_HEURISTIC_SILENCE, true)

    fun setHeuristicSilence(ctx: Context, enabled: Boolean) {
        prefs(ctx).edit().putBoolean(KEY_HEURISTIC_SILENCE, enabled).apply()
    }

    private const val KEY_OWNER_TAG = "owner_tag"

    /** Short identifier (e.g. initials or a nickname) that appears in the Shield watermark. */
    fun ownerTag(ctx: Context): String =
        prefs(ctx).getString(KEY_OWNER_TAG, null).orEmpty().take(24)

    fun setOwnerTag(ctx: Context, value: String) {
        prefs(ctx).edit().putString(KEY_OWNER_TAG, value.trim().take(24)).apply()
    }
}
