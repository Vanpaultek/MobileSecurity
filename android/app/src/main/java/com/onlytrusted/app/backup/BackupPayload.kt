// SPDX-FileCopyrightText: 2026 VanPaulTek Inc
// SPDX-License-Identifier: AGPL-3.0-or-later
package com.onlytrusted.app.backup

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * Serialisable snapshot of everything OnlyTrusted holds locally. Encoded as JSON and then
 * AES-256-GCM encrypted via [BackupCrypto] into a `.ot2bak` file.
 *
 * iOS writes the sibling struct from `Backup/BackupPayload.swift`. Keep both schemas in
 * lock-step; bump the version string whenever a field is renamed or removed.
 */
@Serializable
data class BackupPayload(
    val schema: String = CURRENT_SCHEMA,
    val exportedAtMillis: Long,
    val appVersion: String,
    val platform: String,

    val contacts: List<ContactDto>,
    val blockedNumbers: List<BlockedDto>,
    val events: List<EventDto>,
    val settings: SettingsSnapshot
) {
    companion object {
        const val CURRENT_SCHEMA = "onlytrusted.backup.v1"
    }

    @Serializable
    data class ContactDto(
        val id: Long,
        val displayName: String,
        val relation: String? = null,
        val isOwner: Boolean = false,
        val emailMasked: String? = null,
        val addressEncrypted: String? = null,
        val notesEncrypted: String? = null,
        val createdAt: Long,
        val updatedAt: Long,
        val numbers: List<NumberDto>
    )

    @Serializable
    data class NumberDto(
        val label: String? = null,
        val normalisedNumber: String,
        val maskedForDisplay: String
    )

    @Serializable
    data class BlockedDto(
        val number: String,
        val label: String? = null,
        val addedAt: Long,
        val blockCalls: Boolean = true,
        val blockSms: Boolean = true
    )

    @Serializable
    data class EventDto(
        val timestamp: Long,
        val type: String,
        val packageName: String? = null,
        val detail: String? = null
    )

    @Serializable
    data class SettingsSnapshot(
        val prefs: Map<String, Map<String, JsonElement>>,
        val subscriptionTier: String,
        val subscriptionActive: Boolean,
        val appLockEnabled: Boolean,
        val autoLockSeconds: Int
    )
}
