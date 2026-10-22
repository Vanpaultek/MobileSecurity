// SPDX-FileCopyrightText: 2026 VanPaulTek Inc
// SPDX-License-Identifier: AGPL-3.0-or-later
package com.onlytrusted.app.services

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.onlytrusted.app.security.AppLockManager

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
