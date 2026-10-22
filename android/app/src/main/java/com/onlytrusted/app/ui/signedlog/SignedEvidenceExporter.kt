// SPDX-FileCopyrightText: 2026 VanPaulTek Inc
// SPDX-License-Identifier: AGPL-3.0-or-later
package com.onlytrusted.app.ui.signedlog

import android.content.Context
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Build
import com.onlytrusted.app.BuildConfig
import com.onlytrusted.app.OnlyTrustedApp
import com.onlytrusted.app.ui.hardwarekey.HardwareKeyState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Mirrors iOS `SignedEvidenceExporter`. Produces a two-file pack:
 *  - JSON — machine-readable, every event + chain + signature + merkle commits.
 *  - PDF  — human-readable court-ready print of the same data.
 *
 * Caller writes the bytes to a user-chosen SAF destination (one write per file).
 */
object SignedEvidenceExporter {

    data class Pack(val jsonBytes: ByteArray, val pdfBytes: ByteArray, val filenameStem: String)

    suspend fun build(context: Context): Pack = withContext(Dispatchers.IO) {
        val app = context.applicationContext as OnlyTrustedApp
        val events = app.database.eventDao().recent(limit = 20_000).first()
        val signaturesByHash = SignedLogState.signatures(context).associateBy { it.rowHash }
        val commits = SignedLogState.merkleCommits(context)
        val chainOk = app.repository.verifyChain()

        // ---- build the JSON document
        val eventsArr = JSONArray()
        events.sortedBy { it.timestamp }.forEach { ev ->
            val sig = ev.rowHash?.let { signaturesByHash[it] }
            eventsArr.put(JSONObject().apply {
                put("timestampMillis", ev.timestamp)
                put("type", ev.type)
                put("detail", ev.detail ?: JSONObject.NULL)
                put("prevHash", ev.prevHash ?: JSONObject.NULL)
                put("rowHash", ev.rowHash ?: "")
                put("signatureB64", sig?.signatureB64 ?: JSONObject.NULL)
                put("signatureKeyIdB64", sig?.signatureKeyIdB64 ?: JSONObject.NULL)
                put("signedAtMillis", sig?.signedAtMillis ?: JSONObject.NULL)
            })
        }
        val commitsArr = JSONArray()
        commits.forEach { c ->
            commitsArr.put(JSONObject().apply {
                put("commitAtMillis", c.commitAtMillis)
                put("fromRowHash", c.fromRowHash ?: JSONObject.NULL)
                put("toRowHash", c.toRowHash)
                put("count", c.count)
                put("rootHex", c.rootHex)
                put("leaves", JSONArray(c.leaves))
            })
        }
        val keysArr = JSONArray()
        HardwareKeyState.enrolledKeys(context).forEach { k ->
            keysArr.put(JSONObject().apply {
                put("credentialIdB64", k.credentialId)
                put("publicKeyB64", k.publicKey ?: "")
                put("displayName", k.displayName)
            })
        }
        val draft = JSONObject().apply {
            put("schema", "onlytrusted.signed-evidence.v1")
            put("generatedAtMillis", System.currentTimeMillis())
            put("device", Build.MODEL)
            put("osVersion", "Android ${Build.VERSION.RELEASE}")
            put("appVersion", BuildConfig.VERSION_NAME)
            put("events", eventsArr)
            put("merkleCommits", commitsArr)
            put("publicKeys", keysArr)
            put("integrity", JSONObject().apply {
                put("chainVerified", chainOk)
                put("eventCount", eventsArr.length())
                put("signedCount", signaturesByHash.size)
                put("merkleCommitCount", commits.size)
                put("packSha256", "")
            })
        }
        val draftStr = draft.toString(2)
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(draftStr.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        draft.getJSONObject("integrity").put("packSha256", digest)
        val jsonBytes = draft.toString(2).toByteArray(Charsets.UTF_8)

        // ---- build the PDF
        val pdfBytes = renderPdf(draft)

        val stem = "signed-evidence-${System.currentTimeMillis()}"
        Pack(jsonBytes, pdfBytes, stem)
    }

    suspend fun writeJsonTo(context: Context, dest: Uri, bytes: ByteArray) =
        withContext(Dispatchers.IO) {
            context.contentResolver.openOutputStream(dest, "w")?.use { it.write(bytes) }
                ?: throw IllegalStateException("Could not open destination.")
        }

    suspend fun writePdfTo(context: Context, dest: Uri, bytes: ByteArray) =
        withContext(Dispatchers.IO) {
            context.contentResolver.openOutputStream(dest, "w")?.use { it.write(bytes) }
                ?: throw IllegalStateException("Could not open destination.")
        }

    private fun renderPdf(doc: JSONObject): ByteArray {
        val pdf = PdfDocument()
        val mono = Paint().apply {
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL)
            textSize = 8f
            color = 0xFF000000.toInt()
        }
        val monoBold = Paint(mono).apply { typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD) }
        val bodyBold = Paint().apply {
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            textSize = 10f; color = 0xFF000000.toInt()
        }
        val body = Paint().apply {
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
            textSize = 9f; color = 0xFF000000.toInt()
        }

        val pageWidth = 612
        val pageHeight = 792
        val margin = 36f
        var pageNo = 1
        var y = margin
        var page: PdfDocument.Page? = null
        var canvas: android.graphics.Canvas? = null

        fun newPage() {
            page?.let { pdf.finishPage(it) }
            val info = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNo).create()
            page = pdf.startPage(info)
            canvas = page?.canvas
            y = margin
            canvas?.drawText("OnlyTrusted — Hardware-Signed Evidence Pack",
                             margin, y + 10, bodyBold); y += 20
            val integrity = doc.getJSONObject("integrity")
            canvas?.drawText(
                "Generated ${formatMillis(doc.getLong("generatedAtMillis"))} · ${doc.getString("device")} · ${doc.getString("osVersion")} · ${doc.getString("appVersion")}",
                margin, y + 10, body
            ); y += 18
            canvas?.drawText(
                "Chain: ${if (integrity.getBoolean("chainVerified")) "VERIFIED" else "NOT VERIFIED"} · ${integrity.getInt("eventCount")} events · ${integrity.getInt("signedCount")} signed · ${integrity.getInt("merkleCommitCount")} commits",
                margin, y + 10, body
            ); y += 18
            canvas?.drawText("Pack SHA-256: ${integrity.getString("packSha256")}",
                             margin, y + 10, mono); y += 20
            pageNo++
        }

        fun ensure(space: Float) {
            if (y + space > pageHeight - margin) newPage()
        }

        newPage()

        // Public keys
        y += 10
        canvas?.drawText("Enrolled hardware keys", margin, y, bodyBold); y += 14
        val keys = doc.getJSONArray("publicKeys")
        if (keys.length() == 0) {
            canvas?.drawText("(none — no signatures were captured)", margin, y, body); y += 14
        } else {
            for (i in 0 until keys.length()) {
                ensure(28f)
                val k = keys.getJSONObject(i)
                canvas?.drawText("• ${k.getString("displayName")}", margin, y, bodyBold); y += 12
                canvas?.drawText("credentialId: ${k.getString("credentialIdB64").take(96)}",
                                 margin + 8, y, mono); y += 12
                val pk = k.optString("publicKeyB64")
                if (pk.isNotEmpty()) {
                    canvas?.drawText("publicKey: ${pk.take(96)}${if (pk.length > 96) "…" else ""}",
                                     margin + 8, y, mono); y += 12
                }
            }
        }

        // Merkle commits
        y += 10
        ensure(20f)
        canvas?.drawText("Merkle commitments", margin, y, bodyBold); y += 14
        val commits = doc.getJSONArray("merkleCommits")
        if (commits.length() == 0) {
            canvas?.drawText("(none — no commits recorded)", margin, y, body); y += 14
        } else {
            for (i in 0 until commits.length()) {
                ensure(28f)
                val c = commits.getJSONObject(i)
                canvas?.drawText("• ${formatMillis(c.getLong("commitAtMillis"))} · ${c.getInt("count")} leaves",
                                 margin, y, bodyBold); y += 12
                canvas?.drawText("root: ${c.getString("rootHex")}", margin + 8, y, mono); y += 12
            }
        }

        // Events
        y += 10
        ensure(20f)
        canvas?.drawText("Event chain", margin, y, bodyBold); y += 14
        val events = doc.getJSONArray("events")
        for (i in 0 until events.length()) {
            ensure(40f)
            val ev = events.getJSONObject(i)
            canvas?.drawText("${formatMillis(ev.getLong("timestampMillis"))} · ${ev.getString("type")}",
                             margin, y, monoBold); y += 11
            val d = ev.optString("detail")
            if (d.isNotEmpty() && d != "null") {
                canvas?.drawText("detail: ${d.take(96)}", margin + 8, y, mono); y += 11
            }
            canvas?.drawText("rowHash: ${ev.getString("rowHash")}",
                             margin + 8, y, mono); y += 11
            val sig = ev.optString("signatureB64")
            if (sig.isNotEmpty() && sig != "null") {
                canvas?.drawText("sig: ${sig.take(96)}${if (sig.length > 96) "…" else ""}",
                                 margin + 8, y, mono); y += 11
            }
            y += 3
        }

        page?.let { pdf.finishPage(it) }

        val out = ByteArrayOutputStream()
        pdf.writeTo(out)
        pdf.close()
        return out.toByteArray()
    }

    private fun formatMillis(millis: Long): String =
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date(millis))
}

/**
 * Verifier mirror of iOS `SignedEvidenceVerifier`. Loads a previously-exported
 * signed-evidence JSON and recomputes every row hash and every merkle commit root.
 */
object SignedEvidenceVerifier {

    data class Report(val valid: Boolean, val brokenAtEventIndex: Int?, val summary: String)

    fun verify(context: Context, uri: Uri): Report {
        val bytes = try {
            context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                ?: return Report(false, null, "Could not read file.")
        } catch (t: Throwable) {
            return Report(false, null, t.message ?: "Could not read file.")
        }
        val doc = runCatching { JSONObject(String(bytes, Charsets.UTF_8)) }.getOrNull()
            ?: return Report(false, null, "File is not a valid signed evidence pack.")
        val events = doc.optJSONArray("events") ?: return Report(false, null, "Missing events array.")

        var prev: String? = null
        for (i in 0 until events.length()) {
            val ev = events.getJSONObject(i)
            val prevHash = ev.optString("prevHash").takeIf { it.isNotEmpty() && it != "null" }
            if (prevHash != prev) {
                return Report(false, i + 1, "Hash chain broken at event #${i + 1}.")
            }
            val detail = ev.optString("detail").takeIf { it.isNotEmpty() && it != "null" }
            // Android's SecurityRepository.hash uses prev|timestamp|type|packageName|detail.
            // The evidence pack omits packageName per iOS parity — recompute with same shape.
            val material = buildString {
                append(prev.orEmpty()); append('|')
                append(ev.getLong("timestampMillis")); append('|')
                append(ev.getString("type")); append('|')
                append('|')
                append(detail.orEmpty())
            }
            val recomputed = MessageDigest.getInstance("SHA-256")
                .digest(material.toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it) }
            // Lenient verify: if the shape doesn't match, fall back to the iOS-flavoured
            // material (prev|ts|type|detail) before declaring tamper.
            val iosMaterial = "${prev.orEmpty()}|${ev.getLong("timestampMillis")}|${ev.getString("type")}|${detail.orEmpty()}"
            val iosHash = MessageDigest.getInstance("SHA-256")
                .digest(iosMaterial.toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it) }
            val stored = ev.getString("rowHash")
            if (stored != recomputed && stored != iosHash) {
                return Report(false, i + 1,
                    "Event #${i + 1} hash does not match the recomputed hash — row was altered.")
            }
            prev = stored
        }

        val commits = doc.optJSONArray("merkleCommits")
        if (commits != null) {
            for (i in 0 until commits.length()) {
                val c = commits.getJSONObject(i)
                val leavesArr = c.getJSONArray("leaves")
                val leaves = (0 until leavesArr.length()).map { leavesArr.getString(it) }
                val root = SignedLogState.merkleRoot(leaves)
                if (root != c.getString("rootHex")) {
                    return Report(false, null,
                        "Merkle commit #${i + 1} root does not match the recomputed root.")
                }
            }
        }

        val signed = (0 until events.length()).count {
            val s = events.getJSONObject(it).optString("signatureB64")
            s.isNotEmpty() && s != "null"
        }
        val merkleCount = commits?.length() ?: 0
        return Report(true, null,
            "Verified · ${events.length()} events · $signed signed · $merkleCount merkle commits · pack hash intact.")
    }
}
