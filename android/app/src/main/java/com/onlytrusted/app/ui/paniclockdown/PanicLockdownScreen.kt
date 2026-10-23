// SPDX-FileCopyrightText: 2026 VanPaulTek Inc
// SPDX-License-Identifier: AGPL-3.0-or-later
package com.onlytrusted.app.ui.paniclockdown

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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
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
import androidx.compose.runtime.rememberCoroutineScope
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
import androidx.fragment.app.FragmentActivity
import com.onlytrusted.app.ui.duress.DuressState
import com.onlytrusted.app.ui.hardwarekey.HardwareKeyGate
import com.onlytrusted.app.ui.lock.BiometricPrompter
import com.onlytrusted.app.ui.theme.BrandAccentDark
import com.onlytrusted.app.ui.theme.DangerDark
import com.onlytrusted.app.ui.theme.MidnightCard
import com.onlytrusted.app.ui.theme.MidnightElevated
import com.onlytrusted.app.ui.theme.PrimaryTextDark
import com.onlytrusted.app.ui.theme.SecondaryTextDark
import com.onlytrusted.app.ui.theme.SuccessDark
import com.onlytrusted.app.ui.theme.TertiaryTextDark

/**
 * Setup + activate/disarm UI for Panic Lockdown (Elite). Mirrors iOS
 * `PanicLockdownScreen`.
 *
 * Where Border Mode has a three-step confirmation (two warnings + PIN) because it is a
 * planned preset, Panic Lockdown is explicitly a fast-trigger: one tap on the red button
 * arms the mode, with only a single "Are you sure?" confirmation dialog to prevent a
 * genuine misfire. The disarm flow is strict: biometric + PIN double-factor, plus a
 * hardware-key tap when the owner has one enrolled.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PanicLockdownScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val activity = context as? FragmentActivity
    val scope = rememberCoroutineScope()

    var armed by remember { mutableStateOf(PanicLockdownState.isArmed(context)) }
    var armedAtMillis by remember { mutableStateOf(PanicLockdownState.armedAtMillis(context)) }

    var showActivateConfirm by remember { mutableStateOf(false) }
    var disarmPinPrompt by remember { mutableStateOf(false) }
    var pinInput by remember { mutableStateOf("") }
    var pinError by remember { mutableStateOf<String?>(null) }
    var disarmError by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Panic Lockdown", color = PrimaryTextDark) },
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
            HeaderBanner(armed = armed)

            // Big red primary action — single tap arms, red "DISARM" when armed.
            if (armed) {
                Button(
                    onClick = {
                        if (activity != null && BiometricPrompter.canAuthenticate(context)) {
                            BiometricPrompter.prompt(
                                activity = activity,
                                title = "Disarm Panic Lockdown",
                                subtitle = "Biometric + PIN required.",
                                onSuccess = {
                                    pinInput = ""; pinError = null
                                    disarmPinPrompt = true
                                },
                                onFailure = { msg ->
                                    disarmError = "Biometric check failed: $msg"
                                }
                            )
                        } else {
                            // No biometric → fall back to PIN only (+ hardware key).
                            pinInput = ""; pinError = null
                            disarmPinPrompt = true
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = DangerDark),
                    modifier = Modifier.fillMaxWidth().height(60.dp)
                ) {
                    Icon(Icons.Filled.LockOpen, null, tint = Color.White)
                    Spacer(Modifier.size(8.dp))
                    Text("DISARM", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
                }
            } else {
                Button(
                    onClick = { showActivateConfirm = true },
                    colors = ButtonDefaults.buttonColors(containerColor = DangerDark),
                    modifier = Modifier.fillMaxWidth().height(60.dp)
                ) {
                    Icon(Icons.Filled.Warning, null, tint = Color.White)
                    Spacer(Modifier.size(8.dp))
                    Text(
                        "ACTIVATE PANIC LOCKDOWN NOW",
                        color = Color.White,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 16.sp
                    )
                }
            }

            StatusCard(armed = armed, armedAtMillis = armedAtMillis)
            ExplainerCard()
            CompareCard()

            Text(
                "Panic Lockdown is a defensive tool. It must not be used to obstruct lawful " +
                    "authority. Read Settings → Legal → \"Panic Lockdown & law\" before you " +
                    "rely on it.",
                fontSize = 11.sp,
                color = TertiaryTextDark
            )
        }
    }

    // --- Dialogs ---

    if (showActivateConfirm) {
        AlertDialog(
            onDismissRequest = { showActivateConfirm = false },
            containerColor = MidnightCard,
            title = {
                Text(
                    "Activate Panic Lockdown now?",
                    color = PrimaryTextDark,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    "The app will lock immediately. The next unlock drops into the decoy " +
                        "vault and sends the silent SOS — regardless of which PIN you " +
                        "enter. Disarm requires biometric + PIN.",
                    color = SecondaryTextDark
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showActivateConfirm = false
                    PanicLockdownState.activate(context)
                    armed = true
                    armedAtMillis = PanicLockdownState.armedAtMillis(context)
                    onBack()
                }) { Text("Activate", color = DangerDark, fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { showActivateConfirm = false }) { Text("Cancel") }
            }
        )
    }

    if (disarmPinPrompt) {
        AlertDialog(
            onDismissRequest = { disarmPinPrompt = false },
            containerColor = MidnightCard,
            title = {
                Text(
                    "Enter PIN to disarm",
                    color = PrimaryTextDark,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column {
                    Text(
                        "Panic Lockdown requires biometric AND PIN. If a hardware security key " +
                            "is enrolled, you'll also need to tap it.",
                        color = SecondaryTextDark,
                        fontSize = 12.sp
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
                        visualTransformation = PasswordVisualTransformation()
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
                    // Final gate: hardware key tap when enrolled. No key → no-op.
                    HardwareKeyGate.requireAssertion(
                        context = context,
                        reason = "Confirm disarm with your security key.",
                        scope = scope,
                        onSuccess = {
                            PanicLockdownState.disarm(context)
                            armed = false
                            armedAtMillis = 0L
                        },
                        onCancel = { msg ->
                            disarmError = msg ?: "Disarm cancelled — security key tap missing."
                        }
                    )
                }) { Text("Disarm", color = BrandAccentDark) }
            },
            dismissButton = {
                TextButton(onClick = {
                    disarmPinPrompt = false; pinInput = ""; pinError = null
                }) { Text("Cancel") }
            }
        )
    }

    if (disarmError != null) {
        AlertDialog(
            onDismissRequest = { disarmError = null },
            containerColor = MidnightCard,
            title = {
                Text("Couldn't disarm", color = PrimaryTextDark, fontWeight = FontWeight.Bold)
            },
            text = { Text(disarmError.orEmpty(), color = SecondaryTextDark) },
            confirmButton = {
                TextButton(onClick = { disarmError = null }) {
                    Text("OK", color = BrandAccentDark)
                }
            }
        )
    }

    LaunchedEffect(Unit) {
        armed = PanicLockdownState.isArmed(context)
        armedAtMillis = PanicLockdownState.armedAtMillis(context)
    }
}

@Composable
private fun HeaderBanner(armed: Boolean) {
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
                Icon(Icons.Filled.Warning, null, tint = Color.White)
            }
            Column(Modifier.weight(1f)) {
                Text(
                    "Panic Lockdown",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = PrimaryTextDark
                )
                Text(
                    if (armed) "ARMED" else "Elite · right-now emergency",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (armed) DangerDark else SecondaryTextDark
                )
            }
        }
    }
}

@Composable
private fun StatusCard(armed: Boolean, armedAtMillis: Long) {
    Surface(
        color = MidnightElevated,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                if (armed) Icons.Filled.Warning else Icons.Filled.CheckCircle,
                null,
                tint = if (armed) DangerDark else SuccessDark
            )
            Column(Modifier.weight(1f)) {
                val headline = if (armed) {
                    val formatted = java.text.DateFormat
                        .getDateTimeInstance(java.text.DateFormat.SHORT, java.text.DateFormat.SHORT)
                        .format(java.util.Date(armedAtMillis))
                    "Panic Lockdown is ARMED since $formatted"
                } else {
                    "Panic Lockdown is INACTIVE"
                }
                Text(headline, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = PrimaryTextDark)
                Text(
                    if (armed) "Next unlock drops into the decoy vault and sends a silent SOS."
                    else "Tap the red button above to arm instantly.",
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
                "What activation does",
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = PrimaryTextDark
            )
            Text(
                "One tap. Six emergency responses fire at once:",
                fontSize = 12.sp,
                color = SecondaryTextDark
            )
            listOf(
                "1  Locks the app immediately — you re-enter via the lock screen.",
                "2  Arms Duress Mode. Next unlock drops into the decoy vault and sends the silent SOS — real PIN or duress PIN, same outcome.",
                "3  Forces Sensor Monitor to its most aggressive posture.",
                "4  Disables the Smart Link Guard SMS watcher + QR scanner so nothing leaves accidentally.",
                "5  Disables biometric unlock. PIN-only (Fifth-Amendment protection in US).",
                "6  Logs a PANIC_LOCKDOWN_ACTIVATED event with a timestamp."
            ).forEach { line ->
                Text(line, fontSize = 13.sp, color = PrimaryTextDark)
            }
        }
    }
}

@Composable
private fun CompareCard() {
    Surface(
        color = MidnightElevated,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                "Panic Lockdown vs. Border Mode",
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = PrimaryTextDark
            )
            Text(
                "Both are Elite-tier emergency presets, but they serve different moments:",
                fontSize = 12.sp,
                color = SecondaryTextDark
            )
            Text(
                "• Border Mode — planned risky situations (airport, protest, border crossing). Multi-step triple-confirmation. Stacks nine defensive layers at once.",
                fontSize = 13.sp,
                color = PrimaryTextDark
            )
            Text(
                "• Panic Lockdown — the RIGHT-NOW emergency. Single tap. Instant lock + duress arming + sensor flag storm. Use when you can't safely explain what you're doing on the screen.",
                fontSize = 13.sp,
                color = PrimaryTextDark
            )
        }
    }
}
