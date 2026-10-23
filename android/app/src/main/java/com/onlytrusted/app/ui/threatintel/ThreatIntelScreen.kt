// SPDX-FileCopyrightText: 2026 VanPaulTek Inc
// SPDX-License-Identifier: AGPL-3.0-or-later
package com.onlytrusted.app.ui.threatintel

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.MailLock
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import com.onlytrusted.app.ui.theme.AppBackdropScaffold
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.onlytrusted.app.OnlyTrustedApp
import com.onlytrusted.app.ui.subscription.subGold
import com.onlytrusted.app.ui.theme.BrandCyanDark
import com.onlytrusted.app.ui.theme.MidnightBackground
import com.onlytrusted.app.ui.theme.MidnightCard
import com.onlytrusted.app.ui.theme.MidnightElevated
import com.onlytrusted.app.ui.theme.PrimaryTextDark
import com.onlytrusted.app.ui.theme.SecondaryTextDark
import com.onlytrusted.app.ui.theme.TertiaryTextDark
import com.onlytrusted.app.ui.theme.WarningDark
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import java.io.File
import java.text.DateFormat

/** Elite-tier Offline Threat Intel dashboard — mirrors iOS `ThreatIntelScreen`. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThreatIntelScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val db = remember { ThreatIntelDatabase.get(context) }

    var vaultHits by remember { mutableStateOf<List<ThreatIntelDatabase.VaultScanHit>?>(null) }
    var deviceHits by remember { mutableStateOf<List<ThreatIntelDatabase.DeviceScanHit>?>(null) }

    AppBackdropScaffold(
        topBar = {
            TopAppBar(
                title = { Text("Offline Threat Intel", color = PrimaryTextDark) },
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
            HeaderCard()
            CountersCard(db)

            // Check my vault
            Card {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Check my vault", fontSize = 14.sp,
                         fontWeight = FontWeight.SemiBold, color = PrimaryTextDark)
                    Text(
                        "Cross-references every trusted contact's phone + email against the bundled feeds. No contact data is uploaded.",
                        fontSize = 12.sp, color = SecondaryTextDark
                    )
                    Button(
                        onClick = {
                            scope.launch {
                                val app = context.applicationContext as OnlyTrustedApp
                                val contacts = app.trustedContacts.allContacts.first()
                                val hits = mutableListOf<ThreatIntelDatabase.VaultScanHit>()
                                for (entry in contacts) {
                                    val name = entry.contact.displayName
                                    for (n in entry.numbers) {
                                        if (db.isScamNumber(n.normalisedNumber)) {
                                            hits.add(
                                                ThreatIntelDatabase.VaultScanHit(
                                                    name, "Phone number appears in scam-numbers feed",
                                                    n.normalisedNumber
                                                )
                                            )
                                        }
                                    }
                                    val email = entry.contact.emailMasked.orEmpty()
                                    if (email.contains("@") && db.isBreachedEmail(email)) {
                                        hits.add(
                                            ThreatIntelDatabase.VaultScanHit(
                                                name, "Email prefix matches a known breach dataset", email
                                            )
                                        )
                                    }
                                }
                                vaultHits = hits
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = BrandCyanDark, contentColor = Color.Black),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Filled.Search, null, modifier = Modifier.padding(end = 8.dp))
                        Text("Check my vault", fontWeight = FontWeight.SemiBold)
                    }
                    val hits = vaultHits
                    if (hits != null) {
                        if (hits.isEmpty()) {
                            SuccessLine("No matches in the bundled feeds.")
                        } else {
                            hits.forEach { HitRow(it.contactName, it.reason, it.value) }
                        }
                    }
                }
            }

            // Scan my device
            Card {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Scan my device", fontSize = 14.sp,
                         fontWeight = FontWeight.SemiBold, color = PrimaryTextDark)
                    Text(
                        "Android limits third-party apps to their own data directory. This scan walks OnlyTrusted's own files for any filename matching a bundled Pegasus / Candiru / Predator IoC. Clean result is expected on a healthy device.",
                        fontSize = 12.sp, color = SecondaryTextDark
                    )
                    OutlinedButton(
                        onClick = {
                            val hits = scanAppDirs(context, db)
                            deviceHits = hits
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Filled.Devices, null, modifier = Modifier.padding(end = 8.dp))
                        Text("Scan my device")
                    }
                    val hits = deviceHits
                    if (hits != null) {
                        if (hits.isEmpty()) {
                            SuccessLine("No IoC filenames in the app sandbox.")
                        } else {
                            hits.forEach {
                                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(it.kind, fontSize = 13.sp,
                                         fontWeight = FontWeight.SemiBold, color = WarningDark)
                                    Text(it.value, fontSize = 10.sp, color = TertiaryTextDark)
                                }
                            }
                        }
                    }
                }
            }

            // Freshness
            Card {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Last update", fontSize = 13.sp,
                             fontWeight = FontWeight.SemiBold, color = PrimaryTextDark,
                             modifier = Modifier.weight(1f))
                        Text("${db.totalEntries} entries",
                             fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                             color = PrimaryTextDark)
                    }
                    val lastDate = db.lastUpdatedDate()
                    val label = if (lastDate != null)
                        DateFormat.getDateInstance(DateFormat.MEDIUM).format(lastDate)
                    else "Bundled with this release"
                    Text(label, fontSize = 12.sp, color = SecondaryTextDark)
                    Text(
                        "Feeds refresh with each app update. The runtime never reaches out to any server for these — there is no live-fetch path, by design.",
                        fontSize = 11.sp, color = SecondaryTextDark
                    )
                }
            }

            Spacer(Modifier.height(8.dp))
        }
    }
}

private fun scanAppDirs(ctx: android.content.Context, db: ThreatIntelDatabase): List<ThreatIntelDatabase.DeviceScanHit> {
    val pegasus = db.pegasus ?: return emptyList()
    val names = pegasus.filenames.map { it.lowercase() }.toSet()
    val hits = mutableListOf<ThreatIntelDatabase.DeviceScanHit>()
    val roots = listOf(ctx.filesDir, ctx.cacheDir, ctx.applicationInfo.dataDir?.let { File(it) })
        .filterNotNull()
    for (root in roots) {
        root.walkTopDown().forEach { f ->
            if (f.isFile && f.name.lowercase() in names) {
                hits.add(ThreatIntelDatabase.DeviceScanHit("Pegasus IoC filename", f.absolutePath))
            }
        }
    }
    return hits
}

@Composable
private fun HeaderCard() {
    Card {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Surface(shape = RoundedCornerShape(12.dp), color = subGold) {
                Icon(Icons.Filled.Security, null, tint = Color.White,
                     modifier = Modifier.padding(10.dp))
            }
            Column {
                Text("Elite feature", fontSize = 11.sp, color = subGold, fontWeight = FontWeight.SemiBold)
                Text("Offline threat database.", fontSize = 15.sp,
                     fontWeight = FontWeight.SemiBold, color = PrimaryTextDark)
                Text(
                    "Bundled scam-numbers, phishing-URL patterns, breached-email prefixes, and Pegasus / Candiru / Predator IoCs. Everything is consulted on-device — not a byte of your vault or device metadata ever leaves the phone.",
                    fontSize = 12.sp, color = SecondaryTextDark
                )
            }
        }
    }
}

@Composable
private fun CountersCard(db: ThreatIntelDatabase) {
    val pegCount = db.pegasus?.let {
        it.filenames.size + it.plistEntries.size + it.dylibSha256.size +
            it.processNames.size + it.domains.size
    } ?: 0
    Card {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Today's intel", fontSize = 14.sp,
                 fontWeight = FontWeight.SemiBold, color = PrimaryTextDark)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                CounterTile(Icons.Filled.Phone, "Scam numbers", db.scamNumbers.size, Modifier.weight(1f))
                CounterTile(Icons.Filled.Public, "Phishing URLs", db.phishingPatterns.size, Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                CounterTile(Icons.Filled.MailLock, "Breach prefixes", db.breachHashPrefixes.size, Modifier.weight(1f))
                CounterTile(Icons.Filled.VerifiedUser, "Pegasus IoCs", pegCount, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun CounterTile(icon: ImageVector, title: String, value: Int, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .background(
                color = Color.White.copy(alpha = 0.03f),
                shape = RoundedCornerShape(12.dp)
            )
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = BrandCyanDark, modifier = Modifier.padding(end = 6.dp))
            Text(title, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = SecondaryTextDark)
        }
        Text("$value", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = PrimaryTextDark)
    }
}

@Composable
private fun SuccessLine(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Filled.VerifiedUser, null, tint = BrandCyanDark)
        Spacer(Modifier.width(6.dp))
        Text(text, fontSize = 12.sp,
             fontWeight = FontWeight.SemiBold, color = PrimaryTextDark)
    }
}

@Composable
private fun HitRow(name: String, reason: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Warning, null, tint = WarningDark)
            Spacer(Modifier.width(6.dp))
            Text(name, fontSize = 13.sp,
                 fontWeight = FontWeight.SemiBold, color = PrimaryTextDark)
        }
        Text(reason, fontSize = 11.sp, color = SecondaryTextDark)
        Text(value, fontSize = 10.sp, color = TertiaryTextDark)
    }
}

@Composable
private fun Card(content: @Composable () -> Unit) {
    Surface(shape = RoundedCornerShape(16.dp), color = MidnightElevated,
            modifier = Modifier.fillMaxWidth()) { content() }
}

@Composable
fun ThreatIntelUpsellDialog(onUpgrade: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Offline Threat Intel is an Elite feature",
                 color = PrimaryTextDark, fontWeight = FontWeight.Bold)
        },
        text = {
            Text(
                "Bundled scam numbers, phishing URLs, breached-email prefixes, and Pegasus / Candiru / Predator IoCs — all loaded on-device, never a server call. Check your vault for compromised contacts in one tap. Elite tier.",
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
