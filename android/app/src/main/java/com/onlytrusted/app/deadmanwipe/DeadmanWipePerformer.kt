// SPDX-FileCopyrightText: 2026 VanPaulTek Inc
// SPDX-License-Identifier: AGPL-3.0-or-later
package com.onlytrusted.app.deadmanwipe

import android.content.Context
import com.onlytrusted.app.OnlyTrustedApp
import com.onlytrusted.app.data.db.entities.SecurityEvent
import com.onlytrusted.app.security.AppLockManager
import com.onlytrusted.app.ui.duress.DuressState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The single wipe implementation shared by [DeadmanWipeCheckWorker] and the launch-time
 * check in [com.onlytrusted.app.MainActivity].
 *
 * Mirrors the "Wipe everything" action in [com.onlytrusted.app.ui.settings.SettingsViewModel]
 * but is independent of the ViewModel so it can run from a Worker where there is no VM scope.
 */
object DeadmanWipePerformer {

    /**
     * Runs the wipe only if the dead-man condition is currently satisfied. Idempotent, safe
     * to call multiple times.
     */
    suspend fun wipeIfDeadlineElapsed(context: Context) {
        if (!DeadmanWipeState.shouldWipeNow(context)) return
        performWipe(context)
    }

    /** Unconditional wipe. Logs the reason into the event store before clearing it. */
    suspend fun performWipe(context: Context) = withContext(Dispatchers.IO) {
        val app = context.applicationContext as OnlyTrustedApp
        val interval = DeadmanWipeState.intervalDays(context)

        // Log an event before clearing so the chain of audit entries ends with a marker row.
        // This row itself will be deleted by the clearAll() call below, but if a backup was
        // in flight the hash chain contains proof of the wipe trigger.
        app.repository.log(
            type = SecurityEvent.TYPE_WIPE,
            detail = "dead-man wipe (${interval}d)"
        )

        // Databases.
        app.repository.wipeAll()
        app.trustedContacts.wipeAll()
        app.database.shieldSessionDao().deleteAll()

        // SharedPreferences — every known preference bucket. We don't delete the
        // files on disk (API uses .clear() which empties them in place).
        clearPrefs(context, "settings")
        clearPrefs(context, "protection_prefs")
        clearPrefs(context, "subscription_prefs_v1")
        clearPrefs(context, "autolock_prefs_v1")
        clearPrefs(context, "app_lock_prefs")
        clearPrefs(context, "notification_prefs_v1")
        clearPrefs(context, "blocking_prefs_v1")
        clearPrefs(context, "simswap_prefs_v1")
        clearPrefs(context, "wangiri_prefs_v1")
        clearPrefs(context, "checkin_prefs_v1")
        clearPrefs(context, "smart_link_guard_prefs_v1")
        clearPrefs(context, "tracker_finder_prefs_v1")
        clearPrefs(context, "permission_watchdog_prefs_v1")
        clearPrefs(context, "onboarding_prefs")
        clearPrefs(context, "hardware_key_prefs_v1")

        // EncryptedSharedPreferences — Duress + Watchdog + Dead-Man.
        DuressState.clearAll(context)
        DeadmanWipeState.clearAll(context)

        // AppLockManager flags.
        AppLockManager.setEnabled(context, false)
        AppLockManager.resetUnlock(context)

        // Cancel the two WorkManager jobs so they don't keep firing after the wipe.
        DeadmanWipeScheduler.cancelAll(context)
    }

    private fun clearPrefs(context: Context, name: String) {
        context.applicationContext
            .getSharedPreferences(name, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .apply()
    }
}
