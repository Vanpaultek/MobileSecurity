// SPDX-FileCopyrightText: 2026 VanPaulTek Inc
// SPDX-License-Identifier: AGPL-3.0-or-later
package com.onlytrusted.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import com.onlytrusted.app.data.db.AppDatabase
import com.onlytrusted.app.data.repository.SecurityRepository
import com.onlytrusted.app.data.repository.TrustedContactsRepository
import com.onlytrusted.app.ui.privacyshield.CaptureRegistry
import com.onlytrusted.app.ui.roguenetwork.RogueNetworkMonitor
import com.onlytrusted.app.ui.smartlinkguard.SmartLinkGuardRules
import com.onlytrusted.app.ui.threatintel.ThreatIntelDatabase
import com.onlytrusted.app.ui.wangiri.WangiriPrefs
import com.onlytrusted.app.util.NotificationHelper
import kotlin.concurrent.thread

class OnlyTrustedApp : Application() {

    val database: AppDatabase by lazy { AppDatabase.getInstance(this) }
    val repository: SecurityRepository by lazy {
        SecurityRepository(database.eventDao(), database.blockedNumberDao())
    }
    val trustedContacts: TrustedContactsRepository by lazy {
        TrustedContactsRepository(database.trustedContactDao())
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        createNotificationChannels()
        // Auto Privacy Shield (free tier): attach the process-wide lifecycle observer so
        // every live camera/mic capture OnlyTrusted owns tears down on ON_PAUSE. The
        // SCREEN_OFF receiver is armed lazily by features on their first registration.
        CaptureRegistry.attach(this)
        // Fortress R4 recovery: Rogue Network Scanner (Elite). Snapshot the radio
        // + Wi-Fi state once at launch so a 2G downgrade or lookalike SSID lands as
        // an alert even before the user visits the dedicated screen.
        //
        // Fired on a background thread so the Binder IPC into TelephonyManager +
        // WifiManager + the resulting EncryptedSharedPreferences write don't block
        // Application.onCreate and delay first paint of MainActivity.
        thread(name = "rogue-network-boot-scan", isDaemon = true) {
            runCatching { RogueNetworkMonitor.scanOnce(this) }
        }
        // Smart Link Guard ↔ Offline Threat Intel bridge. Lets the Smart Link Guard
        // engine consult the bundled APWG phishing-URL set when inspecting URLs.
        SmartLinkGuardRules.phishingHostLookup = { host ->
            runCatching { ThreatIntelDatabase.get(this).isPhishingHost(host) }
                .getOrDefault(false)
        }
        // Wangiri Protection ↔ Offline Threat Intel bridge. When this returns true,
        // Wangiri can auto-block immediately instead of waiting for the ring-timing
        // heuristic to fire.
        WangiriPrefs.scamNumberLookup = { number ->
            runCatching { ThreatIntelDatabase.get(this).isScamNumber(number) }
                .getOrDefault(false)
        }
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java) ?: return

        manager.createNotificationChannel(
            NotificationChannel(
                NotificationHelper.CHANNEL_MONITOR,
                "Active monitoring",
                NotificationManager.IMPORTANCE_LOW
            ).apply { description = "Shown while the app is watching your mic and camera." }
        )

        manager.createNotificationChannel(
            NotificationChannel(
                NotificationHelper.CHANNEL_ALERTS,
                "Security alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Mic or camera usage, blocked calls, pocket-dial attempts."
                enableVibration(true)
            }
        )
    }

    companion object {
        lateinit var instance: OnlyTrustedApp
            private set
    }
}
