package com.mobilesecurity.app.data.db.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "blocked_numbers")
data class BlockedNumber(
    @PrimaryKey val number: String,
    val label: String? = null,
    val addedAt: Long = System.currentTimeMillis(),
    val blockCalls: Boolean = true,
    val blockSms: Boolean = true
)
