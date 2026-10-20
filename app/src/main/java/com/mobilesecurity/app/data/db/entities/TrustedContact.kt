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
