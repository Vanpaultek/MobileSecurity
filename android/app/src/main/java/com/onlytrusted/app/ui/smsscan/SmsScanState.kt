// SPDX-FileCopyrightText: 2026 VanPaulTek Inc
// SPDX-License-Identifier: AGPL-3.0-or-later
package com.onlytrusted.app.ui.smsscan

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/** Persists the aggregate result of the last SMS scan for Dashboard counter reads. */
object SmsScanState {

    private const val PREFS = "sms_scan_state_v1"
    private const val KEY_MESSAGES_SCANNED = "messages_scanned"
    private const val KEY_SENDERS_SCANNED = "senders_scanned"
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

    fun matchCount(ctx: Context): Int = prefs(ctx).getInt(KEY_MATCH_COUNT, 0)

    fun save(ctx: Context, report: SmsScanReport, nowMillis: Long = System.currentTimeMillis()) {
        prefs(ctx).edit()
            .putInt(KEY_MESSAGES_SCANNED, report.messagesScanned)
            .putInt(KEY_SENDERS_SCANNED, report.sendersScanned)
            .putInt(KEY_MATCH_COUNT, report.matches.size)
            .putLong(KEY_LAST_SCAN_MILLIS, nowMillis)
            .apply()
    }

    fun clear(ctx: Context) {
        prefs(ctx).edit().clear().apply()
    }
}
