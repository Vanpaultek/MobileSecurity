// SPDX-FileCopyrightText: 2026 VanPaulTek Inc
// SPDX-License-Identifier: AGPL-3.0-or-later
package com.onlytrusted.app.ui.stego

import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.KeyboardOptions
import com.onlytrusted.app.backup.BackupManager
import com.onlytrusted.app.backup.BackupPayload
import com.onlytrusted.app.ui.subscription.subGold
import com.onlytrusted.app.ui.theme.BrandAccentDark
import com.onlytrusted.app.ui.theme.BrandCyanDark
import com.onlytrusted.app.ui.theme.DangerDark
import com.onlytrusted.app.ui.theme.MidnightBackground
import com.onlytrusted.app.ui.theme.MidnightCard
import com.onlytrusted.app.ui.theme.MidnightElevated
import com.onlytrusted.app.ui.theme.PrimaryTextDark
import com.onlytrusted.app.ui.theme.SecondaryTextDark
import kotlinx.coroutines.launch

/**
 * Steganographic Vault (Elite). Mirrors the iOS `StegoVaultScreen` — pick carrier (PNG/PDF),
 * pick file or bundled carrier, enter passphrase, write out via SAF `CreateDocument`. Or in
 * load mode, pick a stego file, enter passphrase, decrypt + overwrite.
 *
 * Caller gates on `SubscriptionState.hasElite(context)`.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StegoVaultScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

    var mode by remember { mutableStateOf(StegoMode.CREATE) }

    // --- create flow state
    var format by remember { mutableStateOf(StegoCrypto.CarrierFormat.PNG) }
    var carrierBytes by remember { mutableStateOf<ByteArray?>(null) }
    var carrierSource by remember { mutableStateOf<String?>(null) }
    var createPass by remember { mutableStateOf("") }
    var createPassAgain by remember { mutableStateOf("") }
    var createError by remember { mutableStateOf<String?>(null) }
    var pendingCreate by remember { mutableStateOf<ByteArray?>(null) }
    var pendingFilename by remember { mutableStateOf("memento.png") }
    var showBundledSheet by remember { mutableStateOf(false) }

    // --- load flow state
    var loadUri by remember { mutableStateOf<android.net.Uri?>(null) }
    var loadFilename by remember { mutableStateOf<String?>(null) }
    var loadPass by remember { mutableStateOf("") }
    var loadError by remember { mutableStateOf<String?>(null) }
    var decoded by remember { mutableStateOf<BackupPayload?>(null) }
    var confirmApply by remember { mutableStateOf(false) }

    val pickCarrierLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            if (bytes != null) {
                carrierBytes = bytes
                carrierSource = uri.lastPathSegment ?: "picked file"
            } else {
                createError = "Could not read carrier file."
            }
        }
    }

    val saveStegoLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument(
            if (format == StegoCrypto.CarrierFormat.PNG) "image/png" else "application/pdf"
        )
    ) { uri ->
        val bytes = pendingCreate ?: return@rememberLauncherForActivityResult
        pendingCreate = null
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            try {
                StegoVaultManager.writeTo(context, uri, bytes)
                snackbar.showSnackbar("Stego file saved · ${bytes.size} bytes.")
                createPass = ""
                createPassAgain = ""
            } catch (t: Throwable) {
                createError = t.message ?: "Could not write stego file."
            }
        }
    }

    val pickLoadLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            loadUri = uri
            loadFilename = uri.lastPathSegment
            decoded = null
            loadError = null
        }
    }

    Scaffold(
        containerColor = MidnightBackground,
        topBar = {
            TopAppBar(
                title = { Text("Steganographic Vault", color = PrimaryTextDark) },
                navigationIcon = {
                    TextButton(onClick = onBack) { Text("Back", color = BrandCyanDark) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MidnightBackground)
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
            HeaderCard()

            // Mode picker
            Card {
                Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ModeChip("Create", selected = mode == StegoMode.CREATE) { mode = StegoMode.CREATE }
                    ModeChip("Load", selected = mode == StegoMode.LOAD) { mode = StegoMode.LOAD }
                }
            }

            if (mode == StegoMode.CREATE) {
                Card {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Carrier format", fontSize = 14.sp,
                             fontWeight = FontWeight.SemiBold, color = PrimaryTextDark)
                        Row {
                            FormatRadio("PNG image", format == StegoCrypto.CarrierFormat.PNG) {
                                format = StegoCrypto.CarrierFormat.PNG
                                carrierBytes = null; carrierSource = null
                            }
                            FormatRadio("PDF document", format == StegoCrypto.CarrierFormat.PDF) {
                                format = StegoCrypto.CarrierFormat.PDF
                                carrierBytes = null; carrierSource = null
                            }
                        }
                    }
                }

                Card {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Carrier file", fontSize = 14.sp,
                             fontWeight = FontWeight.SemiBold, color = PrimaryTextDark)
                        Text(
                            carrierSource ?: "Pick an existing PNG/PDF or use one of the bundled royalty-free samples.",
                            fontSize = 12.sp,
                            color = SecondaryTextDark
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedButton(
                                onClick = {
                                    val mime = if (format == StegoCrypto.CarrierFormat.PNG) "image/png"
                                               else "application/pdf"
                                    pickCarrierLauncher.launch(mime)
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Filled.FolderOpen, null, modifier = Modifier.padding(end = 6.dp))
                                Text("Pick file")
                            }
                            if (format == StegoCrypto.CarrierFormat.PNG) {
                                OutlinedButton(
                                    onClick = { showBundledSheet = true },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Filled.PhotoLibrary, null, modifier = Modifier.padding(end = 6.dp))
                                    Text("Bundled image")
                                }
                            }
                        }
                    }
                }

                Card {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Passphrase", fontSize = 14.sp,
                             fontWeight = FontWeight.SemiBold, color = PrimaryTextDark)
                        Text(
                            "At least 12 characters with a letter, a digit, and a symbol. If you " +
                                "forget it, the hidden vault is gone forever.",
                            fontSize = 12.sp, color = SecondaryTextDark
                        )
                        OutlinedTextField(
                            value = createPass,
                            onValueChange = { createPass = it; createError = null },
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            label = { Text("Passphrase") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = createPassAgain,
                            onValueChange = { createPassAgain = it; createError = null },
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            label = { Text("Confirm") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        createError?.let {
                            Text(it, color = DangerDark, fontSize = 12.sp)
                        }
                    }
                }

                Button(
                    onClick = {
                        createError = null
                        val carrier = carrierBytes
                        when {
                            carrier == null -> createError = "Pick a carrier file first."
                            createPass != createPassAgain -> createError = "Passphrases don't match."
                            createPass.isBlank() -> createError = "Enter a passphrase."
                            StegoCrypto.validatePassphrase(createPass) != null ->
                                createError = StegoCrypto.validatePassphrase(createPass)
                            else -> {
                                scope.launch {
                                    try {
                                        val result = StegoVaultManager.createStego(
                                            context, format, carrier, createPass
                                        )
                                        pendingCreate = result.bytes
                                        pendingFilename = result.suggestedFilename
                                        saveStegoLauncher.launch(result.suggestedFilename)
                                    } catch (e: StegoCrypto.CarrierTooSmallException) {
                                        createError = "Carrier is too small — need ${e.needBytes} byte capacity, have ${e.haveBytes}. Pick a larger image."
                                    } catch (e: StegoVaultManager.PassphraseWeakException) {
                                        createError = e.reason
                                    } catch (t: Throwable) {
                                        createError = t.message ?: "Could not create stego file."
                                    }
                                }
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = subGold, contentColor = Color.Black),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Filled.Save, null, modifier = Modifier.padding(end = 8.dp))
                    Text("Create stego file", fontWeight = FontWeight.SemiBold)
                }
                Spacer(Modifier.height(8.dp))
            } else {
                // Load mode
                if (loadUri == null) {
                    Button(
                        onClick = { pickLoadLauncher.launch("*/*") },
                        colors = ButtonDefaults.buttonColors(containerColor = subGold, contentColor = Color.Black),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Filled.Search, null, modifier = Modifier.padding(end = 8.dp))
                        Text("Pick stego file (.png or .pdf)", fontWeight = FontWeight.SemiBold)
                    }
                } else {
                    Card {
                        Column(Modifier.padding(16.dp)) {
                            Text(loadFilename ?: "file", fontSize = 13.sp,
                                 color = PrimaryTextDark, fontWeight = FontWeight.SemiBold)
                            TextButton(onClick = {
                                loadUri = null; loadFilename = null
                                decoded = null; loadPass = ""; loadError = null
                            }) { Text("Pick a different file", color = BrandCyanDark) }
                        }
                    }
                    Card {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("Passphrase", fontSize = 14.sp,
                                 fontWeight = FontWeight.SemiBold, color = PrimaryTextDark)
                            OutlinedTextField(
                                value = loadPass,
                                onValueChange = { loadPass = it; loadError = null },
                                singleLine = true,
                                visualTransformation = PasswordVisualTransformation(),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                                label = { Text("Passphrase") },
                                modifier = Modifier.fillMaxWidth()
                            )
                            loadError?.let { Text(it, color = DangerDark, fontSize = 12.sp) }
                        }
                    }
                    Button(
                        onClick = {
                            loadError = null
                            if (loadPass.isBlank()) {
                                loadError = "Enter the passphrase you used when you created this vault."
                                return@Button
                            }
                            scope.launch {
                                try {
                                    decoded = StegoVaultManager.openStego(context, loadUri!!, loadPass)
                                } catch (t: Throwable) {
                                    loadError = t.message ?: "No vault found in this file."
                                    decoded = null
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = BrandAccentDark, contentColor = Color.White),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Decrypt and preview", fontWeight = FontWeight.SemiBold)
                    }

                    decoded?.let { payload ->
                        Card {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text("Stego vault contents", fontSize = 14.sp,
                                     fontWeight = FontWeight.SemiBold, color = PrimaryTextDark)
                                PreviewRow("Platform", payload.platform)
                                PreviewRow("Contacts", "${payload.contacts.size}")
                                PreviewRow("Blocked", "${payload.blockedNumbers.size}")
                                PreviewRow("Events", "${payload.events.size}")
                            }
                        }
                        Button(
                            onClick = { confirmApply = true },
                            colors = ButtonDefaults.buttonColors(containerColor = DangerDark, contentColor = Color.White),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Restore and overwrite existing data", fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }

        if (showBundledSheet) {
            BundledCarrierDialog(
                onPick = { name, bytes ->
                    carrierBytes = bytes
                    carrierSource = "Bundled · $name"
                    showBundledSheet = false
                },
                onDismiss = { showBundledSheet = false }
            )
        }

        if (confirmApply) {
            AlertDialog(
                onDismissRequest = { confirmApply = false },
                title = { Text("Overwrite everything?", color = PrimaryTextDark) },
                text = {
                    Text(
                        "All current trusted contacts, blocked numbers, events, and settings will be " +
                            "replaced by the contents of this stego vault.",
                        color = SecondaryTextDark
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        confirmApply = false
                        val p = decoded ?: return@TextButton
                        scope.launch {
                            try {
                                BackupManager.applyRestore(context, p)
                                snackbar.showSnackbar("Vault restored.")
                                decoded = null; loadPass = ""; loadUri = null
                            } catch (t: Throwable) {
                                loadError = t.message ?: "Restore failed."
                            }
                        }
                    }) { Text("Overwrite", color = DangerDark) }
                },
                dismissButton = {
                    TextButton(onClick = { confirmApply = false }) { Text("Cancel") }
                },
                containerColor = MidnightCard
            )
        }
    }
}

private enum class StegoMode { CREATE, LOAD }

@Composable
private fun HeaderCard() {
    Card {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Surface(shape = RoundedCornerShape(12.dp), color = subGold) {
                Icon(Icons.Filled.PhotoLibrary, null, tint = Color.White,
                     modifier = Modifier.padding(10.dp))
            }
            Column {
                Text("Elite feature", fontSize = 11.sp, color = subGold, fontWeight = FontWeight.SemiBold)
                Text("Hide the vault in a decoy file.", fontSize = 15.sp,
                     fontWeight = FontWeight.SemiBold, color = PrimaryTextDark)
                Text(
                    "Choose a PNG family photo or a PDF receipt. OnlyTrusted encrypts your vault " +
                        "and tucks the bytes inside the carrier. The file still opens as a normal " +
                        "image or document — only your passphrase reveals what's inside.",
                    fontSize = 12.sp, color = SecondaryTextDark
                )
            }
        }
    }
}

@Composable
private fun ModeChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(10.dp),
        color = if (selected) BrandAccentDark else MidnightCard,
        modifier = Modifier.height(36.dp)
    ) {
        Text(
            label,
            color = if (selected) Color.White else PrimaryTextDark,
            fontWeight = FontWeight.SemiBold,
            fontSize = 13.sp,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
        )
    }
}

@Composable
private fun FormatRadio(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(end = 16.dp)
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Text(label, fontSize = 13.sp, color = PrimaryTextDark)
    }
}

@Composable
private fun PreviewRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(label, fontSize = 12.sp, color = SecondaryTextDark, modifier = Modifier.weight(1f))
        Text(value, fontSize = 13.sp, color = PrimaryTextDark, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun Card(content: @Composable () -> Unit) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MidnightElevated,
        modifier = Modifier.fillMaxWidth()
    ) { content() }
}

@Composable
private fun BundledCarrierDialog(
    onPick: (String, ByteArray) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Bundled carriers", color = PrimaryTextDark) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "These royalty-free gradients ship with OnlyTrusted. Each is 1024×768 — plenty of room for a full vault snapshot.",
                    fontSize = 12.sp,
                    color = SecondaryTextDark
                )
                StegoVaultManager.bundledCarriers.forEach { (label, resId) ->
                    Surface(
                        onClick = {
                            val bytes = context.resources.openRawResource(resId).use { it.readBytes() }
                            onPick(label, bytes)
                        },
                        shape = RoundedCornerShape(10.dp),
                        color = MidnightCard,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            val bmp = remember(resId) {
                                context.resources.openRawResource(resId).use {
                                    BitmapFactory.decodeStream(it)
                                }
                            }
                            if (bmp != null) {
                                Image(
                                    bitmap = bmp.asImageBitmap(),
                                    contentDescription = null,
                                    modifier = Modifier.size(56.dp)
                                )
                            } else {
                                Box(modifier = Modifier.size(56.dp))
                            }
                            Column {
                                Text(label, color = PrimaryTextDark, fontWeight = FontWeight.SemiBold,
                                     fontSize = 14.sp)
                                Text("Royalty-free · 1024×768 PNG",
                                     color = SecondaryTextDark, fontSize = 11.sp)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
        containerColor = MidnightCard
    )
}

@Composable
fun StegoVaultUpsellDialog(onUpgrade: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                "Steganographic Vault is an Elite feature",
                color = PrimaryTextDark, fontWeight = FontWeight.Bold
            )
        },
        text = {
            Text(
                "Hide your entire OnlyTrusted vault inside a PNG photo or a PDF document. The decoy opens " +
                    "as a normal file in Photos or PDF reader — only your passphrase reveals what's inside. " +
                    "Built for lawyers, journalists, and activists who may be forced to hand over an " +
                    "unlocked phone. Elite tier.",
                color = SecondaryTextDark
            )
        },
        confirmButton = {
            TextButton(onClick = onUpgrade) { Text("Upgrade to Elite", color = subGold) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Not now") } },
        containerColor = MidnightCard
    )
}
