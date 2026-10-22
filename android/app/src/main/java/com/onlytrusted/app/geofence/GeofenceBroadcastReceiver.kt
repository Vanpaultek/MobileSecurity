// SPDX-FileCopyrightText: 2026 VanPaulTek Inc
// SPDX-License-Identifier: AGPL-3.0-or-later
package com.onlytrusted.app.geofence

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingEvent
import com.google.android.gms.location.LocationServices
import com.onlytrusted.app.MainActivity
import com.onlytrusted.app.OnlyTrustedApp
import com.onlytrusted.app.data.db.entities.SecurityEvent
import com.onlytrusted.app.security.AppLockManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Receives geofence transition events from Play Services. We only register for the EXIT
 * transition, so this fires exactly when one of the user's trusted regions is left.
 *
 * The decision tree:
 *   1. If any *other* trusted region currently contains our last known location, the user
 *      is still inside a trusted zone — swallow the event (common home→office commute).
 *   2. Otherwise, auto-lock the app via [AppLockManager], post a notification, send the
 *      silent SMS (if enabled), and log a [SecurityEvent.TYPE_GEOFENCE_EXIT] event.
 */
class GeofenceBroadcastReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val event = GeofencingEvent.fromIntent(intent) ?: return
        if (event.hasError()) return
        if (event.geofenceTransition != Geofence.GEOFENCE_TRANSITION_EXIT) return
        if (!GeofenceState.isEnabled(context)) return

        val triggeringIds = event.triggeringGeofences?.map { it.requestId }?.toSet().orEmpty()
        if (triggeringIds.isEmpty()) return

        // Hop into a coroutine so we can asynchronously confirm the "any other trusted
        // region" check and perform the SecurityRepository.log suspend call. BroadcastReceiver
        // allows up to 10s before the system ANRs us; the location fetch + DB write are
        // well under that budget.
        val pendingResult = goAsync()
        val scope = CoroutineScope(Dispatchers.Default)
        scope.launch {
            try {
                processExit(context, triggeringIds, event.triggeringLocation)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private suspend fun processExit(
        context: Context,
        triggeredIds: Set<String>,
        triggeringLocation: Location?
    ) {
        val fences = GeofenceState.geofences(context)
        val fallback = triggeringLocation ?: lastKnownLocation(context)

        // Any other trusted region currently containing our position means we're still
        // inside a trusted area and should NOT lock.
        val stillInsideSomeOtherFence = fallback?.let { loc ->
            fences.any { fence ->
                fence.id !in triggeredIds && distanceMeters(
                    loc.latitude, loc.longitude, fence.latitude, fence.longitude
                ) <= fence.radiusMeters
            }
        } ?: false
        if (stillInsideSomeOtherFence) return

        triggerExitAlert(context, fallback)
    }

    private suspend fun triggerExitAlert(context: Context, location: Location?) {
        // 1. Audit log.
        val repo = (context.applicationContext as? OnlyTrustedApp)?.repository
        val detail = location?.let {
            "exit all trusted zones @ ${"%.5f".format(it.latitude)},${"%.5f".format(it.longitude)}"
        } ?: "exit all trusted zones (no fix)"
        repo?.log(type = SecurityEvent.TYPE_GEOFENCE_EXIT, detail = detail)

        // 2. Lock the app — the next time the user foregrounds OnlyTrusted, AppLockGate
        // sees `shouldPrompt == true` and demands biometric reauth.
        AppLockManager.resetUnlock(context)

        // 3. Local notification. Tapping it launches the main activity, which will hit the
        // lock gate.
        postNotification(context)

        // 4. Silent SMS. Pulls the alert contact out of the vault; honors the share-location
        // toggle. SMS failures are swallowed — we don't want to surface a crash from a
        // broadcast receiver.
        if (GeofenceState.smsAlertEnabled(context)) {
            val contactId = GeofenceState.alertContactId(context)
            val repoCts = (context.applicationContext as? OnlyTrustedApp)?.trustedContacts
            if (contactId != null && repoCts != null) {
                val record = repoCts.loadByIdSafe(contactId)
                val number = record?.numbers?.firstOrNull()?.normalisedNumber
                if (!number.isNullOrBlank()) {
                    val body = GeofenceSmsSender.buildBody(
                        ownerName = GeofenceState.ownerName(context),
                        location = if (GeofenceState.shareLocationInAlert(context)) location else null
                    )
                    GeofenceSmsSender.send(context, number, body)
                }
            }
        }
    }

    private fun postNotification(context: Context) {
        val notifMgr = context.getSystemService(NotificationManager::class.java) ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            notifMgr.getNotificationChannel(CHANNEL_ID) == null
        ) {
            notifMgr.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    "Geo-fence exit alerts",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Fires when the device leaves every trusted location."
                    enableVibration(true)
                }
            )
        }

        val launchIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pending = PendingIntent.getActivity(
            context,
            NOTIF_REQUEST,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_IMMUTABLE else 0)
        )

        val notif = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle("Device left trusted area")
            .setContentText("OnlyTrusted locked the app and sent an alert because the device left every trusted location.")
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pending)
            .build()

        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
            == PackageManager.PERMISSION_GRANTED
        ) {
            notifMgr.notify(NOTIF_ID, notif)
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun lastKnownLocation(context: Context): Location? {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) return null
        val client: FusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(context)
        return suspendCancellableCoroutine { cont ->
            runCatching {
                client.lastLocation
                    .addOnSuccessListener { cont.resume(it) }
                    .addOnFailureListener { cont.resume(null) }
            }.onFailure { cont.resume(null) }
        }
    }

    private fun distanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Float {
        val out = FloatArray(1)
        Location.distanceBetween(lat1, lon1, lat2, lon2, out)
        return out[0]
    }

    companion object {
        private const val CHANNEL_ID = "geofence_exit_alerts"
        private const val NOTIF_ID = 7311
        private const val NOTIF_REQUEST = 7312
    }
}

/**
 * Tiny suspending adapter around [com.onlytrusted.app.data.repository.TrustedContactsRepository.reveal]
 * so broadcast-receiver code can read a contact's phone numbers without touching the full
 * repository API surface.
 */
private suspend fun com.onlytrusted.app.data.repository.TrustedContactsRepository.loadByIdSafe(
    id: Long
): com.onlytrusted.app.data.repository.RevealedContact? =
    runCatching { reveal(id) }.getOrNull()
