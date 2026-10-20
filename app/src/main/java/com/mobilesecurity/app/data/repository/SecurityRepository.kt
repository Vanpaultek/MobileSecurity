// SPDX-FileCopyrightText: 2026 57Million LLC
// SPDX-License-Identifier: AGPL-3.0-or-later
package com.mobilesecurity.app.data.repository

import com.mobilesecurity.app.data.db.BlockedNumberDao
import com.mobilesecurity.app.data.db.EventDao
import com.mobilesecurity.app.data.db.entities.BlockedNumber
import com.mobilesecurity.app.data.db.entities.SecurityEvent
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.security.MessageDigest

class SecurityRepository(
    private val eventDao: EventDao,
    private val blockedNumberDao: BlockedNumberDao
) {

    val recentEvents = eventDao.recent()
    val blockedNumbers = blockedNumberDao.all()

    fun micCountSince(since: Long) = eventDao.countOfTypeSince(SecurityEvent.TYPE_MIC, since)
    fun cameraCountSince(since: Long) = eventDao.countOfTypeSince(SecurityEvent.TYPE_CAMERA, since)
    fun blockedCallCountSince(since: Long) =
        eventDao.countOfTypeSince(SecurityEvent.TYPE_CALL_BLOCKED, since)
    fun flaggedSmsCountSince(since: Long) =
        eventDao.countOfTypeSince(SecurityEvent.TYPE_SMS_FLAGGED, since)

    private val logMutex = Mutex()
    @Volatile private var lastHash: String? = null
    @Volatile private var writesSinceTrim = 0

    suspend fun log(type: String, packageName: String? = null, detail: String? = null) {
        logMutex.withLock {
            val now = System.currentTimeMillis()
            val prev = lastHash ?: eventDao.lastRowHash()
            val rowHash = hash(prev, now, type, packageName, detail)
            lastHash = rowHash
            eventDao.insert(
                SecurityEvent(
                    timestamp = now,
                    type = type,
                    packageName = packageName,
                    detail = detail,
                    prevHash = prev,
                    rowHash = rowHash
                )
            )
            writesSinceTrim++
            if (writesSinceTrim >= TRIM_CHECK_EVERY_N_WRITES) {
                writesSinceTrim = 0
                if (eventDao.totalCount() > MAX_EVENTS) {
                    eventDao.trimToNewest(MAX_EVENTS)
                }
            }
        }
    }

    companion object {
        /** Keep the newest 10 000 events; older rows are deleted periodically. */
        private const val MAX_EVENTS = 10_000
        /** Trim-check runs every N inserts; keeps the write path cheap. */
        private const val TRIM_CHECK_EVERY_N_WRITES = 50
    }

    suspend fun verifyChain(): Boolean = logMutex.withLock {
        verify(eventDao.allOldestFirst(), checkStart = true)
    }

    /**
     * Light-weight verification for the dashboard: walks the last [limit] events, confirming
     * each links to the previous one. Cannot detect modifications older than the window, but
     * is quick enough to run on every dashboard open without any battery cost.
     */
    suspend fun verifyRecent(limit: Int = 100): Boolean = logMutex.withLock {
        val tail = eventDao.tail(limit).asReversed()
        verify(tail, checkStart = false)
    }

    private fun verify(events: List<SecurityEvent>, checkStart: Boolean): Boolean {
        if (events.isEmpty()) return true
        var prev: String? = if (checkStart) null else events.first().prevHash
        for ((index, event) in events.withIndex()) {
            if (!checkStart && index == 0) {
                val expected = hash(prev, event.timestamp, event.type, event.packageName, event.detail)
                if (event.rowHash != expected) return false
                prev = event.rowHash
                continue
            }
            if (event.prevHash != prev) return false
            val expected = hash(prev, event.timestamp, event.type, event.packageName, event.detail)
            if (event.rowHash != expected) return false
            prev = event.rowHash
        }
        return true
    }

    private fun hash(
        prev: String?,
        timestamp: Long,
        type: String,
        packageName: String?,
        detail: String?
    ): String {
        val material = buildString {
            append(prev.orEmpty()); append('|')
            append(timestamp); append('|')
            append(type); append('|')
            append(packageName.orEmpty()); append('|')
            append(detail.orEmpty())
        }
        val bytes = MessageDigest.getInstance("SHA-256").digest(material.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    suspend fun clearEvents() = eventDao.clearAll()
    suspend fun clearEventsBefore(cutoff: Long) = eventDao.clearBefore(cutoff)

    suspend fun wipeAll() {
        eventDao.clearAll()
        blockedNumberDao.deleteAll()
    }

    suspend fun block(number: String, label: String? = null,
                      blockCalls: Boolean = true, blockSms: Boolean = true) {
        blockedNumberDao.upsert(
            BlockedNumber(
                number = normalise(number),
                label = label,
                blockCalls = blockCalls,
                blockSms = blockSms
            )
        )
    }

    suspend fun unblock(number: String) {
        val target = blockedNumberDao.findByNumber(normalise(number)) ?: return
        blockedNumberDao.delete(target)
    }

    suspend fun isCallBlocked(number: String): Boolean =
        blockedNumberDao.isCallBlocked(normalise(number))

    /** Synchronous — safe to call from a binder thread such as CallScreeningService. */
    fun isCallBlockedSync(number: String): Boolean =
        blockedNumberDao.isCallBlockedSync(normalise(number))

    suspend fun isSmsBlocked(number: String): Boolean =
        blockedNumberDao.isSmsBlocked(normalise(number))

    private fun normalise(number: String): String =
        number.replace(Regex("[^+\\d]"), "")
}
