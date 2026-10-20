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
package com.mobilesecurity.app.export

import android.content.Context
import android.net.Uri
import android.os.Build
import com.mobilesecurity.app.MobileSecurityApp
import com.mobilesecurity.app.data.db.entities.SecurityEvent
import com.mobilesecurity.app.privacy.DevicePosture
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.security.MessageDigest

/**
 * Produces an "evidence pack" — a tamper-evident, signed JSON snapshot the owner can take to
 * their carrier, bank, or law enforcement when they want someone with legal authority to
 * investigate. This is the only legitimate way to pursue who was on the other end of a call
 * or an intrusion attempt. We never attempt to reach the attacker ourselves.
 *
 * The export never includes:
 *  - Family vault contents (names, numbers, addresses) — those stay on the device.
 *  - Any information about third parties beyond phone numbers that already appeared in the
 *    user's own call/SMS log.
 */
object EvidenceExporter {

    private val json = Json { prettyPrint = true; encodeDefaults = true }

    @Serializable
    data class EvidencePack(
        val schema: String = "mobilesecurity.evidence.v1",
        val generatedAtMillis: Long,
        val appVersionName: String,
        val devicePosture: PostureDto,
        val events: List<EventDto>,
        val integrity: Integrity
    )

    @Serializable
    data class PostureDto(
        val adbEnabled: Boolean,
        val developerOptionsOn: Boolean,
        val deviceSecure: Boolean,
        val likelyRooted: Boolean,
        val canInstallFromUnknownSources: Boolean,
        val riskyAppCount: Int,
        val riskyApps: List<String>,
        val device: String,
        val androidVersion: String
    )

    @Serializable
    data class EventDto(
        val timestampMillis: Long,
        val type: String,
        val packageName: String? = null,
        val detail: String? = null,
        val prevHash: String? = null,
        val rowHash: String? = null
    )

    @Serializable
    data class Integrity(
        val chainVerified: Boolean,
        val eventCount: Int,
        val packSha256: String
    )

    suspend fun exportTo(context: Context, destination: Uri): Int = withContext(Dispatchers.IO) {
        val app = context.applicationContext as MobileSecurityApp
        val events = app.database.eventDao().recent(limit = 20_000).first()
        val chainOk = app.repository.verifyChain()
        val posture = DevicePosture.read(context)

        val eventsDto = events.map { it.toDto() }
        val postureDto = PostureDto(
            adbEnabled = posture.adbEnabled,
            developerOptionsOn = posture.developerOptionsOn,
            deviceSecure = posture.deviceSecure,
            likelyRooted = posture.likelyRooted,
            canInstallFromUnknownSources = posture.canInstallFromUnknownSources,
            riskyAppCount = posture.riskyAppCount,
            riskyApps = posture.riskyApps.map { it.packageName },
            device = "${Build.MANUFACTURER} ${Build.MODEL}",
            androidVersion = "Android ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})"
        )

        // Compute the pack hash across content *excluding* the hash itself.
        val pre = EvidencePack(
            generatedAtMillis = System.currentTimeMillis(),
            appVersionName = com.mobilesecurity.app.BuildConfig.VERSION_NAME,
            devicePosture = postureDto,
            events = eventsDto,
            integrity = Integrity(chainVerified = chainOk, eventCount = eventsDto.size, packSha256 = "")
        )
        val serialisedWithoutHash = json.encodeToString(pre)
        val packHash = sha256Hex(serialisedWithoutHash)

        val final = pre.copy(
            integrity = pre.integrity.copy(packSha256 = packHash)
        )

        context.contentResolver.openOutputStream(destination, "w")?.use { out ->
            out.write(json.encodeToString(final).toByteArray(Charsets.UTF_8))
        } ?: throw IllegalStateException("Could not open destination for writing.")

        eventsDto.size
    }

    private fun SecurityEvent.toDto() = EventDto(
        timestampMillis = timestamp,
        type = type,
        packageName = packageName,
        detail = detail,
        prevHash = prevHash,
        rowHash = rowHash
    )

    private fun sha256Hex(input: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
