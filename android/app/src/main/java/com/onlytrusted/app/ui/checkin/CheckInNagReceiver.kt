// SPDX-FileCopyrightText: 2026 VanPaulTek Inc
// SPDX-License-Identifier: AGPL-3.0-or-later
package com.onlytrusted.app.ui.checkin

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.onlytrusted.app.util.NotificationHelper
import java.util.Calendar

/**
 * Fires once per day at the owner-chosen check-in time. If the owner has not pressed
 * "I'm safe" in the last 24 hours, we post a reminder notification.
 */
class CheckInNagReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != ACTION_NAG) return
        if (!CheckInPrefs.scheduledEnabled(context)) return

        val lastMillis = CheckInPrefs.lastCheckInMillis(context)
        if (lastMillis > 0L && withinLast24Hours(lastMillis)) {
            // The owner already checked in today — stay quiet.
            return
        }
        NotificationHelper.postAlert(
            context = context,
            title = "OnlyTrusted check-in",
            body = "Haven't heard from you today. Open OnlyTrusted and tap 'I'm safe' when you can.",
            tag = "checkin-nag"
        )
    }

    private fun withinLast24Hours(lastMillis: Long): Boolean {
        val cal = Calendar.getInstance()
        cal.add(Calendar.HOUR_OF_DAY, -24)
        return lastMillis >= cal.timeInMillis
    }

    companion object {
        const val ACTION_NAG = "com.onlytrusted.app.action.CHECKIN_NAG"
    }
}
