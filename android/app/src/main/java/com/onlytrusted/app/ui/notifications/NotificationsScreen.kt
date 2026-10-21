// SPDX-FileCopyrightText: 2026 57Million LLC
// SPDX-License-Identifier: AGPL-3.0-or-later
package com.onlytrusted.app.ui.notifications

import android.content.Context
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.onlytrusted.app.ui.theme.BrandAccentDark
import com.onlytrusted.app.ui.theme.PrimaryTextDark
import com.onlytrusted.app.ui.theme.SecondaryTextDark
import com.onlytrusted.app.ui.theme.SuccessDark
import com.onlytrusted.app.ui.theme.TertiaryTextDark

object NotificationPreferences {
    private const val PREFS = "notification_prefs"
    private const val KEY_ALERTS = "alerts_v1"
    private const val KEY_SUMMARY = "summary_v1"
    private const val KEY_TRUSTED = "trusted_v1"

    fun protectionAlerts(ctx: Context): Boolean = prefs(ctx).getBoolean(KEY_ALERTS, true)
    fun setProtectionAlerts(ctx: Context, v: Boolean) { prefs(ctx).edit().putBoolean(KEY_ALERTS, v).apply() }

    fun dailySummary(ctx: Context): Boolean = prefs(ctx).getBoolean(KEY_SUMMARY, false)
    fun setDailySummary(ctx: Context, v: Boolean) { prefs(ctx).edit().putBoolean(KEY_SUMMARY, v).apply() }

    fun trustedContactActivity(ctx: Context): Boolean = prefs(ctx).getBoolean(KEY_TRUSTED, true)
    fun setTrustedContactActivity(ctx: Context, v: Boolean) { prefs(ctx).edit().putBoolean(KEY_TRUSTED, v).apply() }

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}

@Composable
fun NotificationsScreen() {
    val ctx = LocalContext.current
    var alerts by remember { mutableStateOf(NotificationPreferences.protectionAlerts(ctx)) }
    var summary by remember { mutableStateOf(NotificationPreferences.dailySummary(ctx)) }
    var trusted by remember { mutableStateOf(NotificationPreferences.trustedContactActivity(ctx)) }
    val anyOn = alerts || summary || trusted

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Notifications", style = MaterialTheme.typography.headlineMedium)

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(vertical = 4.dp)) {
                NotifToggleRow(
                    icon = Icons.Filled.Notifications,
                    title = "Protection Alerts",
                    detail = "Important blocks and security events.",
                    checked = alerts
                ) {
                    alerts = it
                    NotificationPreferences.setProtectionAlerts(ctx, it)
                }
                HorizontalDivider(Modifier.padding(start = 68.dp, end = 20.dp))
                NotifToggleRow(
                    icon = Icons.Filled.WbSunny,
                    title = "Daily Summary",
                    detail = "A brief summary of activity.",
                    checked = summary
                ) {
                    summary = it
                    NotificationPreferences.setDailySummary(ctx, it)
                }
                HorizontalDivider(Modifier.padding(start = 68.dp, end = 20.dp))
                NotifToggleRow(
                    icon = Icons.Filled.Groups,
                    title = "Trusted Contact Activity",
                    detail = "Calls and texts from trusted contacts.",
                    checked = trusted
                ) {
                    trusted = it
                    NotificationPreferences.setTrustedContactActivity(ctx, it)
                }
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(16.dp)
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(36.dp)
                        .background(TertiaryTextDark.copy(alpha = 0.18f), RoundedCornerShape(10.dp))
                ) {
                    Icon(Icons.Filled.AutoAwesome, contentDescription = null, tint = TertiaryTextDark)
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        "New Features",
                        color = PrimaryTextDark,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        "Updates and improvements (never marketing).",
                        color = SecondaryTextDark,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Switch(checked = false, onCheckedChange = {}, enabled = false)
            }
        }

        if (anyOn) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(16.dp)
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(36.dp)
                            .background(SuccessDark.copy(alpha = 0.18f), RoundedCornerShape(50))
                    ) {
                        Icon(
                            Icons.Filled.Notifications,
                            contentDescription = null,
                            tint = SuccessDark
                        )
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            "You're all set!",
                            color = PrimaryTextDark,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            "Notifications are on.",
                            color = SecondaryTextDark,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun NotifToggleRow(
    icon: ImageVector,
    title: String,
    detail: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(36.dp)
                .background(BrandAccentDark.copy(alpha = 0.14f), RoundedCornerShape(10.dp))
        ) {
            Icon(icon, contentDescription = null, tint = BrandAccentDark)
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                title,
                color = PrimaryTextDark,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(detail, color = SecondaryTextDark, style = MaterialTheme.typography.bodySmall)
        }
        Spacer(Modifier.width(8.dp))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
