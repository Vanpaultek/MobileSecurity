// SPDX-FileCopyrightText: 2026 57Million LLC
// SPDX-License-Identifier: AGPL-3.0-or-later
package com.mobilesecurity.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import com.mobilesecurity.app.data.db.AppDatabase
import com.mobilesecurity.app.data.repository.SecurityRepository
import com.mobilesecurity.app.data.repository.TrustedContactsRepository
import com.mobilesecurity.app.util.NotificationHelper

class MobileSecurityApp : Application() {

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
        lateinit var instance: MobileSecurityApp
            private set
    }
}
