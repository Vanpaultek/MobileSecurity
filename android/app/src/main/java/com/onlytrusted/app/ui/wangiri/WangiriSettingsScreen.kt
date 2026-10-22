// SPDX-FileCopyrightText: 2026 VanPaulTek Inc
// SPDX-License-Identifier: AGPL-3.0-or-later
package com.onlytrusted.app.ui.wangiri

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PhoneDisabled
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.onlytrusted.app.ui.theme.BrandAccentDark
import com.onlytrusted.app.ui.theme.BrandCyanDark
import com.onlytrusted.app.ui.theme.DangerDark
import com.onlytrusted.app.ui.theme.MidnightBackground
import com.onlytrusted.app.ui.theme.MidnightElevated
import com.onlytrusted.app.ui.theme.PrimaryTextDark
import com.onlytrusted.app.ui.theme.SecondaryTextDark
import com.onlytrusted.app.ui.theme.WarningDark
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Pro-only Wangiri Protection settings screen. The entry row in Settings → Security & data
 * gates this behind `SubscriptionState.hasHighPremium`; non-Pro users see an upsell dialog
 * before they can reach this screen.
 */
@Composable
fun WangiriSettingsScreen() {
    val context = LocalContext.current
    var enabled by remember { mutableStateOf(WangiriPrefs.isEnabled(context)) }
    var monthly by remember { mutableStateOf(WangiriPrefs.blockedThisMonth(context)) }
    var recent by remember { mutableStateOf(WangiriPrefs.recentBlocks(context)) }

    // Refresh counts every time the screen opens — a wangiri hangup could have landed
    // while the user was elsewhere.
    LaunchedEffect(Unit) {
        monthly = WangiriPrefs.blockedThisMonth(context)
        recent = WangiriPrefs.recentBlocks(context)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MidnightBackground)
            .verticalScroll(rememberScrollState())
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Card {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.PhoneDisabled,
                        contentDescription = null,
                        tint = BrandAccentDark
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        "One-ring hangup blocker",
                        color = PrimaryTextDark,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            "Enabled",
                            color = PrimaryTextDark,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            "Watches your phone line for ring-and-drop scam calls and auto-blocks them.",
                            color = SecondaryTextDark,
                            fontSize = 12.sp
                        )
                    }
                    Switch(
                        checked = enabled,
                        onCheckedChange = { want ->
                            enabled = want
                            WangiriPrefs.setEnabled(context, want)
                            if (want) {
                                WangiriWatcherService.startIfEligible(context)
                            } else {
                                WangiriWatcherService.stop(context)
                            }
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = BrandCyanDark
                        )
                    )
                }
                Divider(color = Color.White.copy(alpha = 0.06f))
                Row {
                    Column(Modifier.weight(1f)) {
                        Text(
                            "Blocked this month",
                            color = SecondaryTextDark,
                            fontSize = 12.sp
                        )
                        Text(
                            "$monthly",
                            color = BrandAccentDark,
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Icon(
                        Icons.Filled.PhoneDisabled,
                        contentDescription = null,
                        tint = BrandAccentDark.copy(alpha = 0.4f),
                        modifier = Modifier.size(36.dp)
                    )
                }
            }
        }

        Card {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "Recent wangiri blocks",
                    color = PrimaryTextDark,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold
                )
                if (recent.isEmpty()) {
                    Text(
                        "No one-ring hangups detected yet. If a wangiri call comes in while " +
                            "Pro is active it will appear here and the number will be auto-added " +
                            "to your Blocked list.",
                        color = SecondaryTextDark,
                        fontSize = 12.sp
                    )
                } else {
                    recent.forEach { block ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Filled.PhoneDisabled,
                                contentDescription = null,
                                tint = DangerDark,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    block.number ?: "Unknown number",
                                    color = PrimaryTextDark,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    "${formatDate(block.timestampMillis)} · rang for ${
                                        String.format(
                                            Locale.US,
                                            "%.1fs",
                                            block.elapsedMillis / 1000.0
                                        )
                                    }",
                                    color = SecondaryTextDark,
                                    fontSize = 11.sp
                                )
                            }
                        }
                        Spacer(Modifier.height(4.dp))
                    }
                }
            }
        }

        Card {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Info, contentDescription = null, tint = BrandCyanDark)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "What is wangiri?",
                        color = PrimaryTextDark,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Text(
                    "Wangiri (Japanese for 'one ring and cut') is a scam where a premium-rate " +
                        "international number dials you, rings for under two seconds, and hangs " +
                        "up. The scammer hopes you call back out of curiosity. The callback " +
                        "routes through a premium number that charges you per minute.",
                    color = SecondaryTextDark,
                    fontSize = 12.sp
                )
                Text(
                    "OnlyTrusted catches these in your Phone State events and auto-adds the " +
                        "caller to your blocked list.",
                    color = SecondaryTextDark,
                    fontSize = 12.sp
                )
            }
        }

        Card(borderColor = WarningDark.copy(alpha = 0.4f)) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Info, contentDescription = null, tint = WarningDark)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Needs the Phone permission",
                        color = PrimaryTextDark,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Text(
                    "This feature uses READ_PHONE_STATE (already requested by SIM-Swap Alarm). " +
                        "If you denied it, open the system app info and re-grant it; the watcher " +
                        "will come back online the next time you open OnlyTrusted.",
                    color = SecondaryTextDark,
                    fontSize = 12.sp
                )
            }
        }
    }
}

@Composable
private fun Card(
    borderColor: Color? = null,
    content: @Composable () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MidnightElevated,
        border = borderColor?.let { androidx.compose.foundation.BorderStroke(1.dp, it) },
        modifier = Modifier.fillMaxWidth()
    ) { content() }
}

private fun formatDate(millis: Long): String =
    SimpleDateFormat("MMM d, h:mm a", Locale.US).format(Date(millis))
