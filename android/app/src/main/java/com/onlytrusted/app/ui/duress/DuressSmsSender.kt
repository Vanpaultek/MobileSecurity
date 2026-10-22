// SPDX-FileCopyrightText: 2026 VanPaulTek Inc
// SPDX-License-Identifier: AGPL-3.0-or-later
package com.onlytrusted.app.ui.duress

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.telephony.SmsManager
import androidx.core.content.ContextCompat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Silent SOS SMS send for Duress Mode.
 *
 * On Android `SmsManager.sendTextMessage` is a true unattended send — no composer, no user
 * interaction, no visible toast if we keep the sender quiet. This is why Duress Mode on
 * Android requests the `SEND_SMS` runtime permission at the first duress trigger.
 *
 * Any send failure (missing permission, airplane mode, no SIM, dual-SIM edge case) is
 * swallowed intentionally — the coercer must not learn that the duress unlock did anything
 * out of the ordinary. The owner can tell afterwards by checking Messages' outbox.
 */
object DuressSmsSender {

    fun hasSendSmsPermission(ctx: Context): Boolean =
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.SEND_SMS) ==
            PackageManager.PERMISSION_GRANTED

    fun buildBody(ownerName: String, at: Date = Date()): String {
        val name = ownerName.ifBlank { "an OnlyTrusted user" }
        val stamp = SimpleDateFormat("MMM d, yyyy, h:mm a", Locale.US).format(at)
        return "⚠️ OnlyTrusted DURESS ALERT from $name: duress unlock triggered at " +
            "$stamp. If you weren't expecting this, call them or 911."
    }

    /** Returns true if the send attempt was dispatched. False means we couldn't try at all. */
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
            // `divideMessage` handles >160 char bodies on cellular SMS fallback.
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
