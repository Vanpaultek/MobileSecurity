// SPDX-FileCopyrightText: 2026 VanPaulTek Inc
// SPDX-License-Identifier: AGPL-3.0-or-later
package com.onlytrusted.app.ui.bordermode

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FlightTakeoff
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.fragment.app.FragmentActivity
import com.onlytrusted.app.ui.duress.DuressState
import com.onlytrusted.app.ui.lock.BiometricPrompter
import com.onlytrusted.app.ui.theme.BrandAccentDark
import com.onlytrusted.app.ui.theme.DangerDark
import com.onlytrusted.app.ui.theme.MidnightCard
import com.onlytrusted.app.ui.theme.MidnightElevated
import com.onlytrusted.app.ui.theme.PrimaryTextDark
import com.onlytrusted.app.ui.theme.SecondaryTextDark
import com.onlytrusted.app.ui.theme.TertiaryTextDark

/**
 * Setup + arm/disarm UI for Border Mode (Elite). Mirrors iOS `BorderModeSetupScreen`.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BorderModeSetupScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val activity = context as? FragmentActivity

    val settingsPrefs = remember {
        context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    }

    var armed by remember { mutableStateOf(BorderModeState.isArmed(context)) }
    var armedAtMillis by remember { mutableStateOf(BorderModeState.armedAtMillis(context)) }
    var cover by remember { mutableStateOf(BorderModeState.coverIdentity(context)) }

    var showCoverEditor by remember { mutableStateOf(false) }
    var confirm1 by remember { mutableStateOf(false) }
    var confirm2 by remember { mutableStateOf(false) }
    var confirm3Pin by remember { mutableStateOf(false) }
    var pinInput by remember { mutableStateOf("") }
    var pinError by remember { mutableStateOf<String?>(null) }
    var armErrorText by remember { mutableStateOf<String?>(null) }
    var showDuressBlockedAlert by remember { mutableStateOf(false) }
    var disarmPinPrompt by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Border Mode", color = PrimaryTextDark) },
                navigationIcon = {
                    TextButton(onClick = onBack) {
                        Text("Back", color = BrandAccentDark)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MidnightCard)
            )
        },
        containerColor = MidnightCard
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            HeaderBanner(armed = armed, armedAtMillis = armedAtMillis)
            ExplainerCard()
            CoverIdentityCard(cover = cover) { showCoverEditor = true }

            if (armed) {
                Button(
                    onClick = {
                        if (activity != null && BiometricPrompter.canAuthenticate(context)) {
                            BiometricPrompter.prompt(
                                activity = activity,
                                title = "Disarm Border Mode",
                                subtitle = "Confirm it's you.",
                                onSuccess = {
                                    pinInput = ""; pinError = null
                                    disarmPinPrompt = true
                                },
                                onFailure = { msg ->
                                    armErrorText = "Biometric check failed: $msg"
                                }
                            )
                        } else {
                            // No biometric → require PIN only.
                            pinInput = ""; pinError = null
                            disarmPinPrompt = true
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = BrandAccentDark),
                    modifier = Modifier.fillMaxWidth().height(52.dp)
                ) {
                    Icon(Icons.Filled.LockOpen, null, tint = Color.White)
                    Spacer(Modifier.size(8.dp))
                    Text("Disarm Border Mode", color = Color.White, fontWeight = FontWeight.Bold)
                }
            } else {
                Button(
                    onClick = {
                        if (BorderModeState.duressReady(context)) {
                            confirm1 = true
                        } else {
                            showDuressBlockedAlert = true
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = DangerDark),
                    modifier = Modifier.fillMaxWidth().height(52.dp)
                ) {
                    Icon(Icons.Filled.FlightTakeoff, null, tint = Color.White)
                    Spacer(Modifier.size(8.dp))
                    Text("ARM BORDER MODE", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }

            Text(
                "Border Mode is a defensive tool. It does not help you evade a lawful search. " +
                    "Read Settings → Legal → \"Border Mode & law\" before you cross a border.",
                fontSize = 11.sp,
                color = TertiaryTextDark
            )
        }
    }

    // --- Dialogs ---

    if (confirm1) {
        AlertDialog(
            onDismissRequest = { confirm1 = false },
            containerColor = MidnightCard,
            title = { Text("Border Mode will reconfigure your defences", color = PrimaryTextDark, fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "Border Mode will disable biometric unlock, enable Duress Mode, force " +
                        "Dead-Man Wipe to 7 days, and change your Dashboard identity. Continue?",
                    color = SecondaryTextDark
                )
            },
            confirmButton = {
                TextButton(onClick = { confirm1 = false; confirm2 = true }) {
                    Text("Continue", color = DangerDark)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirm1 = false }) { Text("Cancel") }
            }
        )
    }

    if (confirm2) {
        AlertDialog(
            onDismissRequest = { confirm2 = false },
            containerColor = MidnightCard,
            title = { Text("You'll unlock with your PIN only", color = PrimaryTextDark, fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "You will need your PIN to unlock the app. Make sure you remember it. Continue?",
                    color = SecondaryTextDark
                )
            },
            confirmButton = {
                TextButton(onClick = { confirm2 = false; confirm3Pin = true }) {
                    Text("Continue", color = DangerDark)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirm2 = false }) { Text("Cancel") }
            }
        )
    }

    if (confirm3Pin) {
        AlertDialog(
            onDismissRequest = { confirm3Pin = false },
            containerColor = MidnightCard,
            title = { Text("Confirm with your PIN", color = PrimaryTextDark, fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text(
                        "Enter your Duress PIN. Border Mode uses it as proof you know how to " +
                            "unlock the app once biometric is turned off.",
                        color = SecondaryTextDark, fontSize = 12.sp
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = pinInput,
                        onValueChange = { pinInput = it },
                        singleLine = true,
                        label = { Text("PIN") },
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            keyboardType = KeyboardType.NumberPassword
                        ),
                        visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation()
                    )
                    if (pinError != null) {
                        Spacer(Modifier.height(4.dp))
                        Text(pinError!!, color = DangerDark, fontSize = 12.sp)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (!DuressState.matches(context, pinInput)) {
                        pinError = "Incorrect PIN."
                        return@TextButton
                    }
                    pinInput = ""; pinError = null
                    confirm3Pin = false
                    val err = BorderModeState.arm(context, settingsPrefs)
                    if (err != null) {
                        armErrorText = err.message
                    } else {
                        armed = true
                        armedAtMillis = BorderModeState.armedAtMillis(context)
                    }
                }) { Text("Arm", color = DangerDark) }
            },
            dismissButton = {
                TextButton(onClick = { confirm3Pin = false; pinInput = ""; pinError = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (disarmPinPrompt) {
        AlertDialog(
            onDismissRequest = { disarmPinPrompt = false },
            containerColor = MidnightCard,
            title = { Text("Enter PIN to disarm", color = PrimaryTextDark, fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    OutlinedTextField(
                        value = pinInput,
                        onValueChange = { pinInput = it },
                        singleLine = true,
                        label = { Text("PIN") },
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            keyboardType = KeyboardType.NumberPassword
                        ),
                        visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation()
                    )
                    if (pinError != null) {
                        Spacer(Modifier.height(4.dp))
                        Text(pinError!!, color = DangerDark, fontSize = 12.sp)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (!DuressState.matches(context, pinInput)) {
                        pinError = "Incorrect PIN."
                        return@TextButton
                    }
                    pinInput = ""; pinError = null
                    disarmPinPrompt = false
                    BorderModeState.disarm(context, settingsPrefs)
                    armed = false
                    armedAtMillis = 0L
                }) { Text("Disarm", color = BrandAccentDark) }
            },
            dismissButton = {
                TextButton(onClick = { disarmPinPrompt = false; pinInput = ""; pinError = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showDuressBlockedAlert) {
        AlertDialog(
            onDismissRequest = { showDuressBlockedAlert = false },
            containerColor = MidnightCard,
            title = { Text("Duress Mode must be set up first", color = PrimaryTextDark, fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "Border Mode needs an active Duress PIN. Set that up first " +
                        "(Settings → Duress Mode) then come back to arm Border Mode.",
                    color = SecondaryTextDark
                )
            },
            confirmButton = {
                TextButton(onClick = { showDuressBlockedAlert = false; onBack() }) {
                    Text("Open Duress setup", color = BrandAccentDark)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDuressBlockedAlert = false }) { Text("Cancel") }
            }
        )
    }

    if (armErrorText != null) {
        AlertDialog(
            onDismissRequest = { armErrorText = null },
            containerColor = MidnightCard,
            title = { Text("Border Mode", color = PrimaryTextDark, fontWeight = FontWeight.Bold) },
            text = { Text(armErrorText!!, color = SecondaryTextDark) },
            confirmButton = {
                TextButton(onClick = { armErrorText = null }) { Text("OK", color = BrandAccentDark) }
            }
        )
    }

    if (showCoverEditor) {
        CoverIdentityEditorSheet(
            initial = cover,
            onSave = { newCover ->
                BorderModeState.saveCoverIdentity(context, newCover)
                cover = newCover
                showCoverEditor = false
            },
            onDismiss = { showCoverEditor = false }
        )
    }

    // Keep local flags in sync if state changes elsewhere.
    LaunchedEffect(Unit) {
        armed = BorderModeState.isArmed(context)
        armedAtMillis = BorderModeState.armedAtMillis(context)
        cover = BorderModeState.coverIdentity(context)
    }
}

@Composable
private fun HeaderBanner(armed: Boolean, armedAtMillis: Long) {
    Surface(
        color = MidnightElevated,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .background(DangerDark, RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.FlightTakeoff, null, tint = Color.White)
            }
            Column(Modifier.weight(1f)) {
                Text("Border Mode", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = PrimaryTextDark)
                Text(
                    if (armed)
                        "Armed at ${java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.SHORT, java.text.DateFormat.SHORT).format(java.util.Date(armedAtMillis))}"
                    else "Elite · high-risk preset",
                    fontSize = 12.sp, color = SecondaryTextDark
                )
            }
        }
    }
}

@Composable
private fun ExplainerCard() {
    Surface(
        color = MidnightElevated,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                "What Border Mode does",
                fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = PrimaryTextDark
            )
            Text(
                "One tap. Every defensive layer at maximum. Designed for border crossings, airports, protests, and hostile territory.",
                fontSize = 12.sp, color = SecondaryTextDark
            )
            listOf(
                "1  Dead-Man Wipe forced to 7 days (shortest).",
                "2  Duress Mode activated.",
                "3  Biometric unlock OFF. PIN-only (Fifth-Amendment advantage in US).",
                "4  Sensor Monitor Service ON.",
                "5  Screenshot protection ON.",
                "6  Shield watermark ON — reads \"DO NOT RECORD\".",
                "7  Pocket-dial protection ON.",
                "8  App Lock auto-locks after 30 s.",
                "9  Dashboard & Vault show cover identity (if set up)."
            ).forEach { line ->
                Text(line, fontSize = 13.sp, color = PrimaryTextDark)
            }
        }
    }
}

@Composable
private fun CoverIdentityCard(
    cover: BorderModeState.CoverIdentity?,
    onEdit: () -> Unit
) {
    Surface(
        color = MidnightElevated,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Cover identity", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = PrimaryTextDark)
            val summary = if (cover != null && cover.contacts.isNotEmpty()) {
                "Cover: ${if (cover.ownerName.isBlank()) "(no name)" else cover.ownerName} · ${cover.contacts.size} contacts"
            } else {
                "Optional. A plausible owner name + 3-5 fake trusted contacts shown while armed."
            }
            Text(summary, fontSize = 12.sp, color = SecondaryTextDark)
            Button(
                onClick = onEdit,
                colors = ButtonDefaults.buttonColors(containerColor = BrandAccentDark),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    if (cover != null && cover.contacts.isNotEmpty())
                        "Edit cover identity" else "Set up cover identity",
                    color = Color.White, fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CoverIdentityEditorSheet(
    initial: BorderModeState.CoverIdentity?,
    onSave: (BorderModeState.CoverIdentity) -> Unit,
    onDismiss: () -> Unit
) {
    var ownerName by remember { mutableStateOf(initial?.ownerName ?: "") }
    val contactsState = remember {
        androidx.compose.runtime.mutableStateListOf<BorderModeState.CoverContact>().apply {
            if (initial?.contacts.isNullOrEmpty()) {
                repeat(3) { add(BorderModeState.CoverContact("", "")) }
            } else {
                addAll(initial!!.contacts)
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MidnightCard,
        title = { Text("Cover identity", color = PrimaryTextDark, fontWeight = FontWeight.Bold) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                Text(
                    "A plausible alias shown while armed. Pick something boring and real-looking.",
                    fontSize = 12.sp, color = SecondaryTextDark
                )
                OutlinedTextField(
                    value = ownerName,
                    onValueChange = { ownerName = it },
                    singleLine = true,
                    label = { Text("Owner name") }
                )
                Text("Cover contacts (3-5)", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = PrimaryTextDark)
                contactsState.forEachIndexed { idx, c ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = c.name,
                            onValueChange = { contactsState[idx] = c.copy(name = it) },
                            singleLine = true,
                            label = { Text("Name") },
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(Modifier.size(4.dp))
                        OutlinedTextField(
                            value = c.number,
                            onValueChange = { contactsState[idx] = c.copy(number = it) },
                            singleLine = true,
                            label = { Text("Number") },
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = { contactsState.removeAt(idx) }) {
                            Icon(Icons.Filled.Delete, null, tint = DangerDark)
                        }
                    }
                }
                if (contactsState.size < 5) {
                    TextButton(onClick = {
                        contactsState.add(BorderModeState.CoverContact("", ""))
                    }) {
                        Icon(Icons.Filled.Add, null, tint = BrandAccentDark)
                        Spacer(Modifier.size(4.dp))
                        Text("Add contact", color = BrandAccentDark)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val cleaned = contactsState
                    .filter { it.name.isNotBlank() && it.number.isNotBlank() }
                onSave(
                    BorderModeState.CoverIdentity(
                        ownerName = ownerName.trim(),
                        contacts = cleaned
                    )
                )
            }) { Text("Save", color = BrandAccentDark) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
