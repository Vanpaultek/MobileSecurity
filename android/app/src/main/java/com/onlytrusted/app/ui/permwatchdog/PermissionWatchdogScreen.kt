// SPDX-FileCopyrightText: 2026 VanPaulTek Inc
// SPDX-License-Identifier: AGPL-3.0-or-later
package com.onlytrusted.app.ui.permwatchdog

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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.onlytrusted.app.ui.theme.BrandAccentDark
import com.onlytrusted.app.ui.theme.BrandCyanDark
import com.onlytrusted.app.ui.theme.MidnightBackground
import com.onlytrusted.app.ui.theme.MidnightCard
import com.onlytrusted.app.ui.theme.MidnightElevated
import com.onlytrusted.app.ui.theme.PrimaryTextDark
import com.onlytrusted.app.ui.theme.SecondaryTextDark
import com.onlytrusted.app.ui.theme.TertiaryTextDark
import com.onlytrusted.app.ui.theme.WarningDark
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * App Permission Watchdog — Pro-gated screen that lists third-party apps that gained
 * sensitive permissions since the last baseline. See [PermissionWatchdogStore].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PermissionWatchdogScreen(onBack: () -> Unit = {}) {
    val context = LocalContext.current

    var result by remember { mutableStateOf<PermissionWatchdogStore.EvaluationResult?>(null) }
    var auditEnabled by remember {
        mutableStateOf(PermissionWatchdogStore.isAuditEnabled(context))
    }
    var refreshNonce by remember { mutableStateOf(0) }

    LaunchedEffect(refreshNonce) {
        // Running on main; the scan is a quick PackageManager call. Fine for a one-shot.
        result = PermissionWatchdogStore.evaluate(context)
    }

    Scaffold(
        containerColor = MidnightBackground,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Permission Watchdog",
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
                actions = {
                    IconButton(onClick = { refreshNonce++ }) {
                        Icon(
                            Icons.Filled.Refresh,
                            contentDescription = "Rescan",
                            tint = BrandCyanDark
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MidnightBackground,
                    titleContentColor = PrimaryTextDark
                )
            )
        }
    ) { padding ->
        val r = result
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp)
                .padding(top = 4.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            HeroCard()

            if (r == null) {
                Text("Scanning installed apps…", color = SecondaryTextDark, fontSize = 14.sp)
            } else {
                BaselineCard(
                    baselineAt = r.baselineAt,
                    scannedCount = r.currentApps.size,
                    onReset = {
                        PermissionWatchdogStore.resetBaseline(context)
                        refreshNonce++
                    }
                )
                AuditToggleCard(
                    enabled = auditEnabled,
                    onChange = { enabled ->
                        auditEnabled = enabled
                        PermissionWatchdogStore.setAuditEnabled(context, enabled)
                    }
                )
                DiffsCard(diffs = r.diffs)
                CurrentAppsCard(apps = r.currentApps)
            }

            Spacer(Modifier.height(8.dp))
            FooterNote()
        }
    }
}

@Composable
private fun HeroCard() {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MidnightElevated,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(48.dp)
                    .background(BrandAccentDark.copy(alpha = 0.18f), RoundedCornerShape(12.dp))
            ) {
                Icon(
                    Icons.Filled.VerifiedUser,
                    contentDescription = null,
                    tint = BrandAccentDark,
                    modifier = Modifier.size(26.dp)
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    "Watch what other apps can do",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = PrimaryTextDark
                )
                Text(
                    "A daily on-device audit of every installed app's sensitive permissions. " +
                        "Everything stays on your phone — no uploads.",
                    fontSize = 12.sp,
                    color = SecondaryTextDark
                )
            }
        }
    }
}

@Composable
private fun BaselineCard(
    baselineAt: Long,
    scannedCount: Int,
    onReset: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MidnightElevated,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                "Baseline",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = BrandCyanDark
            )
            Text(
                if (baselineAt == 0L) "Not captured yet"
                else "Captured ${formatDate(baselineAt)}",
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = PrimaryTextDark
            )
            Text(
                "Tracking $scannedCount third-party apps that request sensitive permissions.",
                fontSize = 12.sp,
                color = SecondaryTextDark
            )
            TextButton(onClick = onReset) {
                Text("Reset baseline", color = BrandAccentDark, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun AuditToggleCard(enabled: Boolean, onChange: (Boolean) -> Unit) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MidnightElevated,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    "Daily audit enabled",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = PrimaryTextDark
                )
                Text(
                    "When on, OnlyTrusted re-scans on every app launch and alerts you if any " +
                        "app has gained a sensitive permission.",
                    fontSize = 12.sp,
                    color = SecondaryTextDark
                )
            }
            Switch(
                checked = enabled,
                onCheckedChange = onChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = BrandCyanDark
                )
            )
        }
    }
}

@Composable
private fun DiffsCard(diffs: List<PermissionWatchdogStore.Diff>) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MidnightElevated,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    Icons.Filled.Warning,
                    contentDescription = null,
                    tint = if (diffs.isEmpty()) BrandAccentDark else WarningDark,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    if (diffs.isEmpty()) "No new changes since baseline"
                    else "${diffs.size} app${if (diffs.size == 1) "" else "s"} gained permissions",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = PrimaryTextDark
                )
            }
            if (diffs.isEmpty()) {
                Text(
                    "You'll see any app here that gains a sensitive permission between scans.",
                    fontSize = 12.sp,
                    color = SecondaryTextDark
                )
            } else {
                diffs.forEachIndexed { idx, d ->
                    if (idx > 0) Divider(
                        color = Color.White.copy(alpha = 0.06f),
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            d.label,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = PrimaryTextDark
                        )
                        Text(
                            d.packageName,
                            fontSize = 11.sp,
                            color = TertiaryTextDark
                        )
                        Text(
                            "Gained: " + d.gained.joinToString(", ") {
                                PermissionWatchdogStore.shortName(it)
                            },
                            fontSize = 12.sp,
                            color = WarningDark
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CurrentAppsCard(apps: List<PermissionWatchdogStore.AppPermissions>) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MidnightElevated,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                "Current audit",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = BrandCyanDark
            )
            if (apps.isEmpty()) {
                Text(
                    "No third-party apps on this device are requesting any of the tracked " +
                        "sensitive permissions.",
                    fontSize = 12.sp,
                    color = SecondaryTextDark
                )
            } else {
                apps.forEachIndexed { idx, app ->
                    if (idx > 0) Divider(
                        color = Color.White.copy(alpha = 0.06f),
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            app.label,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = PrimaryTextDark
                        )
                        Text(
                            app.permissions.joinToString(", ") {
                                PermissionWatchdogStore.shortName(it)
                            },
                            fontSize = 12.sp,
                            color = SecondaryTextDark
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FooterNote() {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MidnightCard.copy(alpha = 0.6f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            "Reviewing a permission here does not grant or revoke it — tap the app's name in " +
                "Android Settings → Apps to change what it can access.",
            fontSize = 11.sp,
            color = TertiaryTextDark,
            modifier = Modifier.padding(12.dp)
        )
    }
}

private fun formatDate(epochMs: Long): String {
    val fmt = SimpleDateFormat("MMM d, yyyy 'at' h:mm a", Locale.US)
    return fmt.format(Date(epochMs))
}
