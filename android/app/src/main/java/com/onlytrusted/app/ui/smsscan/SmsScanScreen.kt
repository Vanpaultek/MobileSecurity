// SPDX-FileCopyrightText: 2026 VanPaulTek Inc
// SPDX-License-Identifier: AGPL-3.0-or-later
package com.onlytrusted.app.ui.smsscan

import android.Manifest
import android.content.pm.PackageManager
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.onlytrusted.app.ui.theme.AppBackdropScaffold
import com.onlytrusted.app.ui.theme.BrandAccentDark
import com.onlytrusted.app.ui.theme.BrandCyanDark
import com.onlytrusted.app.ui.theme.DangerDark
import com.onlytrusted.app.ui.theme.MidnightElevated
import com.onlytrusted.app.ui.theme.PrimaryTextDark
import com.onlytrusted.app.ui.theme.SecondaryTextDark
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date

/**
 * Scan the SMS inbox for scam senders, phishing URLs, and classic scam phrases. Mirrors
 * ContactScanScreen's 4-state flow (permission → ready → scanning → results).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SmsScanScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var permissionGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS)
                == PackageManager.PERMISSION_GRANTED
        )
    }
    var scanning by remember { mutableStateOf(false) }
    var report by remember { mutableStateOf<SmsScanReport?>(null) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        permissionGranted = granted
        if (granted) runScan(scope, context) { r -> report = r; scanning = false }
            .also { scanning = true }
    }

    AppBackdropScaffold(
        topBar = {
            TopAppBar(
                title = { Text("Scam SMS Scan", color = PrimaryTextDark) },
                navigationIcon = {
                    TextButton(onClick = onBack) { Text("Back", color = BrandCyanDark) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            CardBox {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    HeaderRow(Icons.AutoMirrored.Filled.Message, "Scan SMS inbox", BrandAccentDark)
                    Text(
                        "We'll check every message in your SMS inbox for known-scam sender numbers, " +
                            "phishing links, and classic scam phrases (prize/lottery/urgent transfer/" +
                            "fake bank alerts). Runs on this device only — no messages are uploaded.",
                        fontSize = 12.sp, color = SecondaryTextDark
                    )
                }
            }

            if (!permissionGranted) {
                CardBox {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        HeaderRow(Icons.Filled.Info, "Permission needed", SecondaryTextDark)
                        Text(
                            "Reading your SMS inbox requires one-time permission. We only use it to " +
                                "compare messages against the local scam feeds — nothing is transmitted, " +
                                "stored, or shared.",
                            fontSize = 11.sp, color = SecondaryTextDark
                        )
                        Button(
                            onClick = { permissionLauncher.launch(Manifest.permission.READ_SMS) },
                            modifier = Modifier.fillMaxWidth().height(44.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = BrandAccentDark)
                        ) {
                            Text("Grant SMS access", color = Color.White, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            } else if (scanning) {
                CardBox {
                    Row(
                        Modifier.padding(20.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        CircularProgressIndicator(
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(20.dp),
                            color = BrandAccentDark
                        )
                        Text("Scanning your SMS inbox…", fontSize = 13.sp, color = PrimaryTextDark)
                    }
                }
            } else {
                val existing = report
                if (existing == null) {
                    CardBox {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("Ready to scan.", fontSize = 13.sp, color = SecondaryTextDark)
                            Button(
                                onClick = {
                                    scanning = true
                                    runScan(scope, context) { r -> report = r; scanning = false }
                                },
                                modifier = Modifier.fillMaxWidth().height(48.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = BrandAccentDark)
                            ) {
                                Text("Scan my SMS", color = Color.White, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                } else {
                    CardBox {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            val hitColor = if (existing.matches.isEmpty()) BrandAccentDark else DangerDark
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Icon(
                                    if (existing.matches.isEmpty()) Icons.Filled.Info else Icons.Filled.Warning,
                                    null, tint = hitColor
                                )
                                Text(
                                    if (existing.matches.isEmpty())
                                        "No scam messages found"
                                    else
                                        "${existing.matches.size} suspicious message(s) found",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = hitColor
                                )
                            }
                            Text(
                                "Scanned ${existing.messagesScanned} message(s) from " +
                                    "${existing.sendersScanned} sender(s).",
                                fontSize = 11.sp, color = SecondaryTextDark
                            )
                            TextButton(onClick = {
                                scanning = true
                                runScan(scope, context) { r -> report = r; scanning = false }
                            }) { Text("Re-scan", color = BrandCyanDark) }
                        }
                    }

                    existing.matches.forEach { match ->
                        CardBox {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Filled.Warning, null, tint = DangerDark, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.size(6.dp))
                                    Text(match.sender, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = PrimaryTextDark)
                                    Spacer(Modifier.weight(1f))
                                    Text(
                                        DateFormat.getDateInstance(DateFormat.SHORT).format(Date(match.timestamp)),
                                        fontSize = 10.sp, color = SecondaryTextDark
                                    )
                                }
                                Text(match.bodyPreview, fontSize = 12.sp, color = SecondaryTextDark)
                                Text(match.reason, fontSize = 10.sp, color = DangerDark)
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun runScan(
    scope: kotlinx.coroutines.CoroutineScope,
    ctx: android.content.Context,
    onDone: (SmsScanReport) -> Unit
) {
    scope.launch {
        val r = withContext(Dispatchers.IO) { SmsScamScanner.scan(ctx) }
        SmsScanState.save(ctx, r)
        onDone(r)
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
