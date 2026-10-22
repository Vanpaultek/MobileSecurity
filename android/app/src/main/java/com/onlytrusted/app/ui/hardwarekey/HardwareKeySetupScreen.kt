// SPDX-FileCopyrightText: 2026 VanPaulTek Inc
// SPDX-License-Identifier: AGPL-3.0-or-later
package com.onlytrusted.app.ui.hardwarekey

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
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
import com.onlytrusted.app.ui.theme.DangerDark
import com.onlytrusted.app.ui.theme.MidnightBackground
import com.onlytrusted.app.ui.theme.MidnightCard
import com.onlytrusted.app.ui.theme.MidnightElevated
import com.onlytrusted.app.ui.theme.PrimaryTextDark
import com.onlytrusted.app.ui.theme.SecondaryTextDark
import com.onlytrusted.app.ui.theme.WarningDark

/**
 * Elite-tier Hardware Security Key setup screen. Mirrors `HardwareKeySetupScreen.swift`.
 *
 * Reached from Settings → Security & data → "Hardware Security Key (Elite)" when the user has
 * Elite. Non-Elite users see the upsell dialog (same mechanism as Duress / Watchdog upsells).
 */
@Composable
fun HardwareKeySetupScreen(onBack: () -> Unit = {}) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val enrolled = remember { mutableStateListOf<HardwareKeyState.EnrolledKey>() }
    var emulatorSkip by remember { mutableStateOf(HardwareKeyState.isEmulatorSkipEnabled(context)) }

    var showNameDialog by remember { mutableStateOf(false) }
    var pendingName by remember { mutableStateOf("") }
    var enrollError by remember { mutableStateOf<String?>(null) }
    var enrollSuccess by remember { mutableStateOf<String?>(null) }

    // Load on first composition.
    LaunchOnce {
        enrolled.clear()
        enrolled.addAll(HardwareKeyState.enrolledKeys(context))
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MidnightBackground)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = BrandAccentDark)
            }
            Text(
                "Hardware Security Key",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = PrimaryTextDark
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            ExplainerCard()
            EnrolledKeysCard(
                keys = enrolled,
                onRemove = { key ->
                    HardwareKeyState.removeKey(context, key.id)
                    enrolled.clear()
                    enrolled.addAll(HardwareKeyState.enrolledKeys(context))
                }
            )
            EnrollActionCard(
                suggestedName = suggestedName(enrolled.size),
                onEnroll = {
                    pendingName = suggestedName(enrolled.size)
                    showNameDialog = true
                }
            )
            if (HardwareKeyState.isRunningOnEmulator) {
                EmulatorFallbackCard(
                    enabled = emulatorSkip,
                    onToggle = { want ->
                        emulatorSkip = want
                        HardwareKeyState.setEmulatorSkipEnabled(context, want)
                    }
                )
            }
            DeviceLimitationCard()
        }
    }

    if (showNameDialog) {
        AlertDialog(
            onDismissRequest = { showNameDialog = false },
            title = { Text("Name this key", color = PrimaryTextDark, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "Pick a nickname you'll remember if you ever need to remove it " +
                            "(for example, \"YubiKey 5C\" or \"Office backup\").",
                        color = SecondaryTextDark,
                        fontSize = 13.sp
                    )
                    OutlinedTextField(
                        value = pendingName,
                        onValueChange = { pendingName = it },
                        singleLine = true,
                        label = { Text("Key nickname") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val name = pendingName.trim().ifBlank { suggestedName(enrolled.size) }
                    showNameDialog = false
                    HardwareKeyGate.enrollNewKey(
                        context = context,
                        displayName = name,
                        scope = scope
                    ) { outcome ->
                        when (outcome) {
                            is HardwareKeyGate.EnrollOutcome.Success -> {
                                enrolled.clear()
                                enrolled.addAll(HardwareKeyState.enrolledKeys(context))
                                enrollSuccess = "\"${outcome.key.displayName}\" is now armed."
                            }
                            is HardwareKeyGate.EnrollOutcome.Cancelled ->
                                enrollError = "Enrollment cancelled."
                            is HardwareKeyGate.EnrollOutcome.Failed ->
                                enrollError = outcome.message
                        }
                    }
                }) {
                    Text("Continue", color = subGold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showNameDialog = false }) {
                    Text("Cancel", color = SecondaryTextDark)
                }
            },
            containerColor = MidnightCard
        )
    }

    if (enrollError != null) {
        AlertDialog(
            onDismissRequest = { enrollError = null },
            title = { Text("Enrollment failed", color = PrimaryTextDark, fontWeight = FontWeight.Bold) },
            text = { Text(enrollError.orEmpty(), color = SecondaryTextDark) },
            confirmButton = {
                TextButton(onClick = { enrollError = null }) { Text("OK", color = BrandAccentDark) }
            },
            containerColor = MidnightCard
        )
    }
    if (enrollSuccess != null) {
        AlertDialog(
            onDismissRequest = { enrollSuccess = null },
            icon = {
                Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = BrandAccentDark)
            },
            title = { Text("Key enrolled", color = PrimaryTextDark, fontWeight = FontWeight.Bold) },
            text = { Text(enrollSuccess.orEmpty(), color = SecondaryTextDark) },
            confirmButton = {
                TextButton(onClick = { enrollSuccess = null }) { Text("Done", color = BrandAccentDark) }
            },
            containerColor = MidnightCard
        )
    }
}

private fun suggestedName(existingCount: Int): String =
    if (existingCount == 0) "Primary key" else "Backup key $existingCount"

// ---------------------------------------------------------------------------- cards

@Composable
private fun ExplainerCard() {
    SettingsCard {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.VpnKey, contentDescription = null, tint = subGold)
                Spacer(Modifier.width(10.dp))
                Text(
                    "Why hardware security keys matter",
                    color = PrimaryTextDark,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Text(
                "A hardware key is a physical device (like a YubiKey or Google Titan Key) that proves YOU are present — not just someone who knows your PIN. Once enrolled:",
                color = SecondaryTextDark,
                fontSize = 13.sp
            )
            BulletRow("Duress Mode can only be disarmed with the key.")
            BulletRow("\"Wipe everything\" needs the key.")
            BulletRow("Encrypted backup export needs the key.")
            BulletRow("Dead-Man Wipe can only be disabled with the key.")
            Text(
                "This defeats SIM-swap attackers, coerced-PIN entry, and remote account-takeover attempts. Add two keys and keep one in a safe place in case you lose the first.",
                color = SecondaryTextDark,
                fontSize = 13.sp
            )
        }
    }
}

@Composable
private fun EnrolledKeysCard(
    keys: List<HardwareKeyState.EnrolledKey>,
    onRemove: (HardwareKeyState.EnrolledKey) -> Unit
) {
    SettingsCard {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                "Enrolled keys",
                color = PrimaryTextDark,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold
            )
            if (keys.isEmpty()) {
                Text(
                    "No keys enrolled yet. Add one below.",
                    color = SecondaryTextDark,
                    fontSize = 13.sp
                )
            } else {
                keys.forEach { key ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(32.dp)
                                .background(subGold.copy(alpha = 0.18f), CircleShape)
                        ) {
                            Icon(
                                Icons.Filled.VpnKey,
                                contentDescription = null,
                                tint = subGold,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                key.displayName,
                                color = PrimaryTextDark,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                "Added on ${key.formattedDateAdded()}",
                                color = SecondaryTextDark,
                                fontSize = 12.sp
                            )
                        }
                        TextButton(onClick = { onRemove(key) }) {
                            Text("Remove", color = DangerDark, fontSize = 13.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EnrollActionCard(
    suggestedName: String,
    onEnroll: () -> Unit
) {
    SettingsCard {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                "Insert / tap your security key when prompted.",
                color = PrimaryTextDark,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                "Supports USB, NFC, and Bluetooth FIDO2 keys. The key never leaves your hand " +
                    "— Android talks to it directly via the Credential Manager.",
                color = SecondaryTextDark,
                fontSize = 12.sp
            )
            Button(
                onClick = onEnroll,
                colors = ButtonDefaults.buttonColors(containerColor = subGold),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Enroll key ($suggestedName)", color = Color.Black)
            }
        }
    }
}

@Composable
private fun EmulatorFallbackCard(enabled: Boolean, onToggle: (Boolean) -> Unit) {
    SettingsCard {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.BugReport, contentDescription = null, tint = WarningDark)
                Spacer(Modifier.width(10.dp))
                Text(
                    "Emulator developer skip",
                    color = PrimaryTextDark,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Text(
                "The Android emulator cannot talk to a real USB / NFC key. Enable this to " +
                    "short-circuit the hardware-key prompt during manual testing. Real-device " +
                    "builds ignore this flag.",
                color = SecondaryTextDark,
                fontSize = 12.sp
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(
                    checked = enabled,
                    onCheckedChange = onToggle,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = WarningDark
                    )
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    "Skip hardware key check (emulator only)",
                    color = PrimaryTextDark,
                    fontSize = 12.sp
                )
            }
        }
    }
}

@Composable
private fun DeviceLimitationCard() {
    SettingsCard {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Info, contentDescription = null, tint = BrandAccentDark)
                Spacer(Modifier.width(10.dp))
                Text(
                    "Device compatibility",
                    color = PrimaryTextDark,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Text(
                "Hardware key enrollment is not supported in the Android emulator — test on " +
                    "a physical device. Android 9+ (API 28) required. All enrollment data stays " +
                    "on this device in EncryptedSharedPreferences.",
                color = SecondaryTextDark,
                fontSize = 12.sp
            )
        }
    }
}

@Composable
private fun BulletRow(text: String) {
    Row(verticalAlignment = Alignment.Top) {
        Text("•", color = BrandAccentDark, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.width(8.dp))
        Text(text, color = PrimaryTextDark, fontSize = 13.sp)
    }
}

@Composable
private fun SettingsCard(content: @Composable () -> Unit) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MidnightElevated,
        modifier = Modifier.fillMaxWidth()
    ) { content() }
}

// Fire-and-forget "run on first composition" so the hardware key list is loaded once.
@Composable
private fun LaunchOnce(block: () -> Unit) {
    val fired = remember { mutableStateOf(false) }
    if (!fired.value) {
        fired.value = true
        block()
    }
    // No DisposableEffect — the list is reloaded manually after mutations.
    Box(Modifier.height(0.dp))
}
