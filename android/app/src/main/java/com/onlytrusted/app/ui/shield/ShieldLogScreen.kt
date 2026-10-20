// SPDX-FileCopyrightText: 2026 57Million LLC
// SPDX-License-Identifier: AGPL-3.0-or-later
package com.onlytrusted.app.ui.shield

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.lifecycle.viewModelScope
import com.onlytrusted.app.OnlyTrustedApp
import com.onlytrusted.app.data.db.entities.ShieldSession
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import java.text.DateFormat
import java.util.Date

class ShieldLogViewModel(app: OnlyTrustedApp) : AndroidViewModel(app) {
    val sessions = app.database.shieldSessionDao().recent()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList<ShieldSession>())
}

@Composable
fun ShieldLogScreen() {
    val context = LocalContext.current
    val app = context.applicationContext as OnlyTrustedApp
    val vm: ShieldLogViewModel = viewModel(factory = viewModelFactory {
        initializer { ShieldLogViewModel(app) }
    })
    val sessions by vm.sessions.collectAsStateWithLifecycle()
    val fmt = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Shield sessions", style = MaterialTheme.typography.headlineMedium)
        Text(
            "If a screenshot or screen recording of yours surfaces, read the small diagonal " +
                "watermark on it and match the code to one of these sessions. The matching row " +
                "tells you exactly when and under what tag that capture was taken.",
            style = MaterialTheme.typography.bodyMedium
        )
        if (sessions.isEmpty()) {
            Text("No Shield sessions yet. Start one from the Dashboard.")
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(sessions, key = { it.id }) { session ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                "Code ${session.code}",
                                style = MaterialTheme.typography.titleLarge
                            )
                            Text("Tag: ${session.ownerTag}",
                                style = MaterialTheme.typography.bodyMedium)
                            Text(
                                "Started: ${fmt.format(Date(session.startedAt))}",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                "Ended: " + (session.endedAt
                                    ?.let { fmt.format(Date(it)) } ?: "still running"),
                                style = MaterialTheme.typography.bodyMedium
                            )
                            session.label?.let {
                                Text("Note: $it", style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
            }
        }
    }
}
