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
