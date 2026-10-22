// SPDX-FileCopyrightText: 2026 VanPaulTek Inc
// SPDX-License-Identifier: AGPL-3.0-or-later
package com.onlytrusted.app.ui.notifications

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBackIos
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.onlytrusted.app.ui.theme.BrandCyanDark
import com.onlytrusted.app.ui.theme.MidnightBackground
import com.onlytrusted.app.ui.theme.MidnightElevated
import com.onlytrusted.app.ui.theme.PrimaryTextDark
import com.onlytrusted.app.ui.theme.SecondaryTextDark

// Neon tints — iOS parity
private val notifTeal   = Color(0xFF00D1C1)
private val notifPurple = Color(0xFFA855F7)
private val notifOrange = Color(0xFFF59E0B)
private val notifPink   = Color(0xFFEC4899)

object NotificationPreferences {
    private const val PREFS = "notification_prefs"
    private const val KEY_ALERTS = "alerts_v1"
    private const val KEY_SUMMARY = "summary_v1"
    private const val KEY_TRUSTED = "trusted_v1"
    private const val KEY_UPDATES = "notif.updates.v1"
    private const val KEY_TIPS = "notif.tips.v1"
    private const val KEY_ACCOUNT = "notif.account.v1"

    fun protectionAlerts(ctx: Context): Boolean = prefs(ctx).getBoolean(KEY_ALERTS, true)
    fun setProtectionAlerts(ctx: Context, v: Boolean) { prefs(ctx).edit().putBoolean(KEY_ALERTS, v).apply() }

    fun dailySummary(ctx: Context): Boolean = prefs(ctx).getBoolean(KEY_SUMMARY, false)
    fun setDailySummary(ctx: Context, v: Boolean) { prefs(ctx).edit().putBoolean(KEY_SUMMARY, v).apply() }

    fun trustedContactActivity(ctx: Context): Boolean = prefs(ctx).getBoolean(KEY_TRUSTED, true)
    fun setTrustedContactActivity(ctx: Context, v: Boolean) { prefs(ctx).edit().putBoolean(KEY_TRUSTED, v).apply() }

    fun appUpdates(ctx: Context): Boolean = prefs(ctx).getBoolean(KEY_UPDATES, false)
    fun setAppUpdates(ctx: Context, v: Boolean) { prefs(ctx).edit().putBoolean(KEY_UPDATES, v).apply() }

    fun tipsAndSafety(ctx: Context): Boolean = prefs(ctx).getBoolean(KEY_TIPS, true)
    fun setTipsAndSafety(ctx: Context, v: Boolean) { prefs(ctx).edit().putBoolean(KEY_TIPS, v).apply() }

    fun accountAndDeviceAlerts(ctx: Context): Boolean = prefs(ctx).getBoolean(KEY_ACCOUNT, true)
    fun setAccountAndDeviceAlerts(ctx: Context, v: Boolean) { prefs(ctx).edit().putBoolean(KEY_ACCOUNT, v).apply() }

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}

@Composable
fun NotificationsScreen(onBack: () -> Unit = {}) {
    val ctx = LocalContext.current
    var alerts by remember { mutableStateOf(NotificationPreferences.protectionAlerts(ctx)) }
    var summary by remember { mutableStateOf(NotificationPreferences.dailySummary(ctx)) }
    var trusted by remember { mutableStateOf(NotificationPreferences.trustedContactActivity(ctx)) }
    var updates by remember { mutableStateOf(NotificationPreferences.appUpdates(ctx)) }
    var tips by remember { mutableStateOf(NotificationPreferences.tipsAndSafety(ctx)) }
    var account by remember { mutableStateOf(NotificationPreferences.accountAndDeviceAlerts(ctx)) }
    val anyOn = alerts || summary || trusted || updates || tips || account

    Column(modifier = Modifier.fillMaxSize()) {
        // Pinned top header — opaque, extends to status bar
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            MidnightBackground,
                            MidnightBackground.copy(alpha = 0.95f)
                        )
                    )
                )
                .padding(horizontal = 18.dp, vertical = 12.dp)
        ) {
            // --- Centered header with back + title + subtitle ---
            Header(onBack = onBack)
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp)
                .padding(top = 4.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            NotifToggleCard(
            icon = Icons.Filled.Verified,
            tint = notifTeal,
            title = "Protection Alerts",
            detail = "Important blocks and security events.",
            checked = alerts
        ) {
            alerts = it
            NotificationPreferences.setProtectionAlerts(ctx, it)
        }
        NotifToggleCard(
            icon = Icons.Filled.BarChart,
            tint = notifTeal,
            title = "Daily Summary",
            detail = "A brief summary of your activity.",
            checked = summary
        ) {
            summary = it
            NotificationPreferences.setDailySummary(ctx, it)
        }
        NotifToggleCard(
            icon = Icons.Filled.Groups,
            tint = notifTeal,
            title = "Trusted Contact Activity",
            detail = "Calls and texts from your trusted contacts.",
            checked = trusted
        ) {
            trusted = it
            NotificationPreferences.setTrustedContactActivity(ctx, it)
        }
        NotifToggleCard(
            icon = Icons.Filled.Notifications,
            tint = notifPurple,
            title = "App Updates",
            detail = "New features and improvements (never marketing).",
            checked = updates
        ) {
            updates = it
            NotificationPreferences.setAppUpdates(ctx, it)
        }
        NotifToggleCard(
            icon = Icons.AutoMirrored.Filled.Message,
            tint = notifOrange,
            title = "Tips & Safety Insights",
            detail = "Helpful tips to stay safe and protected.",
            checked = tips
        ) {
            tips = it
            NotificationPreferences.setTipsAndSafety(ctx, it)
        }
        NotifToggleCard(
            icon = Icons.Filled.Settings,
            tint = notifPink,
            title = "Account & Device Alerts",
            detail = "Important changes to your account or device.",
            checked = account
        ) {
            account = it
            NotificationPreferences.setAccountAndDeviceAlerts(ctx, it)
        }

        if (anyOn) SuccessBanner()
        }
    }
}

@Composable
private fun Header(onBack: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            // Back link, left-aligned
            Surface(
                onClick = onBack,
                color = Color.Transparent,
                modifier = Modifier.align(Alignment.CenterStart)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBackIos,
                        contentDescription = "Back",
                        tint = BrandCyanDark,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        "Settings",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = BrandCyanDark
                    )
                }
            }
            // Centered title
            Text(
                "Notifications",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = PrimaryTextDark,
                modifier = Modifier.align(Alignment.Center)
            )
        }
        Text(
            "Control what you want to be notified about.",
            fontSize = 14.sp,
            color = SecondaryTextDark,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
        )
    }
}

@Composable
private fun NotifToggleCard(
    icon: ImageVector,
    tint: Color,
    title: String,
    detail: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MidnightElevated,
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = BrandCyanDark.copy(alpha = 0.10f),
                shape = RoundedCornerShape(16.dp)
            )
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(14.dp)
        ) {
            NeonIconChip(icon = icon, tint = tint)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    title,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = PrimaryTextDark
                )
                Text(
                    detail,
                    fontSize = 13.sp,
                    color = SecondaryTextDark
                )
            }
            Spacer(Modifier.width(8.dp))
            Switch(
                checked = checked,
                onCheckedChange = onChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = BrandCyanDark
                )
            )
        }
    }
}

/**
 * Neon icon chip — 46dp box with 1.5dp stroke of tint, tint.alpha=0.15 background,
 * drop shadow in tint color, icon rendered in tint color.
 */
@Composable
private fun NeonIconChip(icon: ImageVector, tint: Color) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(46.dp)
            .shadow(
                elevation = 10.dp,
                shape = RoundedCornerShape(11.dp),
                ambientColor = tint,
                spotColor = tint
            )
            .background(
                color = tint.copy(alpha = 0.15f),
                shape = RoundedCornerShape(11.dp)
            )
            .border(
                width = 1.5.dp,
                color = tint.copy(alpha = 0.7f),
                shape = RoundedCornerShape(11.dp)
            )
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
private fun SuccessBanner() {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MidnightElevated.copy(alpha = 0.5f),
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp)
            .border(
                width = 1.dp,
                color = BrandCyanDark.copy(alpha = 0.55f),
                shape = RoundedCornerShape(16.dp)
            )
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(14.dp)
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(46.dp)
                    .shadow(
                        elevation = 10.dp,
                        shape = CircleShape,
                        ambientColor = BrandCyanDark,
                        spotColor = BrandCyanDark
                    )
                    .border(
                        width = 2.dp,
                        color = BrandCyanDark.copy(alpha = 0.6f),
                        shape = CircleShape
                    )
            ) {
                Icon(
                    Icons.Filled.Notifications,
                    contentDescription = null,
                    tint = BrandCyanDark,
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    "You're all set!",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = BrandCyanDark
                )
                Text(
                    "Notifications are on (except selected options).",
                    fontSize = 13.sp,
                    color = SecondaryTextDark
                )
            }
        }
    }
}
