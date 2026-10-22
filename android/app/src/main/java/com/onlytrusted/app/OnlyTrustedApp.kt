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
import com.onlytrusted.app.util.NotificationHelper

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
