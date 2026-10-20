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
import com.mobilesecurity.app.data.db.entities.ShieldSession
import kotlinx.coroutines.flow.Flow

@Dao
interface ShieldSessionDao {

    @Insert
    suspend fun insert(session: ShieldSession): Long

    @Query("UPDATE shield_sessions SET endedAt = :endedAt WHERE id = :id")
    suspend fun markEnded(id: Long, endedAt: Long)

    @Query("UPDATE shield_sessions SET label = :label WHERE id = :id")
    suspend fun setLabel(id: Long, label: String?)

    @Query("SELECT * FROM shield_sessions ORDER BY startedAt DESC LIMIT :limit")
    fun recent(limit: Int = 200): Flow<List<ShieldSession>>

    @Query("SELECT * FROM shield_sessions WHERE code = :code LIMIT 1")
    suspend fun findByCode(code: String): ShieldSession?

    @Query("DELETE FROM shield_sessions")
    suspend fun deleteAll()
}
