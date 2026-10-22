// SPDX-FileCopyrightText: 2026 VanPaulTek Inc
// SPDX-License-Identifier: AGPL-3.0-or-later
package com.onlytrusted.app.deadmanwipe

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.onlytrusted.app.R
import com.onlytrusted.app.util.NotificationHelper

/**
 * Posts the "Dead-Man Wipe in 24h" notification. Scheduled by [DeadmanWipeScheduler] to fire
 * 24h before the current deadline. Does nothing if the feature was disabled after scheduling.
 */
class DeadmanWipeWarningWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val ctx = applicationContext
        if (!DeadmanWipeState.isEnabled(ctx)) return Result.success()
        // Also skip if the user unlocked after we scheduled — in that case the next
        // deadline is further out than 24h from now.
        val deadline = DeadmanWipeState.wipeDeadlineMillis(ctx)
        val now = System.currentTimeMillis()
        if (deadline - now > 48 * 3_600_000L) {
            // User unlocked after we were scheduled; reschedule to the new deadline.
            DeadmanWipeScheduler.rescheduleAll(ctx)
            return Result.success()
        }

        postNotification(ctx)
        return Result.success()
    }

    private fun postNotification(ctx: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                ctx,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) return
        }
        val n = NotificationCompat.Builder(ctx, NotificationHelper.CHANNEL_ALERTS)
            .setContentTitle("Dead-Man Wipe in 24h")
            .setContentText("Unlock OnlyTrusted to reset the timer.")
            .setStyle(
                NotificationCompat.BigTextStyle().bigText(
                    "Unlock OnlyTrusted to reset the timer. Otherwise all contacts, " +
                        "blocked numbers, events, and settings will be wiped tomorrow."
                )
            )
            .setSmallIcon(R.drawable.ic_shield)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .build()
        val manager = ctx.getSystemService(NotificationManager::class.java) ?: return
        manager.notify(TAG, NOTIF_ID, n)
    }

    companion object {
        private const val TAG = "deadman_wipe_warning"
        private const val NOTIF_ID = 2401
    }
}
