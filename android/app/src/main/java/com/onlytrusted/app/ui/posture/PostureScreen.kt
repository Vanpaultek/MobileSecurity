// SPDX-FileCopyrightText: 2026 57Million LLC
// SPDX-License-Identifier: AGPL-3.0-or-later
package com.onlytrusted.app.ui.posture

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.onlytrusted.app.privacy.DevicePosture

@Composable
fun PostureScreen() {
    val context = LocalContext.current
    var snapshot by remember { mutableStateOf<DevicePosture.Snapshot?>(null) }

    LaunchedEffect(Unit) { snapshot = DevicePosture.read(context) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Device posture", style = MaterialTheme.typography.headlineMedium)
        Text(
            "A read-only health check of this phone. We only observe — we never flip system " +
                "switches for you. Tap any issue to open the system page where you fix it.",
            style = MaterialTheme.typography.bodyMedium
        )

        val data = snapshot ?: return@Column

        Card(
            colors = CardDefaults.cardColors(
                containerColor = if (data.postureOk)
                    MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.errorContainer
            )
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    if (data.postureOk) Icons.Filled.CheckCircle else Icons.Filled.Warning,
                    contentDescription = null
                )
                Text(
                    if (data.postureOk) "Posture looks good." else "${data.issues.size} issue(s) to review",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
        }

        PostureRow(
            label = "USB debugging (ADB)",
            bad = data.adbEnabled,
            fixAction = "Open developer options",
            onFix = {
                val intent = Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                runCatching { context.startActivity(intent) }
            }
        )
        PostureRow(
            label = "Developer options",
            bad = data.developerOptionsOn,
            fixAction = "Open developer options",
            onFix = {
                val intent = Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                runCatching { context.startActivity(intent) }
            }
        )
        PostureRow(
            label = "Lock screen set",
            bad = !data.deviceSecure,
            goodLabel = "A PIN/biometric is set.",
            badLabel = "No PIN/biometric is set.",
            fixAction = "Open screen-lock settings",
            onFix = {
                val intent = Intent(Settings.ACTION_SECURITY_SETTINGS)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                runCatching { context.startActivity(intent) }
            }
        )
        PostureRow(
            label = "Root status",
            bad = data.likelyRooted,
            goodLabel = "No signs of root.",
            badLabel = "Root indicators present.",
            fixAction = null,
            onFix = {}
        )
        PostureRow(
            label = "Unknown-sources install permission",
            bad = data.canInstallFromUnknownSources,
            goodLabel = "No app is allowed to sideload installs.",
            badLabel = "At least one app can sideload installs.",
            fixAction = "Review install-unknown-apps",
            onFix = {
                val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES)
                    .setData(Uri.parse("package:${context.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                runCatching { context.startActivity(intent) }
            }
        )

        if (data.riskyApps.isNotEmpty()) {
            Text(
                "Apps that hold BOTH mic and camera (${data.riskyApps.size})",
                style = MaterialTheme.typography.titleLarge
            )
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(data.riskyApps, key = { it.packageName }) { app ->
                    Card {
                        Column(Modifier.padding(12.dp)) {
                            Text(app.label, style = MaterialTheme.typography.titleLarge)
                            Text(app.packageName, style = MaterialTheme.typography.bodyMedium)
                            OutlinedButton(
                                onClick = {
                                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                                        .setData(Uri.parse("package:${app.packageName}"))
                                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    runCatching { context.startActivity(intent) }
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) { Text("Open in system settings") }
                        }
                    }
                }
            }
        }

        TextButton(onClick = { snapshot = DevicePosture.read(context) }) {
            Text("Re-scan posture")
        }
    }
}

@Composable
private fun PostureRow(
    label: String,
    bad: Boolean,
    goodLabel: String = "OK",
    badLabel: String = "Issue",
    fixAction: String?,
    onFix: () -> Unit
) {
    Card {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                if (bad) Icons.Filled.Warning else Icons.Filled.CheckCircle,
                contentDescription = null,
                tint = if (bad) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
            )
            Column(Modifier.padding(start = 12.dp).weight(1f)) {
                Text(label, style = MaterialTheme.typography.titleLarge)
                Text(if (bad) badLabel else goodLabel, style = MaterialTheme.typography.bodyMedium)
            }
            if (bad && fixAction != null) {
                TextButton(onClick = onFix) { Text(fixAction) }
            }
        }
    }
}
