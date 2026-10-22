// SPDX-FileCopyrightText: 2026 VanPaulTek Inc
// SPDX-License-Identifier: AGPL-3.0-or-later
package com.onlytrusted.app.privacy

import android.content.Context
import android.os.Build

/**
 * Reads Android's system "sensor privacy" state (the mic / camera off toggles) via reflection.
 *
 * The platform API `SensorPrivacyManager.isSensorPrivacyEnabled(sensor)` is restricted to
 * system apps; third-party apps cannot call it. We can, however, call
 * `getSystemService("sensor_privacy")` and reflectively read the per-sensor state on devices
 * where the manifest permits it. On every other device we fall back to `null`, which the UI
 * treats as "unknown" and shows a help card instead.
 */
object SensorPrivacyStatus {

    const val SENSOR_MIC = 1
    const val SENSOR_CAMERA = 2

    data class Snapshot(val micMuted: Boolean?, val cameraMuted: Boolean?) {
        val anyMuted: Boolean get() = micMuted == true || cameraMuted == true
        val known: Boolean get() = micMuted != null || cameraMuted != null
    }

    fun read(ctx: Context): Snapshot {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            return Snapshot(null, null)
        }
        return Snapshot(
            micMuted = readSensorPrivacy(ctx, SENSOR_MIC),
            cameraMuted = readSensorPrivacy(ctx, SENSOR_CAMERA)
        )
    }

    private fun readSensorPrivacy(ctx: Context, sensor: Int): Boolean? {
        return runCatching {
            val manager = ctx.getSystemService("sensor_privacy") ?: return null
            val cls = manager.javaClass
            val method = cls.methods.firstOrNull {
                it.name == "isSensorPrivacyEnabled" && it.parameterTypes.size == 1
            } ?: return null
            method.invoke(manager, sensor) as? Boolean
        }.getOrNull()
    }
}
