// SPDX-FileCopyrightText: 2026 VanPaulTek Inc
// SPDX-License-Identifier: AGPL-3.0-or-later
package com.onlytrusted.app.ui.advancedblocking

import android.app.TimePickerDialog
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BedtimeOff
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Nightlight
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.onlytrusted.app.ui.subscription.SubscriptionState
import com.onlytrusted.app.ui.subscription.subGold
import com.onlytrusted.app.ui.theme.AppBackdropScaffold
import com.onlytrusted.app.ui.theme.BrandAccentDark
import com.onlytrusted.app.ui.theme.BrandCyanDark
import com.onlytrusted.app.ui.theme.MidnightBackground
import com.onlytrusted.app.ui.theme.MidnightElevated
import com.onlytrusted.app.ui.theme.PrimaryTextDark
import com.onlytrusted.app.ui.theme.SecondaryTextDark
import com.onlytrusted.app.ui.theme.WarningDark

/** Premium Advanced Blocking screen (Android). Mirrors iOS `AdvancedBlockingScreen`. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdvancedBlockingScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val isPremium = SubscriptionState.hasPremium(context)

    var quietEnabled by remember { mutableStateOf(AdvancedBlocking.quietHoursEnabled(context)) }
    var quietStart by remember { mutableStateOf(AdvancedBlocking.quietStartMinutes(context)) }
    var quietEnd by remember { mutableStateOf(AdvancedBlocking.quietEndMinutes(context)) }
    var areaCodesDraft by remember {
        mutableStateOf(AdvancedBlocking.areaCodeAllowlist(context).joinToString(", "))
    }
    var areaCodesSaved by remember { mutableStateOf(AdvancedBlocking.areaCodeAllowlist(context)) }

    AppBackdropScaffold(
        topBar = {
            TopAppBar(
                title = { Text("Advanced Blocking", color = PrimaryTextDark) },
                navigationIcon = {
                    TextButton(onClick = onBack) { Text("Back", color = BrandCyanDark) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
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
            CardBox {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    HeaderRow(Icons.Filled.FilterList, "Advanced Blocking", BrandAccentDark)
                    Text(
                        "Finer-grained rules for who can reach you and when. Rules are " +
                            "stored on this device and applied by the call + SMS routing paths.",
                        fontSize = 12.sp, color = SecondaryTextDark
                    )
                }
            }

            if (!isPremium) {
                CardBox {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.WorkspacePremium, null, tint = subGold)
                            Spacer(Modifier.size(8.dp))
                            Text(
                                "Advanced Blocking (Premium)",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = PrimaryTextDark
                            )
                        }
                        Text(
                            "Premium (\$4.99/yr) unlocks Quiet Hours (only trusted contacts " +
                                "ring at night) and an area-code allowlist (whitelist specific " +
                                "NANP area codes so they're never flagged as unknown).",
                            fontSize = 11.sp, color = SecondaryTextDark
                        )
                    }
                }
                return@Column
            }

            // Quiet hours
            CardBox {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    HeaderRow(Icons.Filled.Nightlight, "Quiet Hours", BrandAccentDark)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "Only trusted contacts ring during the window below",
                            fontSize = 13.sp, color = PrimaryTextDark,
                            modifier = Modifier.weight(1f)
                        )
                        Switch(
                            checked = quietEnabled,
                            onCheckedChange = {
                                quietEnabled = it
                                AdvancedBlocking.setQuietHoursEnabled(context, it)
                            },
                            colors = SwitchDefaults.colors(checkedThumbColor = BrandAccentDark)
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        TimeField(
                            label = "From",
                            minutes = quietStart,
                            onPick = {
                                TimePickerDialog(context, { _, h, m ->
                                    val v = h * 60 + m
                                    quietStart = v
                                    AdvancedBlocking.setQuietStartMinutes(context, v)
                                }, quietStart / 60, quietStart % 60, true).show()
                            },
                            modifier = Modifier.weight(1f)
                        )
                        TimeField(
                            label = "To",
                            minutes = quietEnd,
                            onPick = {
                                TimePickerDialog(context, { _, h, m ->
                                    val v = h * 60 + m
                                    quietEnd = v
                                    AdvancedBlocking.setQuietEndMinutes(context, v)
                                }, quietEnd / 60, quietEnd % 60, true).show()
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Text(
                        "Windows that cross midnight (e.g. 22:00 → 07:00) are supported.",
                        fontSize = 10.sp, color = SecondaryTextDark
                    )
                }
            }

            // Area codes
            CardBox {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    HeaderRow(Icons.Filled.PhoneAndroid, "Area-code allowlist", BrandAccentDark)
                    Text(
                        "Comma-separated 3-digit codes. Any inbound caller from one of these " +
                            "area codes is never flagged as unknown-caller spam, even if " +
                            "they're not in your vault.",
                        fontSize = 11.sp, color = SecondaryTextDark
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = areaCodesDraft,
                            onValueChange = { areaCodesDraft = it },
                            singleLine = true,
                            placeholder = { Text("e.g. 415, 212, 650") },
                            modifier = Modifier.weight(1f)
                        )
                        Button(
                            onClick = {
                                val parsed = areaCodesDraft
                                    .split(Regex("[^0-9]+"))
                                    .filter { it.length == 3 }
                                AdvancedBlocking.setAreaCodeAllowlist(context, parsed)
                                areaCodesSaved = AdvancedBlocking.areaCodeAllowlist(context)
                                areaCodesDraft = areaCodesSaved.joinToString(", ")
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = BrandAccentDark)
                        ) {
                            Text("Save", color = Color.White, fontSize = 12.sp)
                        }
                    }
                }
            }

            // Live status
            CardBox {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    HeaderRow(Icons.Filled.Visibility, "Right now", SecondaryTextDark)
                    val quietNow = AdvancedBlocking.isQuietHoursNow(context)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            if (quietNow) Icons.Filled.Nightlight else Icons.Filled.BedtimeOff,
                            null,
                            tint = if (quietNow) BrandAccentDark else WarningDark
                        )
                        Spacer(Modifier.size(8.dp))
                        Text(
                            if (quietNow) "Quiet hours are active" else "Quiet hours are OFF",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = PrimaryTextDark
                        )
                    }
                    Text(
                        "Allowlisted area codes: " +
                            if (areaCodesSaved.isEmpty()) "none" else areaCodesSaved.joinToString(", "),
                        fontSize = 11.sp, color = SecondaryTextDark
                    )
                }
            }
        }
    }
}

@Composable
private fun TimeField(label: String, minutes: Int, onPick: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, fontSize = 11.sp, color = SecondaryTextDark)
        TextButton(
            onClick = onPick,
            modifier = Modifier.fillMaxWidth()
        ) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MidnightBackground,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    AdvancedBlocking.minutesLabel(minutes),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = PrimaryTextDark,
                    modifier = Modifier.padding(12.dp)
                )
            }
        }
    }
}

@Composable
private fun HeaderRow(icon: ImageVector, title: String, tint: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Surface(shape = RoundedCornerShape(10.dp), color = tint) {
            Icon(icon, null, tint = Color.White, modifier = Modifier.padding(8.dp))
        }
        Text(title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = PrimaryTextDark)
    }
}

@Composable
private fun CardBox(content: @Composable () -> Unit) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MidnightElevated,
        modifier = Modifier.fillMaxWidth()
    ) { content() }
}
