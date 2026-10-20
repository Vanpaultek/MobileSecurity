// SPDX-FileCopyrightText: 2026 57Million LLC
// SPDX-License-Identifier: AGPL-3.0-or-later
package com.onlytrusted.app.ui.blocklist

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.onlytrusted.app.OnlyTrustedApp

@Composable
fun BlockListScreen() {
    val context = LocalContext.current
    val app = context.applicationContext as OnlyTrustedApp
    val vm: BlockListViewModel = viewModel(factory = viewModelFactory {
        initializer { BlockListViewModel(app) }
    })
    val numbers by vm.numbers.collectAsStateWithLifecycle()

    var number by remember { mutableStateOf("") }
    var label by remember { mutableStateOf("") }
    var blockCalls by remember { mutableStateOf(true) }
    var blockSms by remember { mutableStateOf(true) }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Blocked numbers", style = MaterialTheme.typography.headlineMedium)
        OutlinedTextField(
            value = number,
            onValueChange = { number = com.onlytrusted.app.privacy.PhoneUtils.sanitiseNumberInput(it) },
            label = { Text("Number") },
            singleLine = true,
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                keyboardType = androidx.compose.ui.text.input.KeyboardType.Phone
            ),
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = label,
            onValueChange = { label = it },
            label = { Text("Label (optional)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = blockCalls, onCheckedChange = { blockCalls = it })
            Text("Block calls")
            Checkbox(
                checked = blockSms,
                onCheckedChange = { blockSms = it },
                modifier = Modifier.padding(start = 16.dp)
            )
            Text("Block SMS")
        }
        Button(
            onClick = {
                vm.add(number, label, blockCalls, blockSms)
                number = ""
                label = ""
            },
            enabled = number.isNotBlank(),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Add to block list")
        }

        if (numbers.isEmpty()) {
            Text("No blocked numbers yet.")
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(numbers, key = { it.number }) { entry ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(entry.number, style = MaterialTheme.typography.titleLarge)
                                entry.label?.let {
                                    Text(it, style = MaterialTheme.typography.bodyMedium)
                                }
                                Text(
                                    buildString {
                                        if (entry.blockCalls) append("Calls ")
                                        if (entry.blockSms) append("SMS")
                                    }.trim(),
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                            IconButton(onClick = { vm.remove(entry.number) }) {
                                Icon(Icons.Filled.Delete, contentDescription = "Remove")
                            }
                        }
                    }
                }
            }
        }
    }
}
