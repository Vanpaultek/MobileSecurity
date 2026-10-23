// SPDX-FileCopyrightText: 2026 VanPaulTek Inc
// SPDX-License-Identifier: AGPL-3.0-or-later
package com.onlytrusted.app.ui.signedlog

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import com.onlytrusted.app.ui.theme.AppBackdropScaffold
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.onlytrusted.app.OnlyTrustedApp
import com.onlytrusted.app.ui.hardwarekey.HardwareKeyState
import com.onlytrusted.app.ui.subscription.subGold
import com.onlytrusted.app.ui.theme.BrandAccentDark
import com.onlytrusted.app.ui.theme.BrandCyanDark
import com.onlytrusted.app.ui.theme.DangerDark
import com.onlytrusted.app.ui.theme.MidnightBackground
import com.onlytrusted.app.ui.theme.MidnightCard
import com.onlytrusted.app.ui.theme.MidnightElevated
import com.onlytrusted.app.ui.theme.PrimaryTextDark
import com.onlytrusted.app.ui.theme.SecondaryTextDark
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Elite-tier screen for the Hardware-Signed Audit Log. Mirrors iOS `SignedLogScreen`:
 *  - If no hardware key is enrolled, show the enrollment prompt.
 *  - Otherwise expose the "Sign every entry" toggle, Merkle commit button, export, and verify.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SignedLogScreen(
    onBack: () -> Unit,
    onOpenHardwareKey: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var signEvery by remember { mutableStateOf(SignedLogState.isSignEveryEntryEnabled(context)) }
    var commits by remember { mutableStateOf(SignedLogState.merkleCommits(context)) }
    var signedCount by remember { mutableStateOf(SignedLogState.signatures(context).size) }
    val hasKey = remember { HardwareKeyState.hasEnrolledKey(context) }

    var exportError by remember { mutableStateOf<String?>(null) }
    var exportPackPending by remember { mutableStateOf<SignedEvidenceExporter.Pack?>(null) }
    var verifyReport by remember { mutableStateOf<SignedEvidenceVerifier.Report?>(null) }
    var commitSuccess by remember { mutableStateOf<String?>(null) }
    // Flag that triggers the PDF save launcher once the JSON save has finished. We use a
    // counter so each successful JSON write re-arms a new PDF prompt (otherwise two consecutive
    // exports would skip the PDF).
    val pdfLaunchTrigger = remember { androidx.compose.runtime.mutableStateOf(0) }

    val savePdfLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri ->
        val pack = exportPackPending ?: return@rememberLauncherForActivityResult
        exportPackPending = null
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            try {
                SignedEvidenceExporter.writePdfTo(context, uri, pack.pdfBytes)
                snackbar.showSnackbar("Signed evidence pack saved.")
            } catch (t: Throwable) {
                exportError = t.message ?: "Could not save PDF."
            }
        }
    }

    val saveJsonLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        val pack = exportPackPending ?: return@rememberLauncherForActivityResult
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            try {
                SignedEvidenceExporter.writeJsonTo(context, uri, pack.jsonBytes)
                snackbar.showSnackbar("JSON saved · now pick a destination for the PDF.")
                pdfLaunchTrigger.value++
            } catch (t: Throwable) {
                exportError = t.message ?: "Could not save evidence pack."
                exportPackPending = null
            }
        }
    }

    // Fire the PDF launcher whenever JSON save completes and the counter bumps.
    androidx.compose.runtime.LaunchedEffect(pdfLaunchTrigger.value) {
        if (pdfLaunchTrigger.value > 0 && exportPackPending != null) {
            savePdfLauncher.launch("${exportPackPending!!.filenameStem}.pdf")
        }
    }

    val verifyPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            verifyReport = SignedEvidenceVerifier.verify(context, uri)
        }
    }

    AppBackdropScaffold(
        topBar = {
            TopAppBar(
                title = { Text("Signed Audit Log", color = PrimaryTextDark) },
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
            if (!hasKey) {
                EnrollPromptCard(onOpenHardwareKey = onOpenHardwareKey)
            } else {
                // Toggle
                Card {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Sign every audit entry", fontSize = 14.sp,
                             fontWeight = FontWeight.SemiBold, color = PrimaryTextDark)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Switch(
                                checked = signEvery,
                                onCheckedChange = { enabled ->
                                    signEvery = enabled
                                    SignedLogState.setSignEveryEntryEnabled(context, enabled)
                                },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = BrandCyanDark
                                )
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                if (signEvery) "Each new security event requires a key tap."
                                else "Signatures only when you commit a Merkle batch.",
                                fontSize = 12.sp,
                                color = SecondaryTextDark
                            )
                        }
                        Text(
                            "Default is OFF because every event (blocked call, SMS filter hit) would otherwise ask for a key tap. Lawyers typically turn this on only during a case or trip.",
                            fontSize = 11.sp,
                            color = SecondaryTextDark
                        )
                    }
                }

                // Merkle card
                Card {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Merkle commitment", fontSize = 14.sp,
                             fontWeight = FontWeight.SemiBold, color = PrimaryTextDark)
                        StatusRow("Last commit", SignedLogState.lastCommitDescription(context))
                        StatusRow("Events signed", "$signedCount")
                        Text(
                            "Commit a batch to compute a root hash over the latest events. A real deployment would submit this root to OpenTimestamps / RFC 3161 for a public time anchor — this build stores it locally so you can still prove the chain to a verifier.",
                            fontSize = 11.sp,
                            color = SecondaryTextDark
                        )
                        OutlinedButton(
                            onClick = {
                                scope.launch {
                                    val commit = commitMerkleBatch(context)
                                    commits = SignedLogState.merkleCommits(context)
                                    commitSuccess = if (commit != null) {
                                        "${commit.count} new events anchored.\nRoot: ${commit.rootHex.take(16)}…"
                                    } else {
                                        "Nothing new to commit — the chain is already anchored."
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Filled.History, null, modifier = Modifier.padding(end = 6.dp))
                            Text("Commit new events")
                        }
                    }
                }

                // Export card
                Card {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Export signed evidence pack", fontSize = 14.sp,
                             fontWeight = FontWeight.SemiBold, color = PrimaryTextDark)
                        Text(
                            "Produces a two-file bundle: a machine-readable JSON for forensic tools and a human-readable PDF for the courtroom. Both include the hash chain, signatures, Merkle roots, and enrolled public keys.",
                            fontSize = 12.sp, color = SecondaryTextDark
                        )
                        Button(
                            onClick = {
                                scope.launch {
                                    try {
                                        val pack = SignedEvidenceExporter.build(context)
                                        exportPackPending = pack
                                        saveJsonLauncher.launch("${pack.filenameStem}.json")
                                    } catch (t: Throwable) {
                                        exportError = t.message ?: "Could not build evidence pack."
                                    }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = subGold, contentColor = Color.Black),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Filled.Save, null, modifier = Modifier.padding(end = 8.dp))
                            Text("Export signed evidence pack", fontWeight = FontWeight.SemiBold)
                        }
                    }
                }

                // Verify card
                Card {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Verify evidence pack", fontSize = 14.sp,
                             fontWeight = FontWeight.SemiBold, color = PrimaryTextDark)
                        Text(
                            "Load a previously-exported pack (from this device or another). We recompute every row hash and Merkle root; the public key you enrolled is used to confirm signatures.",
                            fontSize = 12.sp, color = SecondaryTextDark
                        )
                        OutlinedButton(
                            onClick = { verifyPickerLauncher.launch("application/json") },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Filled.Search, null, modifier = Modifier.padding(end = 6.dp))
                            Text("Pick evidence pack to verify")
                        }
                    }
                }

                // Limitations
                Card {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("What this build does and doesn't", fontSize = 13.sp,
                             fontWeight = FontWeight.SemiBold, color = PrimaryTextDark)
                        Text(
                            "• Hardware signatures use Android Credential Manager + an enrolled FIDO2 key.\n" +
                                "• Merkle commits are stored locally only; this build does not submit them to OpenTimestamps or RFC 3161 servers.\n" +
                                "• A future server-backed verifier can accept this pack unchanged and confirm the signatures off-device.",
                            fontSize = 11.sp, color = SecondaryTextDark
                        )
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }

        if (verifyReport != null) {
            AlertDialog(
                onDismissRequest = { verifyReport = null },
                title = {
                    Text(
                        if (verifyReport!!.valid) "Verified" else "Tampered",
                        color = if (verifyReport!!.valid) BrandCyanDark else DangerDark,
                        fontWeight = FontWeight.Bold
                    )
                },
                text = { Text(verifyReport!!.summary, color = SecondaryTextDark) },
                confirmButton = {
                    TextButton(onClick = { verifyReport = null }) { Text("OK") }
                },
                containerColor = MidnightCard
            )
        }
        if (commitSuccess != null) {
            AlertDialog(
                onDismissRequest = { commitSuccess = null },
                title = { Text("Merkle batch committed", color = PrimaryTextDark) },
                text = { Text(commitSuccess!!, color = SecondaryTextDark) },
                confirmButton = {
                    TextButton(onClick = { commitSuccess = null }) { Text("OK") }
                },
                containerColor = MidnightCard
            )
        }
        if (exportError != null) {
            AlertDialog(
                onDismissRequest = { exportError = null },
                title = { Text("Export failed", color = DangerDark, fontWeight = FontWeight.Bold) },
                text = { Text(exportError!!, color = SecondaryTextDark) },
                confirmButton = {
                    TextButton(onClick = { exportError = null }) { Text("OK") }
                },
                containerColor = MidnightCard
            )
        }
    }
}

@Composable
private fun HeaderCard() {
    Card {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Surface(shape = RoundedCornerShape(12.dp), color = subGold) {
                Icon(Icons.Filled.VerifiedUser, null, tint = Color.White,
                     modifier = Modifier.padding(10.dp))
            }
            Column {
                Text("Elite feature", fontSize = 11.sp, color = subGold, fontWeight = FontWeight.SemiBold)
                Text("Court-ready audit log.", fontSize = 15.sp,
                     fontWeight = FontWeight.SemiBold, color = PrimaryTextDark)
                Text(
                    "Every security event is signed with your hardware key and anchored into a local Merkle commitment. Export a tamper-evident PDF for a lawyer or examiner.",
                    fontSize = 12.sp, color = SecondaryTextDark
                )
            }
        }
    }
}

@Composable
private fun EnrollPromptCard(onOpenHardwareKey: () -> Unit) {
    Card {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Enroll a hardware key first", fontSize = 15.sp,
                 fontWeight = FontWeight.SemiBold, color = PrimaryTextDark)
            Text(
                "The Signed Audit Log needs a FIDO2 key (YubiKey, Google Titan, SoloKey) to produce signatures. Enroll one on the Hardware Security Key screen, then come back here.",
                fontSize = 12.sp, color = SecondaryTextDark
            )
            Button(
                onClick = onOpenHardwareKey,
                colors = ButtonDefaults.buttonColors(containerColor = subGold, contentColor = Color.Black),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Filled.OpenInNew, null, modifier = Modifier.padding(end = 8.dp))
                Text("Open Hardware Security Key setup", fontWeight = FontWeight.SemiBold)
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
private fun StatusRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontSize = 12.sp, color = SecondaryTextDark, modifier = Modifier.weight(1f))
        Text(value, fontSize = 13.sp, color = PrimaryTextDark, fontWeight = FontWeight.SemiBold)
    }
}

/** Walks the EventStore oldest→newest and commits every un-committed row into a new Merkle
 *  batch. Returns the newly-created commit or null if nothing to commit. */
private suspend fun commitMerkleBatch(
    context: android.content.Context
): SignedLogState.MerkleCommit? {
    val app = context.applicationContext as OnlyTrustedApp
    val events = app.database.eventDao().recent(limit = 100_000).first()
        .sortedBy { it.timestamp }
    if (events.isEmpty()) return null

    val existing = SignedLogState.merkleCommits(context)
    val committedTo = existing.lastOrNull()?.toRowHash
    val leaves = mutableListOf<String>()
    var passed = committedTo == null
    var fromHash: String? = null
    var toHash: String? = null
    for (ev in events) {
        if (!passed) {
            if (ev.rowHash == committedTo) passed = true
            continue
        }
        val row = ev.rowHash ?: continue
        if (fromHash == null) fromHash = ev.prevHash
        toHash = row
        leaves.add(row)
        if (leaves.size >= 1024) break
    }
    if (leaves.isEmpty() || toHash == null) return null
    val root = SignedLogState.merkleRoot(leaves)
    val commit = SignedLogState.MerkleCommit(
        commitAtMillis = System.currentTimeMillis(),
        fromRowHash = fromHash,
        toRowHash = toHash,
        count = leaves.size,
        rootHex = root,
        leaves = leaves
    )
    SignedLogState.persistMerkleCommits(context, existing + commit)
    return commit
}

@Composable
fun SignedLogUpsellDialog(onUpgrade: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                "Signed Audit Log is an Elite feature",
                color = PrimaryTextDark, fontWeight = FontWeight.Bold
            )
        },
        text = {
            Text(
                "Every security event is signed with your hardware key and anchored into a local Merkle commitment. Export a court-ready evidence pack: lawyers and examiners can verify every row and spot the exact event number if the pack has been altered. Elite tier.",
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
