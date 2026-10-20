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
package com.mobilesecurity.app.services

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.mobilesecurity.app.security.AppLockManager

/**
 * React to screen on/off events.
 *
 * We used to log every flip, but the user never acts on that data and it balloons the DB on
 * phones that get picked up dozens of times an hour. Now we only use the signal to invalidate
 * the app-lock grace window so the next time the app is opened it re-prompts.
 */
class LockStateReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_SCREEN_OFF) return
        AppLockManager.resetUnlock(context)
    }
}
