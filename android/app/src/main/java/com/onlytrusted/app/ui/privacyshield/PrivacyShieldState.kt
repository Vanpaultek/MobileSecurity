// SPDX-FileCopyrightText: 2026 VanPaulTek Inc
// SPDX-License-Identifier: AGPL-3.0-or-later
package com.onlytrusted.app.ui.privacyshield

import android.content.Context
import android.hardware.SensorPrivacyManager
import android.os.Build

/**
 * Preferences + system-sensor-toggle probes for the Privacy Shield.
 *
 * Two user-tunable prefs (both default ON):
 *   * `autoStopEnabled` — gates [CaptureRegistry.stopAll] for background/screen-off events.
 *   * `requireManualRestart` — informational only in Phase 1 (we never auto-restart).
 *
 * One read-only probe:
 *   * [supportsSystemSensorToggles] — Android 12+ exposes `SensorPrivacyManager.supportsSensorToggle`
 *     which is callable from a consumer app. The current *state* of the toggle requires
 *     `OBSERVE_SENSOR_PRIVACY` (privileged) so we deliberately do not read it. The
 *     dashboard copy acknowledges this limit.
 */
object PrivacyShieldState {

    private const val PREFS = "privacyshield.prefs"
    private const val KEY_AUTO_STOP = "autoStopEnabled"
    private const val KEY_MANUAL_RESTART = "requireManualRestart"

    fun isAutoStopEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_AUTO_STOP, /* default */ true)

    fun setAutoStopEnabled(context: Context, value: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_AUTO_STOP, value).apply()
    }

    fun isRequireManualRestart(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_MANUAL_RESTART, /* default */ true)

    fun setRequireManualRestart(context: Context, value: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_MANUAL_RESTART, value).apply()
    }

    /**
     * On Android 12+ (API 31) `SensorPrivacyManager` can tell us whether the device
     * exposes a system-wide mic + camera toggle. Pre-S devices always report `false`.
     * We deliberately avoid [SensorPrivacyManager.isSensorPrivacyEnabled] which requires
     * `OBSERVE_SENSOR_PRIVACY` (not grantable to consumer apps).
     */
    data class SensorToggleSupport(
        val micSupported: Boolean,
        val cameraSupported: Boolean
    ) {
        val anySupported: Boolean get() = micSupported || cameraSupported
    }

    fun supportsSystemSensorToggles(context: Context): SensorToggleSupport {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            return SensorToggleSupport(micSupported = false, cameraSupported = false)
        }
        return runCatching {
            val svc = context.getSystemService(SensorPrivacyManager::class.java)
                ?: return@runCatching SensorToggleSupport(false, false)
            SensorToggleSupport(
                micSupported = svc.supportsSensorToggle(SensorPrivacyManager.Sensors.MICROPHONE),
                cameraSupported = svc.supportsSensorToggle(SensorPrivacyManager.Sensors.CAMERA)
            )
        }.getOrElse { SensorToggleSupport(false, false) }
    }
}
