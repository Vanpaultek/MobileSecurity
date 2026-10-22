// SPDX-FileCopyrightText: 2026 VanPaulTek Inc
// SPDX-License-Identifier: AGPL-3.0-or-later
package com.onlytrusted.app.ui.dashboard

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.GppGood
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.onlytrusted.app.OnlyTrustedApp
import com.onlytrusted.app.R
import com.onlytrusted.app.ui.theme.BrandAccentDark
import com.onlytrusted.app.ui.theme.BrandCyanDark
import com.onlytrusted.app.ui.theme.BrandPrimaryTeal
import com.onlytrusted.app.ui.theme.DangerDark
import com.onlytrusted.app.ui.theme.MidnightBackground
import com.onlytrusted.app.ui.theme.MidnightCard
import com.onlytrusted.app.ui.theme.MidnightElevated
import com.onlytrusted.app.ui.theme.PrimaryTextDark
import com.onlytrusted.app.ui.theme.SecondaryTextDark
import com.onlytrusted.app.ui.theme.TertiaryTextDark
import com.onlytrusted.app.ui.theme.WarningDark
import java.util.Calendar

@Composable
fun DashboardScreen(
    onPanic: () -> Unit = {},
    onGoToVault: () -> Unit = {},
    onGoToSettings: () -> Unit = {},
    onGoToBlockList: () -> Unit = {},
    onGoToEvents: () -> Unit = {}
) {
    val context = LocalContext.current
    val app = context.applicationContext as OnlyTrustedApp
    val vm: DashboardViewModel = viewModel(factory = viewModelFactory {
        initializer { DashboardViewModel(app) }
    })
    val state by vm.state.collectAsStateWithLifecycle()
    val runtime by vm.runtimeState.collectAsStateWithLifecycle()
    var showHonestLimitsSheet by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        vm.refreshRuntime()
        vm.verifyAuditChain()
    }
    LaunchedEffect(state.trustedContactCount) {
        vm.setHasAnyTrustedContact(state.trustedContactCount > 0)
    }

    val protectionActive = state.trustedContactCount > 0 && runtime.trustedOnlyCalls

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MidnightBackground)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp)
            .padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        CinematicHeader_Unused(name = null, onSettings = onGoToSettings)
        ProtectionActiveCard(isActive = protectionActive)
        StatsRow(
            trusted = state.trustedContactCount,
            blockedToday = state.blockedCalls24h,
            blockedWeek = state.flaggedSms24h
        )
        QuickActionsSection(
            onAddContact = onGoToVault,
            onBlockNumber = onGoToBlockList,
            onViewLog = onGoToEvents
        )
        HonestLimitsCallout(onTap = { showHonestLimitsSheet = true })

        // --- Android-only advanced protections (preserved) ---
        androidx.compose.material3.Card { TrustedModeContent(vm, state, runtime, onGoToVault) }
        androidx.compose.material3.Card { SensorOwnershipContent(context, runtime) }
    }

    if (showHonestLimitsSheet) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showHonestLimitsSheet = false },
            title = { Text("Honest Limits", color = PrimaryTextDark, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    HonestLimitBullet("Monitor other apps' microphone or camera use.")
                    HonestLimitBullet("Stop recordings made on another device.")
                    HonestLimitBullet("Full screenshot blocking across the whole phone needs a hardened OS or root.")
                    HonestLimitBullet("Prevent carrier or operating-system tracking.")
                }
            },
            confirmButton = {
                TextButton(onClick = { showHonestLimitsSheet = false }) {
                    Text("Got it", color = BrandAccentDark)
                }
            },
            containerColor = MidnightCard
        )
    }
}

// MARK: - Cinematic header (lakescape backdrop + wordmark + settings + greeting)

@Composable
private fun CinematicHeader_Unused(name: String?, onSettings: () -> Unit) {
    val headerHeight = 240.dp
    val greeting = remember {
        val h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        when {
            h < 12 -> "Good morning"
            h < 18 -> "Good afternoon"
            else -> "Good evening"
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(headerHeight)
            .clip(RoundedCornerShape(24.dp))
    ) {
        // Lakescape backdrop (reuse SplashHero PNG — anchor top so baked wordmark stays
        // off-screen below the clipped frame; dark gradient overlay preserves greeting
        // legibility.)
        Image(
            painter = painterResource(id = R.drawable.splash_hero),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            alignment = Alignment.TopCenter,
            modifier = Modifier.fillMaxSize()
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            MidnightBackground.copy(alpha = 0.35f),
                            MidnightBackground.copy(alpha = 0.70f),
                            MidnightBackground
                        )
                    )
                )
        )

        // Top row: wordmark + settings
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val wordmark = buildAnnotatedString {
                withStyle(SpanStyle(color = Color.White)) { append("Only") }
                withStyle(SpanStyle(color = BrandCyanDark)) { append("Trusted") }
            }
            Text(
                text = wordmark,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.weight(1f))
            IconButton(
                onClick = onSettings,
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.08f))
                    .border(1.dp, BrandCyanDark.copy(alpha = 0.4f), CircleShape)
            ) {
                Icon(
                    Icons.Filled.Settings,
                    contentDescription = "Settings",
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        // Greeting anchored bottom-left
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 16.dp, bottom = 18.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                "$greeting,",
                color = Color.White,
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    name ?: "Welcome",
                    color = BrandCyanDark,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.width(8.dp))
                Text("👋", fontSize = 22.sp)
            }
        }
    }
}

// MARK: - Protection active card (previous version)

@Composable
private fun ProtectionActiveCard(isActive: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(MidnightElevated)
            .border(
                1.dp,
                BrandCyanDark.copy(alpha = if (isActive) 0.4f else 0.15f),
                RoundedCornerShape(20.dp)
            )
            .padding(18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Neon shield badge — thick glowing cyan ring with heavy bloom + bright white
        // shield in the center. Previous version used a thin ring; this is the "neon" look.
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(86.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(86.dp)
                    .shadow(
                        elevation = 14.dp,
                        shape = CircleShape,
                        ambientColor = BrandCyanDark,
                        spotColor = BrandCyanDark
                    )
                    .border(
                        4.dp,
                        BrandCyanDark.copy(alpha = if (isActive) 1f else 0.35f),
                        CircleShape
                    )
            )
            Icon(
                imageVector = Icons.Filled.GppGood,
                contentDescription = "Protection shield",
                tint = Color.White.copy(alpha = 0.92f),
                modifier = Modifier
                    .size(52.dp)
                    .shadow(
                        elevation = 8.dp,
                        shape = CircleShape,
                        ambientColor = BrandCyanDark,
                        spotColor = BrandCyanDark
                    )
            )
        }

        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                if (isActive) "Protection is active" else "Setup needed",
                color = BrandCyanDark,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                if (isActive) "Only trusted contacts can reach you."
                else "Add trusted contacts to begin.",
                color = SecondaryTextDark,
                fontSize = 14.sp
            )
        }

        Icon(
            Icons.Outlined.ChevronRight,
            contentDescription = null,
            tint = TertiaryTextDark,
            modifier = Modifier.size(18.dp)
        )
    }
}

// MARK: - 3 stat tiles

@Composable
private fun StatsRow(trusted: Int, blockedToday: Int, blockedWeek: Int) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        StatTile(Modifier.weight(1f), Icons.Filled.Groups, BrandAccentDark, trusted, "Trusted\nContacts")
        StatTile(Modifier.weight(1f), Icons.Filled.Block, DangerDark, blockedToday, "Blocked\nToday")
        StatTile(Modifier.weight(1f), Icons.AutoMirrored.Filled.ShowChart, BrandCyanDark, blockedWeek, "Blocked\nThis Week")
    }
}

@Composable
private fun StatTile(modifier: Modifier, icon: ImageVector, tint: Color, value: Int, label: String) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(MidnightElevated)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(32.dp)
                    .background(tint.copy(alpha = 0.18f), RoundedCornerShape(8.dp))
            ) {
                Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(15.dp))
            }
            Spacer(Modifier.weight(1f))
            Text(
                value.toString(),
                color = tint,
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold
            )
        }
        Text(label, color = SecondaryTextDark, fontSize = 12.sp)
    }
}

// MARK: - Quick actions

@Composable
private fun QuickActionsSection(
    onAddContact: () -> Unit,
    onBlockNumber: () -> Unit,
    onViewLog: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            "Quick Actions",
            color = PrimaryTextDark,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold
        )

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            QuickActionCard(
                modifier = Modifier.weight(1f),
                icon = Icons.Filled.PersonAdd,
                title = "Add Contact",
                subtitle = "Add to trusted list",
                tint = BrandAccentDark,
                onClick = onAddContact
            )
            QuickActionCard(
                modifier = Modifier.weight(1f),
                icon = Icons.Filled.CallEnd,
                title = "Block Number",
                subtitle = "Block unwanted calls & messages",
                tint = DangerDark,
                onClick = onBlockNumber
            )
            QuickActionCard(
                modifier = Modifier.weight(1f),
                icon = Icons.Filled.Description,
                title = "View Log",
                subtitle = "See recent blocked activity",
                tint = BrandCyanDark,
                onClick = onViewLog
            )
        }
    }
}

@Composable
private fun QuickActionCard(
    modifier: Modifier,
    icon: ImageVector,
    title: String,
    subtitle: String,
    tint: Color,
    onClick: () -> Unit
) {
    androidx.compose.material3.Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = MidnightElevated,
        modifier = modifier.heightIn(min = 150.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(46.dp)
                    .background(tint.copy(alpha = 0.18f), RoundedCornerShape(12.dp))
            ) {
                Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
            }
            Text(
                title,
                color = PrimaryTextDark,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                subtitle,
                color = SecondaryTextDark,
                fontSize = 11.sp,
                lineHeight = 14.sp
            )
        }
    }
}

// MARK: - Honest Limits callout (restored)

@Composable
private fun HonestLimitsCallout(onTap: () -> Unit) {
    androidx.compose.material3.Surface(
        onClick = onTap,
        shape = RoundedCornerShape(18.dp),
        color = WarningDark.copy(alpha = 0.08f),
        border = androidx.compose.foundation.BorderStroke(1.dp, WarningDark.copy(alpha = 0.5f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.padding(16.dp)
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(48.dp)
                    .background(WarningDark.copy(alpha = 0.18f), RoundedCornerShape(12.dp))
            ) {
                Icon(Icons.Filled.Warning, contentDescription = null, tint = WarningDark, modifier = Modifier.size(22.dp))
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Honest Limits", color = WarningDark, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                Text(
                    "This app cannot monitor other apps' microphone or camera use. Tap to see all platform limits.",
                    color = SecondaryTextDark,
                    fontSize = 13.sp
                )
            }
            Icon(
                Icons.Outlined.ChevronRight,
                contentDescription = null,
                tint = WarningDark.copy(alpha = 0.7f),
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
private fun HonestLimitBullet(text: String) {
    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(
            modifier = Modifier
                .padding(top = 6.dp)
                .size(5.dp)
                .background(WarningDark, CircleShape)
        )
        Text(text, color = PrimaryTextDark, fontSize = 13.sp, lineHeight = 18.sp)
    }
}

// MARK: - Android-only extras (preserved from previous dashboard)

@Composable
private fun TrustedModeContent(
    vm: DashboardViewModel,
    state: DashboardState,
    runtime: DashboardViewModel.RuntimeState,
    onManageVault: () -> Unit
) {
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Groups, contentDescription = null, tint = BrandAccentDark)
            Spacer(Modifier.width(8.dp))
            Text(
                "Trusted-only mode",
                color = PrimaryTextDark,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = onManageVault) { Text("Vault →", color = BrandAccentDark) }
        }
        Text("${state.trustedContactCount} trusted contact(s).", color = SecondaryTextDark, fontSize = 13.sp)
        SwitchRow("Silence calls not in vault", runtime.trustedOnlyCalls, vm::setTrustedOnlyCalls)
        SwitchRow("Flag SMS not in vault", runtime.trustedOnlySms, vm::setTrustedOnlySms)
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = PrimaryTextDark, fontSize = 13.sp, modifier = Modifier.weight(1f))
        androidx.compose.material3.Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun SensorOwnershipContent(
    context: android.content.Context,
    runtime: DashboardViewModel.RuntimeState
) {
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Verified, contentDescription = null, tint = BrandAccentDark)
            Spacer(Modifier.width(8.dp))
            Text(
                "Mic & camera ownership",
                color = PrimaryTextDark,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
        SensorStatus("Mic", runtime.sensorPrivacy.micMuted)
        SensorStatus("Camera", runtime.sensorPrivacy.cameraMuted)
        TextButton(onClick = {
            val intent = Intent(Settings.ACTION_PRIVACY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            runCatching { context.startActivity(intent) }
        }) {
            Text("Open system Privacy settings", color = BrandAccentDark)
            Icon(Icons.Outlined.ChevronRight, contentDescription = null, tint = BrandAccentDark)
        }
    }
}

@Composable
private fun SensorStatus(label: String, muted: Boolean?) {
    val (text, tint) = when (muted) {
        true -> "muted — hardware off" to BrandAccentDark
        false -> "available to granted apps" to SecondaryTextDark
        null -> "state unknown" to SecondaryTextDark
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Filled.Shield, contentDescription = null, tint = tint, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(8.dp))
        Text("$label: $text", color = PrimaryTextDark, fontSize = 13.sp)
    }
}
