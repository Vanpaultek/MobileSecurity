// SPDX-FileCopyrightText: 2026 VanPaulTek Inc
// SPDX-License-Identifier: AGPL-3.0-or-later
package com.onlytrusted.app.ui.privacyshield

import android.content.Intent
import android.provider.Settings
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
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
import com.onlytrusted.app.ui.theme.BrandAccentDark
import com.onlytrusted.app.ui.theme.BrandCyanDark
import com.onlytrusted.app.ui.theme.DangerDark
import com.onlytrusted.app.ui.theme.MidnightBackground
import com.onlytrusted.app.ui.theme.MidnightCard
import com.onlytrusted.app.ui.theme.PrimaryTextDark
import com.onlytrusted.app.ui.theme.SecondaryTextDark
import com.onlytrusted.app.ui.theme.SuccessDark
import com.onlytrusted.app.ui.theme.TertiaryTextDark
import com.onlytrusted.app.ui.theme.WarningDark
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val subGold = Color(0xFFEAB308)

/**
 * Auto Privacy Shield dashboard. Three CLEARLY-SEPARATED cards:
 *
 *   1. App Recording Shutdown — the enforced bit. OnlyTrusted stops its own capture
 *      sessions on background / screen-off. Toggles + Stop-now + last-10 events.
 *   2. Selected Apps Restriction — "Available on Elite" (Phase 2 ships Family Link-shape
 *      restrictions via a Device Owner-free approximation).
 *   3. System Sensor Toggles — Android 12+ exposes `supportsSensorToggle`. We report that
 *      bool but tell the user to flip the Quick Settings tile themselves (consumer apps
 *      can't read current state, let alone toggle it).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivacyShieldDashboard(
    onBack: () -> Unit,
    onOpenHardening: () -> Unit,
    onOpenUpgrade: () -> Unit
) {
    val context = LocalContext.current
    var autoStopEnabled by remember {
        mutableStateOf(PrivacyShieldState.isAutoStopEnabled(context))
    }
    var requireManualRestart by remember {
        mutableStateOf(PrivacyShieldState.isRequireManualRestart(context))
    }
    val sensorToggleSupport = remember { PrivacyShieldState.supportsSystemSensorToggles(context) }

    Scaffold(
        containerColor = MidnightBackground,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Privacy Shield",
                        color = PrimaryTextDark,
                        fontWeight = FontWeight.SemiBold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = PrimaryTextDark
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MidnightBackground
                )
            )
        }
    ) { inner ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            HeroCard()

            // Card 1: App Recording Shutdown (ENFORCED)
            Card {
                CardHeader(Icons.Filled.Security, "App Recording Shutdown")
                StatusChip(SuccessDark, "Enforced")
                Spacer(Modifier.height(10.dp))
                Text(
                    "OnlyTrusted's own camera and microphone sessions stop automatically when the app goes to background or the screen locks. We never auto-restart on return — you re-open the feature yourself.",
                    color = SecondaryTextDark,
                    fontSize = 13.sp
                )
                Spacer(Modifier.height(10.dp))

                ToggleRow(
                    label = "Enable auto-stop",
                    checked = autoStopEnabled,
                    onChange = {
                        autoStopEnabled = it
                        PrivacyShieldState.setAutoStopEnabled(context, it)
                    }
                )
                ToggleRow(
                    label = "Require manual restart after unlock",
                    checked = requireManualRestart,
                    onChange = {
                        requireManualRestart = it
                        PrivacyShieldState.setRequireManualRestart(context, it)
                    }
                )

                Spacer(Modifier.height(8.dp))
                // Live state
                val active = CaptureRegistry.active
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        if (active.isEmpty()) Icons.Filled.MicOff else Icons.Filled.Mic,
                        contentDescription = null,
                        tint = if (active.isEmpty()) SecondaryTextDark else WarningDark,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.size(8.dp))
                    Text(
                        if (active.isEmpty())
                            "No sessions active."
                        else
                            "${active.size} active: ${active.joinToString(", ") { it.feature }}",
                        color = SecondaryTextDark,
                        fontSize = 12.sp
                    )
                }

                if (active.isNotEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    Button(
                        onClick = {
                            CaptureRegistry.stopAll(CaptureRegistry.Reason.MANUAL_STOP_ALL)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = WarningDark,
                            contentColor = Color.Black
                        )
                    ) {
                        Icon(Icons.Filled.Stop, contentDescription = null)
                        Spacer(Modifier.size(6.dp))
                        Text("Stop recording now", fontWeight = FontWeight.SemiBold)
                    }
                }

                val history = CaptureRegistry.history
                if (history.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    val latest = history.first()
                    Row(verticalAlignment = Alignment.Top) {
                        Icon(
                            Icons.Filled.CheckCircle,
                            contentDescription = null,
                            tint = SuccessDark,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.size(6.dp))
                        Text(
                            "Last event: stopped ${latest.feature} ${latest.kind.name.lowercase()} at ${formatTime(latest.timestampMs)} (${latest.reason.name.lowercase().replace('_', ' ')}).",
                            color = SecondaryTextDark,
                            fontSize = 12.sp
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    Text("History (last ${history.size})", color = TertiaryTextDark, fontSize = 11.sp)
                    history.forEach { ev ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                if (ev.kind == CaptureRegistry.Kind.CAMERA) Icons.Filled.CameraAlt else Icons.Filled.Mic,
                                contentDescription = null,
                                tint = SecondaryTextDark,
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(Modifier.size(6.dp))
                            Text(
                                "${formatTime(ev.timestampMs)} · ${ev.feature} · ${ev.reason.name.lowercase().replace('_', ' ')}",
                                color = TertiaryTextDark,
                                fontSize = 11.sp
                            )
                        }
                    }
                }
            }

            // Card 2: Selected Apps Restriction (ELITE PHASE 2)
            Card {
                CardHeader(Icons.Filled.Security, "Selected Apps Restriction")
                StatusChip(BrandCyanDark, "Available on Elite")
                Spacer(Modifier.height(10.dp))
                Text(
                    "Android reserves cross-app sensor control to Device Owner provisioning (not available to consumer apps). Phase 2 ships an Elite restriction surface that leans on Android's shortcut-pinning + accessibility prompts rather than claiming system-wide enforcement.",
                    color = SecondaryTextDark,
                    fontSize = 13.sp
                )
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = onOpenUpgrade,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = subGold,
                        contentColor = Color.Black
                    )
                ) {
                    Icon(Icons.Filled.EmojiEvents, contentDescription = null)
                    Spacer(Modifier.size(6.dp))
                    Text("Upgrade to Elite", fontWeight = FontWeight.SemiBold)
                }
            }

            // Card 3: System Sensor Toggles (OS-LEVEL)
            Card {
                CardHeader(Icons.Filled.Tune, "System Sensor Toggles")
                StatusChip(SecondaryTextDark, "Owned by Android")
                Spacer(Modifier.height(10.dp))
                if (sensorToggleSupport.anySupported) {
                    Text(
                        "Your device supports system-wide mic and camera toggles. We can't operate them from here (that needs OBSERVE_SENSOR_PRIVACY, reserved to privileged apps) — pull down Quick Settings and tap the Mic or Camera tile.",
                        color = SecondaryTextDark,
                        fontSize = 13.sp
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Supported sensors: " +
                            buildString {
                                if (sensorToggleSupport.micSupported) append("microphone")
                                if (sensorToggleSupport.micSupported && sensorToggleSupport.cameraSupported) append(" + ")
                                if (sensorToggleSupport.cameraSupported) append("camera")
                            },
                        color = SuccessDark,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                } else {
                    Text(
                        "Your device doesn't expose system-wide sensor toggles (Android 11 and below, or OEM-stripped). Review per-app permissions via Settings → Apps instead.",
                        color = SecondaryTextDark,
                        fontSize = 13.sp
                    )
                }
                Spacer(Modifier.height(10.dp))
                QuickSettingsGuide()
                Spacer(Modifier.height(10.dp))
                OutlinedButton(
                    onClick = {
                        val intent = Intent(Settings.ACTION_PRIVACY_SETTINGS)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        runCatching { context.startActivity(intent) }.onFailure {
                            runCatching {
                                context.startActivity(
                                    Intent(Settings.ACTION_APPLICATION_SETTINGS)
                                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                )
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Filled.OpenInNew, contentDescription = null, tint = BrandAccentDark)
                    Spacer(Modifier.size(6.dp))
                    Text("Open Privacy Settings", color = BrandAccentDark, fontWeight = FontWeight.SemiBold)
                }
            }

            // Hardening Assistant jump-off
            Card {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .background(BrandAccentDark, RoundedCornerShape(11.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Filled.Tune, contentDescription = null, tint = Color.Black)
                    }
                    Spacer(Modifier.size(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            "Hardening Assistant",
                            color = PrimaryTextDark,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 15.sp
                        )
                        Text(
                            "Review OnlyTrusted's own permissions and jump into Settings.",
                            color = SecondaryTextDark,
                            fontSize = 12.sp
                        )
                    }
                    OutlinedButton(onClick = onOpenHardening) {
                        Text("Open", color = BrandAccentDark)
                    }
                }
            }

            Text(
                "Why not a single green 'Protected' badge? Only card #1 is enforced by OnlyTrusted. Cards #2 and #3 depend on an Elite upgrade and on Android itself. Collapsing them would misrepresent what this app actually does.",
                color = TertiaryTextDark,
                fontSize = 11.sp,
                modifier = Modifier.padding(horizontal = 4.dp)
            )
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun HeroCard() {
    Card {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(BrandAccentDark.copy(alpha = 0.18f), RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Filled.Security,
                    contentDescription = null,
                    tint = BrandAccentDark
                )
            }
            Spacer(Modifier.size(14.dp))
            Column {
                Text(
                    "Three layers, honestly scoped",
                    color = PrimaryTextDark,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp
                )
                Text(
                    "Android reserves global sensor control to Device Owner provisioning, so no consumer app can do that. Here's what we can — and can't — do on your behalf.",
                    color = SecondaryTextDark,
                    fontSize = 12.sp
                )
            }
        }
    }
}

@Composable
private fun Card(content: @Composable () -> Unit) {
    Surface(
        color = MidnightCard,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) { content() }
    }
}

@Composable
private fun CardHeader(icon: ImageVector, title: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = BrandAccentDark, modifier = Modifier.size(20.dp))
        Spacer(Modifier.size(8.dp))
        Text(title, color = PrimaryTextDark, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
    }
    Spacer(Modifier.height(8.dp))
}

@Composable
private fun StatusChip(color: Color, label: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .background(color.copy(alpha = 0.14f), shape = androidx.compose.foundation.shape.RoundedCornerShape(999.dp))
            .padding(horizontal = 10.dp, vertical = 5.dp)
    ) {
        Box(modifier = Modifier.size(8.dp).background(color, CircleShape))
        Spacer(Modifier.size(6.dp))
        Text(label, color = color, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun ToggleRow(
    label: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
    ) {
        Text(
            label,
            color = PrimaryTextDark,
            fontWeight = FontWeight.SemiBold,
            fontSize = 14.sp,
            modifier = Modifier.weight(1f)
        )
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.Black,
                checkedTrackColor = BrandAccentDark,
                uncheckedThumbColor = SecondaryTextDark
            )
        )
    }
}

@Composable
private fun QuickSettingsGuide() {
    Column {
        Text(
            "Quick Settings — 3 steps:",
            color = PrimaryTextDark,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(4.dp))
        Text("1. Swipe down from the top of the screen (twice to fully expand).", color = SecondaryTextDark, fontSize = 12.sp)
        Text("2. Find the Microphone and Camera tiles (you may need to edit tiles to add them).", color = SecondaryTextDark, fontSize = 12.sp)
        Text("3. Tap the tile — grey = off, highlighted = on.", color = SecondaryTextDark, fontSize = 12.sp)
    }
}

private fun formatTime(ms: Long): String {
    return SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(ms))
}
