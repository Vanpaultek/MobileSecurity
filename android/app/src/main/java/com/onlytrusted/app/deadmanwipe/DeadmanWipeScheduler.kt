// SPDX-FileCopyrightText: 2026 VanPaulTek Inc
// SPDX-License-Identifier: AGPL-3.0-or-later
package com.onlytrusted.app.deadmanwipe

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

/**
 * Schedules the two WorkManager tasks the dead-man feature relies on:
 *
 * 1. [DeadmanWipeCheckWorker] — runs daily, checks if the inactivity deadline has elapsed,
 *    and wipes everything on-device if so. The on-launch path in `MainActivity` also runs
 *    this check synchronously so we don't depend on WorkManager being prompt.
 *
 * 2. [DeadmanWipeWarningWorker] — single-shot, scheduled 24h before the deadline. Posts a
 *    system notification telling the user to open the app before the timer fires.
 *
 * Both are no-ops if the user has disabled Dead-Man Wipe since they were scheduled.
 */
object DeadmanWipeScheduler {

    const val DAILY_CHECK_WORK = "deadman_wipe_daily_check_v1"
    const val WARNING_WORK = "deadman_wipe_warning_v1"

    /** Schedule both the daily check and the warning fire. Call on enable + on every reset. */
    fun rescheduleAll(context: Context) {
        scheduleDailyCheck(context)
        scheduleWarning(context)
    }

    fun cancelAll(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(DAILY_CHECK_WORK)
        WorkManager.getInstance(context).cancelUniqueWork(WARNING_WORK)
    }

    private fun scheduleDailyCheck(context: Context) {
        val req = PeriodicWorkRequestBuilder<DeadmanWipeCheckWorker>(
            repeatInterval = 1,
            repeatIntervalTimeUnit = TimeUnit.DAYS
        ).build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            DAILY_CHECK_WORK,
            ExistingPeriodicWorkPolicy.UPDATE,
            req
        )
    }

    private fun scheduleWarning(context: Context) {
        val deadline = DeadmanWipeState.wipeDeadlineMillis(context)
        val warnAt = deadline - 24 * 3_600_000L
        val now = System.currentTimeMillis()
        val delay = warnAt - now
        if (delay <= 0L) {
            // Already past the warning threshold (or the deadline). Skip; the daily check
            // will handle the actual wipe if we're past the deadline.
            WorkManager.getInstance(context).cancelUniqueWork(WARNING_WORK)
            return
        }
        val req = OneTimeWorkRequestBuilder<DeadmanWipeWarningWorker>()
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            WARNING_WORK,
            ExistingWorkPolicy.REPLACE,
            req
        )
    }
}
