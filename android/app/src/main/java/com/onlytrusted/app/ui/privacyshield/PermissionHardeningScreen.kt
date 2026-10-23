// SPDX-FileCopyrightText: 2026 VanPaulTek Inc
// SPDX-License-Identifier: AGPL-3.0-or-later
package com.onlytrusted.app.ui.privacyshield

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
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
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MicNone
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.runtime.DisposableEffect
import com.onlytrusted.app.ui.theme.BrandAccentDark
import com.onlytrusted.app.ui.theme.DangerDark
import com.onlytrusted.app.ui.theme.MidnightBackground
import com.onlytrusted.app.ui.theme.MidnightCard
import com.onlytrusted.app.ui.theme.PrimaryTextDark
import com.onlytrusted.app.ui.theme.SecondaryTextDark
import com.onlytrusted.app.ui.theme.SuccessDark
import com.onlytrusted.app.ui.theme.TertiaryTextDark
import com.onlytrusted.app.ui.theme.WarningDark

/**
 * Permission Hardening Assistant (Android).
 *
 * Shows OnlyTrusted's current grant status for CAMERA / RECORD_AUDIO / READ_MEDIA_IMAGES
 * (or READ_EXTERNAL_STORAGE on pre-33) / ACCESS_FINE_LOCATION. Each row has an "Open
 * Settings → OnlyTrusted → Permissions" button via `ACTION_APPLICATION_DETAILS_SETTINGS`.
 * Below the per-app rows, an "Audit other apps" card opens Android's own Privacy /
 * Permission Manager.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PermissionHardeningScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    // Trigger recomposition when returning from Settings so grant chips refresh.
    var refreshKey by remember { mutableStateOf(0) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val obs = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refreshKey++
        }
        lifecycleOwner.lifecycle.addObserver(obs)
        onDispose { lifecycleOwner.lifecycle.removeObserver(obs) }
    }

    val rows = remember(refreshKey) { buildRows(context) }

    Scaffold(
        containerColor = MidnightBackground,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Hardening Assistant",
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
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MidnightBackground)
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
            Card {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .background(BrandAccentDark.copy(alpha = 0.18f), RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Filled.Tune, contentDescription = null, tint = BrandAccentDark)
                    }
                    Spacer(Modifier.size(14.dp))
                    Column {
                        Text(
                            "Your own permission footprint",
                            color = PrimaryTextDark,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp
                        )
                        Text(
                            "What OnlyTrusted is currently allowed to touch, plus a one-tap jump into Settings for each.",
                            color = SecondaryTextDark,
                            fontSize = 12.sp
                        )
                    }
                }
            }

            Card {
                CardHeader(Icons.Filled.Security, "OnlyTrusted's permissions")
                rows.forEachIndexed { index, row ->
                    if (index > 0) Spacer(Modifier.height(12.dp))
                    PermissionRow(row, context)
                }
            }

            Card {
                CardHeader(Icons.Filled.Security, "Audit other apps")
                Text(
                    "Android lets you see which apps hold Camera and Microphone grants in the Permission Manager. Opening below takes you there — we don't claim to enumerate other apps' grants ourselves.",
                    color = SecondaryTextDark,
                    fontSize = 13.sp
                )
                Spacer(Modifier.height(12.dp))
                OutlinedButton(
                    onClick = {
                        // Prefer the dedicated Privacy screen on 12+, fall back to Application
                        // settings on older releases.
                        val intents = listOf(
                            Intent(Settings.ACTION_PRIVACY_SETTINGS),
                            Intent(Settings.ACTION_APPLICATION_SETTINGS),
                            Intent(Settings.ACTION_SETTINGS)
                        )
                        for (i in intents) {
                            val ok = runCatching {
                                context.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                            }.isSuccess
                            if (ok) return@OutlinedButton
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Filled.OpenInNew, contentDescription = null, tint = BrandAccentDark)
                    Spacer(Modifier.size(6.dp))
                    Text("Open Android Permission Manager", color = BrandAccentDark, fontWeight = FontWeight.SemiBold)
                }
            }

            Text(
                "On Android, the Permission Manager lists per-sensor grants grouped by app. Flip a toggle there and the app loses mic / camera / location until it asks again.",
                color = TertiaryTextDark,
                fontSize = 11.sp,
                modifier = Modifier.padding(horizontal = 4.dp)
            )
            Spacer(Modifier.height(12.dp))
        }
    }
}

// ---------------------------------------------------------------------------------- Rows

private data class Row(
    val title: String,
    val permission: String?,
    val icon: ImageVector,
    val blurb: String,
    val status: Status
)

private enum class Status(val label: String, val tint: Color) {
    GRANTED("Granted", SuccessDark),
    DENIED("Denied", DangerDark),
    NOT_REQUESTED("Not requested", WarningDark),
    SPLIT("Mixed", WarningDark)
}

private fun buildRows(context: Context): List<Row> {
    val cameraGranted = isGranted(context, Manifest.permission.CAMERA)
    val micGranted = isGranted(context, Manifest.permission.RECORD_AUDIO)
    // Pre-33 uses READ_EXTERNAL_STORAGE; 33+ uses READ_MEDIA_IMAGES.
    val photosPermission = if (Build.VERSION.SDK_INT >= 33)
        Manifest.permission.READ_MEDIA_IMAGES
    else
        Manifest.permission.READ_EXTERNAL_STORAGE
    val photosGranted = isGranted(context, photosPermission)
    val fineGranted = isGranted(context, Manifest.permission.ACCESS_FINE_LOCATION)
    val bgGranted =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
            isGranted(context, Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        else fineGranted
    val locationStatus = when {
        fineGranted && bgGranted -> Status.GRANTED
        fineGranted && !bgGranted -> Status.SPLIT
        else -> Status.DENIED
    }

    return listOf(
        Row(
            title = "Camera",
            permission = Manifest.permission.CAMERA,
            icon = Icons.Filled.CameraAlt,
            blurb = "FaceLock, Smart Link Guard QR scanner, and future camera features fail silently without this.",
            status = if (cameraGranted) Status.GRANTED else Status.DENIED
        ),
        Row(
            title = "Microphone",
            permission = Manifest.permission.RECORD_AUDIO,
            icon = Icons.Filled.MicNone,
            blurb = "Voice Biometric enrollment and verification need this; nothing else does.",
            status = if (micGranted) Status.GRANTED else Status.DENIED
        ),
        Row(
            title = "Photos / media",
            permission = photosPermission,
            icon = Icons.Filled.PhotoLibrary,
            blurb = "Only required if you load/save a Steganographic Vault decoy image.",
            status = if (photosGranted) Status.GRANTED else Status.DENIED
        ),
        Row(
            title = "Location",
            permission = Manifest.permission.ACCESS_FINE_LOCATION,
            icon = Icons.Filled.LocationOn,
            blurb = "Geo-fence Auto-Lock needs foreground AND background location to fire exit events while backgrounded.",
            status = locationStatus
        )
    )
}

private fun isGranted(context: Context, permission: String): Boolean =
    ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

@Composable
private fun PermissionRow(row: Row, context: Context) {
    Column {
        androidx.compose.foundation.layout.Row(verticalAlignment = Alignment.Top) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(row.status.tint.copy(alpha = 0.18f), RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(row.icon, contentDescription = null, tint = row.status.tint)
            }
            Spacer(Modifier.size(10.dp))
            Column(Modifier.weight(1f)) {
                androidx.compose.foundation.layout.Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        row.title,
                        color = PrimaryTextDark,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                        modifier = Modifier.weight(1f)
                    )
                    StatusChip(row.status.tint, row.status.label)
                }
                Spacer(Modifier.height(4.dp))
                Text(row.blurb, color = SecondaryTextDark, fontSize = 12.sp)
            }
        }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(
            onClick = { openAppDetails(context) },
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Filled.OpenInNew, contentDescription = null, tint = BrandAccentDark)
            Spacer(Modifier.size(6.dp))
            Text(
                "Open Android Settings → OnlyTrusted → Permissions",
                color = BrandAccentDark,
                fontWeight = FontWeight.SemiBold,
                fontSize = 12.sp
            )
        }
    }
}

private fun openAppDetails(context: Context) {
    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
        .setData(Uri.fromParts("package", context.packageName, null))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(intent) }
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
    androidx.compose.foundation.layout.Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = BrandAccentDark, modifier = Modifier.size(20.dp))
        Spacer(Modifier.size(8.dp))
        Text(title, color = PrimaryTextDark, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
    }
    Spacer(Modifier.height(10.dp))
}

@Composable
private fun StatusChip(color: Color, label: String) {
    androidx.compose.foundation.layout.Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .background(color.copy(alpha = 0.14f), shape = RoundedCornerShape(999.dp))
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Box(modifier = Modifier.size(8.dp).background(color, CircleShape))
        Spacer(Modifier.size(6.dp))
        Text(label, color = color, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
    }
}
