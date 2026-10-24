// SPDX-FileCopyrightText: 2026 VanPaulTek Inc
// SPDX-License-Identifier: AGPL-3.0-or-later
package com.onlytrusted.app.services

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.onlytrusted.app.ui.settings.SettingsViewModel

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        // Honor the user's in-app toggles after reboot. Previously both services
        // auto-restarted on BOOT_COMPLETED regardless of whether the user had
        // disabled them from Settings — meaning the always-on foreground notification
        // + sensor callbacks came back unprompted every reboot. MainActivity.onResume
        // uses the same gating via `maybeStartServices()`; this mirrors that logic
        // so disabled features stay disabled across reboots.
        val app = context.applicationContext as android.app.Application
        if (SettingsViewModel.isSensorMonitorEnabled(app)) {
            SensorMonitorService.start(context)
        }
        if (SettingsViewModel.isPocketDetectionEnabled(app)) {
            PocketDetectionService.start(context)
        }
    }
}
