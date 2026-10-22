// SPDX-FileCopyrightText: 2026 VanPaulTek Inc
// SPDX-License-Identifier: AGPL-3.0-or-later
package com.onlytrusted.app.services

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import com.onlytrusted.app.OnlyTrustedApp
import com.onlytrusted.app.data.db.entities.SecurityEvent
import com.onlytrusted.app.privacy.ProtectionSettings
import com.onlytrusted.app.util.NotificationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class SmsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return
        val app = context.applicationContext as? OnlyTrustedApp ?: return

        val sender = messages.firstOrNull()?.originatingAddress ?: return
        val body = messages.joinToString(separator = "") { it.messageBody.orEmpty() }
        val preview = body.take(60)

        val trustedOnly = ProtectionSettings.trustedOnlySms(context)

        // goAsync() keeps the receiver alive until pending.finish() is called, so our launched
        // DB work isn't killed by the system. The scope lives only for this receive.
        val pending = goAsync()
        val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        scope.launch {
            try {
                val isBlocked = app.repository.isSmsBlocked(sender)
                val isTrusted = app.trustedContacts.isTrusted(sender)

                when {
                    isBlocked -> {
                        app.repository.log(
                            type = SecurityEvent.TYPE_SMS_FLAGGED,
                            detail = "from=$sender reason=blocklist preview=$preview"
                        )
                        NotificationHelper.postAlert(
                            context = context,
                            title = context.getString(com.onlytrusted.app.R.string.alert_sms_flagged_title),
                            body = context.getString(com.onlytrusted.app.R.string.alert_sms_flagged_body, sender),
                            tag = "sms-$sender"
                        )
                    }
                    trustedOnly && !isTrusted -> {
                        app.repository.log(
                            type = SecurityEvent.TYPE_UNTRUSTED_SMS,
                            detail = "from=$sender preview=$preview"
                        )
                        NotificationHelper.postAlert(
                            context = context,
                            title = context.getString(com.onlytrusted.app.R.string.alert_untrusted_sms_title),
                            body = context.getString(
                                com.onlytrusted.app.R.string.alert_untrusted_sms_body, sender
                            ),
                            tag = "untrusted-sms-$sender"
                        )
                    }
                }
            } finally {
                pending.finish()
            }
        }
    }
}
