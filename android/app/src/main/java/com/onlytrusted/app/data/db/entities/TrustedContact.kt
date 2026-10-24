// SPDX-FileCopyrightText: 2026 VanPaulTek Inc
// SPDX-License-Identifier: AGPL-3.0-or-later
package com.onlytrusted.app.data.db.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "trusted_contacts",
    indices = [Index(value = ["isOwner"]), Index(value = ["displayName"])]
)
data class TrustedContact(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val displayName: String,
    val relation: String? = null,
    val isOwner: Boolean = false,
    val emailMasked: String? = null,
    val addressEncrypted: String? = null,
    val notesEncrypted: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    /** ContactsContract LOOKUP_KEY of the linked system contact, when imported via
     *  the picker. Null for manually-entered contacts. Enables Phase 2 live-read of
     *  always-fresh name + number from the OS Contacts database. */
    val systemContactLookupKey: String? = null
)

@Entity(
    tableName = "trusted_numbers",
    indices = [Index(value = ["contactId"]), Index(value = ["normalisedNumber"], unique = true)]
)
data class TrustedNumber(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val contactId: Long,
    val label: String? = null,
    val normalisedNumber: String,
    val maskedForDisplay: String
)
