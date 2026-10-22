// SPDX-FileCopyrightText: 2026 VanPaulTek Inc
// SPDX-License-Identifier: AGPL-3.0-or-later
package com.onlytrusted.app.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import androidx.room.Upsert
import com.onlytrusted.app.data.db.entities.TrustedContact
import com.onlytrusted.app.data.db.entities.TrustedNumber
import kotlinx.coroutines.flow.Flow

data class TrustedContactWithNumbers(
    @androidx.room.Embedded val contact: TrustedContact,
    @androidx.room.Relation(parentColumn = "id", entityColumn = "contactId")
    val numbers: List<TrustedNumber>
)

@Dao
interface TrustedContactDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertContact(contact: TrustedContact): Long

    @Update
    suspend fun updateContact(contact: TrustedContact)

    @Delete
    suspend fun deleteContact(contact: TrustedContact)

    @Upsert
    suspend fun upsertNumber(number: TrustedNumber)

    @Query("DELETE FROM trusted_numbers WHERE contactId = :contactId")
    suspend fun deleteNumbersForContact(contactId: Long)

    @Transaction
    @Query("SELECT * FROM trusted_contacts ORDER BY isOwner DESC, displayName COLLATE NOCASE ASC")
    fun observeAll(): Flow<List<TrustedContactWithNumbers>>

    @Transaction
    @Query("SELECT * FROM trusted_contacts WHERE id = :id LIMIT 1")
    suspend fun loadById(id: Long): TrustedContactWithNumbers?

    @Query("SELECT EXISTS(SELECT 1 FROM trusted_numbers WHERE normalisedNumber = :number)")
    suspend fun isTrusted(number: String): Boolean

    /** Synchronous variant for the Telecom binder-thread call-screening callback. */
    @Query("SELECT EXISTS(SELECT 1 FROM trusted_numbers WHERE normalisedNumber = :number)")
    fun isTrustedSync(number: String): Boolean

    @Query("SELECT displayName FROM trusted_contacts c INNER JOIN trusted_numbers n " +
        "ON c.id = n.contactId WHERE n.normalisedNumber = :number LIMIT 1")
    suspend fun ownerNameForNumber(number: String): String?

    @Query("SELECT COUNT(*) FROM trusted_contacts")
    fun observeCount(): Flow<Int>

    @Query("DELETE FROM trusted_contacts")
    suspend fun deleteAllContacts()

    @Query("DELETE FROM trusted_numbers")
    suspend fun deleteAllNumbers()

    @Transaction
    suspend fun replaceNumbers(contactId: Long, numbers: List<TrustedNumber>) {
        deleteNumbersForContact(contactId)
        numbers.forEach { upsertNumber(it.copy(contactId = contactId)) }
    }
}
