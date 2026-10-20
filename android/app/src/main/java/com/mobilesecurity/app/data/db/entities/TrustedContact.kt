// SPDX-FileCopyrightText: 2026 57Million LLC
// SPDX-License-Identifier: AGPL-3.0-or-later
package com.mobilesecurity.app.data.db.entities

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
    val updatedAt: Long = System.currentTimeMillis()
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
