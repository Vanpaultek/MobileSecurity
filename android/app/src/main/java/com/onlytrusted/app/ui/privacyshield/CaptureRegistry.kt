// SPDX-FileCopyrightText: 2026 VanPaulTek Inc
// SPDX-License-Identifier: AGPL-3.0-or-later
package com.onlytrusted.app.ui.privacyshield

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Auto Privacy Shield — Phase 1 (Android).
 *
 * Honest scope: Android reserves cross-app sensor control to Device Owner provisioning,
 * which is off-limits for a consumer Play Store app. So this registry only owns
 * OnlyTrusted's own camera (`CameraX` / `Camera2`) + microphone (`AudioRecord` /
 * `MediaRecorder`) sessions. Every feature that opens one registers a [Handle] with a
 * `stop` callback; the registry calls `stop()` on every live handle on three triggers:
 *
 *   * `ProcessLifecycleOwner` fires `ON_PAUSE` / `ON_STOP` (app backgrounded).
 *   * The system broadcasts `ACTION_SCREEN_OFF` while any capture is live.
 *   * The user taps "Stop recording now" on the Privacy Shield dashboard.
 *
 * We never auto-restart on return — explicit user action re-opens the feature.
 *
 * Compose can observe `active` and `history` directly since both are
 * `SnapshotStateList`s (any modification notifies composable observers).
 */
object CaptureRegistry {

    enum class Kind { CAMERA, MICROPHONE }

    enum class Reason {
        BACKGROUND,            // ProcessLifecycleOwner ON_PAUSE / ON_STOP
        SCREEN_OFF,            // ACTION_SCREEN_OFF broadcast
        MANUAL_STOP_ALL,       // user tapped Stop recording now
        FEATURE_UNREGISTERED   // feature finished its session normally
    }

    class Handle internal constructor(
        val id: UUID,
        val feature: String,
        val kind: Kind,
        val registeredAtMs: Long,
        internal val stop: () -> Unit
    )

    data class Event(
        val id: UUID = UUID.randomUUID(),
        val timestampMs: Long = System.currentTimeMillis(),
        val feature: String,
        val kind: Kind,
        val reason: Reason
    )

    private const val HISTORY_CAP = 10

    /** Live handles. Compose observes this directly. Reads safe from any thread; mutation on main. */
    val active: SnapshotStateList<Handle> = mutableStateListOf()
    /** Teardown history, newest first, bounded to [HISTORY_CAP]. */
    val history: SnapshotStateList<Event> = mutableStateListOf()

    private var screenOffReceiver: BroadcastReceiver? = null
    private val screenReceiverRegistered = AtomicBoolean(false)
    private var observerAttached = AtomicBoolean(false)
    @Volatile private var autoStopEnabledSnapshot: Boolean = true

    // ---------------------------------------------------------------- Feature API

    fun register(
        feature: String,
        kind: Kind,
        stop: () -> Unit
    ): Handle {
        val h = Handle(
            id = UUID.randomUUID(),
            feature = feature,
            kind = kind,
            registeredAtMs = System.currentTimeMillis(),
            stop = stop
        )
        active.add(h)
        return h
    }

    /** Normal unregister path: the feature finished on its own. Logs an event, no `stop` call. */
    fun unregister(handle: Handle) {
        val removed = active.removeAll { it.id == handle.id }
        if (removed) {
            appendEvent(
                Event(
                    feature = handle.feature,
                    kind = handle.kind,
                    reason = Reason.FEATURE_UNREGISTERED
                )
            )
        }
    }

    // ---------------------------------------------------------------- Teardown

    /**
     * Walk every live handle, call `stop()`, drain `active`, and append events. Honours the
     * user's "Enable auto-stop" preference — if disabled, [reason] [Reason.BACKGROUND] and
     * [Reason.SCREEN_OFF] are no-ops. User-driven [Reason.MANUAL_STOP_ALL] always runs.
     *
     * @return the number of sessions actually stopped.
     */
    fun stopAll(reason: Reason): Int {
        val isUserInitiated = reason == Reason.MANUAL_STOP_ALL || reason == Reason.FEATURE_UNREGISTERED
        if (!isUserInitiated && !autoStopEnabledSnapshot) return 0
        if (active.isEmpty()) return 0

        val snapshot = active.toList()
        active.clear()
        snapshot.forEach { h ->
            // `stop` closures may throw if a feature's capture is already torn down; swallow
            // so one broken feature doesn't block the rest.
            runCatching { h.stop() }
            appendEvent(
                Event(
                    feature = h.feature,
                    kind = h.kind,
                    reason = reason
                )
            )
        }
        return snapshot.size
    }

    // ---------------------------------------------------------------- Lifecycle wiring

    /**
     * Called once from [com.onlytrusted.app.OnlyTrustedApp.onCreate]. Attaches the
     * app-wide lifecycle observer and the screen-off receiver so teardown fires without
     * any UI being present.
     */
    fun attach(context: Context) {
        if (!observerAttached.compareAndSet(false, true)) return
        autoStopEnabledSnapshot = PrivacyShieldState.isAutoStopEnabled(context)
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onPause(owner: LifecycleOwner) {
                // Refresh the snapshot in case the user toggled the preference mid-session.
                autoStopEnabledSnapshot = PrivacyShieldState.isAutoStopEnabled(context)
                stopAll(Reason.BACKGROUND)
            }
        })
        // Register the SCREEN_OFF receiver lazily on first capture (see [maybeArmScreenOffReceiver]).
    }

    /**
     * Register the `ACTION_SCREEN_OFF` receiver if (a) at least one capture is live and
     * (b) it's not already registered. Safe to call repeatedly — guarded by [screenReceiverRegistered].
     * Called by features right after they `register()`.
     */
    fun maybeArmScreenOffReceiver(context: Context) {
        if (!screenReceiverRegistered.compareAndSet(false, true)) return
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, intent: Intent) {
                if (intent.action == Intent.ACTION_SCREEN_OFF) {
                    autoStopEnabledSnapshot = PrivacyShieldState.isAutoStopEnabled(c)
                    stopAll(Reason.SCREEN_OFF)
                }
            }
        }
        // Receiver lives for the lifetime of the app process — stable, no leak concerns.
        context.applicationContext.registerReceiver(
            receiver,
            IntentFilter(Intent.ACTION_SCREEN_OFF)
        )
        screenOffReceiver = receiver
    }

    private fun appendEvent(ev: Event) {
        history.add(0, ev)
        while (history.size > HISTORY_CAP) history.removeAt(history.size - 1)
    }
}
