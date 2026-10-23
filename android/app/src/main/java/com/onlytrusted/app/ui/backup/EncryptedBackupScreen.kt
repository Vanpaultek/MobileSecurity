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
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import com.onlytrusted.app.ui.theme.AppBackdropScaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.KeyboardOptions
import com.onlytrusted.app.backup.BackupManager
import com.onlytrusted.app.ui.subscription.subGold
import com.onlytrusted.app.ui.theme.BrandAccentDark
import com.onlytrusted.app.ui.theme.BrandCyanDark
import com.onlytrusted.app.ui.theme.DangerDark
import com.onlytrusted.app.ui.theme.MidnightBackground
import com.onlytrusted.app.ui.theme.MidnightElevated
import com.onlytrusted.app.ui.theme.PrimaryTextDark
import com.onlytrusted.app.ui.theme.SecondaryTextDark
import kotlinx.coroutines.launch

/**
 * Encrypted vault backup (Elite). Prompts for a passphrase then writes a `.ot2bak` to a
 * user-chosen Storage Access Framework destination (iCloud is not available on Android, but
 * Google Drive / Dropbox / local Documents all surface here).
 *
 * Caller gates on `SubscriptionState.hasElite(context)`.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EncryptedBackupScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

    var passphrase by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var pendingWrite by remember { mutableStateOf(false) }

    val createDocument = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        if (uri == null) {
            pendingWrite = false
            return@rememberLauncherForActivityResult
        }
        scope.launch {
            try {
                val result = BackupManager.exportToUri(context, uri, passphrase)
                snackbar.showSnackbar("Backup saved · ${result.bytes} bytes.")
                passphrase = ""
                confirm = ""
            } catch (e: BackupManager.PassphraseWeakException) {
                error = e.reason
            } catch (t: Throwable) {
                error = t.message ?: "Could not create backup."
            }
            pendingWrite = false
        }
    }

    AppBackdropScaffold(
        topBar = {
            TopAppBar(
                title = { Text("Encrypted backup", color = PrimaryTextDark) },
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
            HeaderCard()

            Card {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Passphrase", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = PrimaryTextDark)
                    Text(
                        "At least 12 characters with a letter, a digit, and a symbol. We cannot " +
                            "recover it for you — if you forget it, the backup is gone.",
                        fontSize = 12.sp,
                        color = SecondaryTextDark
                    )
                    OutlinedTextField(
                        value = passphrase,
                        onValueChange = { passphrase = it; error = null },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        label = { Text("Passphrase") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = confirm,
                        onValueChange = { confirm = it; error = null },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        label = { Text("Confirm passphrase") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    error?.let {
                        Text(it, color = DangerDark, fontSize = 12.sp)
                    }
                }
            }

            Button(
                onClick = {
                    error = null
                    val reason = BackupManager::class.java // force class load for test envs
                    when {
                        passphrase != confirm -> error = "Passphrases don't match."
                        passphrase.isBlank() -> error = "Enter a passphrase."
                        com.onlytrusted.app.backup.BackupCrypto.validatePassphrase(passphrase) != null ->
                            error = com.onlytrusted.app.backup.BackupCrypto.validatePassphrase(passphrase)
                        else -> {
                            pendingWrite = true
                            createDocument.launch(BackupManager.suggestedFilename())
                        }
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = subGold, contentColor = Color.Black),
                enabled = !pendingWrite,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Filled.CloudUpload, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                Text("Create encrypted backup", fontWeight = FontWeight.SemiBold)
            }

            Spacer(Modifier.height(8.dp))
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
                color = subGold
            ) {
                Icon(
                    imageVector = Icons.Filled.CloudUpload,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.padding(10.dp)
                )
            }
            Column {
                Text("Elite feature", fontSize = 11.sp, color = subGold, fontWeight = FontWeight.SemiBold)
                Text(
                    "Portable, offline, zero-trust backup.",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = PrimaryTextDark
                )
                Text(
                    "Everything in one AES-256-GCM encrypted .ot2bak file. Save it to Google Drive, Dropbox, or your Documents folder — only your passphrase can decrypt it.",
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
