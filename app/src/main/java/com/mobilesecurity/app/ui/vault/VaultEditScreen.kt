package com.mobilesecurity.app.ui.vault

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.ui.text.input.KeyboardType
import com.mobilesecurity.app.privacy.PhoneUtils
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.mobilesecurity.app.MobileSecurityApp
import com.mobilesecurity.app.data.repository.RevealedContact
import com.mobilesecurity.app.data.repository.TrustedContactDraft
import com.mobilesecurity.app.ui.lock.BiometricPrompter

@Composable
fun VaultEditScreen(
    contactId: Long?,
    onDone: () -> Unit
) {
    val context = LocalContext.current
    val app = context.applicationContext as MobileSecurityApp
    val vm: VaultViewModel = viewModel(factory = viewModelFactory {
        initializer { VaultViewModel(app) }
    })

    var authorised by remember { mutableStateOf(contactId == null) }
    var authError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(contactId) {
        if (contactId != null) {
            val activity = context as? FragmentActivity
            if (activity != null && BiometricPrompter.canAuthenticate(activity)) {
                BiometricPrompter.prompt(
                    activity = activity,
                    title = "Unlock to edit",
                    subtitle = "Confirm it's you before editing a vault entry",
                    onSuccess = { authorised = true },
                    onFailure = { authError = it }
                )
            } else {
                authorised = true
            }
        }
    }

    if (!authorised) {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Vault is locked", style = MaterialTheme.typography.headlineMedium)
            authError?.let { Text(it, modifier = Modifier.padding(top = 8.dp)) }
            Button(onClick = onDone, modifier = Modifier.padding(top = 16.dp)) { Text("Back") }
        }
        return
    }

    EditForm(
        contactId = contactId,
        onLoad = { id -> vm.loadForEdit(id) },
        onSave = { draft -> vm.save(draft) { onDone() } },
        onDelete = { id -> vm.delete(id); onDone() },
        onCancel = onDone
    )
}

@Composable
private fun EditForm(
    contactId: Long?,
    onLoad: suspend (Long) -> RevealedContact?,
    onSave: (TrustedContactDraft) -> Unit,
    onDelete: (Long) -> Unit,
    onCancel: () -> Unit
) {
    var name by remember { mutableStateOf("") }
    var relation by remember { mutableStateOf("") }
    var isOwner by remember { mutableStateOf(false) }
    var email by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    val numbers = remember { mutableStateListOf(Pair("", "")) }

    LaunchedEffect(contactId) {
        if (contactId == null) return@LaunchedEffect
        val existing = onLoad(contactId) ?: return@LaunchedEffect
        name = existing.displayName
        relation = existing.relation.orEmpty()
        isOwner = existing.isOwner
        address = existing.addressPlain.orEmpty()
        notes = existing.notesPlain.orEmpty()
        numbers.clear()
        existing.numbers.forEach { numbers += it.normalisedNumber to (it.label ?: "") }
        if (numbers.isEmpty()) numbers += "" to ""
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            if (contactId == null) "Add trusted contact" else "Edit trusted contact",
            style = MaterialTheme.typography.headlineMedium
        )

        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Display name") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = relation,
            onValueChange = { relation = it },
            label = { Text("Relation (e.g. spouse, mother, child)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = isOwner, onCheckedChange = { isOwner = it })
            Text("This is the phone owner")
        }
        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text("Email (optional, stored masked)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Text("Phone numbers", style = MaterialTheme.typography.titleLarge)
        numbers.forEachIndexed { index, (number, label) ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = number,
                    onValueChange = { numbers[index] = PhoneUtils.sanitiseNumberInput(it) to label },
                    label = { Text("Number") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = label,
                    onValueChange = { numbers[index] = number to it },
                    label = { Text("Label") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = {
                    if (numbers.size > 1) numbers.removeAt(index) else numbers[index] = "" to ""
                }) {
                    Icon(Icons.Filled.Delete, contentDescription = "Remove number")
                }
            }
        }
        OutlinedButton(
            onClick = { numbers += "" to "" },
            modifier = Modifier.fillMaxWidth()
        ) { Text("Add another number") }

        OutlinedTextField(
            value = address,
            onValueChange = { address = it },
            label = { Text("Address (encrypted at rest)") },
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = notes,
            onValueChange = { notes = it },
            label = { Text("Notes (encrypted at rest)") },
            modifier = Modifier.fillMaxWidth()
        )

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f)) { Text("Cancel") }
            if (contactId != null) {
                OutlinedButton(
                    onClick = { onDelete(contactId) },
                    modifier = Modifier.weight(1f)
                ) { Text("Delete") }
            }
            Button(
                onClick = {
                    onSave(
                        TrustedContactDraft(
                            id = contactId ?: 0L,
                            displayName = name,
                            relation = relation,
                            isOwner = isOwner,
                            email = email,
                            address = address,
                            notes = notes,
                            phoneNumbers = numbers.map { it.first to it.second.ifBlank { null } }
                        )
                    )
                },
                enabled = name.isNotBlank(),
                modifier = Modifier.weight(1f)
            ) { Text("Save") }
        }
    }
}
