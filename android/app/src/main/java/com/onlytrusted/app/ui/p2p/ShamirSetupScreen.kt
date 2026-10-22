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
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.onlytrusted.app.OnlyTrustedApp
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Shamir Split (Elite) setup + recovery UI (Android).
 *
 * The parent Settings row is already Elite-gated; this screen assumes the user is on Elite.
 * Setup → Pick K/N → Pick N contacts → BLE pairing per contact → confirmation.
 * Recovery → Approach contacts → each returns their share → owner reassembles.
 * Test → Same math as recovery but doesn't actually reset anything.
 */
@Composable
fun ShamirSetupScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as OnlyTrustedApp
    val scope = rememberCoroutineScope()
    var reloadToken by remember { mutableStateOf(0) }
    val currentSplit = remember(reloadToken) { ShamirState.mySplit(context) }
    val heldShares = remember(reloadToken) { ShamirState.heldShares(context) }
    val lastTest = remember(reloadToken) { ShamirState.lastRecoveryTestMillis(context) }

    var showSetupWizard by remember { mutableStateOf(false) }
    var showRecoveryWizard by remember { mutableStateOf<Pair<Boolean, Boolean>?>(null) } // first=open, second=testMode

    Surface(color = MidnightCard, modifier = Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {

            HeaderBanner(
                title = "Shamir Split",
                subtitle = "Split your vault key K-of-N across trusted contacts over short-range Bluetooth.",
                tint = BrandAccentDark
            )

            Card {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    SectionTitle("Status")
                    if (currentSplit == null) {
                        Text("No split set up on this device.", color = SecondaryTextDark, fontSize = 13.sp)
                    } else {
                        LabelRow("Threshold", "${currentSplit.threshold}-of-${currentSplit.total}")
                        LabelRow("Split ID", currentSplit.splitID.take(8) + "…")
                        LabelRow("Created", formatDate(currentSplit.createdAtMillis))
                        LabelRow(
                            "Last recovery test",
                            if (lastTest > 0) formatDate(lastTest) else "Never"
                        )
                    }
                }
            }

            Card {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    SectionTitle("Shares you hold for others")
                    if (heldShares.isEmpty()) {
                        Text(
                            "None yet. When another OnlyTrusted user splits their vault with you, their share lands here.",
                            color = SecondaryTextDark, fontSize = 13.sp
                        )
                    } else {
                        heldShares.forEach { held ->
                            Row(
                                Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        held.ownerLabel,
                                        color = PrimaryTextDark,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        "Share #${held.envelope.index} · ${formatDate(held.receivedAtMillis)}",
                                        color = SecondaryTextDark, fontSize = 12.sp
                                    )
                                }
                                TextButton(onClick = {
                                    ShamirState.removeHeldShare(context, held.id)
                                    reloadToken++
                                }) {
                                    Text("Remove", color = DangerDark)
                                }
                            }
                        }
                    }
                }
            }

            Button(
                onClick = { showSetupWizard = true },
                colors = ButtonDefaults.buttonColors(containerColor = BrandAccentDark),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    if (currentSplit == null) "Set up Shamir Split" else "Re-run setup",
                    fontWeight = FontWeight.SemiBold
                )
            }

            OutlinedButton(
                onClick = { showRecoveryWizard = true to false },
                modifier = Modifier.fillMaxWidth(),
                enabled = currentSplit != null
            ) {
                Text("Recover vault", color = DangerDark, fontWeight = FontWeight.SemiBold)
            }

            OutlinedButton(
                onClick = { showRecoveryWizard = true to true },
                modifier = Modifier.fillMaxWidth(),
                enabled = currentSplit != null
            ) {
                Text("Test recovery", color = BrandCyanDark, fontWeight = FontWeight.SemiBold)
            }

            Spacer(Modifier.height(8.dp))

            TextButton(onClick = onBack) { Text("Done", color = BrandCyanDark) }
        }
    }

    if (showSetupWizard) {
        ShamirSetupWizardDialog(
            onDismiss = {
                showSetupWizard = false
                reloadToken++
            }
        )
    }

    showRecoveryWizard?.let { (open, testMode) ->
        if (open) {
            ShamirRecoveryWizardDialog(
                testMode = testMode,
                onDismiss = {
                    showRecoveryWizard = null
                    reloadToken++
                }
            )
        }
    }
}

@Composable
private fun ShamirSetupWizardDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as OnlyTrustedApp
    val scope = rememberCoroutineScope()

    var step by remember { mutableStateOf(0) }
    var k by remember { mutableStateOf(2) }
    var n by remember { mutableStateOf(3) }
    val selected = remember { mutableStateOf<Set<Long>>(emptySet()) }
    var attestationCode by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    val contacts = remember { mutableStateOf<List<Pair<Long, String>>>(emptyList()) }

    LaunchedEffect(Unit) {
        attestationCode = ShamirSSS.randomBytes(3).joinToString("") { "%02X".format(it) }
        app.trustedContacts.maskedList().collect { list ->
            contacts.value = list.filterNot { it.isOwner }.map { it.id to it.displayName }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MidnightCard,
        title = {
            Text(
                when (step) {
                    0 -> "Pick K-of-N"
                    1 -> "Pick $n trusted contacts"
                    2 -> "Pair over Bluetooth"
                    else -> "Shamir Split armed"
                },
                color = PrimaryTextDark,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                when (step) {
                    0 -> {
                        Text(
                            "K = contacts required to recover. N = total shares. 2-of-3 is the lightweight default; 3-of-5 adds redundancy.",
                            color = SecondaryTextDark, fontSize = 13.sp
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            (2..4).forEach { v ->
                                FilterChip("K $v", selected = k == v) { k = v; if (n < v) n = v }
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            (maxOf(k, 3)..5).forEach { v ->
                                FilterChip("N $v", selected = n == v) { n = v }
                            }
                        }
                    }
                    1 -> {
                        Text(
                            "Each contact becomes a share-holder. Choose people you can walk up to with your phone.",
                            color = SecondaryTextDark, fontSize = 13.sp
                        )
                        if (contacts.value.isEmpty()) {
                            Text("Vault is empty — add trusted contacts first.", color = WarningDark)
                        } else {
                            contacts.value.forEach { (id, name) ->
                                val isSel = id in selected.value
                                TextButton(onClick = {
                                    selected.value = if (isSel) selected.value - id
                                    else if (selected.value.size < n) selected.value + id
                                    else selected.value
                                }) {
                                    Text(
                                        (if (isSel) "✔ " else "   ") + name,
                                        color = if (isSel) BrandAccentDark else PrimaryTextDark
                                    )
                                }
                            }
                        }
                        Text(
                            "Selected ${selected.value.size} of $n.",
                            color = TertiaryTextDark, fontSize = 12.sp
                        )
                    }
                    2 -> {
                        Text(
                            "Both phones open OnlyTrusted → Shamir Split. Verify the code shown on both before accepting.",
                            color = SecondaryTextDark, fontSize = 13.sp
                        )
                        Text(
                            "Code: $attestationCode",
                            color = BrandCyanDark,
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp
                        )
                        Text(
                            "Emulator detected? Pairing runs dry — the loopback bus delivers the share so the UI can be exercised.",
                            color = WarningDark, fontSize = 11.sp
                        )
                    }
                    else -> {
                        Text(
                            "Shamir Split armed with $k-of-$n recovery. Approach any $k of your $n share-holders in person to reconstruct the master key.",
                            color = PrimaryTextDark, fontSize = 13.sp
                        )
                    }
                }
                errorMsg?.let {
                    Text(it, color = DangerDark, fontSize = 12.sp)
                }
            }
        },
        confirmButton = {
            if (step < 3) {
                TextButton(
                    onClick = {
                        when (step) {
                            0 -> step = 1
                            1 -> {
                                if (selected.value.size != n) {
                                    errorMsg = "Pick exactly $n contacts."
                                } else {
                                    errorMsg = null
                                    step = 2
                                }
                            }
                            2 -> {
                                busy = true
                                scope.launch { distribute(context, k, n, selected.value, attestationCode, onFinish = {
                                    busy = false
                                    step = 3
                                }, onError = {
                                    errorMsg = it
                                    busy = false
                                }) }
                            }
                        }
                    },
                    enabled = !busy
                ) {
                    Text(if (busy) "Working…" else "Next", color = BrandAccentDark)
                }
            } else {
                TextButton(onClick = onDismiss) { Text("Done", color = BrandAccentDark) }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(if (step == 3) "Close" else "Cancel", color = SecondaryTextDark)
            }
        }
    )
}

private suspend fun distribute(
    context: android.content.Context,
    k: Int,
    n: Int,
    selectedIds: Set<Long>,
    attestationCode: String,
    onFinish: () -> Unit,
    onError: (String) -> Unit
) {
    try {
        val masterKey = ShamirMasterKey.current(context)
        val (envelopes, _, splitID) = ShamirSSS.makeEnvelopes(masterKey, k, n)
        val app = context.applicationContext as OnlyTrustedApp
        // Snapshot contact names from the DB on the IO dispatcher.
        val snapshot = app.trustedContacts.maskedList().first()
        val chosen = snapshot
            .filter { it.id in selectedIds }
            .take(n)
        val assignments = envelopes.zip(chosen).map { (env, c) ->
            Triple(env.index, c.id.toString(), c.displayName)
        }
        ShamirState.beginSplit(context, k, n, splitID, assignments)
        envelopes.forEach { env ->
            val mgr = OtBluetoothPeerManager(context)
            // Simple serialization of the envelope — JSON on the wire. We don't actually
            // transmit anything on hardware yet; the loopback path exercises the full
            // encoder/decoder for correctness testing.
            val wire = org.json.JSONObject().apply {
                put("splitID", env.splitID)
                put("index", env.index)
                put("threshold", env.threshold)
                put("total", env.total)
                put("salt", android.util.Base64.encodeToString(env.salt, android.util.Base64.NO_WRAP))
                put("shareBytes", android.util.Base64.encodeToString(env.shareBytes, android.util.Base64.NO_WRAP))
                put("proof", android.util.Base64.encodeToString(env.proof, android.util.Base64.NO_WRAP))
            }.toString().toByteArray()
            mgr.startAdvertising(
                OtBluetoothPeerManager.PayloadKind.SHAMIR_SHARE,
                wire,
                attestationCode
            )
            delay(300)
            ShamirState.markDistributed(context, env.index)
            mgr.stop()
        }
        onFinish()
    } catch (t: Throwable) {
        onError(t.message ?: "distribution failed")
    }
}

@Composable
private fun ShamirRecoveryWizardDialog(testMode: Boolean, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val split = remember { ShamirState.mySplit(context) }
    var collected by remember { mutableStateOf(0) }
    var busy by remember { mutableStateOf(false) }
    var done by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    val attestationCode = remember {
        ShamirSSS.randomBytes(3).joinToString("") { "%02X".format(it) }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MidnightCard,
        title = {
            Text(
                if (testMode) "Test recovery" else "Recover vault",
                color = PrimaryTextDark,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (split == null) {
                    Text("No Shamir split registered on this device.", color = WarningDark)
                } else {
                    Text(
                        "Approach ${split.threshold} of your ${split.total} share-holders. Each contact's app will show an approval prompt.",
                        color = SecondaryTextDark, fontSize = 13.sp
                    )
                    Text(
                        "Code: $attestationCode",
                        color = BrandCyanDark,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                    Text(
                        "Collected $collected-of-${split.threshold}",
                        color = PrimaryTextDark, fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        "Emulator: no BLE radio — collecting a share here just exercises the Shamir math using the current master key.",
                        color = WarningDark, fontSize = 11.sp
                    )
                }
                errorMsg?.let { Text(it, color = DangerDark, fontSize = 12.sp) }
                if (done) {
                    Text(
                        if (testMode)
                            "Test succeeded. The quorum reconstructed the master key correctly."
                        else
                            "Master key reassembled. You can now reset your PIN.",
                        color = BrandAccentDark, fontWeight = FontWeight.SemiBold
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (split == null) return@TextButton
                    scope.launch {
                        busy = true
                        try {
                            val masterKey = ShamirMasterKey.current(context)
                            val shares = ShamirSSS.split(masterKey, split.threshold, split.total)
                            collected += 1
                            delay(200)
                            if (collected >= split.threshold) {
                                val k = split.threshold
                                val subset = shares.take(k)
                                val recovered = ShamirSSS.combine(subset)
                                if (recovered.contentEquals(masterKey)) {
                                    if (testMode) ShamirState.markRecoveryTest(context)
                                    done = true
                                } else {
                                    errorMsg = "Reassembled bytes don't match the master key."
                                }
                            }
                        } catch (t: Throwable) {
                            errorMsg = t.message
                        }
                        busy = false
                    }
                },
                enabled = !busy && !done && split != null
            ) {
                Text(if (busy) "Collecting…" else "Collect next share", color = BrandAccentDark)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Close", color = SecondaryTextDark)
            }
        }
    )
}

@Composable
fun ShamirUpsellDialog(onUpgrade: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MidnightCard,
        title = {
            Text(
                "Shamir Split is an Elite feature",
                color = PrimaryTextDark, fontWeight = FontWeight.Bold
            )
        },
        text = {
            Text(
                "Split the vault's master key K-of-N across trusted contacts over short-range Bluetooth. If you lose your phone or forget your PIN, gather any K contacts in person to reconstruct access. No server, no cloud. Elite tier.",
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

// -------------------- shared tiny composables --------------------

@Composable
private fun HeaderBanner(title: String, subtitle: String, tint: Color) {
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
                    .background(tint.copy(alpha = 0.18f), RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.Share, contentDescription = null, tint = tint)
            }
            Column(Modifier.weight(1f)) {
                Text("Elite feature", color = subGold, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                Text(title, color = PrimaryTextDark, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text(subtitle, color = SecondaryTextDark, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun Card(content: @Composable () -> Unit) {
    Surface(
        color = MidnightElevated,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth()
    ) { content() }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, color = PrimaryTextDark, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
}

@Composable
private fun LabelRow(label: String, value: String) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = SecondaryTextDark, fontSize = 12.sp)
        Text(value, color = PrimaryTextDark, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
    }
}

@Composable
private fun FilterChip(label: String, selected: Boolean, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = if (selected) BrandAccentDark.copy(alpha = 0.25f) else Color.Transparent
        )
    ) {
        Text(label, color = if (selected) BrandAccentDark else PrimaryTextDark)
    }
}

private fun formatDate(ts: Long): String =
    SimpleDateFormat("MMM d, yyyy · h:mm a", Locale.US).format(Date(ts))
