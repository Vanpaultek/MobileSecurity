// SPDX-FileCopyrightText: 2026 VanPaulTek Inc
// SPDX-License-Identifier: AGPL-3.0-or-later
package com.onlytrusted.app.ui.checkin

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import java.util.Calendar

/**
 * Daily reminder scheduler for Safety Check-In.
 *
 * The brief asks for `AlarmManager.setInexactRepeating` + a receiver that posts a
 * notification. Inexact fits the "nag if you forgot" use-case and avoids the
 * SCHEDULE_EXACT_ALARM permission dance on API 31+.
 */
object CheckInScheduler {

    private const val REQUEST_CODE = 7714
    private const val DAY_MILLIS = 24L * 60L * 60L * 1000L

    fun schedule(ctx: Context) {
        if (!CheckInPrefs.scheduledEnabled(ctx)) return
        val alarm = ctx.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return

        val trigger = nextTriggerMillis(ctx)
        val pi = pendingIntent(ctx, create = true) ?: return
        alarm.setInexactRepeating(AlarmManager.RTC_WAKEUP, trigger, DAY_MILLIS, pi)
    }

    fun cancel(ctx: Context) {
        val alarm = ctx.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val pi = pendingIntent(ctx, create = false) ?: return
        alarm.cancel(pi)
    }

    private fun nextTriggerMillis(ctx: Context): Long {
        val hour = CheckInPrefs.scheduledHour(ctx)
        val minute = CheckInPrefs.scheduledMinute(ctx)
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, hour)
        cal.set(Calendar.MINUTE, minute)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        if (cal.timeInMillis <= System.currentTimeMillis()) {
            cal.add(Calendar.DAY_OF_YEAR, 1)
        }
        return cal.timeInMillis
    }

    private fun pendingIntent(ctx: Context, create: Boolean): PendingIntent? {
        val intent = Intent(ctx, CheckInNagReceiver::class.java).apply {
            action = CheckInNagReceiver.ACTION_NAG
        }
        val flags = if (create) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        }
        return PendingIntent.getBroadcast(ctx.applicationContext, REQUEST_CODE, intent, flags)
    }
}
