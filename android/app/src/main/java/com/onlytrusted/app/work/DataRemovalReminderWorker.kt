// SPDX-FileCopyrightText: 2026 VanPaulTek Inc
// SPDX-License-Identifier: AGPL-3.0-or-later
package com.onlytrusted.app.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.onlytrusted.app.util.NotificationHelper

/**
 * One-shot WorkManager worker that fires ~90 days after the user marks a broker site
 * as "removal requested" from the Premium Data Removal flow. Posts a local notification
 * nudging the user to re-check that site.
 *
 * No network — purely a local reminder.
 */
class DataRemovalReminderWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val site = inputData.getString("site") ?: return Result.success()
        NotificationHelper.postAlert(
            context = applicationContext,
            title = "Re-check $site",
            body = "90 days since you requested removal from $site. " +
                "Open OnlyTrusted → Data Removal to search for your name again " +
                "and confirm the record is gone.",
            tag = "data_removal_reminder_$site"
        )
        return Result.success()
    }
}
