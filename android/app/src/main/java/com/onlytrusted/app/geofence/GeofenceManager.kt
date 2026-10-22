// SPDX-FileCopyrightText: 2026 VanPaulTek Inc
// SPDX-License-Identifier: AGPL-3.0-or-later
package com.onlytrusted.app.geofence

import android.Manifest
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingClient
import com.google.android.gms.location.GeofencingRequest
import com.google.android.gms.location.LocationServices

/**
 * Elite-tier Geo-fenced Auto-Lock + Alert. Wraps [GeofencingClient] so the setup screen
 * doesn't have to reason about Play Services boilerplate.
 *
 * Transition model: we only ever register the EXIT transition. On exit, the system fires
 * an intent to [GeofenceBroadcastReceiver], which then checks whether the device is
 * *also* currently inside any other trusted region (home → office drive-through case)
 * before escalating to a lock + SMS.
 */
object GeofenceManager {

    private const val REQUEST_CODE = 7301
    private const val INTENT_ACTION = "com.onlytrusted.app.action.GEOFENCE_TRANSITION"

    private fun client(ctx: Context): GeofencingClient =
        LocationServices.getGeofencingClient(ctx.applicationContext)

    fun hasForegroundLocationPermission(ctx: Context): Boolean =
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    fun hasBackgroundLocationPermission(ctx: Context): Boolean {
        // ACCESS_BACKGROUND_LOCATION was introduced in Android 10 (29) — we minSdk 29, so
        // the runtime grant always matters.
        return ContextCompat.checkSelfPermission(
            ctx,
            Manifest.permission.ACCESS_BACKGROUND_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * The pending intent that GeofencingClient will fire into when any registered geofence
     * transition fires. We use FLAG_UPDATE_CURRENT so re-registration (new fence added)
     * reuses the same slot.
     */
    private fun transitionIntent(ctx: Context): PendingIntent {
        val intent = Intent(ctx.applicationContext, GeofenceBroadcastReceiver::class.java).apply {
            action = INTENT_ACTION
        }
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        return PendingIntent.getBroadcast(ctx.applicationContext, REQUEST_CODE, intent, flags)
    }

    /**
     * Registers every trusted geofence currently stored in [GeofenceState]. Returns true
     * if the request was dispatched; false if we couldn't call Play Services (missing
     * permission or empty list).
     *
     * `@SuppressLint("MissingPermission")` is scoped to the single `addGeofences` call —
     * we gate on [hasForegroundLocationPermission] above so Lint's warning is false.
     */
    @SuppressLint("MissingPermission")
    fun startMonitoring(ctx: Context): Boolean {
        val fences = GeofenceState.geofences(ctx)
        if (fences.isEmpty()) return false
        if (!hasForegroundLocationPermission(ctx)) return false

        val request = GeofencingRequest.Builder()
            // We don't fire on initial trigger — we only care about exits that happen
            // after the user has already defined "home base" through the setup screen.
            .setInitialTrigger(0)
            .addGeofences(
                fences.map { fence ->
                    Geofence.Builder()
                        .setRequestId(fence.id)
                        .setCircularRegion(fence.latitude, fence.longitude, fence.radiusMeters)
                        .setExpirationDuration(Geofence.NEVER_EXPIRE)
                        .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_EXIT)
                        .build()
                }
            )
            .build()

        return try {
            client(ctx).addGeofences(request, transitionIntent(ctx))
            true
        } catch (_: SecurityException) {
            // Thrown if the system revoked the location grant between our check and the
            // registration call. Swallow — the setup screen will nudge the user to re-grant.
            false
        } catch (_: Throwable) {
            false
        }
    }

    /** Removes every registered geofence from Play Services. Safe to call unconditionally. */
    fun stopMonitoring(ctx: Context) {
        runCatching { client(ctx).removeGeofences(transitionIntent(ctx)) }
    }

    /** Re-register in response to a geofence add/remove while monitoring is on. */
    fun resyncMonitoring(ctx: Context) {
        if (!GeofenceState.isEnabled(ctx)) return
        stopMonitoring(ctx)
        startMonitoring(ctx)
    }
}
