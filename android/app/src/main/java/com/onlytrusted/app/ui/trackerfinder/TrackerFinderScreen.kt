// SPDX-FileCopyrightText: 2026 VanPaulTek Inc
// SPDX-License-Identifier: AGPL-3.0-or-later
package com.onlytrusted.app.ui.trackerfinder

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BluetoothSearching
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.NearbyError
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import com.onlytrusted.app.ui.theme.AppBackdropScaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import com.onlytrusted.app.ui.theme.MidnightElevated
import com.onlytrusted.app.ui.theme.PrimaryTextDark
import com.onlytrusted.app.ui.theme.SecondaryTextDark
import com.onlytrusted.app.ui.theme.TertiaryTextDark
import com.onlytrusted.app.ui.theme.WarningDark
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

/**
 * Tracker Finder (Pro) screen. The entry row in SettingsScreen already gates on
 * `SubscriptionState.hasHighPremium(context)`; if we're here the viewer is on Pro.
 *
 * Capabilities:
 *   • One-shot foreground 30-s scan ("Scan now").
 *   • Continuous background scan toggle (foreground service).
 *   • Followers list — devices meeting the "3+ sessions, >15 min" rule.
 *   • Recent sightings list for the current one-shot scan.
 *   • Clear history button.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrackerFinderScreen(onBack: () -> Unit = {}) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var hasPermissions by remember {
        mutableStateOf(TrackerFinderScanner.hasRequiredPermissions(context))
    }
    var continuous by remember {
        mutableStateOf(TrackerFinderStore.isContinuousEnabled(context))
    }
    var scanning by remember { mutableStateOf(false) }
    var remainingSeconds by remember { mutableStateOf(0) }
    var scanError by remember { mutableStateOf<String?>(null) }
    var confirmClear by remember { mutableStateOf(false) }
    val liveSightings = remember { mutableStateListOf<TrackerFinderStore.Sighting>() }
    var followers by remember {
        mutableStateOf(TrackerFinderStore.followingTrackers(context))
    }

    val permLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        hasPermissions = grants.values.all { it }
    }

    var scanner by remember { mutableStateOf<TrackerFinderScanner?>(null) }

    DisposableEffect(Unit) {
        onDispose { runCatching { scanner?.stop() } }
    }

    fun startOneShot() {
        if (!hasPermissions) {
            permLauncher.launch(TrackerFinderScanner.requiredRuntimePermissions().toTypedArray())
            return
        }
        if (scanning) return
        scanError = null
        liveSightings.clear()
        remainingSeconds = ONE_SHOT_SECONDS
        val s = TrackerFinderScanner(
            context = context,
            onClassified = { sighting ->
                liveSightings.add(0, sighting)
                if (liveSightings.size > 50) liveSightings.removeAt(liveSightings.lastIndex)
            },
            onFinished = { _ ->
                scanning = false
                followers = TrackerFinderStore.followingTrackers(context)
            },
            onError = { msg ->
                scanning = false
                scanError = msg
            }
        )
        if (s.start()) {
            scanner = s
            scanning = true
            scope.launch {
                while (remainingSeconds > 0) {
                    delay(1_000)
                    remainingSeconds -= 1
                }
                runCatching { s.stop() }
                scanner = null
            }
        }
    }

    fun stopScan() {
        runCatching { scanner?.stop() }
        scanner = null
        scanning = false
    }

    LaunchedEffect(Unit) {
        hasPermissions = TrackerFinderScanner.hasRequiredPermissions(context)
        continuous = TrackerFinderStore.isContinuousEnabled(context)
        followers = TrackerFinderStore.followingTrackers(context)
    }

    AppBackdropScaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Tracker Finder",
                        fontWeight = FontWeight.SemiBold,
                        color = PrimaryTextDark
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = PrimaryTextDark
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MidnightBackground)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            HeroCard()
            if (!hasPermissions) {
                PermissionCard(onGrant = {
                    permLauncher.launch(
                        TrackerFinderScanner.requiredRuntimePermissions().toTypedArray()
                    )
                })
            }
            ScanCard(
                scanning = scanning,
                remainingSeconds = remainingSeconds,
                scanError = scanError,
                onScan = { startOneShot() },
                onStop = { stopScan() }
            )
            ContinuousCard(
                enabled = continuous,
                permissionGranted = hasPermissions,
                onToggle = { newValue ->
                    continuous = newValue
                    TrackerFinderStore.setContinuousEnabled(context, newValue)
                    if (newValue) {
                        TrackerFinderService.startIfEligible(context)
                    } else {
                        TrackerFinderService.stop(context)
                    }
                }
            )
            FollowersCard(followers = followers)
            RecentCard(sightings = liveSightings.toList())
            ExplainerCard()
            OutlinedButton(
                onClick = { confirmClear = true },
                modifier = Modifier.fillMaxWidth(),
                border = BorderStroke(1.dp, DangerDark)
            ) {
                Icon(Icons.Filled.Delete, contentDescription = null, tint = DangerDark)
                Spacer(Modifier.width(8.dp))
                Text("Clear history", color = DangerDark, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(8.dp))
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Clear tracker history?", color = PrimaryTextDark) },
            text = {
                Text(
                    "Removes every stored sighting from this device. The follower list will " +
                        "reset — a device will only reappear after three new scan sessions.",
                    color = SecondaryTextDark
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmClear = false
                    TrackerFinderStore.clear(context)
                    liveSightings.clear()
                    followers = emptyList()
                }) { Text("Clear", color = DangerDark) }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) { Text("Cancel") }
            },
            containerColor = MidnightElevated
        )
    }
}

private const val ONE_SHOT_SECONDS = 30

// ---------- Cards ----------

@Composable
private fun HeroCard() {
    Card {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            IconChip(Icons.Filled.NearbyError, BrandAccentDark)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    "Scan for AirTags, Tiles, and SmartTags",
                    color = PrimaryTextDark,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    "A 30-second scan looks for known tracker signatures and flags any device that reappears across your scans — the pattern a stalker-tag would create.",
                    color = SecondaryTextDark,
                    fontSize = 12.sp
                )
            }
        }
    }
}

@Composable
private fun PermissionCard(onGrant: () -> Unit) {
    Card(border = WarningDark.copy(alpha = 0.5f)) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Warning, contentDescription = null, tint = WarningDark)
                Spacer(Modifier.width(10.dp))
                Text(
                    "Grant Bluetooth / Location to scan",
                    color = PrimaryTextDark,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Text(
                "Android requires Nearby devices (Android 12+) or Fine Location (Android 11-) " +
                    "for a BLE scan to return anything. We never share your location — we " +
                    "only use the permission to read tracker advertisement data.",
                color = SecondaryTextDark,
                fontSize = 12.sp
            )
            Button(
                onClick = onGrant,
                colors = ButtonDefaults.buttonColors(containerColor = WarningDark),
                modifier = Modifier.fillMaxWidth()
            ) { Text("Grant permission") }
        }
    }
}

@Composable
private fun ScanCard(
    scanning: Boolean,
    remainingSeconds: Int,
    scanError: String?,
    onScan: () -> Unit,
    onStop: () -> Unit
) {
    Card {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            CardTitle(Icons.Filled.Radar, "One-shot scan")
            val statusLine = when {
                scanError != null -> scanError
                scanning -> "Scanning… $remainingSeconds seconds left."
                else -> "Tap scan to look for AirTags, Tiles, SmartTags, and unknown BLE devices."
            }
            Text(statusLine, color = SecondaryTextDark, fontSize = 12.sp)
            Button(
                onClick = onScan,
                enabled = !scanning,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = BrandAccentDark)
            ) {
                Icon(Icons.Filled.BluetoothSearching, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(
                    if (scanning) "Scanning… ${remainingSeconds}s" else "Scan now (30 s)",
                    fontWeight = FontWeight.SemiBold
                )
            }
            if (scanning) {
                TextButton(onClick = onStop, modifier = Modifier.fillMaxWidth()) {
                    Text("Stop scan", color = SecondaryTextDark)
                }
            }
        }
    }
}

@Composable
private fun ContinuousCard(
    enabled: Boolean,
    permissionGranted: Boolean,
    onToggle: (Boolean) -> Unit
) {
    Card {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconChip(Icons.Filled.BluetoothSearching, BrandCyanDark)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        "Continuous background scan",
                        color = PrimaryTextDark,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        "Runs a short scan every 15 minutes so persistent followers can surface.",
                        color = SecondaryTextDark,
                        fontSize = 12.sp
                    )
                }
                Switch(
                    checked = enabled,
                    enabled = permissionGranted,
                    onCheckedChange = onToggle,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = BrandCyanDark
                    )
                )
            }
            Text(
                "Uses a foreground service with a persistent notification. Battery impact is " +
                    "modest — the radio is only on for ~20 seconds per cycle. You can turn " +
                    "this off any time.",
                color = TertiaryTextDark,
                fontSize = 11.sp
            )
        }
    }
}

@Composable
private fun FollowersCard(followers: List<TrackerFinderStore.FollowerTracker>) {
    Card {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            CardTitle(Icons.Filled.Warning, "Possible followers")
            if (followers.isEmpty()) {
                Text(
                    "No device is reappearing across your scans yet. A tracker has to be seen in 3+ scans over more than 15 minutes before it lands here.",
                    color = SecondaryTextDark,
                    fontSize = 12.sp
                )
            } else {
                followers.forEachIndexed { index, tracker ->
                    if (index > 0) RowDivider()
                    FollowerRow(tracker)
                }
            }
        }
    }
}

@Composable
private fun FollowerRow(tracker: TrackerFinderStore.FollowerTracker) {
    val fmt = remember { DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        IconChip(Icons.Filled.NearbyError, WarningDark, size = 36)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    tracker.type.displayName,
                    color = PrimaryTextDark,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    "${tracker.sessionCount} scans",
                    color = WarningDark,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Text(
                "ID ${tracker.shortLabel} · avg RSSI ${tracker.averageRssi} dBm",
                color = SecondaryTextDark,
                fontSize = 12.sp
            )
            Text(
                "First ${fmt.format(Date(tracker.firstSeenMs))} · " +
                    "last ${fmt.format(Date(tracker.lastSeenMs))}",
                color = TertiaryTextDark,
                fontSize = 10.sp
            )
        }
    }
}

@Composable
private fun RecentCard(sightings: List<TrackerFinderStore.Sighting>) {
    Card {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            CardTitle(Icons.Filled.Radar, "What this scan saw")
            if (sightings.isEmpty()) {
                Text(
                    "No tracker signatures in this session yet. Tap Scan now, then walk a few metres so the signal strength can change.",
                    color = SecondaryTextDark,
                    fontSize = 12.sp
                )
            } else {
                val fmt = remember { DateFormat.getTimeInstance(DateFormat.MEDIUM) }
                sightings.take(10).forEachIndexed { index, s ->
                    if (index > 0) RowDivider()
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            when (s.type) {
                                TrackerFinderStore.TrackerType.APPLE_AIRTAG -> Icons.Filled.NearbyError
                                TrackerFinderStore.TrackerType.TILE -> Icons.Filled.Radar
                                TrackerFinderStore.TrackerType.SAMSUNG_SMARTTAG -> Icons.Filled.NearbyError
                                TrackerFinderStore.TrackerType.UNKNOWN_BLE -> Icons.Filled.Warning
                            },
                            contentDescription = null,
                            tint = BrandCyanDark,
                            modifier = Modifier.size(20.dp)
                        )
                        Column(Modifier.weight(1f)) {
                            Text(
                                s.type.displayName,
                                color = PrimaryTextDark,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                "ID …${s.macHash.takeLast(6)} · ${s.rssi} dBm",
                                color = TertiaryTextDark,
                                fontSize = 10.sp
                            )
                        }
                        Text(
                            fmt.format(Date(s.timestamp)),
                            color = TertiaryTextDark,
                            fontSize = 10.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ExplainerCard() {
    Card {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            CardTitle(Icons.Filled.Info, "What this does — and doesn't")
            ExplainerLine(
                icon = Icons.Filled.CheckCircle,
                tint = BrandAccentDark,
                text = "Flags AirTag, Tile, and Samsung SmartTag signatures, plus any unnamed BLE device that reappears across 3+ scans over >15 minutes."
            )
            ExplainerLine(
                icon = Icons.Filled.Warning,
                tint = WarningDark,
                text = "Android needs Nearby devices (12+) or Fine Location (<12) to see BLE advertisements. We never phone home with location — the permission is read-only, on-device."
            )
            ExplainerLine(
                icon = Icons.Filled.Lock,
                tint = BrandAccentDark,
                text = "MAC addresses are SHA-256-hashed with a per-install salt before persistence, and the sightings file is stored in EncryptedSharedPreferences (AES-256-GCM)."
            )
        }
    }
}

@Composable
private fun ExplainerLine(icon: ImageVector, tint: Color, text: String) {
    Row(
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
        Text(text, color = SecondaryTextDark, fontSize = 12.sp, modifier = Modifier.weight(1f))
    }
}

// ---------- Shared bits ----------

@Composable
private fun Card(border: Color? = null, content: @Composable () -> Unit) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MidnightElevated,
        border = border?.let { BorderStroke(1.dp, it) },
        modifier = Modifier.fillMaxWidth()
    ) { content() }
}

@Composable
private fun CardTitle(icon: ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = BrandAccentDark)
        Spacer(Modifier.width(10.dp))
        Text(text, color = PrimaryTextDark, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun IconChip(icon: ImageVector, tint: Color, size: Int = 44) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(size.dp)
            .background(tint.copy(alpha = 0.18f), RoundedCornerShape(11.dp))
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size((size * 0.5).dp)
        )
    }
}

@Composable
private fun RowDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(Color.White.copy(alpha = 0.06f))
            .padding(start = 48.dp)
    )
}
