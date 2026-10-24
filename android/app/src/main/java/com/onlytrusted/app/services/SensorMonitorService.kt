// SPDX-FileCopyrightText: 2026 VanPaulTek Inc
// SPDX-License-Identifier: AGPL-3.0-or-later
package com.onlytrusted.app.services

import android.app.Service
import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.media.AudioRecordingConfiguration
import android.os.Build
import android.os.IBinder
import com.onlytrusted.app.OnlyTrustedApp
import com.onlytrusted.app.data.db.entities.SecurityEvent
import com.onlytrusted.app.util.NotificationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class SensorMonitorService : Service() {

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var pollJob: Job? = null

    private val audioManager by lazy { getSystemService(AudioManager::class.java) }
    private val cameraManager by lazy { getSystemService(CameraManager::class.java) }

    private val recordingCallback = object : AudioManager.AudioRecordingCallback() {
        override fun onRecordingConfigChanged(configs: MutableList<AudioRecordingConfiguration>) {
            super.onRecordingConfigChanged(configs)
            handleAudioConfigs(configs)
        }
    }

    private val cameraCallback = object : CameraManager.AvailabilityCallback() {
        override fun onCameraUnavailable(cameraId: String) {
            super.onCameraUnavailable(cameraId)
            logCameraInUse(cameraId)
        }
    }

    private val micAlertedSessions = mutableSetOf<Int>()
    private val cameraAlertCooldown = mutableMapOf<String, Long>()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        startForegroundInternal()
        audioManager?.registerAudioRecordingCallback(recordingCallback, null)
        cameraManager?.registerAvailabilityCallback(cameraCallback, null)
        startPolling()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForegroundInternal()
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        pollJob?.cancel()
        scope.cancel()
        runCatching { audioManager?.unregisterAudioRecordingCallback(recordingCallback) }
        runCatching { cameraManager?.unregisterAvailabilityCallback(cameraCallback) }
    }

    private fun startForegroundInternal() {
        val notification = NotificationHelper.buildMonitorNotification(
            this,
            getString(com.onlytrusted.app.R.string.monitor_text_idle)
        )
        // The manifest already declares the foreground-service types; the system picks them up.
        startForeground(NotificationHelper.NOTIF_FG_MONITOR, notification)
    }

    private fun startPolling() {
        val am = audioManager ?: return
        // The AudioRecordingCallback fires on every change; this loop is only a safety net
        // against missed callbacks. We adapt the interval to activity: tight 30 s poll
        // while a recording is live (catch any missed end-events quickly) and relaxed
        // 2 min poll while idle (don't burn CPU wake-ups when nothing is recording).
        pollJob = scope.launch {
            var lastWasActive = false
            while (true) {
                val interval = if (lastWasActive) ACTIVE_POLL_INTERVAL_MS else IDLE_POLL_INTERVAL_MS
                delay(interval)
                val configs = am.activeRecordingConfigurations
                lastWasActive = configs.isNotEmpty()
                handleAudioConfigs(configs)
            }
        }
    }

    private fun handleAudioConfigs(configs: List<AudioRecordingConfiguration>) {
        if (configs.isEmpty()) {
            micAlertedSessions.clear()
            return
        }
        for (config in configs) {
            if (config.isClientSilenced) continue
            val id = config.clientAudioSessionId
            if (!micAlertedSessions.add(id)) continue

            val packageName = resolveOwnerForAudioSession(config)
            val repo = (application as? OnlyTrustedApp)?.repository
            scope.launch {
                repo?.log(
                    type = SecurityEvent.TYPE_MIC,
                    packageName = packageName,
                    detail = "session=$id"
                )
            }
            NotificationHelper.postAlert(
                context = this,
                title = getString(com.onlytrusted.app.R.string.alert_mic_title),
                body = packageName?.let {
                    getString(com.onlytrusted.app.R.string.alert_mic_body_with_pkg, it)
                } ?: getString(com.onlytrusted.app.R.string.alert_mic_body_generic),
                tag = "mic-$id"
            )
        }
    }

    private fun resolveOwnerForAudioSession(config: AudioRecordingConfiguration): String? {
        // AudioRecordingConfiguration does not expose the UID/package publicly on all API levels.
        // We expose what we can — if nothing is available, we return null and show a generic alert.
        return null
    }

    private fun logCameraInUse(cameraId: String) {
        val now = System.currentTimeMillis()
        val last = cameraAlertCooldown[cameraId] ?: 0L
        if (now - last < CAMERA_ALERT_COOLDOWN_MS) return
        cameraAlertCooldown[cameraId] = now

        val repo = (application as? OnlyTrustedApp)?.repository
        scope.launch {
            repo?.log(
                type = SecurityEvent.TYPE_CAMERA,
                packageName = null,
                detail = "cameraId=$cameraId"
            )
        }
        NotificationHelper.postAlert(
            context = this,
            title = getString(com.onlytrusted.app.R.string.alert_camera_title),
            body = getString(com.onlytrusted.app.R.string.alert_camera_body, cameraId),
            tag = "cam-$cameraId"
        )
    }

    companion object {
        private const val ACTIVE_POLL_INTERVAL_MS = 30_000L
        private const val IDLE_POLL_INTERVAL_MS = 120_000L
        private const val CAMERA_ALERT_COOLDOWN_MS = 10_000L

        fun start(context: Context) {
            val intent = Intent(context, SensorMonitorService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, SensorMonitorService::class.java))
        }
    }
}
