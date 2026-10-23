// SPDX-FileCopyrightText: 2026 VanPaulTek Inc
// SPDX-License-Identifier: AGPL-3.0-or-later
package com.onlytrusted.app.ui.appshield

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import org.json.JSONArray

/**
 * Auto Privacy Shield — Phase 2: Selected App Restriction (Android side).
 *
 * Honest reality:
 *   True "block other apps" on Android requires Device Owner provisioning via `dpm set-device-owner`
 *   from ADB, which the user can only run after a factory reset. OnlyTrusted ships through the
 *   Play Store, so it CANNOT enforce the restriction itself. The Phase 2 Android experience is a
 *   guided manual setup that walks the user through Google's own Digital Wellbeing / Focus Mode
 *   app-limit controls (same underlying `UsageStatsManager` / `DevicePolicyManager` APIs, just
 *   wrapped in Google's privileged UI).
 *
 * This state object only persists an informational list of app package names the user WANTS
 * restricted, plus the "has walked the user through the Wellbeing hand-off" flag. The list is
 * purely a reminder — we do not read, write, or infer any status from the actual Wellbeing
 * configuration.
 *
 * Storage uses `EncryptedSharedPreferences` so a filesystem copy can't be read without the
 * device-bound master key. Matches the pattern used by FaceLockState / HardwareKeyState.
 */
object AppShieldState {

    private const val PREFS = "app_shield_prefs_v1"
    private const val KEY_WATCHED_PACKAGES = "appshield.v1.watched_packages"
    private const val KEY_WELLBEING_WALKTHROUGH_DONE = "appshield.v1.wellbeing_walkthrough_done"

    private fun prefs(ctx: Context): SharedPreferences {
        val masterKey = MasterKey.Builder(ctx)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        return EncryptedSharedPreferences.create(
            ctx.applicationContext,
            PREFS,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    /**
     * The user's informational "want restricted" list. Stored as a JSON array of package names.
     * This list is NEVER enforced by OnlyTrusted — it only drives the reminder UI telling the
     * user which apps they still need to add inside Digital Wellbeing.
     */
    fun watchedPackages(ctx: Context): List<String> {
        val raw = prefs(ctx).getString(KEY_WATCHED_PACKAGES, null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            List(arr.length()) { i -> arr.getString(i) }
        }.getOrDefault(emptyList())
    }

    fun setWatchedPackages(ctx: Context, packages: List<String>) {
        val cleaned = packages.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        val arr = JSONArray()
        cleaned.forEach { arr.put(it) }
        prefs(ctx).edit().putString(KEY_WATCHED_PACKAGES, arr.toString()).apply()
    }

    fun addWatchedPackage(ctx: Context, pkg: String) {
        val current = watchedPackages(ctx).toMutableList()
        val cleaned = pkg.trim()
        if (cleaned.isEmpty() || current.contains(cleaned)) return
        current.add(cleaned)
        setWatchedPackages(ctx, current)
    }

    fun removeWatchedPackage(ctx: Context, pkg: String) {
        val current = watchedPackages(ctx).toMutableList()
        if (current.remove(pkg)) {
            setWatchedPackages(ctx, current)
        }
    }

    fun clearWatchedPackages(ctx: Context) {
        prefs(ctx).edit().remove(KEY_WATCHED_PACKAGES).apply()
    }

    /** `true` after the user has completed the "Open Digital Wellbeing" guided walkthrough. */
    fun wellbeingWalkthroughDone(ctx: Context): Boolean =
        prefs(ctx).getBoolean(KEY_WELLBEING_WALKTHROUGH_DONE, false)

    fun setWellbeingWalkthroughDone(ctx: Context, value: Boolean) {
        prefs(ctx).edit().putBoolean(KEY_WELLBEING_WALKTHROUGH_DONE, value).apply()
    }

    /** `true` when the user has at least one entry in the informational list. */
    fun isConfigured(ctx: Context): Boolean = watchedPackages(ctx).isNotEmpty()
}
