// SPDX-FileCopyrightText: 2026 VanPaulTek Inc
// SPDX-License-Identifier: AGPL-3.0-or-later
package com.onlytrusted.app.ui.p2p

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PeopleAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.onlytrusted.app.ui.subscription.subGold
import com.onlytrusted.app.ui.theme.BrandAccentDark
import com.onlytrusted.app.ui.theme.BrandCyanDark
import com.onlytrusted.app.ui.theme.DangerDark
import com.onlytrusted.app.ui.theme.MidnightCard
import com.onlytrusted.app.ui.theme.MidnightElevated
import com.onlytrusted.app.ui.theme.PrimaryTextDark
import com.onlytrusted.app.ui.theme.SecondaryTextDark
import com.onlytrusted.app.ui.theme.TertiaryTextDark
import com.onlytrusted.app.ui.theme.WarningDark
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun TrustedWitnessSetupScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var token by remember { mutableStateOf(0) }
    val featureEnabled = remember(token) { TrustedWitnessState.isFeatureEnabled(context) }
    val witnesses = remember(token) { TrustedWitnessState.witnesses(context) }
    val required = remember(token) { TrustedWitnessState.requiredActions(context) }

    var showAdd by remember { mutableStateOf(false) }

    Surface(color = MidnightCard, modifier = Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header
            Surface(
                color = MidnightElevated,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        Modifier.size(44.dp)
                            .background(BrandCyanDark.copy(alpha = 0.18f), RoundedCornerShape(10.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Filled.PeopleAlt, contentDescription = null, tint = BrandCyanDark)
                    }
                    Column(Modifier.weight(1f)) {
                        Text("Elite feature", color = subGold, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        Text("Trusted Witness", color = PrimaryTextDark, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Text(
                            "A second person physically approves sensitive actions (Wipe, Disarm Duress, Disable Dead-Man Wipe, Vault export, Change master PIN).",
                            color = SecondaryTextDark, fontSize = 12.sp
                        )
                    }
                }
            }

            // Toggle
            Surface(color = MidnightElevated, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
                Row(
                    Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Require Trusted Witness", color = PrimaryTextDark, fontWeight = FontWeight.SemiBold)
                        Text(
                            if (featureEnabled)
                                "Sensitive actions below need a witness nearby."
                            else "Off. Sensitive actions proceed as before.",
                            color = SecondaryTextDark, fontSize = 12.sp
                        )
                    }
                    Switch(
                        checked = featureEnabled,
                        onCheckedChange = {
                            TrustedWitnessState.setFeatureEnabled(context, it)
                            token++
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = BrandAccentDark
                        )
                    )
                }
            }

            if (featureEnabled) {
                Text(
                    "Warning: if every witness is unreachable, you cannot complete the gated actions below. Enrol more than one witness and verify Bluetooth is working on their phone.",
                    color = WarningDark, fontSize = 12.sp
                )
            }

            // Witnesses
            Surface(color = MidnightElevated, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Witnesses", color = PrimaryTextDark, fontWeight = FontWeight.SemiBold)
                    if (witnesses.isEmpty()) {
                        Text(
                            "No witnesses enrolled yet. Tap 'Add witness' and bring their phone next to yours.",
                            color = SecondaryTextDark, fontSize = 12.sp
                        )
                    } else {
                        witnesses.forEach { w ->
                            Row(
                                Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(w.displayName, color = PrimaryTextDark, fontWeight = FontWeight.SemiBold)
                                    Text(
                                        "Device ID ${w.deviceID.take(8)}…",
                                        color = SecondaryTextDark, fontSize = 11.sp
                                    )
                                }
                                TextButton(onClick = {
                                    TrustedWitnessState.removeWitness(context, w.id)
                                    token++
                                }) {
                                    Text("Remove", color = DangerDark)
                                }
                            }
                        }
                    }
                    Button(
                        onClick = { showAdd = true },
                        colors = ButtonDefaults.buttonColors(containerColor = BrandAccentDark),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Add witness", fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            // Gated actions
            Surface(color = MidnightElevated, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Gated actions", color = PrimaryTextDark, fontWeight = FontWeight.SemiBold)
                    Text(
                        "Pick which actions need witness approval. Unchecked actions proceed without one even when the feature is on.",
                        color = SecondaryTextDark, fontSize = 12.sp
                    )
                    TrustedWitnessState.GatedAction.entries.forEach { action ->
                        val isOn = action in required
                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(action.displayName, color = PrimaryTextDark, fontSize = 13.sp)
                            Switch(
                                checked = isOn,
                                onCheckedChange = { on ->
                                    val updated = if (on) required + action else required - action
                                    TrustedWitnessState.setRequiredActions(context, updated)
                                    token++
                                },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = BrandAccentDark
                                )
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
            TextButton(onClick = onBack) { Text("Done", color = BrandCyanDark) }
        }
    }

    if (showAdd) {
        AddWitnessDialog(onDismiss = {
            showAdd = false
            token++
        })
    }

    // Shared approval overlay — composed here so it's visible from the setup screen too.
    TrustedWitnessApprovalOverlay()
}

@Composable
private fun AddWitnessDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var witnessName by remember { mutableStateOf("") }
    val attestationCode = remember {
        ShamirSSS.randomBytes(3).joinToString("") { "%02X".format(it) }
    }
    var busy by remember { mutableStateOf(false) }
    var paired by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MidnightCard,
        title = {
            Text("Add witness", color = PrimaryTextDark, fontWeight = FontWeight.Bold)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Nickname shown in your Settings — the witness won't see this label.", color = SecondaryTextDark, fontSize = 12.sp)
                OutlinedTextField(
                    value = witnessName,
                    onValueChange = { witnessName = it },
                    singleLine = true,
                    label = { Text("e.g. Alex (partner)") }
                )
                Text(
                    "Both apps show this code: $attestationCode",
                    color = BrandCyanDark, fontWeight = FontWeight.Bold, fontSize = 18.sp
                )
                Text(
                    "Emulator detected? Pairing runs dry — a synthetic device ID will be assigned.",
                    color = WarningDark, fontSize = 11.sp
                )
                if (paired) {
                    Text(
                        "Witness '$witnessName' enrolled.",
                        color = BrandAccentDark, fontWeight = FontWeight.SemiBold
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (paired) {
                        onDismiss()
                        return@TextButton
                    }
                    scope.launch {
                        busy = true
                        val mgr = OtBluetoothPeerManager(context)
                        mgr.startAdvertising(
                            OtBluetoothPeerManager.PayloadKind.WITNESS_APPROVAL_REQUEST,
                            "enroll".toByteArray(),
                            attestationCode
                        )
                        delay(1000)
                        val synthID = ShamirSSS.randomBytes(16).joinToString("") { "%02x".format(it) }
                        TrustedWitnessState.addWitness(context, witnessName.trim(), synthID)
                        mgr.stop()
                        busy = false
                        paired = true
                    }
                },
                enabled = !busy && (paired || witnessName.trim().isNotEmpty())
            ) {
                Text(
                    if (paired) "Done" else if (busy) "Pairing…" else "Begin pairing",
                    color = BrandAccentDark
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = SecondaryTextDark) }
        }
    )
}

/**
 * Full-screen overlay shown while a Trusted Witness gate call is in flight. Watches the
 * singleton [TrustedWitnessApprovalPresenter] and renders a cancel-only dialog with the
 * attestation code the owner should read out loud to the witness.
 */
@Composable
fun TrustedWitnessApprovalOverlay() {
    val isActive by TrustedWitnessApprovalPresenter.isActive.collectAsState()
    val action by TrustedWitnessApprovalPresenter.currentAction.collectAsState()
    val code by TrustedWitnessApprovalPresenter.attestationCode.collectAsState()
    if (!isActive) return
    AlertDialog(
        onDismissRequest = { /* don't let user tap outside — require cancel */ },
        containerColor = MidnightCard,
        title = {
            Text("Trusted Witness required", color = PrimaryTextDark, fontWeight = FontWeight.Bold)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Have your witness open OnlyTrusted and bring their phone within 2 metres. They'll see a prompt to approve ${action?.approvalPrompt ?: "the action"}.",
                    color = SecondaryTextDark, fontSize = 13.sp
                )
                Text(
                    "Code: $code",
                    color = BrandCyanDark, fontWeight = FontWeight.Bold, fontSize = 20.sp
                )
                Text(
                    "Emulator: no BLE radio — the auto-approve path fires in ~1 s.",
                    color = WarningDark, fontSize = 11.sp
                )
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = { TrustedWitnessApprovalPresenter.cancel() }) {
                Text("Cancel", color = DangerDark)
            }
        }
    )
}

@Composable
fun TrustedWitnessUpsellDialog(onUpgrade: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MidnightCard,
        title = {
            Text(
                "Trusted Witness is an Elite feature",
                color = PrimaryTextDark, fontWeight = FontWeight.Bold
            )
        },
        text = {
            Text(
                "Require a trusted contact to be physically present (Bluetooth proximity) and tap approve on their phone before actions like Wipe everything, Disarm Duress, or Disable Dead-Man Wipe can run. Elite tier.",
                color = SecondaryTextDark
            )
        },
        confirmButton = {
            TextButton(onClick = onUpgrade) { Text("Upgrade to Elite", color = subGold) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Not now") }
        }
    )
}
