// SPDX-FileCopyrightText: 2026 VanPaulTek Inc
// SPDX-License-Identifier: AGPL-3.0-or-later
package com.onlytrusted.app.ui.deadmanwipe

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Timelapse
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import com.onlytrusted.app.deadmanwipe.DeadmanWipeState
import com.onlytrusted.app.ui.subscription.subGold
import com.onlytrusted.app.ui.theme.BrandAccentDark
import com.onlytrusted.app.ui.theme.BrandCyanDark
import com.onlytrusted.app.ui.theme.DangerDark
import com.onlytrusted.app.ui.theme.MidnightBackground
import com.onlytrusted.app.ui.theme.MidnightCard
import com.onlytrusted.app.ui.theme.MidnightElevated
import com.onlytrusted.app.ui.theme.PrimaryTextDark
import com.onlytrusted.app.ui.theme.SecondaryTextDark
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Dead-Man Wipe (Elite) setup. The caller gates on `SubscriptionState.hasElite(context)`;
 * this composable assumes the user is on Elite.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeadmanWipeSetupScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()

    var enabled by remember { mutableStateOf(DeadmanWipeState.isEnabled(context)) }
    var interval by remember { mutableIntStateOf(DeadmanWipeState.intervalDays(context)) }
    var lastUnlock by remember { mutableStateOf(DeadmanWipeState.lastUnlock(context)) }
    var deadline by remember { mutableStateOf(DeadmanWipeState.wipeDeadline(context)) }

    var showEnableDialog by remember { mutableStateOf(false) }
    var showResetDialog by remember { mutableStateOf(false) }

    fun refreshStatus() {
        lastUnlock = DeadmanWipeState.lastUnlock(context)
        deadline = DeadmanWipeState.wipeDeadline(context)
    }

    Scaffold(
        containerColor = MidnightBackground,
        topBar = {
            TopAppBar(
                title = { Text("Dead-Man Wipe", color = PrimaryTextDark) },
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
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Timelapse,
                            contentDescription = null,
                            tint = if (enabled) DangerDark else subGold,
                            modifier = Modifier.padding(end = 4.dp)
                        )
                        Column(Modifier.weight(1f)) {
                            Text("Enabled", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = PrimaryTextDark)
                            Text(
                                if (enabled) "Timer is live. Open the app before the deadline to reset it."
                                else "Off. Nothing will be wiped automatically.",
                                fontSize = 12.sp,
                                color = SecondaryTextDark
                            )
                        }
                        Switch(
                            checked = enabled,
                            onCheckedChange = { newValue ->
                                if (newValue && !enabled) {
                                    showEnableDialog = true
                                } else if (!newValue) {
                                    // Elite Trusted Witness gate — disabling the Dead-Man Wipe
                                    // is on the gated-action list. No witness armed → no-op.
                                    com.onlytrusted.app.ui.p2p.TrustedWitnessGate.require(
                                        context = context,
                                        action = com.onlytrusted.app.ui.p2p.TrustedWitnessState.GatedAction.DISABLE_DEADMAN_WIPE,
                                        reason = "Have your witness approve disabling Dead-Man Wipe.",
                                        scope = scope,
                                        onSuccess = {
                                            DeadmanWipeState.disable(context)
                                            enabled = false
                                            refreshStatus()
                                        },
                                        onCancel = { /* keep toggle on */ }
                                    )
                                }
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = DangerDark
                            )
                        )
                    }
                }
            }

            Card {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Wipe interval", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = PrimaryTextDark)
                    Text(
                        "How long OnlyTrusted will wait without an unlock before it wipes everything.",
                        fontSize = 12.sp,
                        color = SecondaryTextDark
                    )
                    DeadmanWipeState.allowedIntervals.forEach { d ->
                        RadioRow(label = "$d days", selected = interval == d) {
                            interval = d
                            DeadmanWipeState.setIntervalDays(context, d)
                            if (enabled) {
                                com.onlytrusted.app.deadmanwipe.DeadmanWipeScheduler.rescheduleAll(context)
                            }
                            refreshStatus()
                        }
                    }
                }
            }

            if (enabled) {
                Card {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Status", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = PrimaryTextDark)
                        LabelValueRow("Last unlock", formatDate(lastUnlock))
                        LabelValueRow("Will wipe at", formatDate(deadline), DangerDark)
                    }
                }

                Card {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Warning", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = DangerDark)
                        Text(
                            "The app will delete every trusted contact, every blocked number, every event, every setting, and every encrypted vault entry on ${formatDate(deadline)} if you don't unlock the app before then. This cannot be undone.",
                            fontSize = 12.sp,
                            color = PrimaryTextDark
                        )
                    }
                }

                Button(
                    onClick = { showResetDialog = true },
                    colors = ButtonDefaults.buttonColors(containerColor = BrandAccentDark),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Reset timer", color = Color.White, fontWeight = FontWeight.SemiBold)
                }
            }

            Spacer(Modifier.height(8.dp))
        }

        if (showEnableDialog) {
            AlertDialog(
                onDismissRequest = { showEnableDialog = false },
                containerColor = MidnightCard,
                title = { Text("Enable Dead-Man Wipe?", color = PrimaryTextDark, fontWeight = FontWeight.Bold) },
                text = {
                    Text(
                        "If you don't open OnlyTrusted for $interval days, every trusted contact, " +
                            "every blocked number, every event, every setting, and every encrypted " +
                            "vault entry will be deleted. This cannot be undone.",
                        color = SecondaryTextDark
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        showEnableDialog = false
                        DeadmanWipeState.enable(context, interval)
                        enabled = true
                        refreshStatus()
                    }) { Text("Enable", color = DangerDark, fontWeight = FontWeight.Bold) }
                },
                dismissButton = {
                    TextButton(onClick = { showEnableDialog = false }) { Text("Cancel") }
                }
            )
        }

        if (showResetDialog) {
            AlertDialog(
                onDismissRequest = { showResetDialog = false },
                containerColor = MidnightCard,
                title = { Text("Reset timer?", color = PrimaryTextDark) },
                text = { Text("Resets the countdown to $interval days from now.", color = SecondaryTextDark) },
                confirmButton = {
                    TextButton(onClick = {
                        showResetDialog = false
                        DeadmanWipeState.markUnlockNow(context)
                        refreshStatus()
                    }) { Text("Reset", color = DangerDark, fontWeight = FontWeight.Bold) }
                },
                dismissButton = {
                    TextButton(onClick = { showResetDialog = false }) { Text("Cancel") }
                }
            )
        }
    }

    // Elite Trusted Witness approval overlay — fires when disabling the Dead-Man Wipe
    // requires witness approval.
    com.onlytrusted.app.ui.p2p.TrustedWitnessApprovalOverlay()
}

@Composable
private fun HeaderCard() {
    Card {
        Row(
            Modifier.padding(16.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = DangerDark,
                modifier = Modifier.padding(end = 4.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Timelapse,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.padding(10.dp)
                )
            }
            Column {
                Text("Elite feature", fontSize = 11.sp, color = subGold, fontWeight = FontWeight.SemiBold)
                Text(
                    "A trip-wire for lost or seized devices.",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = PrimaryTextDark
                )
                Text(
                    "If you can't unlock the app for a long time, it wipes itself. All data is local; there is no server fallback.",
                    fontSize = 12.sp,
                    color = SecondaryTextDark
                )
            }
        }
    }
}

@Composable
private fun Card(content: @Composable () -> Unit) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MidnightElevated,
        modifier = Modifier.fillMaxWidth()
    ) { content() }
}

@Composable
private fun RadioRow(label: String, selected: Boolean, onClick: () -> Unit) {
    TextButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                if (selected) "• " else "   ",
                color = if (selected) BrandAccentDark else SecondaryTextDark
            )
            Text(label, color = if (selected) BrandAccentDark else PrimaryTextDark)
        }
    }
}

@Composable
private fun LabelValueRow(label: String, value: String, valueTint: Color = PrimaryTextDark) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, fontSize = 12.sp, color = SecondaryTextDark)
        Text(value, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = valueTint)
    }
}

private fun formatDate(date: Date): String =
    SimpleDateFormat("MMM d, yyyy · h:mm a", Locale.US).format(date)
