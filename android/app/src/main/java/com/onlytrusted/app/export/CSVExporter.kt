// SPDX-FileCopyrightText: 2026 VanPaulTek Inc
// SPDX-License-Identifier: AGPL-3.0-or-later
package com.onlytrusted.app.export

import android.content.Context
import android.net.Uri
import com.onlytrusted.app.OnlyTrustedApp
import com.onlytrusted.app.data.db.entities.BlockedNumber
import com.onlytrusted.app.data.db.entities.SecurityEvent
import com.onlytrusted.app.privacy.PhoneUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * CSV export of events + trusted contacts + blocked numbers (F6).
 *
 * Columns: Type, Timestamp, Number, Name, Label, Action, Notes.
 *
 * Numbers are MASKED. This file is user-visible — it may land in Drive, Gmail, or any
 * SAF location, which can be a cloud-synced folder. The JSON exporter already applies
 * the same mask policy.
 */
object CSVExporter {

    private val numberRegex = Regex("\\+?\\d{7,}")

    suspend fun exportTo(context: Context, destination: Uri): Int = withContext(Dispatchers.IO) {
        val app = context.applicationContext as OnlyTrustedApp
        val events = app.database.eventDao().recent(limit = 10_000).first()
        val blocked = app.database.blockedNumberDao().all().first()
        val contacts = app.trustedContacts.maskedList().first()

        val iso = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US)
        val rows = StringBuilder()

        rows.appendLine("Type,Timestamp,Number,Name,Label,Action,Notes")

        var total = 0

        for (contact in contacts) {
            if (contact.maskedNumbers.isEmpty()) {
                writeRow(rows, listOf(
                    "Trusted contact",
                    "",
                    "",
                    contact.displayName,
                    contact.relation.orEmpty(),
                    "trusted",
                    ""
                ))
                total++
            } else {
                for (masked in contact.maskedNumbers) {
                    writeRow(rows, listOf(
                        "Trusted contact",
                        "",
                        masked,
                        contact.displayName,
                        contact.relation.orEmpty(),
                        "trusted",
                        ""
                    ))
                    total++
                }
            }
        }

        for (b in blocked) {
            val actions = mutableListOf<String>()
            if (b.blockCalls) actions.add("calls")
            if (b.blockSms) actions.add("sms")
            writeRow(rows, listOf(
                "Blocked number",
                iso.format(Date(b.addedAt)),
                PhoneUtils.mask(b.number),
                "",
                b.label.orEmpty(),
                "block:" + actions.joinToString("+"),
                ""
            ))
            total++
        }

        for (e in events) {
            writeRow(rows, listOf(
                humanType(e.type),
                iso.format(Date(e.timestamp)),
                maskNumbersInDetail(e.detail.orEmpty()),
                "",
                "",
                humanAction(e.type),
                ""
            ))
            total++
        }

        context.contentResolver.openOutputStream(destination, "w")?.use { out ->
            out.write(rows.toString().toByteArray(Charsets.UTF_8))
        } ?: throw IllegalStateException("Could not open destination for writing.")

        total
    }

    private fun writeRow(sb: StringBuilder, fields: List<String>) {
        sb.appendLine(fields.joinToString(",") { escape(it) })
    }

    private fun escape(field: String): String {
        return if (field.contains(',') || field.contains('"') ||
            field.contains('\n') || field.contains('\r')) {
            "\"" + field.replace("\"", "\"\"") + "\""
        } else field
    }

    private fun maskNumbersInDetail(detail: String): String =
        numberRegex.replace(detail) { PhoneUtils.mask(it.value) }

    private fun humanType(type: String): String = when (type) {
        SecurityEvent.TYPE_CALL_BLOCKED -> "Blocked call"
        SecurityEvent.TYPE_CALL_ALLOWED -> "Allowed call"
        SecurityEvent.TYPE_SMS_FLAGGED -> "Blocked text"
        SecurityEvent.TYPE_UNTRUSTED_CALL -> "Blocked call (untrusted)"
        SecurityEvent.TYPE_UNTRUSTED_SMS -> "Blocked text (untrusted)"
        SecurityEvent.TYPE_PANIC_LOCK -> "Panic lock"
        SecurityEvent.TYPE_POCKET_LOCK -> "Pocket lock"
        SecurityEvent.TYPE_MIC -> "Mic in use"
        SecurityEvent.TYPE_CAMERA -> "Camera in use"
        else -> type
    }

    private fun humanAction(type: String): String = when (type) {
        SecurityEvent.TYPE_CALL_BLOCKED, SecurityEvent.TYPE_SMS_FLAGGED,
        SecurityEvent.TYPE_UNTRUSTED_CALL, SecurityEvent.TYPE_UNTRUSTED_SMS -> "blocked"
        SecurityEvent.TYPE_CALL_ALLOWED -> "allowed"
        SecurityEvent.TYPE_PANIC_LOCK, SecurityEvent.TYPE_POCKET_LOCK,
        SecurityEvent.TYPE_SCREEN_LOCKED -> "locked"
        SecurityEvent.TYPE_SCREEN_UNLOCKED -> "unlocked"
        else -> ""
    }

    @Suppress("UNUSED_PARAMETER")
    private fun BlockedNumber.ignored(): Int = 0  // keep import stable
}
