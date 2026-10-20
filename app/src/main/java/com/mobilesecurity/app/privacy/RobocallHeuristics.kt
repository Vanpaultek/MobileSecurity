/*
 * Copyright (C) 2026 57Million LLC
 *
 * This file is part of MobileSecurity.
 *
 * MobileSecurity is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * MobileSecurity is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */
package com.mobilesecurity.app.privacy

/**
 * Local, deterministic scoring for incoming call risk.
 *
 * No network, no cloud, no third-party reputation DB. The score is a sum of weighted signals
 * each of which can also be explained in plain language — the UI shows both the score and the
 * reasons so the owner can see *why* a call was flagged.
 */
object RobocallHeuristics {

    data class Signal(val weight: Int, val reason: String)

    data class Verdict(
        val score: Int,
        val signals: List<Signal>,
        val recommendation: Recommendation
    ) {
        val explanation: String = signals.joinToString(separator = "\n") { "• ${it.reason}" }
    }

    enum class Recommendation { ALLOW, WARN, SILENCE }

    /**
     * @param rawNumber the incoming number as the dialer reports it.
     * @param ownerNumber the owner's own number, if known. Used for neighbor-spoofing detection.
     * @param recentCallers the last ~24 h of inbound numbers (newest first). Used for storm detection.
     */
    fun evaluate(
        rawNumber: String,
        ownerNumber: String? = null,
        recentCallers: List<String> = emptyList(),
        insight: CallerInsight.Insight = CallerInsight.analyse(rawNumber)
    ): Verdict {
        val signals = mutableListOf<Signal>()

        when (insight.category) {
            CallerInsight.Category.PREMIUM_RATE ->
                signals += Signal(70, "Premium-rate number (900/976) — classic scam call-back trap.")
            CallerInsight.Category.SHORT_CODE ->
                signals += Signal(10, "Short code — usually service SMS, rarely abusive.")
            CallerInsight.Category.INTERNATIONAL -> {
                signals += Signal(15, "International call from ${insight.country ?: "unknown country"}.")
                if (RISK_COUNTRIES.contains(insight.country)) {
                    signals += Signal(30,
                        "Country commonly used for scam call-outs; verify before engaging.")
                }
            }
            CallerInsight.Category.UNKNOWN ->
                signals += Signal(40, "Number does not match any known format — likely spoofed.")
            CallerInsight.Category.OWNER_FAMILY,
            CallerInsight.Category.TOLL_FREE,
            CallerInsight.Category.LIKELY_LOCAL -> Unit
        }

        // Neighbour spoofing: incoming shares the owner's first 6 digits.
        if (ownerNumber != null) {
            val ownerDigits = PhoneUtils.normalise(ownerNumber).removePrefix("+")
            val incomingDigits = insight.normalised.removePrefix("+")
            if (ownerDigits.length >= 6 && incomingDigits.length >= 6 &&
                ownerDigits.take(6) == incomingDigits.take(6) &&
                ownerDigits != incomingDigits
            ) {
                signals += Signal(55,
                    "Neighbour-spoofing: shares your first 6 digits — favourite trick to look local.")
            }
        }

        // Call storm: more than 3 unique callers share the first 7 digits in the recent window.
        if (recentCallers.isNotEmpty()) {
            val prefix = insight.normalised.removePrefix("+").take(7)
            if (prefix.length == 7) {
                val stormCount = recentCallers
                    .map { PhoneUtils.normalise(it).removePrefix("+") }
                    .filter { it.take(7) == prefix }
                    .distinct()
                    .size
                if (stormCount >= 3) {
                    signals += Signal(50,
                        "Call storm: $stormCount different numbers from this prefix recently.")
                }
            }
        }

        // Unusual length for the country code resolved.
        val digits = insight.normalised.removePrefix("+")
        if (digits.length !in 7..15) {
            signals += Signal(25, "Number length (${digits.length}) is outside E.164 range (7–15).")
        }

        // Letters or non-standard characters.
        if (rawNumber.any { !it.isDigit() && it != '+' && it != ' ' && it != '-' && it != '(' && it != ')' }) {
            signals += Signal(20, "Number contains unusual characters.")
        }

        val score = signals.sumOf { it.weight }
        val recommendation = when {
            score >= 80 -> Recommendation.SILENCE
            score >= 40 -> Recommendation.WARN
            else -> Recommendation.ALLOW
        }
        return Verdict(score, signals, recommendation)
    }

    private val RISK_COUNTRIES = setOf(
        // These are countries from which large volumes of scam calls are regularly observed by
        // telecoms worldwide. They are flagged, never blocked outright — many legitimate calls
        // originate from them too.
        "Nigeria", "India", "Pakistan", "Philippines"
    )
}
