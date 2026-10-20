// SPDX-FileCopyrightText: 2026 57Million LLC
// SPDX-License-Identifier: AGPL-3.0-or-later
package com.onlytrusted.app.services

import android.telecom.Call
import android.telecom.CallScreeningService
import com.onlytrusted.app.OnlyTrustedApp
import com.onlytrusted.app.data.db.entities.SecurityEvent
import com.onlytrusted.app.privacy.CallerInsight
import com.onlytrusted.app.privacy.ProtectionSettings
import com.onlytrusted.app.privacy.RobocallHeuristics
import com.onlytrusted.app.util.NotificationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class CallBlockerService : CallScreeningService() {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }

    override fun onScreenCall(callDetails: Call.Details) {
        val app = application as? OnlyTrustedApp
        val raw = callDetails.handle?.schemeSpecificPart

        if (app == null || raw.isNullOrBlank()) {
            respondWith(callDetails, block = false)
            return
        }

        val trustedOnly = ProtectionSettings.trustedOnlyCalls(this)
        val silenceUntrusted = ProtectionSettings.silenceUntrustedCalls(this)
        val heuristicSilenceEnabled = ProtectionSettings.heuristicSilence(this)
        val ownerNumber = ProtectionSettings.ownerNumber(this)

        // Synchronous DAO reads — safe on the binder thread, no runBlocking.
        val isBlocked = app.repository.isCallBlockedSync(raw)
        val isTrusted = app.trustedContacts.isTrustedSync(raw)

        val insight = CallerInsight.analyse(raw, isTrusted = isTrusted)
        val verdict = RobocallHeuristics.evaluate(
            rawNumber = raw,
            ownerNumber = ownerNumber,
            recentCallers = emptyList(),
            insight = insight
        )

        val heuristicShouldSilence = heuristicSilenceEnabled && !isTrusted &&
            verdict.recommendation == RobocallHeuristics.Recommendation.SILENCE

        val block = when {
            isTrusted -> false
            isBlocked -> true
            trustedOnly && silenceUntrusted -> true
            heuristicShouldSilence -> true
            else -> false
        }

        respondWith(callDetails, block = block)

        // Side-effects (DB write + notification) do not need to block the response.
        scope.launch {
            when {
                isBlocked -> {
                    app.repository.log(
                        type = SecurityEvent.TYPE_CALL_BLOCKED,
                        detail = "from=$raw reason=blocklist"
                    )
                    NotificationHelper.postAlert(
                        context = this@CallBlockerService,
                        title = getString(com.onlytrusted.app.R.string.alert_call_blocked_title),
                        body = getString(com.onlytrusted.app.R.string.alert_call_blocked_body, raw),
                        tag = "call-$raw"
                    )
                }
                heuristicShouldSilence -> {
                    app.repository.log(
                        type = SecurityEvent.TYPE_CALL_BLOCKED,
                        detail = "from=$raw reason=heuristic score=${verdict.score} " +
                            "signals=${verdict.signals.joinToString(";") { it.reason }}"
                    )
                    NotificationHelper.postAlert(
                        context = this@CallBlockerService,
                        title = getString(com.onlytrusted.app.R.string.alert_robocall_title),
                        body = buildString {
                            appendLine(raw)
                            append(verdict.explanation)
                        },
                        tag = "robo-$raw"
                    )
                }
                trustedOnly && !isTrusted -> {
                    app.repository.log(
                        type = SecurityEvent.TYPE_UNTRUSTED_CALL,
                        detail = "from=$raw silenced=$silenceUntrusted score=${verdict.score}"
                    )
                    NotificationHelper.postAlert(
                        context = this@CallBlockerService,
                        title = getString(com.onlytrusted.app.R.string.alert_untrusted_call_title),
                        body = getString(
                            com.onlytrusted.app.R.string.alert_untrusted_call_body, raw
                        ),
                        tag = "untrusted-$raw"
                    )
                }
                verdict.recommendation == RobocallHeuristics.Recommendation.WARN -> {
                    app.repository.log(
                        type = SecurityEvent.TYPE_CALL_ALLOWED,
                        detail = "from=$raw warn score=${verdict.score}"
                    )
                    NotificationHelper.postAlert(
                        context = this@CallBlockerService,
                        title = getString(com.onlytrusted.app.R.string.alert_robocall_warn_title),
                        body = buildString {
                            appendLine(raw)
                            append(verdict.explanation)
                        },
                        tag = "roboinfo-$raw"
                    )
                }
                else -> {
                    val name = app.trustedContacts.ownerNameFor(raw)
                    app.repository.log(
                        type = SecurityEvent.TYPE_CALL_ALLOWED,
                        detail = buildString {
                            append("from=$raw")
                            if (name != null) append(" trusted=$name")
                            append(" score=${verdict.score}")
                        }
                    )
                }
            }
        }
    }

    private fun respondWith(details: Call.Details, block: Boolean) {
        val response = CallResponse.Builder()
            .setDisallowCall(block)
            .setRejectCall(block)
            .setSilenceCall(block)
            .setSkipCallLog(false)
            .setSkipNotification(block)
            .build()
        respondToCall(details, response)
    }
}
