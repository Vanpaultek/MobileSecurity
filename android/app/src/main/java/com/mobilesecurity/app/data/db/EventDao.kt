// SPDX-FileCopyrightText: 2026 57Million LLC
// SPDX-License-Identifier: AGPL-3.0-or-later
package com.mobilesecurity.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.mobilesecurity.app.data.db.entities.SecurityEvent
import kotlinx.coroutines.flow.Flow

@Dao
interface EventDao {

    @Insert
    suspend fun insert(event: SecurityEvent): Long

    @Query("SELECT * FROM security_events ORDER BY timestamp DESC LIMIT :limit")
    fun recent(limit: Int = 200): Flow<List<SecurityEvent>>

    @Query("SELECT COUNT(*) FROM security_events WHERE type = :type AND timestamp >= :since")
    fun countOfTypeSince(type: String, since: Long): Flow<Int>

    @Query("SELECT COUNT(*) FROM security_events WHERE timestamp >= :since")
    fun countSince(since: Long): Flow<Int>

    @Query("DELETE FROM security_events")
    suspend fun clearAll()

    @Query("DELETE FROM security_events WHERE timestamp < :cutoff")
    suspend fun clearBefore(cutoff: Long)

    @Query("SELECT rowHash FROM security_events ORDER BY id DESC LIMIT 1")
    suspend fun lastRowHash(): String?

    @Query("SELECT * FROM security_events ORDER BY id ASC")
    suspend fun allOldestFirst(): List<SecurityEvent>

    @Query("SELECT * FROM security_events ORDER BY id DESC LIMIT :limit")
    suspend fun tail(limit: Int): List<SecurityEvent>

    @Query("SELECT COUNT(*) FROM security_events")
    suspend fun totalCount(): Int

    /** Delete everything older than the newest [keep] rows. */
    @Query("""
        DELETE FROM security_events
        WHERE id NOT IN (SELECT id FROM security_events ORDER BY id DESC LIMIT :keep)
    """)
    suspend fun trimToNewest(keep: Int)
}
