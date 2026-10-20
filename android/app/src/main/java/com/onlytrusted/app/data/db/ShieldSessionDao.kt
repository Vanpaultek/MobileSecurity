// SPDX-FileCopyrightText: 2026 57Million LLC
// SPDX-License-Identifier: AGPL-3.0-or-later
package com.onlytrusted.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.onlytrusted.app.data.db.entities.ShieldSession
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
