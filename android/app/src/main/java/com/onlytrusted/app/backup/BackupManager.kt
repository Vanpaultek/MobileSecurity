// SPDX-FileCopyrightText: 2026 VanPaulTek Inc
// SPDX-License-Identifier: AGPL-3.0-or-later
package com.onlytrusted.app.backup

import android.content.Context
import android.net.Uri
import android.os.Build
import com.onlytrusted.app.BuildConfig
import com.onlytrusted.app.OnlyTrustedApp
import com.onlytrusted.app.data.db.entities.BlockedNumber
import com.onlytrusted.app.data.db.entities.SecurityEvent
import com.onlytrusted.app.data.repository.TrustedContactDraft
import com.onlytrusted.app.security.AppLockManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.doubleOrNull
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Builds + restores encrypted vault backups. Mirrors iOS `BackupManager`.
 *
 * Entry points:
 *  - [exportToUri] — called by the Settings "Encrypted vault backup (Elite)" row after the
 *    user has entered a passphrase and the Storage Access Framework handed us a destination
 *    `Uri` via `ActivityResultContracts.CreateDocument("application/octet-stream")`.
 *  - [decrypt] — unlocks a backup for preview before restoring.
 *  - [applyRestore] — overwrites every store with the decoded payload.
 */
object BackupManager {

    private val json = Json { prettyPrint = false; encodeDefaults = true; ignoreUnknownKeys = true }

    /**
     * List of SharedPreferences buckets whose entries we export. We DELIBERATELY omit any
     * EncryptedSharedPreferences buckets — their contents already contain hashed PINs /
     * carrier baselines that are device-specific and shouldn't travel in a backup.
     */
    private val PREF_BUCKETS = listOf(
        "settings",
        "protection_prefs",
        "subscription_prefs_v1",
        "autolock_prefs_v1",
        "app_lock_prefs",
        "notification_prefs_v1",
        "blocking_prefs_v1",
        "simswap_prefs_v1",
        "wangiri_prefs_v1",
        "checkin_prefs_v1",
        "smart_link_guard_prefs_v1",
        "onboarding_prefs"
    )

    data class BackupResult(val filename: String, val bytes: Int)

    class PassphraseWeakException(val reason: String) : Exception(reason)

    suspend fun exportToUri(context: Context, destination: Uri, passphrase: String): BackupResult =
        withContext(Dispatchers.IO) {
            BackupCrypto.validatePassphrase(passphrase)?.let { throw PassphraseWeakException(it) }

            val payload = snapshot(context)
            val plain = json.encodeToString(payload).toByteArray(Charsets.UTF_8)
            val encrypted = BackupCrypto.encrypt(plain, passphrase.toCharArray())

            context.contentResolver.openOutputStream(destination, "w")?.use { out ->
                out.write(encrypted)
            } ?: throw IllegalStateException("Could not open destination for writing.")

            BackupResult(filename = suggestedFilename(), bytes = encrypted.size)
        }

    suspend fun decrypt(context: Context, source: Uri, passphrase: String): BackupPayload =
        withContext(Dispatchers.IO) {
            val bytes = context.contentResolver.openInputStream(source)?.use { it.readBytes() }
                ?: throw IllegalStateException("Could not read backup file.")
            val plain = BackupCrypto.decrypt(bytes, passphrase.toCharArray())
            json.decodeFromString(BackupPayload.serializer(), String(plain, Charsets.UTF_8))
        }

    suspend fun applyRestore(context: Context, payload: BackupPayload) = withContext(Dispatchers.IO) {
        val app = context.applicationContext as OnlyTrustedApp

        // Wipe first so stale rows don't survive a partial-overlap restore.
        app.repository.wipeAll()
        app.trustedContacts.wipeAll()
        app.database.shieldSessionDao().deleteAll()

        // Contacts.
        for (c in payload.contacts) {
            app.trustedContacts.save(
                TrustedContactDraft(
                    id = 0,
                    displayName = c.displayName,
                    relation = c.relation,
                    isOwner = c.isOwner,
                    email = null,
                    address = null,
                    notes = null,
                    phoneNumbers = c.numbers.map { it.normalisedNumber to it.label }
                )
            )
        }

        // Blocked numbers.
        for (b in payload.blockedNumbers) {
            app.database.blockedNumberDao().upsert(
                BlockedNumber(
                    number = b.number,
                    label = b.label,
                    addedAt = b.addedAt,
                    blockCalls = b.blockCalls,
                    blockSms = b.blockSms
                )
            )
        }

        // Events — re-append via repository.log so the hash chain re-anchors cleanly.
        for (ev in payload.events) {
            app.repository.log(
                type = ev.type,
                packageName = ev.packageName,
                detail = ev.detail
            )
        }
        app.repository.log(
            type = SecurityEvent.TYPE_BACKUP_RESTORED,
            detail = "backup restored (schema=${payload.schema})"
        )

        // Settings: restore every prefs bucket we exported.
        for ((bucket, entries) in payload.settings.prefs) {
            val sp = context.applicationContext.getSharedPreferences(bucket, Context.MODE_PRIVATE)
            val editor = sp.edit().clear()
            for ((k, v) in entries) {
                writePrefValue(editor, k, v)
            }
            editor.apply()
        }

        // Elite-ish flags we store outside the general prefs map.
        AppLockManager.setEnabled(context, payload.settings.appLockEnabled)
    }

    // MARK: - Snapshot

    private suspend fun snapshot(context: Context): BackupPayload {
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

        val prefs = mutableMapOf<String, Map<String, JsonElement>>()
        for (bucket in PREF_BUCKETS) {
            val sp = context.applicationContext.getSharedPreferences(bucket, Context.MODE_PRIVATE)
            val all = sp.all
            if (all.isNotEmpty()) {
                prefs[bucket] = all.mapValues { (_, v) -> prefValueToJson(v) }
            }
        }

        val sub = context.applicationContext
            .getSharedPreferences("subscription_prefs_v1", Context.MODE_PRIVATE)
        val lockPrefs = context.applicationContext
            .getSharedPreferences("app_lock_prefs", Context.MODE_PRIVATE)

        val settings = BackupPayload.SettingsSnapshot(
            prefs = prefs,
            subscriptionTier = sub.getString("subscription.v1.tier", "FREE") ?: "FREE",
            subscriptionActive = sub.getBoolean("subscription.v1.active", false),
            appLockEnabled = lockPrefs.getBoolean("enabled", false),
            autoLockSeconds = context.applicationContext
                .getSharedPreferences("autolock_prefs_v1", Context.MODE_PRIVATE)
                .getInt("seconds", 60)
        )

        return BackupPayload(
            exportedAtMillis = System.currentTimeMillis(),
            appVersion = BuildConfig.VERSION_NAME,
            platform = "Android ${Build.VERSION.RELEASE}",
            contacts = contacts,
            blockedNumbers = blocked,
            events = events,
            settings = settings
        )
    }

    private fun prefValueToJson(v: Any?): JsonElement = when (v) {
        is Boolean -> JsonPrimitive(v)
        is Int -> JsonPrimitive(v)
        is Long -> JsonPrimitive(v)
        is Float -> JsonPrimitive(v)
        is Double -> JsonPrimitive(v)
        is String -> JsonPrimitive(v)
        else -> JsonPrimitive(v?.toString().orEmpty())
    }

    private fun writePrefValue(
        editor: android.content.SharedPreferences.Editor,
        key: String,
        value: JsonElement
    ) {
        if (value is JsonPrimitive) {
            when {
                value.booleanOrNull != null -> editor.putBoolean(key, value.booleanOrNull!!)
                value.longOrNull != null && (value.longOrNull!! > Int.MAX_VALUE || value.longOrNull!! < Int.MIN_VALUE) ->
                    editor.putLong(key, value.longOrNull!!)
                value.intOrNull != null -> editor.putInt(key, value.intOrNull!!)
                value.doubleOrNull != null -> editor.putFloat(key, value.doubleOrNull!!.toFloat())
                else -> editor.putString(key, value.content)
            }
        }
    }

    fun suggestedFilename(): String {
        val f = SimpleDateFormat("yyyy-MM-dd-HHmmss", Locale.US)
        return "OnlyTrusted-backup-${f.format(Date())}.ot2bak"
    }
}
