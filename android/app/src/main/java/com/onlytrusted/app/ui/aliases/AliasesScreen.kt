// SPDX-FileCopyrightText: 2026 VanPaulTek Inc
// SPDX-License-Identifier: AGPL-3.0-or-later
package com.onlytrusted.app.ui.aliases

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.AlternateEmail
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CallSplit
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.onlytrusted.app.ui.theme.AppBackdropScaffold
import com.onlytrusted.app.ui.theme.BrandAccentDark
import com.onlytrusted.app.ui.theme.BrandCyanDark
import com.onlytrusted.app.ui.theme.MidnightBackground
import com.onlytrusted.app.ui.theme.MidnightElevated
import com.onlytrusted.app.ui.theme.PrimaryTextDark
import com.onlytrusted.app.ui.theme.SecondaryTextDark
import com.onlytrusted.app.ui.theme.TertiaryTextDark
import kotlinx.coroutines.launch

/** Private Email Aliases (FREE, unlimited). Mirrors iOS `AliasesScreen`. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AliasesScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var aliases by remember { mutableStateOf(AliasStore.load(context)) }
    var relay by remember { mutableStateOf(AliasStore.relay(context)) }
    var customDomain by remember { mutableStateOf(AliasStore.customDomain(context)) }
    var relayPickerOpen by remember { mutableStateOf(false) }
    var editingLabelFor by remember { mutableStateOf<String?>(null) }
    var labelDraft by remember { mutableStateOf("") }
    var showingBulkDialog by remember { mutableStateOf<BulkAction?>(null) }
    val isPremium = com.onlytrusted.app.ui.subscription.SubscriptionState.hasPremium(context)

    fun reload() { aliases = AliasStore.load(context) }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    out.write(AliasStore.exportCsv(context).toByteArray(Charsets.UTF_8))
                }
                scope.launch { snackbar.showSnackbar("Exported alias book") }
            }.onFailure {
                scope.launch { snackbar.showSnackbar("Export failed") }
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            runCatching {
                val text = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    ?.toString(Charsets.UTF_8).orEmpty()
                val added = AliasStore.importCsv(context, text)
                reload()
                scope.launch { snackbar.showSnackbar("Imported $added alias${if (added == 1) "" else "es"}") }
            }.onFailure {
                scope.launch { snackbar.showSnackbar("Import failed") }
            }
        }
    }

    AppBackdropScaffold(
        topBar = {
            TopAppBar(
                title = { Text("Private Aliases", color = PrimaryTextDark) },
                navigationIcon = {
                    TextButton(onClick = onBack) { Text("Back", color = BrandCyanDark) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Hero
            CardBox {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    HeaderRow(Icons.Filled.AlternateEmail, "Private Aliases", BrandAccentDark)
                    Text(
                        "Stop giving out your real email. Generate a disposable alias per site, " +
                            "keep track of them here, and toggle them off when a sender gets noisy.",
                        fontSize = 12.sp, color = SecondaryTextDark
                    )
                    Text(
                        "We don't operate a mail server. Your chosen relay provider forwards. " +
                            "Toggles live on this device.",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = BrandAccentDark
                    )
                }
            }

            // Relay picker
            CardBox {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    HeaderRow(Icons.Filled.CallSplit, "Your relay", BrandAccentDark)
                    Box {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .background(MidnightBackground, RoundedCornerShape(10.dp))
                                .clickable { relayPickerOpen = true }
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    relay.displayName,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = PrimaryTextDark
                                )
                                Text(
                                    relay.subtitle,
                                    fontSize = 11.sp, color = SecondaryTextDark
                                )
                            }
                            Icon(Icons.Filled.ExpandMore, null, tint = BrandCyanDark)
                        }
                        DropdownMenu(
                            expanded = relayPickerOpen,
                            onDismissRequest = { relayPickerOpen = false }
                        ) {
                            RelayChoice.values().forEach { choice ->
                                DropdownMenuItem(
                                    text = { Text(choice.displayName) },
                                    onClick = {
                                        relay = choice
                                        AliasStore.setRelay(context, choice)
                                        relayPickerOpen = false
                                    }
                                )
                            }
                        }
                    }

                    if (relay == RelayChoice.CUSTOM) {
                        OutlinedTextField(
                            value = customDomain,
                            onValueChange = {
                                customDomain = it
                                AliasStore.setCustomDomain(context, it)
                            },
                            label = { Text("yourdomain.example") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    relay.portalUrl?.let { url ->
                        TextButton(onClick = { openUrl(context, url) }) {
                            Icon(Icons.Filled.OpenInNew, null, tint = BrandCyanDark)
                            Spacer(Modifier.size(6.dp))
                            Text("Open ${relay.displayName}", color = BrandCyanDark)
                        }
                    }
                }
            }

            // Generate button
            CardBox {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    HeaderRow(Icons.Filled.Add, "Generate alias", BrandAccentDark)
                    Text(
                        "Tap to make a fresh random prefix. Copy it, then add it to your relay " +
                            "provider's dashboard so forwarding starts.",
                        fontSize = 11.sp, color = SecondaryTextDark
                    )
                    Button(
                        onClick = {
                            val a = AliasStore.generate(context)
                            copyToClipboard(context, AliasStore.address(context, a))
                            reload()
                            scope.launch { snackbar.showSnackbar("Alias copied to clipboard") }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = BrandAccentDark)
                    ) {
                        Icon(Icons.Filled.Add, null, tint = Color.White)
                        Spacer(Modifier.size(8.dp))
                        Text(
                            "Generate & copy new alias",
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            // Premium tools (CSV + bulk)
            if (isPremium) {
                CardBox {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        HeaderRow(Icons.Filled.ContentCopy, "Premium tools", BrandAccentDark)
                        Text(
                            "Portable alias book. Export your list to move to a new device, or " +
                                "batch-tidy a long list.",
                            fontSize = 11.sp, color = SecondaryTextDark
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Button(
                                onClick = {
                                    val stamp = System.currentTimeMillis() / 1000
                                    exportLauncher.launch("onlytrusted_aliases_$stamp.csv")
                                },
                                enabled = aliases.isNotEmpty(),
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(containerColor = BrandAccentDark)
                            ) { Text("Export CSV", color = Color.White, fontSize = 12.sp) }
                            Button(
                                onClick = { importLauncher.launch(arrayOf("text/csv", "text/plain", "text/comma-separated-values")) },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(containerColor = MidnightBackground)
                            ) { Text("Import CSV", color = BrandAccentDark, fontSize = 12.sp) }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            TextButton(
                                onClick = { showingBulkDialog = BulkAction.DISABLE_ALL },
                                enabled = aliases.isNotEmpty(),
                                modifier = Modifier.weight(1f)
                            ) { Text("Disable all", color = SecondaryTextDark, fontSize = 11.sp) }
                            TextButton(
                                onClick = { showingBulkDialog = BulkAction.DELETE_DISABLED },
                                enabled = aliases.any { !it.enabled },
                                modifier = Modifier.weight(1f)
                            ) { Text("Delete disabled", color = BrandAccentDark, fontSize = 11.sp) }
                        }
                    }
                }
            }

            // List or empty state
            if (aliases.isEmpty()) {
                CardBox {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        HeaderRow(Icons.Filled.Inbox, "No aliases yet", SecondaryTextDark)
                        Text(
                            "Tap \"Generate & copy new alias\" above to create your first. We " +
                                "keep the list on this device and never sync it anywhere.",
                            fontSize = 11.sp, color = SecondaryTextDark
                        )
                    }
                }
            } else {
                CardBox {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        HeaderRow(
                            Icons.Filled.AlternateEmail,
                            "Your aliases (${aliases.size})",
                            BrandAccentDark
                        )
                        aliases.forEach { alias ->
                            AliasRow(
                                alias = alias,
                                fullAddress = AliasStore.address(context, alias),
                                editingLabel = editingLabelFor == alias.id,
                                labelDraft = labelDraft,
                                onLabelDraftChange = { labelDraft = it },
                                onStartEdit = {
                                    editingLabelFor = alias.id
                                    labelDraft = alias.label
                                },
                                onSaveLabel = {
                                    AliasStore.rename(context, alias.id, labelDraft)
                                    editingLabelFor = null
                                    reload()
                                },
                                onToggle = {
                                    AliasStore.toggle(context, alias.id)
                                    reload()
                                },
                                onCopy = {
                                    copyToClipboard(context, AliasStore.address(context, alias))
                                    scope.launch { snackbar.showSnackbar("Copied") }
                                },
                                onDelete = {
                                    AliasStore.delete(context, alias.id)
                                    reload()
                                }
                            )
                        }
                    }
                }
            }

            // Bulk confirmation dialog
            showingBulkDialog?.let { bulk ->
                AlertDialog(
                    onDismissRequest = { showingBulkDialog = null },
                    title = { Text(bulk.title, color = PrimaryTextDark) },
                    text = { Text(bulk.detail, color = SecondaryTextDark) },
                    confirmButton = {
                        TextButton(onClick = {
                            when (bulk) {
                                BulkAction.DISABLE_ALL -> AliasStore.disableAll(context)
                                BulkAction.DELETE_DISABLED -> AliasStore.deleteAllDisabled(context)
                            }
                            reload()
                            showingBulkDialog = null
                            scope.launch { snackbar.showSnackbar("Done") }
                        }) { Text("Do it", color = BrandAccentDark) }
                    },
                    dismissButton = {
                        TextButton(onClick = { showingBulkDialog = null }) {
                            Text("Cancel", color = TertiaryTextDark)
                        }
                    },
                    containerColor = MidnightElevated
                )
            }

            // How it works
            CardBox {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    HeaderRow(Icons.Filled.Info, "How this works", SecondaryTextDark)
                    Step(1, "Pick the relay you already use (or will sign up for).")
                    Step(2, "Tap Generate — we make a random disposable prefix.")
                    Step(3, "Add that alias in the relay's own dashboard so forwarding starts.")
                    Step(4, "Toggle an alias off here to remember you've stopped using it — then disable it in the relay's dashboard too.")
                    Text(
                        "The on/off toggle is a bookmark. It does not reach into your relay " +
                            "provider; you still need to disable the alias there if you want " +
                            "forwarding to stop.",
                        fontSize = 11.sp, color = SecondaryTextDark
                    )
                }
            }
        }
    }
}

@Composable
private fun AliasRow(
    alias: AliasStore.EmailAlias,
    fullAddress: String,
    editingLabel: Boolean,
    labelDraft: String,
    onLabelDraftChange: (String) -> Unit,
    onStartEdit: () -> Unit,
    onSaveLabel: () -> Unit,
    onToggle: () -> Unit,
    onCopy: () -> Unit,
    onDelete: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text(
                    fullAddress,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = PrimaryTextDark
                )
                if (editingLabel) {
                    OutlinedTextField(
                        value = labelDraft,
                        onValueChange = onLabelDraftChange,
                        placeholder = { Text("Label (e.g. Amazon, Netflix…)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                    )
                } else if (alias.label.isNotEmpty()) {
                    Text(alias.label, fontSize = 11.sp, color = SecondaryTextDark)
                } else {
                    TextButton(onClick = onStartEdit, contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
                        Text("Add label", fontSize = 11.sp, color = BrandCyanDark)
                    }
                }
            }
            Switch(
                checked = alias.enabled,
                onCheckedChange = { onToggle() },
                colors = SwitchDefaults.colors(checkedThumbColor = BrandAccentDark)
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            TextButton(onClick = onCopy) {
                Icon(Icons.Filled.ContentCopy, null, tint = BrandCyanDark)
                Spacer(Modifier.size(4.dp))
                Text("Copy", color = BrandCyanDark, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
            TextButton(onClick = onDelete) {
                Icon(Icons.Filled.Delete, null, tint = SecondaryTextDark)
                Spacer(Modifier.size(4.dp))
                Text("Delete", color = SecondaryTextDark, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.weight(1f))
            if (editingLabel) {
                TextButton(onClick = onSaveLabel) {
                    Text("Save", color = BrandAccentDark, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                }
            } else if (alias.label.isNotEmpty()) {
                TextButton(onClick = onStartEdit) {
                    Icon(Icons.Filled.Edit, null, tint = BrandCyanDark)
                    Spacer(Modifier.size(4.dp))
                    Text("Rename", color = BrandCyanDark, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
        Spacer(Modifier.height(4.dp))
    }
}

@Composable
private fun Step(num: Int, text: String) {
    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(
            modifier = Modifier
                .size(20.dp)
                .background(BrandAccentDark, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text("$num", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MidnightBackground)
        }
        Text(text, fontSize = 11.sp, color = SecondaryTextDark)
    }
}

@Composable
private fun HeaderRow(icon: ImageVector, title: String, tint: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Surface(shape = RoundedCornerShape(10.dp), color = tint) {
            Icon(icon, null, tint = Color.White, modifier = Modifier.padding(8.dp))
        }
        Text(title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = PrimaryTextDark)
    }
}

@Composable
private fun CardBox(content: @Composable () -> Unit) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MidnightElevated,
        modifier = Modifier.fillMaxWidth()
    ) { content() }
}

private fun copyToClipboard(ctx: Context, text: String) {
    val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText("alias", text))
}

private enum class BulkAction(val title: String, val detail: String) {
    DISABLE_ALL(
        "Disable every alias?",
        "Flips the on/off bookmark for every alias to OFF. Your relay provider still " +
            "forwards — remember to disable there too if you want forwarding to stop."
    ),
    DELETE_DISABLED(
        "Delete every disabled alias?",
        "Removes every alias currently marked disabled. Cannot be undone unless you've " +
            "exported a CSV."
    )
}

private fun openUrl(ctx: Context, url: String) {
    val i = Intent(Intent.ACTION_VIEW, Uri.parse(url))
    i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try { ctx.startActivity(i) } catch (_: Exception) {}
}
