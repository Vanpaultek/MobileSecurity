// SPDX-FileCopyrightText: 2026 VanPaulTek Inc
// SPDX-License-Identifier: AGPL-3.0-or-later
package com.onlytrusted.app.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.onlytrusted.app.util.NotificationHelper

/**
 * One-shot WorkManager worker that fires ~1 year after the user sets a yearly renewal
 * reminder on an Identity Protection action (Premium). Posts a local notification
 * nudging the user to re-verify the action is still in place.
 *
 * No network — purely a local reminder.
 */
class IdentityReminderWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val id = inputData.getString("action_id") ?: return Result.success()
        val title = inputData.getString("title") ?: "Identity protection step"
        NotificationHelper.postAlert(
            context = applicationContext,
            title = "Renew: $title",
            body = "It's been a year since you flagged this identity-protection step. " +
                "Open OnlyTrusted → Identity Protection to re-verify.",
            tag = "identity_reminder_$id"
        )
        return Result.success()
    }
}
