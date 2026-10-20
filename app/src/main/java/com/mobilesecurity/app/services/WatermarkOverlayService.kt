// SPDX-FileCopyrightText: 2026 57Million LLC
// SPDX-License-Identifier: AGPL-3.0-or-later
package com.mobilesecurity.app.services

import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.WindowManager
import com.mobilesecurity.app.MobileSecurityApp
import com.mobilesecurity.app.data.db.entities.ShieldSession
import com.mobilesecurity.app.privacy.ProtectionSettings
import com.mobilesecurity.app.util.NotificationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.security.SecureRandom
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Draws a semi-transparent watermark across the entire screen while the user has Shield on.
 *
 * The watermark contains:
 *   • The owner's chosen tag (e.g. initials or a nickname).
 *   • A short per-session code the user can look up in the Shield log to prove when a
 *     captured image was taken.
 *   • Rolling timestamp refreshed every 30 s.
 *
 * We draw the overlay on top of the system; we never read, record, or interact with what
 * is underneath. We use FLAG_NOT_FOCUSABLE and FLAG_NOT_TOUCHABLE so every tap passes
 * through to the app the user is actually using.
 */
class WatermarkOverlayService : Service() {

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var refreshJob: Job? = null
    private var windowManager: WindowManager? = null
    private var overlayView: WatermarkView? = null

    private var sessionId: Long = -1L
    private var sessionCode: String = ""

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        startForegroundInternal()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!Settings.canDrawOverlays(this)) {
            // The user turned the permission off while we were running. Stop cleanly.
            stopSelf()
            return START_NOT_STICKY
        }
        startForegroundInternal()
        if (overlayView == null) {
            attachOverlay()
            beginSession()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        detachOverlay()
        refreshJob?.cancel()
        // Persist the session end before we cancel our scope. Using NonCancellable guarantees
        // the DB write finishes even if someone is already asking us to shut down.
        runBlocking {
            withContext(NonCancellable + Dispatchers.IO) { endSessionSync() }
        }
        scope.cancel()
    }

    private fun startForegroundInternal() {
        val notification = NotificationHelper.buildMonitorNotification(
            this,
            getString(com.mobilesecurity.app.R.string.shield_text_running)
        )
        startForeground(NotificationHelper.NOTIF_FG_SHIELD, notification)
    }

    private fun attachOverlay() {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_SYSTEM_OVERLAY
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
        }

        sessionCode = generateSessionCode()
        val ownerTag = ProtectionSettings.ownerTag(this).ifEmpty { "owner" }
        val view = WatermarkView(this, buildTagLine(ownerTag, sessionCode))

        windowManager = getSystemService(WindowManager::class.java)
        runCatching { windowManager?.addView(view, params) }
        overlayView = view
        startRefreshLoop(ownerTag)
    }

    private fun startRefreshLoop(ownerTag: String) {
        refreshJob?.cancel()
        refreshJob = scope.launch {
            while (true) {
                delay(REFRESH_INTERVAL_MS)
                val line = buildTagLine(ownerTag, sessionCode)
                withContext(Dispatchers.Main) {
                    val vm = windowManager ?: return@withContext
                    val existing = overlayView ?: return@withContext
                    runCatching { vm.removeView(existing) }
                    val replacement = WatermarkView(this@WatermarkOverlayService, line)
                    val params = (existing.layoutParams as WindowManager.LayoutParams)
                    runCatching { vm.addView(replacement, params) }
                    overlayView = replacement
                }
            }
        }
    }

    private fun detachOverlay() {
        val vm = windowManager
        val view = overlayView
        if (vm != null && view != null) runCatching { vm.removeView(view) }
        overlayView = null
        windowManager = null
    }

    private fun beginSession() {
        val app = application as? MobileSecurityApp ?: return
        val ownerTag = ProtectionSettings.ownerTag(this).ifEmpty { "owner" }
        scope.launch {
            sessionId = app.database.shieldSessionDao().insert(
                ShieldSession(
                    code = sessionCode,
                    ownerTag = ownerTag,
                    startedAt = System.currentTimeMillis()
                )
            )
        }
    }

    private suspend fun endSessionSync() {
        val app = application as? MobileSecurityApp ?: return
        val id = sessionId
        if (id < 0) return
        app.database.shieldSessionDao().markEnded(id, System.currentTimeMillis())
    }

    private fun buildTagLine(ownerTag: String, code: String): String {
        val timestamp = TIMESTAMP_FORMAT.format(Date())
        return "$ownerTag · $timestamp · $code"
    }

    private fun generateSessionCode(): String {
        val bytes = ByteArray(5).also { SecureRandom().nextBytes(it) }
        val base32 = "ABCDEFGHJKMNPQRSTUVWXYZ23456789"
        return bytes.joinToString(separator = "") {
            base32[(it.toInt() and 0x1F)].toString()
        }
    }

    companion object {
        private const val REFRESH_INTERVAL_MS = 30_000L
        private val TIMESTAMP_FORMAT = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)

        fun start(context: Context) {
            if (!Settings.canDrawOverlays(context)) return
            val intent = Intent(context, WatermarkOverlayService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, WatermarkOverlayService::class.java))
        }
    }
}
