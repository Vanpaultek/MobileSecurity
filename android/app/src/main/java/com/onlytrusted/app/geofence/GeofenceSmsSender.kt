// SPDX-FileCopyrightText: 2026 VanPaulTek Inc
// SPDX-License-Identifier: AGPL-3.0-or-later
package com.onlytrusted.app.geofence

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.os.Build
import android.telephony.SmsManager
import androidx.core.content.ContextCompat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Silent SMS send for Geo-fence Auto-Lock (Elite). Mirrors [com.onlytrusted.app.ui.duress.DuressSmsSender]
 * — we reuse the same SEND_SMS runtime permission so the user doesn't get prompted twice if
 * they already enabled Duress Mode.
 */
object GeofenceSmsSender {

    fun hasSendSmsPermission(ctx: Context): Boolean =
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.SEND_SMS) ==
            PackageManager.PERMISSION_GRANTED

    fun buildBody(
        ownerName: String,
        at: Date = Date(),
        location: Location? = null
    ): String {
        val name = ownerName.ifBlank { "an OnlyTrusted user" }
        val stamp = SimpleDateFormat("MMM d, yyyy, h:mm a", Locale.US).format(at)
        val base = "⚠️ OnlyTrusted alert: ${name}'s device left all trusted locations at $stamp."
        return if (location != null) {
            "$base Location: https://maps.google.com/?q=${location.latitude},${location.longitude}"
        } else {
            base
        }
    }

    /** Returns true if the send attempt was dispatched; false means permission or SMS stack unavailable. */
    fun send(ctx: Context, phoneNumber: String, body: String): Boolean {
        if (phoneNumber.isBlank() || body.isBlank()) return false
        if (!hasSendSmsPermission(ctx)) return false
        return try {
            val manager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                ctx.getSystemService(SmsManager::class.java)
            } else {
                @Suppress("DEPRECATION")
                SmsManager.getDefault()
            } ?: return false
            val parts = manager.divideMessage(body)
            if (parts.size == 1) {
                manager.sendTextMessage(phoneNumber, null, body, null, null)
            } else {
                manager.sendMultipartTextMessage(phoneNumber, null, parts, null, null)
            }
            true
        } catch (_: Throwable) {
            false
        }
    }
}
