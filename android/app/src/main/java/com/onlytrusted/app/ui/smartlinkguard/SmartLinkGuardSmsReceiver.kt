// SPDX-FileCopyrightText: 2026 VanPaulTek Inc
// SPDX-License-Identifier: AGPL-3.0-or-later
package com.onlytrusted.app.ui.smartlinkguard

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import com.onlytrusted.app.ui.subscription.SubscriptionState
import com.onlytrusted.app.util.NotificationHelper

/**
 * Smart Link Guard — SMS link scanner (Pro).
 *
 * Registered against `android.provider.Telephony.SMS_RECEIVED` in AndroidManifest.xml.
 * We no-op aggressively when:
 *   • the user is not on Pro (`SubscriptionState.hasHighPremium`), or
 *   • the user disabled the Smart Link Guard SMS toggle, or
 *   • the SMS body contains no URL-shaped substring.
 *
 * When a URL *is* present we run [SmartLinkGuardRules.inspect] on each candidate and,
 * if any verdict is ≥ Caution, post a system notification telling the owner which link
 * was flagged. Tapping it opens the main app on the Smart Link Guard screen (handled by
 * MainActivity reading the "open_smartlinkguard" extra on resume — this receiver only
 * surfaces the alert).
 *
 * This is additive to [com.onlytrusted.app.services.SmsReceiver] (which handles
 * trusted-only / blocklist filtering) — both receivers can fire for the same SMS and
 * they do not interfere with each other.
 */
class SmartLinkGuardSmsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        // Fast opt-outs — don't inspect anything until we know the owner is a paying,
        // opted-in Pro user. Both reads are SharedPreferences-cheap and bounded.
        if (!SubscriptionState.hasHighPremium(context)) return
        if (!SmartLinkGuardPrefs.isSmsScanEnabled(context)) return

        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return
        val sender = messages.firstOrNull()?.originatingAddress ?: "unknown sender"
        val body = messages.joinToString(separator = "") { it.messageBody.orEmpty() }
        if (body.isBlank()) return

        val urls = SmartLinkGuardRules.extractUrls(body)
        if (urls.isEmpty()) return

        val worst = urls
            .map { SmartLinkGuardRules.inspect(it) }
            .maxByOrNull { it.verdict.ordinal } ?: return
        if (worst.verdict == URLVerdict.Safe) return

        // Persist for the recent-scans list so the owner can review inside the app.
        SmartLinkGuardPrefs.addScan(
            context,
            RecentScan(
                timestampMillis = System.currentTimeMillis(),
                url = worst.url,
                verdict = worst.verdict,
                source = RecentScan.Source.SMS,
                topReason = worst.reasons.firstOrNull() ?: ""
            )
        )

        val reasonSummary = worst.reasons.firstOrNull() ?: worst.badgeText
        NotificationHelper.postAlert(
            context = context,
            title = "Link in SMS from $sender flagged",
            body = "${worst.badgeText}: $reasonSummary Tap to review in OnlyTrusted.",
            tag = "slg-${sender}-${System.currentTimeMillis()}"
        )
    }
}

