// SPDX-FileCopyrightText: 2026 VanPaulTek Inc
// SPDX-License-Identifier: AGPL-3.0-or-later
package com.onlytrusted.app.ui.wangiri

import android.Manifest
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.IBinder
import android.telephony.PhoneStateListener
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager
import androidx.core.content.ContextCompat
import com.onlytrusted.app.OnlyTrustedApp
import com.onlytrusted.app.data.db.entities.SecurityEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.util.concurrent.Executors

/**
 * Watches phone-state transitions for the classic wangiri (one-ring-and-cut) scam.
 *
 * Flow:
 *  - CALL_STATE_RINGING arrives → remember start timestamp + incoming number.
 *  - CALL_STATE_IDLE arrives without CALL_STATE_OFFHOOK → compute elapsed.
 *  - If elapsed < [WANGIRI_THRESHOLD_MS] AND the number isn't trusted → auto-block.
 *
 * The service is purposely lightweight — a plain started `Service`, not a foreground
 * service. It's bound to the application lifecycle; we start it from
 * `OnlyTrustedApp.onCreate` whenever Pro is active and Wangiri is enabled, and stop
 * it when the user toggles the feature off.
 *
 * Permissions reused (NOT newly requested): `READ_PHONE_STATE` is already declared
 * for HP2/SIM-Swap work. We no-op if the runtime grant isn't present.
 */
class WangiriWatcherService : Service() {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var telephonyManager: TelephonyManager? = null
    private var modernCallback: Any? = null // TelephonyCallback on API 31+
    private var legacyListener: PhoneStateListener? = null

    @Volatile private var ringStartMillis: Long = 0L
    @Volatile private var ringNumber: String? = null
    @Volatile private var sawOffHook: Boolean = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        if (!hasReadPhoneState()) {
            // Nothing we can do without the permission — stop immediately. The settings
            // screen explains how to grant it.
            stopSelf()
            return
        }
        telephonyManager = getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            registerModern()
        } else {
            registerLegacy()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        unregister()
        scope.cancel()
    }

    private fun hasReadPhoneState(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE) ==
            PackageManager.PERMISSION_GRANTED

    private fun registerModern() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        val tm = telephonyManager ?: return
        val cb = object : TelephonyCallback(), TelephonyCallback.CallStateListener {
            override fun onCallStateChanged(state: Int) {
                handleStateChange(state, incomingNumber = null)
            }
        }
        modernCallback = cb
        try {
            tm.registerTelephonyCallback(Executors.newSingleThreadExecutor(), cb)
        } catch (_: Throwable) {
            // Device-specific crash — fall through, service exits on next GC.
        }
    }

    @Suppress("DEPRECATION")
    private fun registerLegacy() {
        val tm = telephonyManager ?: return
        val listener = object : PhoneStateListener() {
            override fun onCallStateChanged(state: Int, phoneNumber: String?) {
                handleStateChange(state, phoneNumber)
            }
        }
        legacyListener = listener
        try {
            tm.listen(listener, PhoneStateListener.LISTEN_CALL_STATE)
        } catch (_: Throwable) {
            // ignore
        }
    }

    @Suppress("DEPRECATION")
    private fun unregister() {
        val tm = telephonyManager ?: return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                (modernCallback as? TelephonyCallback)?.let { tm.unregisterTelephonyCallback(it) }
            } else {
                legacyListener?.let { tm.listen(it, PhoneStateListener.LISTEN_NONE) }
            }
        } catch (_: Throwable) { /* ignore */ }
        modernCallback = null
        legacyListener = null
    }

    private fun handleStateChange(state: Int, incomingNumber: String?) {
        when (state) {
            TelephonyManager.CALL_STATE_RINGING -> {
                ringStartMillis = System.currentTimeMillis()
                ringNumber = incomingNumber?.ifBlank { null }
                sawOffHook = false
            }
            TelephonyManager.CALL_STATE_OFFHOOK -> {
                // User answered — this is NOT wangiri.
                sawOffHook = true
            }
            TelephonyManager.CALL_STATE_IDLE -> {
                val start = ringStartMillis
                val wasRinging = start > 0L
                val answered = sawOffHook
                val elapsed = if (wasRinging) System.currentTimeMillis() - start else 0L
                val number = ringNumber
                // Reset state before any heavy work so re-entrant events don't double-count.
                ringStartMillis = 0L
                ringNumber = null
                sawOffHook = false
                if (!wasRinging || answered) return
                if (elapsed <= 0L || elapsed >= WANGIRI_THRESHOLD_MS) return
                scope.launch { classifyAndMaybeBlock(number, elapsed) }
            }
        }
    }

    private suspend fun classifyAndMaybeBlock(number: String?, elapsed: Long) {
        if (!WangiriPrefs.isEnabled(this)) return
        val app = application as? OnlyTrustedApp ?: return

        // Trusted-vault check — never flag numbers already in the owner's roster.
        if (number != null && app.trustedContacts.isTrustedSync(number)) return

        val label = "Wangiri (one-ring hangup)"
        if (number != null) {
            app.repository.block(
                number = number,
                label = label,
                blockCalls = true,
                blockSms = false
            )
        }
        app.repository.log(
            type = SecurityEvent.TYPE_WANGIRI_BLOCKED,
            detail = buildString {
                append("elapsed_ms=").append(elapsed)
                if (number != null) append(" from=").append(number)
            }
        )
        WangiriPrefs.addBlock(
            this,
            WangiriBlock(
                timestampMillis = System.currentTimeMillis(),
                number = number,
                elapsedMillis = elapsed
            )
        )
    }

    companion object {
        /** Any ring-to-idle delta under this is classified as wangiri. */
        const val WANGIRI_THRESHOLD_MS: Long = 2_000L

        /**
         * Start the service if Pro is active AND Wangiri is enabled.
         * Safe to call repeatedly — Android dedupes started services.
         */
        fun startIfEligible(context: Context) {
            if (!WangiriPrefs.isEnabled(context)) return
            if (!com.onlytrusted.app.ui.subscription.SubscriptionState.hasHighPremium(context)) return
            try {
                val intent = Intent(context, WangiriWatcherService::class.java)
                context.startService(intent)
            } catch (_: Throwable) {
                // Launching a background service from the background on API 26+ can be
                // refused — the watcher simply misses the next ring. Not a crash case.
            }
        }

        fun stop(context: Context) {
            try {
                context.stopService(Intent(context, WangiriWatcherService::class.java))
            } catch (_: Throwable) { /* ignore */ }
        }
    }
}
