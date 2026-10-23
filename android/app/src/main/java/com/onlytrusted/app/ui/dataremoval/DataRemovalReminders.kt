// SPDX-FileCopyrightText: 2026 VanPaulTek Inc
// SPDX-License-Identifier: AGPL-3.0-or-later
package com.onlytrusted.app.ui.dataremoval

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.onlytrusted.app.work.DataRemovalReminderWorker
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Premium-only Data Removal reminder state. Mirrors iOS `DataRemovalReminders`:
 * tracks per-site "removal requested" timestamps in EncryptedSharedPreferences and
 * schedules a WorkManager one-shot 90 days later to post a local notification.
 *
 * All on-device: no server knows which brokers the user asked for removal from.
 */
object DataRemovalReminders {

    private const val PREFS = "data_removal_reminders_v1"
    private const val KEY_REQUESTED = "requested_json"

    /** Window between request and re-check reminder — 90 days matches typical broker SLA. */
    const val REMINDER_DAYS: Long = 90

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

    /** `[siteName -> requestedAtMillis]` for every site the user has flagged. */
    fun requested(ctx: Context): Map<String, Long> {
        val raw = prefs(ctx).getString(KEY_REQUESTED, null) ?: return emptyMap()
        return runCatching {
            val obj = JSONObject(raw)
            buildMap<String, Long> {
                obj.keys().forEach { k -> put(k, obj.getLong(k)) }
            }
        }.getOrDefault(emptyMap())
    }

    fun isRequested(ctx: Context, site: String): Boolean = requested(ctx).containsKey(site)

    fun requestedAt(ctx: Context, site: String): Long? = requested(ctx)[site]

    /** Marks the site as removal-requested now and schedules the WorkManager reminder. */
    fun markRequested(ctx: Context, site: String, nowMillis: Long = System.currentTimeMillis()) {
        val current = requested(ctx).toMutableMap()
        current[site] = nowMillis
        persist(ctx, current)
        scheduleReminder(ctx, site)
    }

    fun unmark(ctx: Context, site: String) {
        val updated = requested(ctx).toMutableMap().apply { remove(site) }
        persist(ctx, updated)
        cancelReminder(ctx, site)
    }

    fun clearAll(ctx: Context) {
        requested(ctx).keys.forEach { cancelReminder(ctx, it) }
        prefs(ctx).edit().remove(KEY_REQUESTED).apply()
    }

    private fun scheduleReminder(ctx: Context, site: String) {
        val data = Data.Builder().putString("site", site).build()
        val req = OneTimeWorkRequestBuilder<DataRemovalReminderWorker>()
            .setInitialDelay(REMINDER_DAYS, TimeUnit.DAYS)
            .setInputData(data)
            .addTag(tag(site))
            .build()
        WorkManager.getInstance(ctx.applicationContext).enqueue(req)
    }

    private fun cancelReminder(ctx: Context, site: String) {
        WorkManager.getInstance(ctx.applicationContext).cancelAllWorkByTag(tag(site))
    }

    private fun tag(site: String) = "data_removal_reminder_$site"

    private fun persist(ctx: Context, map: Map<String, Long>) {
        val obj = JSONObject()
        map.forEach { (k, v) -> obj.put(k, v) }
        prefs(ctx).edit().putString(KEY_REQUESTED, obj.toString()).apply()
    }
}
