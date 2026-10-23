// SPDX-FileCopyrightText: 2026 VanPaulTek Inc
// SPDX-License-Identifier: AGPL-3.0-or-later
package com.onlytrusted.app.ui.breachwatch

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.onlytrusted.app.util.NotificationHelper
import java.util.concurrent.TimeUnit

/**
 * Monthly `PeriodicWorkRequest` scheduler for Breach Watch (FREE tier). Mirrors iOS
 * `BreachWatchScheduler`. Zero network calls — the worker only runs the local lookup
 * against the bundled hash feed and posts a notification when a hit appears.
 */
object BreachWatchScheduler {

    const val MONTHLY_CHECK_WORK = "breach_watch_monthly_check_v1"

    fun scheduleMonthly(context: Context) {
        val constraints = Constraints.Builder().build()
        val req = PeriodicWorkRequestBuilder<BreachWatchCheckWorker>(
            repeatInterval = 30,
            repeatIntervalTimeUnit = TimeUnit.DAYS
        ).setConstraints(constraints).build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            MONTHLY_CHECK_WORK,
            ExistingPeriodicWorkPolicy.UPDATE,
            req
        )
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(MONTHLY_CHECK_WORK)
    }
}

/**
 * Monthly worker: re-runs the local breach lookup against the stored email and posts a
 * single notification when a NEW hit is discovered.
 */
class BreachWatchCheckWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val ctx = applicationContext
        if (BreachWatchState.email(ctx).isEmpty()) return Result.success()
        val before = BreachWatchState.hits(ctx).size
        val now = BreachWatchState.runCheck(ctx)
        if (now.size > before) {
            NotificationHelper.postAlert(
                context = ctx,
                title = "Breach Watch — new hit",
                body = "Your monitored email appeared in a breach list. " +
                    "Open OnlyTrusted → Settings → Breach Watch to review.",
                tag = "breach_watch_new_hit"
            )
        }
        return Result.success()
    }
}
