package com.mobilesecurity.app.data.db.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "shield_sessions",
    indices = [Index(value = ["code"], unique = true)]
)
data class ShieldSession(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val code: String,
    val ownerTag: String,
    val startedAt: Long,
    val endedAt: Long? = null,
    val label: String? = null
)
