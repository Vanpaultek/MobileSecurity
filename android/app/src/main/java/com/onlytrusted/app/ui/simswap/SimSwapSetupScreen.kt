// SPDX-FileCopyrightText: 2026 VanPaulTek Inc
// SPDX-License-Identifier: AGPL-3.0-or-later
package com.onlytrusted.app.ui.simswap

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.SimCard
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.onlytrusted.app.ui.theme.BrandAccentDark
import com.onlytrusted.app.ui.theme.BrandCyanDark
import com.onlytrusted.app.ui.theme.DangerDark
import com.onlytrusted.app.ui.theme.MidnightBackground
import com.onlytrusted.app.ui.theme.appBackdropBackground
import com.onlytrusted.app.ui.theme.MidnightElevated
import com.onlytrusted.app.ui.theme.PrimaryTextDark
import com.onlytrusted.app.ui.theme.SecondaryTextDark
import com.onlytrusted.app.ui.theme.TertiaryTextDark
import com.onlytrusted.app.ui.theme.WarningDark

/**
 * Setup + management screen for the Android SIM-Swap Alarm (Pro feature).
 *
 * Flow:
 *  1. Grant `READ_PHONE_STATE` (requested here if not already granted at app startup).
 *  2. Show the current SIM carrier + serial tail, with "Capture baseline" / "Re-capture"
 *     / "Reset" controls.
 *  3. Toggle "Alarm enabled" (defaults ON after a baseline is captured).
 *  4. Edit the carrier phone number used by the alarm's "Call my carrier" button.
 *
 * The entry row (SettingsScreen) gates on `SubscriptionState.hasHighPremium(context)`.
 */
@Composable
fun SimSwapSetupScreen(onDone: () -> Unit = {}) {
    val context = LocalContext.current

    var hasPermission by remember { mutableStateOf(SimSwapState.hasPhoneStatePermission(context)) }
    var baseline by remember { mutableStateOf(SimSwapState.baseline(context)) }
    var enabled by remember { mutableStateOf(SimSwapState.isEnabled(context)) }
    var carrierNumber by remember { mutableStateOf(SimSwapState.carrierNumber(context)) }
    var showResetConfirm by remember { mutableStateOf(false) }

    val permLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasPermission = granted
        if (granted && baseline == null) {
            baseline = SimSwapState.captureBaseline(context)
            enabled = true
        }
    }

    LaunchedEffect(Unit) {
        hasPermission = SimSwapState.hasPhoneStatePermission(context)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .appBackdropBackground()
            .verticalScroll(rememberScrollState())
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Intro
        SettingsCard {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.SimCard, contentDescription = null, tint = BrandAccentDark)
                    Spacer(Modifier.width(10.dp))
                    Text(
                        "What this does",
                        color = PrimaryTextDark,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Text(
                    "A SIM swap is when an attacker tricks your carrier into porting your " +
                        "number to their SIM so they can intercept bank 2FA codes. OnlyTrusted " +
                        "fingerprints your SIM identity (SIM serial + carrier) when you enable " +
                        "this, then alerts you if it ever changes.",
                    color = SecondaryTextDark,
                    fontSize = 13.sp
                )
            }
        }

        // Permission card (only visible when missing)
        if (!hasPermission) {
            SettingsCard(border = WarningDark.copy(alpha = 0.5f)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Warning, contentDescription = null, tint = WarningDark)
                        Spacer(Modifier.width(10.dp))
                        Text(
                            "Phone permission needed",
                            color = PrimaryTextDark,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Text(
                        "This feature needs READ_PHONE_STATE to read the SIM serial and " +
                            "carrier operator for fingerprinting. We never share this data " +
                            "with anyone and only store a SHA-256 hash on this device.",
                        color = SecondaryTextDark,
                        fontSize = 12.sp
                    )
                    Button(
                        onClick = { permLauncher.launch(Manifest.permission.READ_PHONE_STATE) },
                        colors = ButtonDefaults.buttonColors(containerColor = WarningDark),
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Grant permission") }
                }
            }
        }

        // Baseline card
        SettingsCard {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.SimCard, contentDescription = null, tint = BrandAccentDark)
                    Spacer(Modifier.width(10.dp))
                    Text(
                        "SIM baseline",
                        color = PrimaryTextDark,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                val current = SimSwapState.capture(context)
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        "Current SIM",
                        color = SecondaryTextDark,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        current.displayString,
                        color = PrimaryTextDark,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
                if (baseline != null) {
                    val b = baseline!!
                    Spacer(Modifier.height(6.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            "Last baseline update",
                            color = SecondaryTextDark,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(b.formattedTimestamp(), color = PrimaryTextDark, fontSize = 14.sp)
                        Text(b.display, color = TertiaryTextDark, fontSize = 11.sp)
                        if (b.isSentinel) {
                            Text(
                                "Baseline was captured with no readable SIM data — the alarm cannot detect a swap.",
                                color = WarningDark,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                } else {
                    Text(
                        "No baseline captured yet.",
                        color = WarningDark,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            baseline = SimSwapState.captureBaseline(context)
                            enabled = true
                        },
                        enabled = hasPermission,
                        colors = ButtonDefaults.buttonColors(containerColor = BrandAccentDark),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(if (baseline == null) "Capture baseline" else "Re-capture baseline")
                    }
                    if (baseline != null) {
                        OutlinedButton(onClick = { showResetConfirm = true }) {
                            Text("Reset", color = DangerDark)
                        }
                    }
                }
            }
        }

        // Alarm toggle
        SettingsCard {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        "Alarm enabled",
                        color = PrimaryTextDark,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        if (enabled)
                            "You'll see a full-screen alert on next app open if the SIM changes."
                        else
                            "Capture a baseline above, then enable to arm the alarm.",
                        color = SecondaryTextDark,
                        fontSize = 12.sp
                    )
                }
                Switch(
                    checked = enabled,
                    onCheckedChange = {
                        enabled = it
                        SimSwapState.setEnabled(context, it)
                    },
                    enabled = baseline != null,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = BrandAccentDark
                    )
                )
            }
        }

        // Carrier phone number
        SettingsCard {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Phone, contentDescription = null, tint = BrandAccentDark)
                    Spacer(Modifier.width(10.dp))
                    Text(
                        "Carrier phone number",
                        color = PrimaryTextDark,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Text(
                    "Used by the \"Call my carrier\" button on the alarm screen. Defaults to " +
                        "611 (US carriers). Set your carrier's fraud / account-services line here.",
                    color = SecondaryTextDark,
                    fontSize = 12.sp
                )
                OutlinedTextField(
                    value = carrierNumber,
                    onValueChange = { carrierNumber = it },
                    singleLine = true,
                    label = { Text("Carrier number") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    modifier = Modifier.fillMaxWidth()
                )
                Button(
                    onClick = { SimSwapState.setCarrierNumber(context, carrierNumber) },
                    colors = ButtonDefaults.buttonColors(containerColor = BrandAccentDark),
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Save") }
            }
        }

        // iOS parity note
        SettingsCard(border = BrandCyanDark.copy(alpha = 0.3f)) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Info, contentDescription = null, tint = BrandCyanDark)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Platform note",
                        color = PrimaryTextDark,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Text(
                    "Android gives us SIM serial + operator reliably, so this alarm catches " +
                        "most SIM swaps. On Android 10+ IMSI reads require carrier privilege; " +
                        "we fall back to SIM serial + operator in that case.",
                    color = SecondaryTextDark,
                    fontSize = 12.sp
                )
            }
        }
    }

    if (showResetConfirm) {
        AlertDialog(
            onDismissRequest = { showResetConfirm = false },
            title = { Text("Reset the baseline?") },
            text = {
                Text("The alarm will stay disabled until you capture a new baseline.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        SimSwapState.resetBaseline(context)
                        baseline = null
                        enabled = false
                        showResetConfirm = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = DangerDark)
                ) { Text("Reset") }
            },
            dismissButton = {
                OutlinedButton(onClick = { showResetConfirm = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun SettingsCard(
    border: Color? = null,
    content: @Composable () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MidnightElevated,
        border = border?.let { BorderStroke(1.dp, it) },
        modifier = Modifier.fillMaxWidth()
    ) { content() }
}
