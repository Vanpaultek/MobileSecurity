// SPDX-FileCopyrightText: 2026 57Million LLC
// SPDX-License-Identifier: AGPL-3.0-or-later
package com.mobilesecurity.app.ui.permissions

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
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.mobilesecurity.app.MobileSecurityApp

@Composable
fun PermissionAuditScreen() {
    val context = LocalContext.current
    val app = context.applicationContext as MobileSecurityApp
    val vm: PermissionAuditViewModel = viewModel(factory = viewModelFactory {
        initializer { PermissionAuditViewModel(app) }
    })
    val state by vm.state.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Permission audit", style = MaterialTheme.typography.headlineMedium)
        Text(
            "Apps on this phone that already hold mic, camera, location, SMS, phone or contacts " +
                "permissions. Tap any app to open its system settings and revoke what you don't need.",
            style = MaterialTheme.typography.bodyMedium
        )

        FilterChip(
            selected = state.onlyWithMicOrCam,
            onClick = { vm.setFilter(!state.onlyWithMicOrCam) },
            label = {
                Text(if (state.onlyWithMicOrCam) "Showing mic/camera holders" else "Showing all sensitive")
            }
        )

        if (state.loading) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
                horizontalArrangement = Arrangement.Center
            ) { CircularProgressIndicator() }
        } else {
            val filtered = if (state.onlyWithMicOrCam) {
                state.rows.filter { it.mic || it.camera }
            } else state.rows

            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(filtered, key = { it.packageName }) { row ->
                    AppRow(row) { pkg ->
                        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                            .setData(Uri.parse("package:$pkg"))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        runCatching { context.startActivity(intent) }
                    }
                }
            }
        }
    }
}

@Composable
private fun AppRow(row: AppPermissionRow, onOpen: (String) -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(row.label, style = MaterialTheme.typography.titleLarge)
            Text(row.packageName, style = MaterialTheme.typography.bodyMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (row.mic) PermChip("Mic")
                if (row.camera) PermChip("Camera")
                if (row.fineLocation) PermChip("Location")
                if (row.sms) PermChip("SMS")
                if (row.phone) PermChip("Phone")
                if (row.contacts) PermChip("Contacts")
            }
            OutlinedButton(
                onClick = { onOpen(row.packageName) },
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
            ) { Text("Open in system settings") }
        }
    }
}

@Composable
private fun PermChip(label: String) {
    AssistChip(onClick = {}, label = { Text(label) })
}
