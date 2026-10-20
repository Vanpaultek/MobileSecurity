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
import androidx.room.Delete
import androidx.room.Query
import androidx.room.Upsert
import com.mobilesecurity.app.data.db.entities.BlockedNumber
import kotlinx.coroutines.flow.Flow

@Dao
interface BlockedNumberDao {

    @Upsert
    suspend fun upsert(blocked: BlockedNumber)

    @Delete
    suspend fun delete(blocked: BlockedNumber)

    @Query("SELECT * FROM blocked_numbers ORDER BY addedAt DESC")
    fun all(): Flow<List<BlockedNumber>>

    @Query("SELECT * FROM blocked_numbers WHERE number = :number LIMIT 1")
    suspend fun findByNumber(number: String): BlockedNumber?

    @Query("SELECT EXISTS(SELECT 1 FROM blocked_numbers WHERE number = :number AND blockCalls = 1)")
    suspend fun isCallBlocked(number: String): Boolean

    /** Synchronous variant for the Telecom binder-thread call-screening callback. */
    @Query("SELECT EXISTS(SELECT 1 FROM blocked_numbers WHERE number = :number AND blockCalls = 1)")
    fun isCallBlockedSync(number: String): Boolean

    @Query("SELECT EXISTS(SELECT 1 FROM blocked_numbers WHERE number = :number AND blockSms = 1)")
    suspend fun isSmsBlocked(number: String): Boolean

    @Query("DELETE FROM blocked_numbers")
    suspend fun deleteAll()
}
