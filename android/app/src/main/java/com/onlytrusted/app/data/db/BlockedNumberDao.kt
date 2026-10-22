// SPDX-FileCopyrightText: 2026 VanPaulTek Inc
// SPDX-License-Identifier: AGPL-3.0-or-later
package com.onlytrusted.app.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Query
import androidx.room.Upsert
import com.onlytrusted.app.data.db.entities.BlockedNumber
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
