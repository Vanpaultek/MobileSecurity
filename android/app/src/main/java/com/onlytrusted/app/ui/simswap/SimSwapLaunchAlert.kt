// SPDX-FileCopyrightText: 2026 VanPaulTek Inc
// SPDX-License-Identifier: AGPL-3.0-or-later
package com.onlytrusted.app.ui.simswap

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import com.onlytrusted.app.data.db.entities.SecurityEvent
import com.onlytrusted.app.ui.subscription.SubscriptionState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * App-root composable that:
 *   1. Re-checks the SIM identity on every composition (effectively every launch/resume).
 *   2. Logs a `SIM_SWAP` security event when a mismatch is detected.
 *   3. Renders the non-dismissable `SimSwapAlarmDialog` while [SimSwapState.pendingAlarm]
 *      is non-null.
 *
 * The dialog's "Lock app now" button calls [onLock] (which should invoke
 * `AppLockManager.resetUnlock` so the next `shouldPrompt` returns true).
 */
@Composable
fun SimSwapLaunchAlert(onLock: () -> Unit) {
    val context = LocalContext.current
    val alarm by SimSwapState.pendingAlarm.collectAsState()

    LaunchedEffect(Unit) {
        if (!SubscriptionState.hasHighPremium(context)) return@LaunchedEffect
        val fired = SimSwapState.checkOnForeground(context) ?: return@LaunchedEffect
        // Log asynchronously so a hash-chain write doesn't block the UI thread.
        val app = context.applicationContext as com.onlytrusted.app.OnlyTrustedApp
        CoroutineScope(Dispatchers.IO).launch {
            app.repository.log(
                type = SecurityEvent.TYPE_SIM_SWAP,
                detail = "old=${fired.oldHashShort}… new=${fired.newHashShort}… " +
                    "display=${fired.currentDisplay}"
            )
        }
    }

    val a = alarm ?: return
    SimSwapAlarmDialog(
        alarm = a,
        onLock = {
            SimSwapState.dismissAlarm()
            onLock()
        },
        onAcknowledge = {
            SimSwapState.acknowledgeAndRebaseline(context)
        }
    )
}
