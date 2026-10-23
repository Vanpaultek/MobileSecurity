// SPDX-FileCopyrightText: 2026 VanPaulTek Inc
// SPDX-License-Identifier: AGPL-3.0-or-later
package com.onlytrusted.app.ui.roguenetwork

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
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
import com.onlytrusted.app.ui.subscription.subGold
import com.onlytrusted.app.ui.theme.BrandCyanDark
import com.onlytrusted.app.ui.theme.MidnightBackground
import com.onlytrusted.app.ui.theme.MidnightCard
import com.onlytrusted.app.ui.theme.MidnightElevated
import com.onlytrusted.app.ui.theme.PrimaryTextDark
import com.onlytrusted.app.ui.theme.SecondaryTextDark
import com.onlytrusted.app.ui.theme.TertiaryTextDark
import com.onlytrusted.app.ui.theme.WarningDark
import java.text.DateFormat
import java.util.Date

/** Elite-tier Rogue Network Scanner UI — mirrors iOS `RogueNetworkScreen`. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RogueNetworkScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var cellular by remember { mutableStateOf(RogueNetworkState.isCellularEnabled(context)) }
    var wifi by remember { mutableStateOf(RogueNetworkState.isWifiEnabled(context)) }
    var alerts by remember { mutableStateOf(RogueNetworkState.alerts(context)) }
    var clearConfirm by remember { mutableStateOf(false) }

    androidx.compose.runtime.LaunchedEffect(Unit) {
        RogueNetworkMonitor.scanOnce(context)
        alerts = RogueNetworkState.alerts(context)
    }

    Scaffold(
        containerColor = MidnightBackground,
        topBar = {
            TopAppBar(
                title = { Text("Rogue Network Scanner", color = PrimaryTextDark) },
                navigationIcon = {
                    TextButton(onClick = onBack) { Text("Back", color = BrandCyanDark) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MidnightBackground)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            HeaderCard()

            Card {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("What we watch", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = PrimaryTextDark)
                    ToggleRow(
                        label = "Cellular monitoring",
                        checked = cellular,
                        onChange = {
                            cellular = it
                            RogueNetworkState.setCellularEnabled(context, it)
                        }
                    )
                    ToggleRow(
                        label = "Wi-Fi monitoring",
                        checked = wifi,
                        onChange = {
                            wifi = it
                            RogueNetworkState.setWifiEnabled(context, it)
                        }
                    )
                    Text(
                        "Wi-Fi scans need the location permission you granted for Geo-fence. Without it the Wi-Fi probe is a safe no-op; cellular monitoring works regardless.",
                        fontSize = 11.sp, color = SecondaryTextDark
                    )
                }
            }

            Button(
                onClick = {
                    RogueNetworkMonitor.scanOnce(context)
                    alerts = RogueNetworkState.alerts(context)
                },
                colors = ButtonDefaults.buttonColors(containerColor = BrandCyanDark, contentColor = Color.Black),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Filled.Refresh, null, modifier = Modifier.padding(end = 8.dp))
                Text("Scan now", fontWeight = FontWeight.SemiBold)
            }

            Card {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.NotificationsActive, null, tint = BrandCyanDark)
                        Spacer(Modifier.width(8.dp))
                        Text("Recent alerts", fontSize = 14.sp,
                             fontWeight = FontWeight.SemiBold,
                             color = PrimaryTextDark,
                             modifier = Modifier.weight(1f))
                        if (alerts.isNotEmpty()) {
                            TextButton(onClick = { clearConfirm = true }) {
                                Text("Clear", color = BrandCyanDark, fontSize = 12.sp)
                            }
                        }
                    }
                    if (alerts.isEmpty()) {
                        Text(
                            "Nothing suspicious yet. We'll notify you here if a rogue tower or lookalike Wi-Fi lights up during a future scan.",
                            fontSize = 12.sp, color = SecondaryTextDark
                        )
                    } else {
                        alerts.forEachIndexed { idx, alert ->
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Filled.Warning, null, tint = WarningDark)
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        alert.title,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = PrimaryTextDark
                                    )
                                }
                                Text(alert.detail, fontSize = 12.sp, color = SecondaryTextDark)
                                Text(
                                    DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
                                        .format(Date(alert.timestampMillis)),
                                    fontSize = 10.sp, color = TertiaryTextDark
                                )
                            }
                            if (idx != alerts.lastIndex) {
                                Divider(color = Color.White.copy(alpha = 0.06f))
                            }
                        }
                    }
                }
            }

            Card {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("What we check", fontSize = 13.sp,
                         fontWeight = FontWeight.SemiBold, color = PrimaryTextDark)
                    Text(
                        "• Forced 2G/EDGE/GPRS downgrade (classic IMSI-catcher symptom).\n" +
                        "• Blank carrier name while a SIM is active.\n" +
                        "• Open Wi-Fi broadcasting a bank or airline brand prefix.\n" +
                        "• 3+ different radios sharing one SSID near you.\n" +
                        "All checks run strictly on-device — nothing is uploaded.",
                        fontSize = 11.sp, color = SecondaryTextDark
                    )
                }
            }

            Spacer(Modifier.height(8.dp))
        }

        if (clearConfirm) {
            AlertDialog(
                onDismissRequest = { clearConfirm = false },
                title = { Text("Clear alerts?", color = PrimaryTextDark) },
                text = { Text("The recent-alerts list will be emptied. New alerts will appear on the next scan.", color = SecondaryTextDark) },
                confirmButton = {
                    TextButton(onClick = {
                        RogueNetworkState.clearAlerts(context)
                        alerts = emptyList()
                        clearConfirm = false
                    }) { Text("Clear") }
                },
                dismissButton = {
                    TextButton(onClick = { clearConfirm = false }) { Text("Cancel") }
                },
                containerColor = MidnightCard
            )
        }
    }
}

@Composable
private fun HeaderCard() {
    Card {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Surface(shape = RoundedCornerShape(12.dp), color = subGold) {
                Icon(Icons.Filled.NetworkCheck, null, tint = Color.White,
                     modifier = Modifier.padding(10.dp))
            }
            Column {
                Text("Elite feature", fontSize = 11.sp, color = subGold, fontWeight = FontWeight.SemiBold)
                Text("Watch for rogue cell towers + lookalike Wi-Fi.", fontSize = 15.sp,
                     fontWeight = FontWeight.SemiBold, color = PrimaryTextDark)
                Text(
                    "OnlyTrusted samples the active radio tech and nearby SSIDs at launch. A forced 2G downgrade or an open AP broadcasting a bank/airline SSID lands here as an alert. 100% on-device.",
                    fontSize = 12.sp, color = SecondaryTextDark
                )
            }
        }
    }
}

@Composable
private fun Card(content: @Composable () -> Unit) {
    Surface(shape = RoundedCornerShape(16.dp), color = MidnightElevated,
            modifier = Modifier.fillMaxWidth()) { content() }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()) {
        Text(label, fontSize = 14.sp, color = PrimaryTextDark, modifier = Modifier.weight(1f))
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

@Composable
fun RogueNetworkUpsellDialog(onUpgrade: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Rogue Network Scanner is an Elite feature",
                 color = PrimaryTextDark, fontWeight = FontWeight.Bold)
        },
        text = {
            Text(
                "Spot IMSI-catchers and lookalike bank Wi-Fi before you connect. OnlyTrusted checks the cellular radio and SSID on launch — a forced 2G downgrade or an open \"CHASE_WIFI\" sends an alert straight to this screen. Elite tier.",
                color = SecondaryTextDark
            )
        },
        confirmButton = {
            TextButton(onClick = onUpgrade) { Text("Upgrade to Elite", color = subGold) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Not now") } },
        containerColor = MidnightCard
    )
}
