// SPDX-FileCopyrightText: 2026 VanPaulTek Inc
// SPDX-License-Identifier: AGPL-3.0-or-later
package com.onlytrusted.app.ui.contactscan

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Persists the latest Contact Safety Scan result so the Dashboard can show the match
 * count without re-running the scan on every launch. Encrypted at rest; nothing leaves
 * the device.
 */
object ContactScanState {

    private const val PREFS = "contact_scan_state_v1"
    private const val KEY_CONTACTS_SCANNED = "contacts_scanned"
    private const val KEY_NUMBERS_SCANNED = "numbers_scanned"
    private const val KEY_MATCH_COUNT = "match_count"
    private const val KEY_LAST_SCAN_MILLIS = "last_scan_millis"

    @Volatile private var cachedMasterKey: MasterKey? = null

    private fun prefs(ctx: Context): SharedPreferences {
        val master = cachedMasterKey ?: synchronized(this) {
            cachedMasterKey ?: MasterKey.Builder(ctx.applicationContext)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
                .also { cachedMasterKey = it }
        }
        return EncryptedSharedPreferences.create(
            ctx.applicationContext, PREFS, master,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    data class Snapshot(
        val contactsScanned: Int,
        val numbersScanned: Int,
        val matchCount: Int,
        val lastScanMillis: Long
    )

    fun snapshot(ctx: Context): Snapshot? {
        val last = prefs(ctx).getLong(KEY_LAST_SCAN_MILLIS, 0L)
        if (last == 0L) return null
        return Snapshot(
            contactsScanned = prefs(ctx).getInt(KEY_CONTACTS_SCANNED, 0),
            numbersScanned = prefs(ctx).getInt(KEY_NUMBERS_SCANNED, 0),
            matchCount = prefs(ctx).getInt(KEY_MATCH_COUNT, 0),
            lastScanMillis = last
        )
    }

    fun matchCount(ctx: Context): Int = prefs(ctx).getInt(KEY_MATCH_COUNT, 0)

    fun save(ctx: Context, report: ContactsScamScanner.ScamScanReport, nowMillis: Long = System.currentTimeMillis()) {
        prefs(ctx).edit()
            .putInt(KEY_CONTACTS_SCANNED, report.contactsScanned)
            .putInt(KEY_NUMBERS_SCANNED, report.numbersScanned)
            .putInt(KEY_MATCH_COUNT, report.matches.size)
            .putLong(KEY_LAST_SCAN_MILLIS, nowMillis)
            .apply()
    }

    fun clear(ctx: Context) {
        prefs(ctx).edit().clear().apply()
    }
}
