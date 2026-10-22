// SPDX-FileCopyrightText: 2026 VanPaulTek Inc
// SPDX-License-Identifier: AGPL-3.0-or-later
package com.onlytrusted.app.ui.stego

import android.content.Context
import android.net.Uri
import android.os.Build
import com.onlytrusted.app.BuildConfig
import com.onlytrusted.app.OnlyTrustedApp
import com.onlytrusted.app.backup.BackupManager
import com.onlytrusted.app.backup.BackupPayload
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Glues `StegoCrypto` to the app's vault snapshot. Reuses the Elite B1 `BackupPayload` shape
 * so a stego vault round-trips through the exact same restore code path as a `.ot2bak`.
 */
object StegoVaultManager {

    private val json = Json { prettyPrint = false; encodeDefaults = true; ignoreUnknownKeys = true }

    data class CreationResult(val bytes: ByteArray, val suggestedFilename: String)

    class PassphraseWeakException(val reason: String) : Exception(reason)
    class ParseFailedException :
        Exception("No vault found in this file. Check your passphrase or try a different carrier.")

    /**
     * Produces a stego-encoded carrier. `carrierBytes` is the raw PNG or PDF bytes; `format`
     * must match. Returned bytes are suitable for writing to a SAF-selected `Uri`.
     */
    suspend fun createStego(
        context: Context,
        format: StegoCrypto.CarrierFormat,
        carrierBytes: ByteArray,
        passphrase: String
    ): CreationResult = withContext(Dispatchers.IO) {
        val weak = StegoCrypto.validatePassphrase(passphrase)
        if (weak != null) throw PassphraseWeakException(weak)

        val plaintext = snapshotPlaintext(context)
        val payload = StegoCrypto.makeOT3SGPayload(plaintext, passphrase.toCharArray())

        val outBytes = when (format) {
            StegoCrypto.CarrierFormat.PNG -> StegoCrypto.embedIntoPNG(carrierBytes, payload)
            StegoCrypto.CarrierFormat.PDF -> StegoCrypto.embedIntoPDF(carrierBytes, payload)
        }

        val ts = SimpleDateFormat("yyyy-MM-dd-HHmmss", Locale.US).format(Date())
        val ext = if (format == StegoCrypto.CarrierFormat.PNG) "png" else "pdf"
        CreationResult(outBytes, "memento-$ts.$ext")
    }

    /** Reads the file at [source], detects PNG vs PDF, decrypts, returns the decoded payload. */
    suspend fun openStego(context: Context, source: Uri, passphrase: String): BackupPayload =
        withContext(Dispatchers.IO) {
            val raw = context.contentResolver.openInputStream(source)?.use { it.readBytes() }
                ?: throw ParseFailedException()
            val payload = when {
                looksLikePNG(raw) -> StegoCrypto.extractFromPNG(raw)
                looksLikePDF(raw) -> StegoCrypto.extractFromPDF(raw)
                else -> throw ParseFailedException()
            }
            val plaintext = StegoCrypto.decryptOT3SGPayload(payload, passphrase.toCharArray())
            json.decodeFromString(BackupPayload.serializer(), String(plaintext, Charsets.UTF_8))
        }

    /** Writes `bytes` to the SAF-selected destination; wraps IOException as a human message. */
    suspend fun writeTo(context: Context, destination: Uri, bytes: ByteArray) =
        withContext(Dispatchers.IO) {
            context.contentResolver.openOutputStream(destination, "w")?.use { it.write(bytes) }
                ?: throw IllegalStateException("Could not open destination for writing.")
        }

    private suspend fun snapshotPlaintext(context: Context): ByteArray {
        val app = context.applicationContext as OnlyTrustedApp

        val contacts = app.database.trustedContactDao().observeAll().first().map { entry ->
            BackupPayload.ContactDto(
                id = entry.contact.id,
                displayName = entry.contact.displayName,
                relation = entry.contact.relation,
                isOwner = entry.contact.isOwner,
                emailMasked = entry.contact.emailMasked,
                addressEncrypted = entry.contact.addressEncrypted,
                notesEncrypted = entry.contact.notesEncrypted,
                createdAt = entry.contact.createdAt,
                updatedAt = entry.contact.updatedAt,
                numbers = entry.numbers.map {
                    BackupPayload.NumberDto(
                        label = it.label,
                        normalisedNumber = it.normalisedNumber,
                        maskedForDisplay = it.maskedForDisplay
                    )
                }
            )
        }
        val blocked = app.database.blockedNumberDao().all().first().map { b ->
            BackupPayload.BlockedDto(
                number = b.number,
                label = b.label,
                addedAt = b.addedAt,
                blockCalls = b.blockCalls,
                blockSms = b.blockSms
            )
        }
        val events = app.database.eventDao().recent(limit = 10_000).first().map { ev ->
            BackupPayload.EventDto(
                timestamp = ev.timestamp,
                type = ev.type,
                packageName = ev.packageName,
                detail = ev.detail
            )
        }
        val sub = context.applicationContext
            .getSharedPreferences("subscription_prefs_v1", Context.MODE_PRIVATE)
        val lockPrefs = context.applicationContext
            .getSharedPreferences("app_lock_prefs", Context.MODE_PRIVATE)
        val settings = BackupPayload.SettingsSnapshot(
            prefs = emptyMap<String, Map<String, JsonElement>>(),
            subscriptionTier = sub.getString("subscription.v1.tier", "FREE") ?: "FREE",
            subscriptionActive = sub.getBoolean("subscription.v1.active", false),
            appLockEnabled = lockPrefs.getBoolean("enabled", false),
            autoLockSeconds = context.applicationContext
                .getSharedPreferences("autolock_prefs_v1", Context.MODE_PRIVATE)
                .getInt("seconds", 60)
        )
        val payload = BackupPayload(
            exportedAtMillis = System.currentTimeMillis(),
            appVersion = BuildConfig.VERSION_NAME,
            platform = "Android ${Build.VERSION.RELEASE}",
            contacts = contacts,
            blockedNumbers = blocked,
            events = events,
            settings = settings
        )
        return json.encodeToString(payload).toByteArray(Charsets.UTF_8)
    }

    // Signature sniffers
    private val pngSig = byteArrayOf(
        0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A
    )
    private val pdfSig = byteArrayOf(0x25, 0x50, 0x44, 0x46) // "%PDF"

    private fun looksLikePNG(d: ByteArray): Boolean {
        if (d.size < pngSig.size) return false
        for (i in pngSig.indices) if (d[i] != pngSig[i]) return false
        return true
    }
    private fun looksLikePDF(d: ByteArray): Boolean {
        if (d.size < pdfSig.size) return false
        for (i in pdfSig.indices) if (d[i] != pdfSig[i]) return false
        return true
    }

    /** Bundled royalty-free sample PNGs the user can pick as a cover carrier. */
    val bundledCarriers: List<Pair<String, Int>> = listOf(
        "Nature" to com.onlytrusted.app.R.raw.stego_bundled_nature,
        "Family" to com.onlytrusted.app.R.raw.stego_bundled_family,
        "Beach" to com.onlytrusted.app.R.raw.stego_bundled_beach
    )
}
