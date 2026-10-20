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
