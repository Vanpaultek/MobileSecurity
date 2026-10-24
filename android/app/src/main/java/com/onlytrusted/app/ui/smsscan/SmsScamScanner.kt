// SPDX-FileCopyrightText: 2026 VanPaulTek Inc
// SPDX-License-Identifier: AGPL-3.0-or-later
package com.onlytrusted.app.ui.smsscan

import android.content.Context
import android.net.Uri
import android.provider.Telephony

/**
 * Scans the inbox of SMS messages against the bundled scam-numbers + phishing-URLs
 * feeds plus a small set of classic scam-phrase heuristics. 100% on-device; the inbox
 * is read once via ContentResolver on every scan, bodies and senders are examined in
 * memory, nothing is persisted beyond the aggregate report.
 */
object SmsScamScanner {

    data class ScamMatch(
        val sender: String,
        val bodyPreview: String,
        val reason: String,
        val timestamp: Long
    )

    data class SmsScanReport(
        val messagesScanned: Int,
        val sendersScanned: Int,
        val matches: List<ScamMatch>
    )

    // Classic scam phrase heuristics — case-insensitive substring matches on message
    // body. Deliberately broad; false positives are preferable to false negatives for
    // a user-triggered scan where the user reviews each hit.
    private val scamPhrases = listOf(
        "won a prize", "won ₹", "won \\$", "lottery winner", "claim your prize",
        "verify your account", "suspended your account", "unusual activity on your",
        "urgent action required", "click here to claim", "last chance",
        "irs refund", "tax refund pending", "parcel delivery pending", "parcel is held",
        "bank account will be blocked", "update your kyc", "aadhaar update required",
        "wire transfer", "western union", "crypto investment", "bitcoin opportunity",
        "guaranteed returns", "double your money"
    )

    fun scan(ctx: Context): SmsScanReport {
        val scamNumbers = loadScamNumbers(ctx)
        val phishingHosts = loadPhishingHosts(ctx)

        val matches = mutableListOf<ScamMatch>()
        val uniqueSenders = mutableSetOf<String>()
        var messagesScanned = 0

        val projection = arrayOf(
            Telephony.Sms.ADDRESS,
            Telephony.Sms.BODY,
            Telephony.Sms.DATE
        )
        val uri: Uri = Telephony.Sms.Inbox.CONTENT_URI
        ctx.contentResolver.query(uri, projection, null, null, "${Telephony.Sms.DATE} DESC")?.use { cursor ->
            while (cursor.moveToNext()) {
                messagesScanned++
                val sender = cursor.getString(0).orEmpty()
                val body = cursor.getString(1).orEmpty()
                val timestamp = cursor.getLong(2)
                uniqueSenders.add(sender)

                val normalisedSender = normaliseNumber(sender)
                val reasons = mutableListOf<String>()
                if (scamNumbers.contains(normalisedSender)) {
                    reasons.add("Sender in bundled scam-numbers list")
                }
                for (host in extractHosts(body)) {
                    if (phishingHosts.contains(host.lowercase())) {
                        reasons.add("Links to known phishing domain ($host)")
                        break
                    }
                }
                val bodyLower = body.lowercase()
                for (phrase in scamPhrases) {
                    if (bodyLower.contains(phrase)) {
                        reasons.add("Contains scam phrase: \"$phrase\"")
                        break
                    }
                }
                if (reasons.isNotEmpty()) {
                    matches.add(
                        ScamMatch(
                            sender = sender.ifBlank { "Unknown" },
                            bodyPreview = body.take(140),
                            reason = reasons.joinToString(" · "),
                            timestamp = timestamp
                        )
                    )
                }
            }
        }

        return SmsScanReport(
            messagesScanned = messagesScanned,
            sendersScanned = uniqueSenders.size,
            matches = matches
        )
    }

    private fun loadScamNumbers(ctx: Context): Set<String> =
        runCatching {
            ctx.assets.open("threatintel/scam_numbers.v1.txt").bufferedReader().use { reader ->
                reader.lineSequence()
                    .map { it.trim() }
                    .filter { it.isNotEmpty() && !it.startsWith("#") }
                    .map { normaliseNumber(it) }
                    .toSet()
            }
        }.getOrDefault(emptySet())

    private fun loadPhishingHosts(ctx: Context): Set<String> =
        runCatching {
            ctx.assets.open("threatintel/phishing_urls.v1.txt").bufferedReader().use { reader ->
                reader.lineSequence()
                    .map { it.trim().lowercase() }
                    .filter { it.isNotEmpty() && !it.startsWith("#") }
                    .map { extractHost(it) ?: it }
                    .toSet()
            }
        }.getOrDefault(emptySet())

    private fun normaliseNumber(raw: String): String {
        val digits = raw.filter(Char::isDigit)
        return when {
            digits.length == 10 -> "+1$digits"
            raw.startsWith("+") -> "+$digits"
            else -> "+$digits"
        }
    }

    /** Pull out every bare host from a message body (http(s)?:// or bare domain forms). */
    private fun extractHosts(body: String): List<String> {
        val results = mutableListOf<String>()
        // Rough tokeniser: split on whitespace, keep tokens that look like URLs or hosts.
        for (token in body.split(Regex("\\s+"))) {
            val cleaned = token.trimEnd('.', ',', ')', ']', '!', '?')
            if (cleaned.isEmpty()) continue
            val host = extractHost(cleaned)
            if (host != null) results.add(host)
        }
        return results
    }

    private fun extractHost(token: String): String? {
        val lower = token.lowercase()
        val stripped = lower
            .removePrefix("https://")
            .removePrefix("http://")
            .removePrefix("www.")
        if (!stripped.contains(".") || stripped.contains(" ")) return null
        val host = stripped.substringBefore("/").substringBefore("?").substringBefore("#")
        // Must look like a domain — at least two labels, no spaces, length > 3.
        if (host.length < 4 || host.count { it == '.' } < 1) return null
        return host
    }
}

/** Convenience alias so callers can reference the result type without the enclosing object. */
typealias SmsScanReport = SmsScamScanner.SmsScanReport
