// SPDX-FileCopyrightText: 2026 VanPaulTek Inc
// SPDX-License-Identifier: AGPL-3.0-or-later
package com.onlytrusted.app.ui.checkin

import android.Manifest
import android.app.TimePickerDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocationSearching
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
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
import androidx.core.content.ContextCompat
import com.onlytrusted.app.OnlyTrustedApp
import com.onlytrusted.app.data.db.entities.SecurityEvent
import com.onlytrusted.app.ui.duress.DuressState
import com.onlytrusted.app.ui.theme.BrandAccentDark
import com.onlytrusted.app.ui.theme.BrandCyanDark
import com.onlytrusted.app.ui.theme.DangerDark
import com.onlytrusted.app.ui.theme.MidnightBackground
import com.onlytrusted.app.ui.theme.MidnightElevated
import com.onlytrusted.app.ui.theme.PrimaryTextDark
import com.onlytrusted.app.ui.theme.SecondaryTextDark
import com.onlytrusted.app.ui.theme.WarningDark
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Pro-only Safety Check-In screen.
 *
 * Reached via Settings → Security & data → "Safety Check-In (Pro)". Gated by
 * `SubscriptionState.hasHighPremium(context)` at the entry row — free users see an
 * upsell dialog before they can navigate here.
 */
@Composable
fun SafetyCheckInScreen() {
    val context = LocalContext.current
    val app = context.applicationContext as OnlyTrustedApp
    val maskedFlow = remember { app.trustedContacts.maskedList() }
    val masked by maskedFlow.collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()

    val trustedNumbers = remember(masked) {
        masked.flatMap { it.normalisedNumbers }.filter { it.isNotBlank() }.distinct()
    }

    var scheduledEnabled by remember { mutableStateOf(CheckInPrefs.scheduledEnabled(context)) }
    var scheduledHour by remember { mutableStateOf(CheckInPrefs.scheduledHour(context)) }
    var scheduledMinute by remember { mutableStateOf(CheckInPrefs.scheduledMinute(context)) }
    var lastAtMillis by remember { mutableStateOf(CheckInPrefs.lastCheckInMillis(context)) }
    var status by remember { mutableStateOf<String?>(null) }

    val ownerName = DuressState.ownerName(context).ifBlank {
        masked.firstOrNull { it.isOwner }?.displayName ?: ""
    }

    val smsPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            scope.launch { performBlast(app, context, ownerName, trustedNumbers) { sent ->
                lastAtMillis = System.currentTimeMillis()
                CheckInPrefs.setLastCheckIn(context, lastAtMillis)
                status = if (sent > 0) {
                    "Check-in sent to $sent contact${if (sent == 1) "" else "s"}."
                } else {
                    "Could not send any check-in — no SIM or airplane mode?"
                }
            } }
        } else {
            status = "SMS permission denied. Enable it in system settings to blast check-ins."
        }
    }

    val notificationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            scheduledEnabled = true
            CheckInPrefs.setScheduledEnabled(context, true)
            CheckInScheduler.schedule(context)
        } else {
            status = "Notification permission denied. Enable it in system settings to use scheduled check-ins."
        }
    }

    LaunchedEffect(Unit) {
        lastAtMillis = CheckInPrefs.lastCheckInMillis(context)
    }

    val previewBody = CheckInSmsSender.buildBody(ownerName)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MidnightBackground)
            .verticalScroll(rememberScrollState())
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Status card
        Card {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.LocationSearching,
                        contentDescription = null,
                        tint = BrandAccentDark
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        "Blast your status in one tap",
                        color = PrimaryTextDark,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Text(
                    "Sends a pre-written 'I'm safe' SMS to every number in your Trusted Vault.",
                    color = SecondaryTextDark,
                    fontSize = 12.sp
                )
                Divider(color = Color.White.copy(alpha = 0.06f))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Last check-in", color = SecondaryTextDark, fontSize = 12.sp)
                        Text(
                            lastCheckInLabel(lastAtMillis),
                            color = PrimaryTextDark,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("Will notify", color = SecondaryTextDark, fontSize = 12.sp)
                        Text(
                            "${trustedNumbers.size} contact${if (trustedNumbers.size == 1) "" else "s"}",
                            color = BrandCyanDark,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }

        // Primary CTA
        Card {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Preview", color = SecondaryTextDark, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                Surface(
                    color = Color.Black.copy(alpha = 0.25f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        previewBody,
                        color = PrimaryTextDark,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(14.dp)
                    )
                }
                Button(
                    onClick = {
                        if (trustedNumbers.isEmpty()) {
                            status = "Add a trusted contact first — the check-in has nowhere to go."
                            return@Button
                        }
                        if (!hasSmsPermission(context)) {
                            smsPermission.launch(Manifest.permission.SEND_SMS)
                            return@Button
                        }
                        scope.launch { performBlast(app, context, ownerName, trustedNumbers) { sent ->
                            lastAtMillis = System.currentTimeMillis()
                            CheckInPrefs.setLastCheckIn(context, lastAtMillis)
                            status = if (sent > 0) {
                                "Check-in sent to $sent contact${if (sent == 1) "" else "s"}."
                            } else {
                                "Could not send any check-in — no SIM or airplane mode?"
                            }
                        } }
                    },
                    enabled = trustedNumbers.isNotEmpty(),
                    colors = ButtonDefaults.buttonColors(containerColor = BrandAccentDark),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Filled.CheckCircle, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("I'm safe", fontWeight = FontWeight.SemiBold)
                }
                if (trustedNumbers.isEmpty()) {
                    Text(
                        "Add a trusted contact first so the check-in has somewhere to go.",
                        color = WarningDark,
                        fontSize = 11.sp
                    )
                }
            }
        }

        // Scheduled nag
        Card {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Scheduled check-in", color = PrimaryTextDark, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        Text(
                            "Nags you with a notification if you haven't pressed 'I'm safe' by this time of day.",
                            color = SecondaryTextDark,
                            fontSize = 12.sp
                        )
                    }
                    Switch(
                        checked = scheduledEnabled,
                        onCheckedChange = { want ->
                            if (want) {
                                if (hasNotificationPermission(context)) {
                                    scheduledEnabled = true
                                    CheckInPrefs.setScheduledEnabled(context, true)
                                    CheckInScheduler.schedule(context)
                                } else if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                                    notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                                } else {
                                    scheduledEnabled = true
                                    CheckInPrefs.setScheduledEnabled(context, true)
                                    CheckInScheduler.schedule(context)
                                }
                            } else {
                                scheduledEnabled = false
                                CheckInPrefs.setScheduledEnabled(context, false)
                                CheckInScheduler.cancel(context)
                            }
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = WarningDark
                        )
                    )
                }
                if (scheduledEnabled) {
                    OutlinedButton(
                        onClick = {
                            TimePickerDialog(
                                context,
                                { _, h, m ->
                                    scheduledHour = h
                                    scheduledMinute = m
                                    CheckInPrefs.setScheduledTime(context, h, m)
                                    CheckInScheduler.schedule(context)
                                },
                                scheduledHour,
                                scheduledMinute,
                                false
                            ).show()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Reminder time: ${formatTime(scheduledHour, scheduledMinute)}")
                    }
                }
            }
        }

        // How-it-works
        Card {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Info, contentDescription = null, tint = BrandCyanDark)
                    Spacer(Modifier.width(8.dp))
                    Text("How it works on Android", color = PrimaryTextDark, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                }
                Text(
                    "The send happens silently via SmsManager — no composer, no user tap. " +
                        "Pro flow already granted SEND_SMS for Duress Mode, so this reuses the same permission.",
                    color = SecondaryTextDark,
                    fontSize = 12.sp
                )
            }
        }

        status?.let { message ->
            Card(borderColor = BrandCyanDark.copy(alpha = 0.4f)) {
                Row(
                    Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Filled.Info, contentDescription = null, tint = BrandCyanDark)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(message, color = PrimaryTextDark, fontSize = 13.sp)
                    }
                    OutlinedButton(onClick = {
                        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = Uri.fromParts("package", context.packageName, null)
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        runCatching { context.startActivity(intent) }
                    }) { Text("Settings") }
                }
            }
        }
    }
}

private suspend fun performBlast(
    app: OnlyTrustedApp,
    context: android.content.Context,
    ownerName: String,
    numbers: List<String>,
    onDone: (Int) -> Unit
) {
    val body = CheckInSmsSender.buildBody(ownerName)
    val sent = CheckInSmsSender.blast(context, numbers, body)
    app.repository.log(
        type = SecurityEvent.TYPE_SAFETY_CHECKIN_SENT,
        detail = "recipients=${numbers.size} sent=$sent"
    )
    onDone(sent)
}

private fun hasSmsPermission(ctx: android.content.Context): Boolean =
    ContextCompat.checkSelfPermission(ctx, Manifest.permission.SEND_SMS) ==
        PackageManager.PERMISSION_GRANTED

private fun hasNotificationPermission(ctx: android.content.Context): Boolean {
    if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.TIRAMISU) return true
    return ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) ==
        PackageManager.PERMISSION_GRANTED
}

@Composable
private fun Card(
    borderColor: Color? = null,
    content: @Composable () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MidnightElevated,
        border = borderColor?.let { androidx.compose.foundation.BorderStroke(1.dp, it) },
        modifier = Modifier.fillMaxWidth()
    ) { content() }
}

private fun lastCheckInLabel(millis: Long): String {
    if (millis <= 0L) return "Never"
    return SimpleDateFormat("MMM d, h:mm a", Locale.US).format(Date(millis))
}

private fun formatTime(hour: Int, minute: Int): String {
    val cal = java.util.Calendar.getInstance().apply {
        set(java.util.Calendar.HOUR_OF_DAY, hour)
        set(java.util.Calendar.MINUTE, minute)
    }
    return SimpleDateFormat("h:mm a", Locale.US).format(cal.time)
}
