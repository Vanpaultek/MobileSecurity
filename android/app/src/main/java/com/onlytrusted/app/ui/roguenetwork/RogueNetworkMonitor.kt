// SPDX-FileCopyrightText: 2026 VanPaulTek Inc
// SPDX-License-Identifier: AGPL-3.0-or-later
package com.onlytrusted.app.ui.roguenetwork

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.wifi.WifiManager
import android.os.Build
import android.telephony.TelephonyManager
import androidx.core.content.ContextCompat

/**
 * Elite-tier (`SubscriptionState.hasElite`) rogue-network monitor.
 *
 * Public entry point is [scanOnce]. It samples the current cellular + Wi-Fi state
 * once and funnels any red-flag findings into [RogueNetworkState].
 *
 * Cellular heuristics:
 *  - **Forced 2G / GSM downgrade** — `TelephonyManager.getDataNetworkType()` is
 *    GPRS/EDGE/CDMA. Classic IMSI-catcher symptom on a modern phone.
 *
 * Wi-Fi heuristics (requires ACCESS_WIFI_STATE and, for scan results on API 23+,
 * ACCESS_FINE_LOCATION — both already in the manifest):
 *  - **Open AP broadcasting bank / airline SSID** — scan results that match a
 *    known brand prefix *and* have an empty capability string.
 *  - **SSID collision** — multiple APs sharing the exact SSID but on different
 *    BSSIDs. Not perfect (hotels do this), but a cheap sanity check.
 */
object RogueNetworkMonitor {

    private val brandSsidPrefixes = listOf(
        "chase", "bofa", "boa", "wellsfargo", "wells_fargo", "citi", "capitalone",
        "amex", "apple pay", "applepay", "paypal",
        "united wifi", "unitedwifi", "deltawifi", "delta_wifi", "aawifi", "aa_wifi",
        "southwestwifi", "jetblue wifi", "lufthansa"
    )

    fun scanOnce(ctx: Context) {
        if (RogueNetworkState.isCellularEnabled(ctx)) scanCellular(ctx)
        if (RogueNetworkState.isWifiEnabled(ctx)) scanWifi(ctx)
    }

    private fun scanCellular(ctx: Context) {
        val tm = ctx.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
            ?: return
        // READ_PHONE_STATE is already in the manifest for SIM-Swap, but getDataNetworkType
        // on API 30+ requires it to be *granted*. Guard defensively.
        val granted = ContextCompat.checkSelfPermission(
            ctx, Manifest.permission.READ_PHONE_STATE
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) return
        val type = try {
            @Suppress("DEPRECATION")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) tm.dataNetworkType
            else tm.networkType
        } catch (_: SecurityException) {
            return
        }
        val gsmTypes = setOf(
            TelephonyManager.NETWORK_TYPE_GPRS,
            TelephonyManager.NETWORK_TYPE_EDGE,
            TelephonyManager.NETWORK_TYPE_CDMA,
            TelephonyManager.NETWORK_TYPE_1xRTT
        )
        if (type in gsmTypes) {
            val friendly = friendlyName(type)
            RogueNetworkState.recordAlert(
                ctx,
                RogueNetworkState.Alert(
                    kind = RogueNetworkState.AlertKind.FORCED_DOWNGRADE,
                    title = "Possible 2G downgrade attack",
                    detail = "The cellular radio dropped to $friendly. On a modern network this usually means an IMSI-catcher is forcing an insecure link. Switch to airplane mode until you've moved on.",
                    timestampMillis = System.currentTimeMillis()
                )
            )
        }
        // SIM present but no operator name is anomalous on real networks.
        val op = runCatching { tm.networkOperatorName }.getOrNull().orEmpty()
        if (tm.simState == TelephonyManager.SIM_STATE_READY && op.isBlank()) {
            RogueNetworkState.recordAlert(
                ctx,
                RogueNetworkState.Alert(
                    kind = RogueNetworkState.AlertKind.MISSING_CARRIER,
                    title = "Carrier name is blank",
                    detail = "The SIM is active but no carrier name is being reported. This is unusual on real networks and can happen on devices connected to a rogue base station.",
                    timestampMillis = System.currentTimeMillis()
                )
            )
        }
    }

    private fun friendlyName(type: Int): String = when (type) {
        TelephonyManager.NETWORK_TYPE_GPRS -> "2G/GPRS"
        TelephonyManager.NETWORK_TYPE_EDGE -> "2G/EDGE"
        TelephonyManager.NETWORK_TYPE_CDMA -> "CDMA"
        TelephonyManager.NETWORK_TYPE_1xRTT -> "CDMA 1xRTT"
        else -> "2G"
    }

    private fun scanWifi(ctx: Context) {
        val wm = ctx.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            ?: return
        val wifiGranted = ContextCompat.checkSelfPermission(
            ctx, Manifest.permission.ACCESS_WIFI_STATE
        ) == PackageManager.PERMISSION_GRANTED
        if (!wifiGranted) return
        val locationGranted = ContextCompat.checkSelfPermission(
            ctx, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        if (!locationGranted) return
        val results = try {
            @Suppress("DEPRECATION", "MissingPermission")
            wm.scanResults.orEmpty()
        } catch (_: SecurityException) {
            return
        }
        if (results.isEmpty()) return

        // Brand-SSID + open capability.
        for (r in results) {
            val ssid = r.SSID.orEmpty()
            if (ssid.isBlank()) continue
            val lowered = ssid.lowercase()
            val isOpen = r.capabilities.isNullOrBlank() ||
                !(r.capabilities.contains("WPA") || r.capabilities.contains("WEP") ||
                  r.capabilities.contains("PSK") || r.capabilities.contains("EAP"))
            if (isOpen && brandSsidPrefixes.any { lowered.startsWith(it) }) {
                RogueNetworkState.recordAlert(
                    ctx,
                    RogueNetworkState.Alert(
                        kind = RogueNetworkState.AlertKind.OPEN_WIFI_ON_BRAND_SSID,
                        title = "Lookalike Wi-Fi SSID",
                        detail = "An open AP is broadcasting \"$ssid\". Real banks and airlines don't put their brand on open Wi-Fi. Avoid connecting.",
                        timestampMillis = System.currentTimeMillis()
                    )
                )
            }
        }

        // SSID collision: more than one BSSID sharing a non-trivial SSID.
        val grouped = results.filter { !it.SSID.isNullOrBlank() }
            .groupBy { it.SSID }
            .filterValues { aps -> aps.map { it.BSSID }.distinct().size > 1 }
        for ((ssid, aps) in grouped) {
            // Skip clearly-benign cases (large hotels / airports typically broadcast
            // the same SSID across many APs; cap flag to >=3 differing BSSIDs so
            // normal mesh networks don't trip).
            if (aps.map { it.BSSID }.distinct().size < 3) continue
            RogueNetworkState.recordAlert(
                ctx,
                RogueNetworkState.Alert(
                    kind = RogueNetworkState.AlertKind.SSID_COLLISION,
                    title = "SSID collision near you",
                    detail = "At least ${aps.map { it.BSSID }.distinct().size} different Wi-Fi radios are broadcasting \"$ssid\". Common in hotels, but also a trick a rogue AP uses to impersonate yours.",
                    timestampMillis = System.currentTimeMillis()
                )
            )
        }
    }
}
