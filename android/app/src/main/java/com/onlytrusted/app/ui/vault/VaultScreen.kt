// SPDX-FileCopyrightText: 2026 57Million LLC
// SPDX-License-Identifier: AGPL-3.0-or-later
package com.onlytrusted.app.ui.vault

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.onlytrusted.app.OnlyTrustedApp
import com.onlytrusted.app.data.repository.MaskedContact
import com.onlytrusted.app.ui.lock.BiometricPrompter

@Composable
fun VaultScreen(
    onAdd: () -> Unit,
    onEdit: (Long) -> Unit
) {
    val context = LocalContext.current
    val app = context.applicationContext as OnlyTrustedApp
    val vm: VaultViewModel = viewModel(factory = viewModelFactory {
        initializer { VaultViewModel(app) }
    })
    val contacts by vm.contacts.collectAsStateWithLifecycle()
    val revealed by vm.revealed.collectAsStateWithLifecycle()

    Scaffold(
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAdd,
                text = { Text("Add trusted contact") },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Family vault", style = MaterialTheme.typography.headlineMedium)
            Text(
                "Owner and family only. Numbers and addresses are masked by default and shown " +
                    "only after biometric unlock. Encrypted with Android Keystore on top of the " +
                    "database's SQLCipher encryption.",
                style = MaterialTheme.typography.bodyMedium
            )

            if (contacts.isEmpty()) {
                Card {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("No trusted contacts yet.", style = MaterialTheme.typography.titleLarge)
                        Text(
                            "Start by adding yourself as the owner, then add immediate family. " +
                                "Everything you type on the next screen is encrypted immediately."
                        )
                    }
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(contacts, key = { it.id }) { contact ->
                        ContactRow(
                            contact = contact,
                            onReveal = {
                                val activity = context as? FragmentActivity ?: return@ContactRow
                                if (!BiometricPrompter.canAuthenticate(activity)) {
                                    vm.reveal(contact.id)
                                    return@ContactRow
                                }
                                BiometricPrompter.prompt(
                                    activity = activity,
                                    title = "Unlock vault",
                                    subtitle = "Reveal ${contact.displayName}'s details",
                                    onSuccess = { vm.reveal(contact.id) },
                                    onFailure = { }
                                )
                            },
                            onEdit = { onEdit(contact.id) }
                        )
                    }
                }
            }
        }
    }

    revealed?.let { data ->
        RevealedDialog(data = data, onClose = { vm.hideRevealed() })
    }
}

@Composable
private fun ContactRow(
    contact: MaskedContact,
    onReveal: () -> Unit,
    onEdit: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (contact.isOwner) Icons.Filled.Shield else Icons.Filled.Lock,
                    contentDescription = null
                )
                Text(
                    contact.displayName,
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(start = 8.dp).weight(1f)
                )
                if (contact.isOwner) AssistChip(onClick = {}, label = { Text("Owner") })
            }
            contact.relation?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
            contact.maskedNumbers.forEach {
                Text(it, style = MaterialTheme.typography.bodyMedium)
            }
            contact.maskedEmail?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
            Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                TextButton(onClick = onEdit) { Text("Edit") }
                IconButton(onClick = onReveal) {
                    Icon(Icons.Filled.Visibility, contentDescription = "Reveal")
                }
            }
        }
    }
}

@Composable
private fun RevealedDialog(data: com.onlytrusted.app.data.repository.RevealedContact, onClose: () -> Unit) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onClose,
        confirmButton = { TextButton(onClick = onClose) { Text("Hide") } },
        title = { Text(data.displayName) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                data.relation?.let { Text("Relation: $it") }
                data.numbers.forEach {
                    Text("${it.label ?: "Phone"}: ${it.normalisedNumber}")
                }
                data.addressPlain?.let { Text("Address: $it") }
                data.notesPlain?.let { Text("Notes: $it") }
            }
        }
    )
}
