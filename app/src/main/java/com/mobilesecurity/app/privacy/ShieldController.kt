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
package com.mobilesecurity.app.privacy

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import com.mobilesecurity.app.services.WatermarkOverlayService

/**
 * Thin façade around the watermark overlay service so UI code does not need to know about the
 * foreground-service plumbing or the SYSTEM_ALERT_WINDOW permission dance.
 */
object ShieldController {

    fun canDraw(ctx: Context): Boolean = Settings.canDrawOverlays(ctx)

    fun openOverlayPermissionSettings(ctx: Context) {
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:${ctx.packageName}")
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { ctx.startActivity(intent) }
    }

    fun turnOn(ctx: Context): Boolean {
        if (!canDraw(ctx)) return false
        WatermarkOverlayService.start(ctx)
        return true
    }

    fun turnOff(ctx: Context) {
        WatermarkOverlayService.stop(ctx)
    }
}
