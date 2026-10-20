package com.mobilesecurity.app.ui.events

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.mobilesecurity.app.data.db.entities.SecurityEvent
import java.text.DateFormat
import java.util.Date

@Composable
fun EventsScreen() {
    val context = LocalContext.current
    val app = context.applicationContext as MobileSecurityApp
    val vm: EventsViewModel = viewModel(factory = viewModelFactory {
        initializer { EventsViewModel(app) }
    })
    val events by vm.events.collectAsStateWithLifecycle()
    val formatter = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Event log", style = MaterialTheme.typography.headlineMedium)
            TextButton(onClick = { vm.clear() }) { Text("Clear") }
        }
        if (events.isEmpty()) {
            Text(
                "No events yet. Alerts for microphone, camera, blocked calls and spam SMS appear here.",
                modifier = Modifier.padding(top = 24.dp)
            )
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(top = 12.dp)
            ) {
                items(events, key = { it.id }) { event ->
                    EventRow(event, formatter)
                }
            }
        }
    }
}

@Composable
private fun EventRow(event: SecurityEvent, formatter: DateFormat) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(label(event.type), style = MaterialTheme.typography.titleLarge)
            Text(formatter.format(Date(event.timestamp)),
                style = MaterialTheme.typography.bodyMedium)
            event.packageName?.let { Text("App: $it", style = MaterialTheme.typography.bodyMedium) }
            event.detail?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
        }
    }
}

private fun label(type: String): String = when (type) {
    SecurityEvent.TYPE_MIC -> "Microphone in use"
    SecurityEvent.TYPE_CAMERA -> "Camera in use"
    SecurityEvent.TYPE_CALL_BLOCKED -> "Call blocked"
    SecurityEvent.TYPE_CALL_ALLOWED -> "Call allowed"
    SecurityEvent.TYPE_SMS_FLAGGED -> "Spam SMS flagged"
    SecurityEvent.TYPE_POCKET_LOCK -> "Pocket detected"
    SecurityEvent.TYPE_SCREEN_LOCKED -> "Screen locked"
    SecurityEvent.TYPE_SCREEN_UNLOCKED -> "Screen unlocked"
    else -> type
}
