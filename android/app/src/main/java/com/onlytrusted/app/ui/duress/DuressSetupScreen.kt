// SPDX-FileCopyrightText: 2026 VanPaulTek Inc
// SPDX-License-Identifier: AGPL-3.0-or-later
package com.onlytrusted.app.ui.duress

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.rememberCoroutineScope
import com.onlytrusted.app.OnlyTrustedApp
import com.onlytrusted.app.data.repository.MaskedContact
import com.onlytrusted.app.ui.hardwarekey.HardwareKeyGate
import com.onlytrusted.app.ui.theme.BrandAccentDark
import com.onlytrusted.app.ui.theme.BrandCyanDark
import com.onlytrusted.app.ui.theme.DangerDark
import com.onlytrusted.app.ui.theme.MidnightBackground
import com.onlytrusted.app.ui.theme.MidnightCard
import com.onlytrusted.app.ui.theme.MidnightElevated
import com.onlytrusted.app.ui.theme.PrimaryTextDark
import com.onlytrusted.app.ui.theme.SecondaryTextDark
import com.onlytrusted.app.ui.theme.WarningDark
import com.onlytrusted.app.ui.subscription.subGold

/**
 * Android Duress Mode setup. Four steps:
 *  1. Set / confirm duress PIN (4–6 digits).
 *  2. Pick a real trusted contact to receive the SOS SMS.
 *  3. Edit three decoy contact placeholders.
 *  4. Toggle active.
 */
@Composable
fun DuressSetupScreen(onDone: () -> Unit = {}) {
    val context = LocalContext.current
    val app = context.applicationContext as OnlyTrustedApp
    val realContactsFlow = remember { app.trustedContacts.maskedList() }
    val realContacts by realContactsFlow.collectAsState(initial = emptyList())

    var step by remember { mutableStateOf(1) }
    var pin by remember { mutableStateOf("") }
    var pinConfirm by remember { mutableStateOf("") }
    var pinError by remember { mutableStateOf<String?>(null) }
    var selectedAlertId by remember { mutableStateOf(DuressState.alertContactId(context)) }
    var ownerName by remember { mutableStateOf(DuressState.ownerName(context)) }
    var enabled by remember { mutableStateOf(DuressState.isEnabled(context)) }
    var decoy by remember { mutableStateOf(defaultDecoy()) }
    var success by remember { mutableStateOf(false) }
    // Elite hardware-key gate feedback. Shown when the user attempts to disarm Duress Mode
    // but the FIDO2 tap is cancelled or fails.
    var hardwareKeyGateError by remember { mutableStateOf<String?>(null) }
    val gateScope = rememberCoroutineScope()

    // Auto-fill owner name from the real "owner" contact if we have one and the field is blank.
    LaunchedEffect(realContacts) {
        if (ownerName.isBlank()) {
            val owner = realContacts.firstOrNull { it.isOwner }
            if (owner != null) {
                ownerName = owner.displayName
                DuressState.setOwnerName(context, owner.displayName)
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MidnightBackground)
            .verticalScroll(rememberScrollState())
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header
        Card {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Warning, contentDescription = null, tint = BrandAccentDark)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Step $step of 4",
                        color = BrandCyanDark,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Text(
                    stepTitle(step),
                    color = PrimaryTextDark,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(stepHint(step), color = SecondaryTextDark, fontSize = 13.sp)
            }
        }

        when (step) {
            1 -> PinStep(
                pin = pin,
                pinConfirm = pinConfirm,
                error = pinError,
                onPin = { pin = it },
                onPinConfirm = { pinConfirm = it },
                onNext = {
                    pinError = validate(pin, pinConfirm, context)
                    if (pinError == null) {
                        DuressState.setDuressPin(context, pin)
                        step = 2
                    }
                }
            )
            2 -> PickAlertStep(
                contacts = realContacts,
                selectedId = selectedAlertId,
                onSelect = {
                    selectedAlertId = it
                    DuressState.setAlertContactId(context, it)
                },
                onBack = { step = 1 },
                onNext = { step = 3 }
            )
            3 -> DecoyStep(
                rows = decoy,
                onChange = { decoy = it },
                onBack = { step = 2 },
                onNext = { step = 4 }
            )
            else -> FinalStep(
                enabled = enabled,
                ownerName = ownerName,
                canArm = selectedAlertId != null && ownerName.isNotBlank(),
                onOwnerNameChange = {
                    ownerName = it
                    DuressState.setOwnerName(context, it)
                },
                onToggle = { want ->
                    if (want) {
                        armWithDecoy(context, decoy)
                        enabled = true
                        DuressState.setEnabled(context, true)
                    } else {
                        // Elite gate stack: Trusted Witness (if armed) → Hardware Key (if
                        // enrolled). Both are opt-in; if neither is configured this collapses
                        // to the pre-Elite behaviour.
                        com.onlytrusted.app.ui.p2p.TrustedWitnessGate.require(
                            context = context,
                            action = com.onlytrusted.app.ui.p2p.TrustedWitnessState.GatedAction.DISARM_DURESS,
                            reason = "Have your witness approve the Duress disarm.",
                            scope = gateScope,
                            onSuccess = {
                                HardwareKeyGate.requireAssertion(
                                    context = context,
                                    reason = "Confirm Duress disarm with your security key.",
                                    scope = gateScope,
                                    onSuccess = {
                                        enabled = false
                                        DuressState.setEnabled(context, false)
                                    },
                                    onCancel = { msg ->
                                        hardwareKeyGateError = msg
                                            ?: "Disarm cancelled — security key tap missing."
                                    }
                                )
                            },
                            onCancel = { msg ->
                                hardwareKeyGateError = msg
                                    ?: "Disarm cancelled — witness did not approve."
                            }
                        )
                    }
                },
                onBack = { step = 3 },
                onFinish = {
                    if (!enabled) {
                        armWithDecoy(context, decoy)
                        enabled = true
                        DuressState.setEnabled(context, true)
                    }
                    success = true
                }
            )
        }

        Card(border = WarningDark.copy(alpha = 0.4f)) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Info, contentDescription = null, tint = WarningDark)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Android send is silent",
                        color = PrimaryTextDark,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Text(
                    "SmsManager delivers the SOS with no composer. The owner can confirm the " +
                        "send afterwards by opening the Messages app outbox.",
                    color = SecondaryTextDark,
                    fontSize = 12.sp
                )
            }
        }
    }

    if (success) {
        AlertDialog(
            onDismissRequest = { success = false; onDone() },
            icon = { Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = BrandAccentDark) },
            title = { Text("Duress Mode ready", color = PrimaryTextDark, fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "Duress Mode is armed. Memorise your duress PIN. If you forget it, open this " +
                        "screen again to reset.",
                    color = SecondaryTextDark
                )
            },
            confirmButton = {
                TextButton(onClick = { success = false; onDone() }) {
                    Text("Done", color = BrandAccentDark)
                }
            },
            containerColor = MidnightCard
        )
    }

    if (hardwareKeyGateError != null) {
        AlertDialog(
            onDismissRequest = { hardwareKeyGateError = null },
            title = {
                Text(
                    "Hardware key required",
                    color = PrimaryTextDark,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(hardwareKeyGateError.orEmpty(), color = SecondaryTextDark)
            },
            confirmButton = {
                TextButton(onClick = { hardwareKeyGateError = null }) {
                    Text("OK", color = BrandAccentDark)
                }
            },
            containerColor = MidnightCard
        )
    }
}

@Composable
private fun PinStep(
    pin: String,
    pinConfirm: String,
    error: String?,
    onPin: (String) -> Unit,
    onPinConfirm: (String) -> Unit,
    onNext: () -> Unit
) {
    Card {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(
                value = pin,
                onValueChange = onPin,
                singleLine = true,
                label = { Text("Duress PIN (4–6 digits)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = pinConfirm,
                onValueChange = onPinConfirm,
                singleLine = true,
                label = { Text("Confirm PIN") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth()
            )
            if (error != null) {
                Text(error, color = DangerDark, fontSize = 13.sp)
            }
            Button(
                onClick = onNext,
                colors = ButtonDefaults.buttonColors(containerColor = BrandAccentDark),
                modifier = Modifier.fillMaxWidth()
            ) { Text("Continue") }
        }
    }
}

@Composable
private fun PickAlertStep(
    contacts: List<MaskedContact>,
    selectedId: Long?,
    onSelect: (Long) -> Unit,
    onBack: () -> Unit,
    onNext: () -> Unit
) {
    Card {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (contacts.isEmpty()) {
                Text(
                    "You have no trusted contacts yet. Add one from the Contacts tab, then " +
                        "come back here.",
                    color = SecondaryTextDark,
                    fontSize = 13.sp
                )
            } else {
                contacts.forEach { c ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = selectedId == c.id,
                            onClick = { onSelect(c.id) }
                        )
                        Spacer(Modifier.width(8.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                c.displayName,
                                color = PrimaryTextDark,
                                fontWeight = FontWeight.SemiBold
                            )
                            c.maskedNumbers.firstOrNull()?.let {
                                Text(it, color = SecondaryTextDark, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
            Row(modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(onClick = onBack) { Text("Back") }
                Spacer(Modifier.weight(1f))
                Button(
                    onClick = onNext,
                    enabled = selectedId != null,
                    colors = ButtonDefaults.buttonColors(containerColor = BrandAccentDark)
                ) { Text("Continue") }
            }
        }
    }
}

@Composable
private fun DecoyStep(
    rows: List<DuressVaultStore.DecoyContact>,
    onChange: (List<DuressVaultStore.DecoyContact>) -> Unit,
    onBack: () -> Unit,
    onNext: () -> Unit
) {
    Card {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            rows.forEachIndexed { idx, row ->
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedTextField(
                        value = row.displayName,
                        onValueChange = { v ->
                            val next = rows.toMutableList().also {
                                it[idx] = row.copy(displayName = v)
                            }
                            onChange(next)
                        },
                        singleLine = true,
                        label = { Text("Name") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = row.rawNumber.orEmpty(),
                        onValueChange = { v ->
                            val next = rows.toMutableList().also {
                                it[idx] = row.copy(rawNumber = v.ifBlank { null })
                            }
                            onChange(next)
                        },
                        singleLine = true,
                        label = { Text("Number (optional)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
            Row(modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(onClick = onBack) { Text("Back") }
                Spacer(Modifier.weight(1f))
                Button(
                    onClick = onNext,
                    colors = ButtonDefaults.buttonColors(containerColor = BrandAccentDark)
                ) { Text("Continue") }
            }
        }
    }
}

@Composable
private fun FinalStep(
    enabled: Boolean,
    ownerName: String,
    canArm: Boolean,
    onOwnerNameChange: (String) -> Unit,
    onToggle: (Boolean) -> Unit,
    onBack: () -> Unit,
    onFinish: () -> Unit
) {
    Card {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "Duress Mode enabled",
                        color = PrimaryTextDark,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        "Arms the duress PIN and decoy vault.",
                        color = SecondaryTextDark,
                        fontSize = 12.sp
                    )
                }
                Switch(
                    checked = enabled,
                    onCheckedChange = onToggle,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = BrandAccentDark
                    )
                )
            }
            OutlinedTextField(
                value = ownerName,
                onValueChange = onOwnerNameChange,
                singleLine = true,
                label = { Text("Your name (used in the SOS text)") },
                modifier = Modifier.fillMaxWidth()
            )
            Row(modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(onClick = onBack) { Text("Back") }
                Spacer(Modifier.weight(1f))
                Button(
                    onClick = onFinish,
                    enabled = canArm,
                    colors = ButtonDefaults.buttonColors(containerColor = subGold)
                ) { Text("Finish") }
            }
        }
    }
}

@Composable
private fun Card(
    border: Color? = null,
    content: @Composable () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MidnightElevated,
        border = border?.let { androidx.compose.foundation.BorderStroke(1.dp, it) },
        modifier = Modifier.fillMaxWidth()
    ) { content() }
}

private fun stepTitle(step: Int): String = when (step) {
    1 -> "Set your duress PIN"
    2 -> "Pick a duress alert contact"
    3 -> "Decoy vault contents"
    else -> "Enable Duress Mode"
}

private fun stepHint(step: Int): String = when (step) {
    1 -> "4–6 digits. Keep it different from your device PIN so you can type it under coercion without triggering muscle-memory."
    2 -> "They receive the SOS when the duress PIN unlocks. Pick someone who always answers."
    3 -> "What a coercer sees. Edit any field to make it believable."
    else -> "Everything is stored on this device only. Toggle on to arm."
}

private fun defaultDecoy() = listOf(
    DuressVaultStore.DecoyContact(1, "Mom", null, "+1 (555) 010-0100"),
    DuressVaultStore.DecoyContact(2, "Work", null, "+1 (555) 010-0200"),
    DuressVaultStore.DecoyContact(3, "Bank", null, "+1 (555) 010-0300")
)

private fun armWithDecoy(context: android.content.Context, rows: List<DuressVaultStore.DecoyContact>) {
    val filtered = rows.filter { it.displayName.isNotBlank() }
    DuressVaultStore.seedPlaceholdersIfEmpty(context, filtered)
    DuressVaultStore.refresh(context)
}

private fun validate(pin: String, confirm: String, context: android.content.Context): String? {
    if (pin.length !in 4..6 || !pin.all { it.isDigit() }) return "PIN must be 4–6 digits."
    if (pin != confirm) return "PINs do not match."
    if (DuressState.collidesWithRealPin(context, pin)) {
        return "This matches your real app PIN. Pick something different."
    }
    return null
}
