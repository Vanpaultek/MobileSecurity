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
import androidx.room.PrimaryKey

@Entity(tableName = "security_events")
data class SecurityEvent(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,
    val type: String,
    val packageName: String? = null,
    val detail: String? = null,
    val prevHash: String? = null,
    val rowHash: String? = null
) {
    companion object {
        const val TYPE_MIC = "MIC_IN_USE"
        const val TYPE_CAMERA = "CAMERA_IN_USE"
        const val TYPE_CALL_BLOCKED = "CALL_BLOCKED"
        const val TYPE_CALL_ALLOWED = "CALL_ALLOWED"
        const val TYPE_SMS_FLAGGED = "SMS_FLAGGED"
        const val TYPE_POCKET_LOCK = "POCKET_LOCK"
        const val TYPE_SCREEN_LOCKED = "SCREEN_LOCKED"
        const val TYPE_SCREEN_UNLOCKED = "SCREEN_UNLOCKED"
        const val TYPE_UNTRUSTED_CALL = "UNTRUSTED_CALL"
        const val TYPE_UNTRUSTED_SMS = "UNTRUSTED_SMS"
        const val TYPE_PANIC_LOCK = "PANIC_LOCK"
        const val TYPE_SENSOR_PRIVACY_ON = "SENSOR_PRIVACY_ON"
        const val TYPE_SENSOR_PRIVACY_OFF = "SENSOR_PRIVACY_OFF"
    }
}
