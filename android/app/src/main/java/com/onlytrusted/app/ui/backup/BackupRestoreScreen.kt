// SPDX-FileCopyrightText: 2026 VanPaulTek Inc
// SPDX-License-Identifier: AGPL-3.0-or-later
package com.onlytrusted.app.ui.backup

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.KeyboardOptions
import android.net.Uri
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Restore (Elite). SAF OpenDocument picker → passphrase → decrypt → confirm → apply.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupRestoreScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

    var pickedUri by remember { mutableStateOf<Uri?>(null) }
    var pickedName by remember { mutableStateOf<String?>(null) }
    var passphrase by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var decoded by remember { mutableStateOf<BackupPayload?>(null) }
    var confirmApply by remember { mutableStateOf(false) }

    val openDocument = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        pickedUri = uri
        pickedName = uri.lastPathSegment
        decoded = null
        error = null
    }

    Scaffold(
        containerColor = MidnightBackground,
        topBar = {
            TopAppBar(
                title = { Text("Restore backup", color = PrimaryTextDark) },
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

            if (pickedUri == null) {
                Button(
                    onClick = {
                        openDocument.launch(arrayOf("application/octet-stream", "*/*"))
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = subGold, contentColor = Color.Black),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Filled.CloudDownload, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                    Text("Pick backup file (.ot2bak)", fontWeight = FontWeight.SemiBold)
                }
            } else {
                Card {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("File", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = PrimaryTextDark)
                        Text(pickedName ?: pickedUri.toString(), fontSize = 12.sp, color = PrimaryTextDark)
                        TextButton(onClick = {
                            pickedUri = null
                            pickedName = null
                            decoded = null
                            passphrase = ""
                            error = null
                        }) { Text("Pick a different file", color = BrandCyanDark, fontSize = 12.sp) }
                    }
                }

                Card {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Passphrase", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = PrimaryTextDark)
                        OutlinedTextField(
                            value = passphrase,
                            onValueChange = { passphrase = it; error = null },
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            label = { Text("Passphrase") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        error?.let { Text(it, color = DangerDark, fontSize = 12.sp) }
                    }
                }

                Button(
                    onClick = {
                        val uri = pickedUri ?: return@Button
                        error = null
                        scope.launch {
                            try {
                                decoded = BackupManager.decrypt(context, uri, passphrase)
                            } catch (t: Throwable) {
                                decoded = null
                                error = t.message
                                    ?: "Backup could not be decrypted. Check your passphrase or try a different file."
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = BrandAccentDark),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Decrypt and preview", color = Color.White, fontWeight = FontWeight.SemiBold)
                }
            }

            decoded?.let { p ->
                Card {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Backup contents", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = PrimaryTextDark)
                        PreviewRow("Exported", formatDate(p.exportedAtMillis))
                        PreviewRow("Platform", p.platform)
                        PreviewRow("Contacts", p.contacts.size.toString())
                        PreviewRow("Blocked numbers", p.blockedNumbers.size.toString())
                        PreviewRow("Events", p.events.size.toString())
                    }
                }

                Button(
                    onClick = { confirmApply = true },
                    colors = ButtonDefaults.buttonColors(containerColor = DangerDark),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Restore and overwrite existing data", color = Color.White, fontWeight = FontWeight.SemiBold)
                }
            }

            Spacer(Modifier.height(8.dp))
        }

        if (confirmApply && decoded != null) {
            AlertDialog(
                onDismissRequest = { confirmApply = false },
                containerColor = MidnightCard,
                title = { Text("Overwrite everything?", color = PrimaryTextDark, fontWeight = FontWeight.Bold) },
                text = {
                    Text(
                        "All current trusted contacts, blocked numbers, events, and settings " +
                            "will be replaced by the backup. This cannot be undone.",
                        color = SecondaryTextDark
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        confirmApply = false
                        val payload = decoded ?: return@TextButton
                        scope.launch {
                            try {
                                BackupManager.applyRestore(context, payload)
                                snackbar.showSnackbar("Restored.")
                                passphrase = ""
                                decoded = null
                                pickedUri = null
                                pickedName = null
                            } catch (t: Throwable) {
                                error = t.message ?: "Restore failed."
                            }
                        }
                    }) { Text("Overwrite and restore", color = DangerDark, fontWeight = FontWeight.Bold) }
                },
                dismissButton = {
                    TextButton(onClick = { confirmApply = false }) { Text("Cancel") }
                }
            )
        }
    }
}

@Composable
private fun HeaderCard() {
    Card {
        Row(
            Modifier.padding(16.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = BrandCyanDark
            ) {
                Icon(
                    imageVector = Icons.Filled.CloudDownload,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.padding(10.dp)
                )
            }
            Column {
                Text("Elite feature", fontSize = 11.sp, color = subGold, fontWeight = FontWeight.SemiBold)
                Text(
                    "Restore from an encrypted .ot2bak.",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = PrimaryTextDark
                )
                Text(
                    "Pick the file, enter the passphrase, and confirm. Everything in the app will be overwritten with the backup contents.",
                    fontSize = 12.sp,
                    color = SecondaryTextDark
                )
            }
        }
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
private fun PreviewRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, fontSize = 12.sp, color = SecondaryTextDark)
        Text(value, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = PrimaryTextDark)
    }
}

private fun formatDate(millis: Long): String =
    SimpleDateFormat("MMM d, yyyy · h:mm a", Locale.US).format(Date(millis))
