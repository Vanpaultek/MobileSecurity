// SPDX-FileCopyrightText: 2026 VanPaulTek Inc
// SPDX-License-Identifier: AGPL-3.0-or-later
package com.onlytrusted.app.ui.trackerfinder

import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import com.onlytrusted.app.MainActivity
import com.onlytrusted.app.R
import com.onlytrusted.app.util.NotificationHelper

/**
 * Foreground service that runs a repeating BLE scan window (default: 20 s scan, 15 min idle)
 * for the Tracker Finder (Pro) continuous mode. Starts another scan each tick so the
 * "3+ sessions over >15 min" rule in [TrackerFinderStore.followingTrackers] can fire.
 *
 * Requires:
 *   • `FOREGROUND_SERVICE`
 *   • `FOREGROUND_SERVICE_LOCATION` (Android 14+ — BLE scanning that could reveal
 *     presence counts as a location-adjacent FGS type).
 *   • `BLUETOOTH_SCAN` + `BLUETOOTH_CONNECT` (Android 12+) OR `ACCESS_FINE_LOCATION` (<12).
 *
 * Started by the Settings toggle via [startIfEligible] and stopped via [stop].
 * The service exits itself if Pro lapses or the continuous toggle flips off.
 */
class TrackerFinderService : Service() {

    private val handler = Handler(Looper.getMainLooper())
    private var scanner: TrackerFinderScanner? = null

    private val scanTick = object : Runnable {
        override fun run() {
            // Gate-check every tick so the service self-terminates if Pro lapses.
            if (!com.onlytrusted.app.ui.subscription.SubscriptionState.hasHighPremium(this@TrackerFinderService) ||
                !TrackerFinderStore.isContinuousEnabled(this@TrackerFinderService)) {
                stopSelf()
                return
            }
            runScanWindow()
            handler.postDelayed(this, SESSION_INTERVAL_MS)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        startFgs()
        handler.post(scanTick)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onDestroy() {
        handler.removeCallbacks(scanTick)
        runCatching { scanner?.stop() }
        super.onDestroy()
    }

    private fun startFgs() {
        val notification = NotificationCompat.Builder(this, NotificationHelper.CHANNEL_MONITOR)
            .setContentTitle("Tracker Finder is scanning")
            .setContentText("Looking for AirTag / Tile / SmartTag signatures in the background.")
            .setSmallIcon(R.drawable.ic_shield)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setVisibility(NotificationCompat.VISIBILITY_SECRET)
            .setContentIntent(
                PendingIntent.getActivity(
                    this,
                    0,
                    Intent(this, MainActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    },
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
            )
            .build()

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                // Android 14+ requires an explicit foreground service type that matches the
                // declared FGS types in AndroidManifest.xml.
                startForeground(
                    FGS_NOTIF_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
                )
            } else {
                startForeground(FGS_NOTIF_ID, notification)
            }
        } catch (t: Throwable) {
            // startForeground is picky on some OEMs. If we can't go foreground, exit so we
            // don't silently become a background service that the OS will kill anyway.
            stopSelf()
        }
    }

    private fun runScanWindow() {
        // One-shot: let BLE discover peripherals for SCAN_WINDOW_MS, then stop so the
        // radio idles. If a scan is already in flight (slow callback), skip.
        if (scanner != null) return
        val s = TrackerFinderScanner(
            context = this,
            onClassified = { sighting ->
                evaluateAndMaybeAlert(sighting.macHash, sighting.type)
            },
            onFinished = { _ ->
                scanner = null
            },
            onError = { _ ->
                scanner = null
            }
        )
        if (s.start()) {
            scanner = s
            handler.postDelayed({ runCatching { s.stop() }; scanner = null }, SCAN_WINDOW_MS)
        } else {
            scanner = null
        }
    }

    private fun evaluateAndMaybeAlert(hash: String, type: TrackerFinderStore.TrackerType) {
        val followers = TrackerFinderStore.followingTrackers(this)
        val follower = followers.firstOrNull { it.macHash == hash } ?: return
        if (!TrackerFinderStore.shouldAlertAndMark(this, follower.macHash)) return
        NotificationHelper.postAlert(
            context = this,
            title = "Possible tracker following you",
            body = "A ${follower.type.displayName} has reappeared across ${follower.sessionCount} scans. Tap to review.",
            tag = "tracker-finder-${follower.macHash.take(8)}"
        )
    }

    companion object {
        private const val FGS_NOTIF_ID = 1_501
        /** Short active-scan window per tick. */
        private const val SCAN_WINDOW_MS: Long = 20_000L
        /** Gap between scan windows — matches the brief's "every 15 minutes" cadence. */
        private const val SESSION_INTERVAL_MS: Long = 15 * 60 * 1000L

        fun startIfEligible(context: Context) {
            if (!com.onlytrusted.app.ui.subscription.SubscriptionState.hasHighPremium(context)) return
            if (!TrackerFinderStore.isContinuousEnabled(context)) return
            if (!TrackerFinderScanner.hasRequiredPermissions(context)) return
            try {
                val intent = Intent(context, TrackerFinderService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (_: Throwable) {
                // OEM-specific throttling; the user can retry from Settings.
            }
        }

        fun stop(context: Context) {
            runCatching {
                context.stopService(Intent(context, TrackerFinderService::class.java))
            }
        }
    }
}
