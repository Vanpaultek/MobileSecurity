// SPDX-FileCopyrightText: 2026 VanPaulTek Inc
// SPDX-License-Identifier: AGPL-3.0-or-later
package com.onlytrusted.app.ui.roguenetwork

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import org.json.JSONArray
import org.json.JSONObject

/**
 * Elite-tier (`SubscriptionState.hasElite`) rogue Wi-Fi + IMSI-catcher scanner state.
 *
 * Alerts and toggle flags live in `EncryptedSharedPreferences` (AES-256-GCM via the
 * Android Keystore), the same pattern used by HP1 Duress. The companion monitor
 * ([RogueNetworkMonitor]) samples the cellular + Wi-Fi state and funnels findings
 * through [recordAlert].
 */
object RogueNetworkState {

    enum class AlertKind { FORCED_DOWNGRADE, OPEN_WIFI_ON_BRAND_SSID, SSID_COLLISION, MISSING_CARRIER }

    data class Alert(
        val kind: AlertKind,
        val title: String,
        val detail: String,
        val timestampMillis: Long
    )

    private const val PREFS = "onlytrusted_rogue_network_v1"
    private const val KEY_CELLULAR = "cellular_enabled"
    private const val KEY_WIFI = "wifi_enabled"
    private const val KEY_ALERTS = "alerts_json"
    private const val MAX_ALERTS = 50

    private fun prefs(ctx: Context): SharedPreferences {
        val masterKey = MasterKey.Builder(ctx)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        return EncryptedSharedPreferences.create(
            ctx.applicationContext,
            PREFS,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    fun isCellularEnabled(ctx: Context): Boolean =
        prefs(ctx).getBoolean(KEY_CELLULAR, true)

    fun setCellularEnabled(ctx: Context, enabled: Boolean) {
        prefs(ctx).edit().putBoolean(KEY_CELLULAR, enabled).apply()
    }

    fun isWifiEnabled(ctx: Context): Boolean =
        prefs(ctx).getBoolean(KEY_WIFI, true)

    fun setWifiEnabled(ctx: Context, enabled: Boolean) {
        prefs(ctx).edit().putBoolean(KEY_WIFI, enabled).apply()
    }

    fun alerts(ctx: Context): List<Alert> {
        val raw = prefs(ctx).getString(KEY_ALERTS, null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    add(
                        Alert(
                            kind = AlertKind.valueOf(obj.getString("kind")),
                            title = obj.getString("title"),
                            detail = obj.getString("detail"),
                            timestampMillis = obj.getLong("ts")
                        )
                    )
                }
            }
        }.getOrElse { emptyList() }
    }

    fun recordAlert(ctx: Context, alert: Alert) {
        val existing = alerts(ctx).toMutableList()
        // Dedup: same (kind,title) within 10 minutes gets collapsed.
        val last = existing.firstOrNull()
        if (last != null &&
            last.kind == alert.kind &&
            last.title == alert.title &&
            alert.timestampMillis - last.timestampMillis < 10 * 60 * 1000L) {
            return
        }
        existing.add(0, alert)
        if (existing.size > MAX_ALERTS) {
            existing.subList(MAX_ALERTS, existing.size).clear()
        }
        persist(ctx, existing)
    }

    fun clearAlerts(ctx: Context) {
        prefs(ctx).edit().remove(KEY_ALERTS).apply()
    }

    private fun persist(ctx: Context, list: List<Alert>) {
        val arr = JSONArray()
        for (alert in list) {
            val obj = JSONObject()
            obj.put("kind", alert.kind.name)
            obj.put("title", alert.title)
            obj.put("detail", alert.detail)
            obj.put("ts", alert.timestampMillis)
            arr.put(obj)
        }
        prefs(ctx).edit().putString(KEY_ALERTS, arr.toString()).apply()
    }
}
