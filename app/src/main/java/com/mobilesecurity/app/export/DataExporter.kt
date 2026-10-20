package com.mobilesecurity.app.export

import android.content.Context
import android.net.Uri
import com.mobilesecurity.app.MobileSecurityApp
import com.mobilesecurity.app.data.db.entities.BlockedNumber
import com.mobilesecurity.app.data.db.entities.SecurityEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

object DataExporter {

    private val json = Json { prettyPrint = true; encodeDefaults = true }

    @Serializable
    data class Payload(
        val schema: String = "mobilesecurity.export.v1",
        val exportedAtMillis: Long,
        val appVersionName: String,
        val events: List<EventDto>,
        val blockedNumbers: List<BlockedDto>
    )

    @Serializable
    data class EventDto(
        val timestampMillis: Long,
        val type: String,
        val packageName: String? = null,
        val detail: String? = null
    )

    @Serializable
    data class BlockedDto(
        val number: String,
        val label: String? = null,
        val addedAtMillis: Long,
        val blockCalls: Boolean,
        val blockSms: Boolean
    )

    suspend fun exportTo(context: Context, destination: Uri): Int = withContext(Dispatchers.IO) {
        val app = context.applicationContext as MobileSecurityApp
        val events = app.database.eventDao().recent(limit = 10_000).first()
        val blocked = app.database.blockedNumberDao().all().first()

        val payload = Payload(
            exportedAtMillis = System.currentTimeMillis(),
            appVersionName = com.mobilesecurity.app.BuildConfig.VERSION_NAME,
            events = events.map { it.toDto() },
            blockedNumbers = blocked.map { it.toDto() }
        )

        context.contentResolver.openOutputStream(destination, "w")?.use { out ->
            out.write(json.encodeToString(payload).toByteArray(Charsets.UTF_8))
        } ?: throw IllegalStateException("Could not open destination for writing.")

        events.size + blocked.size
    }

    private fun SecurityEvent.toDto() = EventDto(timestamp, type, packageName, detail)
    private fun BlockedNumber.toDto() = BlockedDto(number, label, addedAt, blockCalls, blockSms)
}
