// SPDX-FileCopyrightText: 2026 57Million LLC
// SPDX-License-Identifier: AGPL-3.0-or-later
package com.mobilesecurity.app.privacy

object PhoneUtils {

    /** Characters a phone-number input is allowed to contain. */
    private const val NUMBER_INPUT_ALLOWED = "+0123456789 -()"
    private const val NUMBER_INPUT_MAX_LENGTH = 24

    /**
     * Sanitises raw text pasted or typed into a phone-number field. Strips every character we
     * do not want (letters, zero-width, emoji, shell metachars that leak through adb) and caps
     * length so a user cannot wedge the UI with a 100 KB blob.
     */
    fun sanitiseNumberInput(raw: String): String =
        raw.filter { it in NUMBER_INPUT_ALLOWED }.take(NUMBER_INPUT_MAX_LENGTH)

    /** Owner-tag field: keep it to printable ASCII, cap short. */
    fun sanitiseOwnerTag(raw: String): String =
        raw.filter { it in ' '..'~' }.take(24)


    /** Reduces a dialled or received phone number to a canonical form for matching. */
    fun normalise(raw: String): String =
        raw.trim()
            .replace(Regex("[^+\\d]"), "")
            .let { if (it.startsWith("+")) "+" + it.drop(1).trimStart('0') else it.trimStart('0') }

    /** Masks a number for the UI: keep the first 2 and last 2 digits. */
    fun mask(raw: String): String {
        val digits = raw.filter { it.isDigit() || it == '+' }
        if (digits.length <= 4) return "••••"
        val keepFront = digits.take(if (digits.startsWith("+")) 3 else 2)
        val keepBack = digits.takeLast(2)
        val hiddenCount = (digits.length - keepFront.length - keepBack.length).coerceAtLeast(2)
        return keepFront + "•".repeat(hiddenCount) + keepBack
    }

    /** Masks an email for the UI, keeping first + last char of local part. */
    fun maskEmail(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        val at = raw.indexOf('@').takeIf { it > 0 } ?: return "••••"
        val local = raw.substring(0, at)
        val domain = raw.substring(at)
        if (local.length <= 2) return "••$domain"
        return local.first() + "•".repeat(local.length - 2) + local.last() + domain
    }
}
