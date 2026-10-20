package com.mobilesecurity.app.util

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import com.mobilesecurity.app.MainActivity
import com.mobilesecurity.app.R

object NotificationHelper {

    const val CHANNEL_MONITOR = "monitor_channel"
    const val CHANNEL_ALERTS = "alerts_channel"

    const val NOTIF_FG_MONITOR = 1001
    const val NOTIF_FG_POCKET = 1002
    const val NOTIF_FG_SHIELD = 1003
    private const val NOTIF_ALERT_BASE = 2000

    fun buildMonitorNotification(context: Context, text: String): Notification {
        return NotificationCompat.Builder(context, CHANNEL_MONITOR)
            .setContentTitle(context.getString(R.string.monitor_title))
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_shield)
            .setOngoing(true)
            .setContentIntent(mainActivityIntent(context))
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setVisibility(NotificationCompat.VISIBILITY_SECRET)
            .build()
    }

    fun postAlert(context: Context, title: String, body: String, tag: String) {
        val notification = NotificationCompat.Builder(context, CHANNEL_ALERTS)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setSmallIcon(R.drawable.ic_shield)
            .setAutoCancel(true)
            .setContentIntent(mainActivityIntent(context))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_LIGHTS)
            .build()
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.notify(tag, NOTIF_ALERT_BASE, notification)
        vibrate(context)
    }

    private fun mainActivityIntent(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    private fun vibrate(context: Context) {
        val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vm = context.getSystemService(VibratorManager::class.java)
            vm?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
        vibrator ?: return
        if (!vibrator.hasVibrator()) return
        val pattern = longArrayOf(0, 180, 90, 180)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(pattern, -1)
        }
    }
}
