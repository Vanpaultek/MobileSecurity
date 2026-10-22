// SPDX-FileCopyrightText: 2026 VanPaulTek Inc
// SPDX-License-Identifier: AGPL-3.0-or-later
package com.onlytrusted.app.deadmanwipe

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

/**
 * WorkManager worker that performs the actual wipe when the dead-man deadline fires.
 *
 * Called both by [DeadmanWipeScheduler]'s daily periodic check and by [MainActivity] on
 * launch via [DeadmanWipePerformer.wipeIfDeadlineElapsed]. Having one authority keeps the
 * wipe logic in a single place and ensures the on-launch and background paths behave the
 * same.
 */
class DeadmanWipeCheckWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        DeadmanWipePerformer.wipeIfDeadlineElapsed(applicationContext)
        return Result.success()
    }
}
