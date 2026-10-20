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
