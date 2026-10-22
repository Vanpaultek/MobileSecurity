// SPDX-FileCopyrightText: 2026 VanPaulTek Inc
// SPDX-License-Identifier: AGPL-3.0-or-later
package com.onlytrusted.app.ui.smartlinkguard

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.onlytrusted.app.ui.theme.BrandAccentDark
import com.onlytrusted.app.ui.theme.BrandCyanDark
import com.onlytrusted.app.ui.theme.DangerDark
import com.onlytrusted.app.ui.theme.MidnightBackground
import com.onlytrusted.app.ui.theme.MidnightElevated
import com.onlytrusted.app.ui.theme.PrimaryTextDark
import com.onlytrusted.app.ui.theme.SecondaryTextDark
import com.onlytrusted.app.ui.theme.TertiaryTextDark
import com.onlytrusted.app.ui.theme.WarningDark
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Smart Link Guard (Pro) screen. Entry point from
 *   Settings → Security & data → "Smart Link Guard (Pro)".
 *
 * Shows:
 *  • "Scan QR" button that launches [SmartLinkGuardQrActivity] (CameraX + ML Kit).
 *  • Manual "paste a URL" text field + Check button, which runs the on-device rules
 *    engine and surfaces the verdict in a dialog.
 *  • Rolling list of recent inspections (SMS, QR, manual) with verdict badges.
 *  • Explainer card listing the 8 on-device rules in plain language.
 *  • Toggle for the SMS link scanner (Android only — requires RECEIVE_SMS at install
 *    time, which we declare in AndroidManifest.xml with a Pro-feature justification).
 */
@Composable
fun SmartLinkGuardScreen(onBack: () -> Unit = {}) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current

    var smsEnabled by remember { mutableStateOf(SmartLinkGuardPrefs.isSmsScanEnabled(context)) }
    var recent by remember { mutableStateOf(SmartLinkGuardPrefs.recentScans(context)) }
    var manualInput by remember { mutableStateOf("") }
    var activeResult by remember { mutableStateOf<URLSafetyResult?>(null) }
    var urlOpenError by remember { mutableStateOf<String?>(null) }

    fun refreshRecent() {
        recent = SmartLinkGuardPrefs.recentScans(context)
    }

    val smsPermissionsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        val readOk = grants[Manifest.permission.READ_SMS] == true ||
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_SMS
            ) == PackageManager.PERMISSION_GRANTED
        val receiveOk = grants[Manifest.permission.RECEIVE_SMS] == true ||
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECEIVE_SMS
            ) == PackageManager.PERMISSION_GRANTED
        val granted = readOk && receiveOk
        smsEnabled = granted
        SmartLinkGuardPrefs.setSmsScanEnabled(context, granted)
    }

    val qrLauncher = rememberLauncherForActivityResult(
        contract = SmartLinkGuardQrActivity.Contract()
    ) { url: String? ->
        if (!url.isNullOrBlank()) {
            val result = SmartLinkGuardRules.inspect(url)
            SmartLinkGuardPrefs.addScan(
                context,
                RecentScan(
                    timestampMillis = System.currentTimeMillis(),
                    url = result.url,
                    verdict = result.verdict,
                    source = RecentScan.Source.QR,
                    topReason = result.reasons.firstOrNull().orEmpty()
                )
            )
            refreshRecent()
            activeResult = result
        }
    }

    LaunchedEffect(Unit) { refreshRecent() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MidnightBackground)
            .verticalScroll(rememberScrollState())
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {

        // Header
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.LinkOff, contentDescription = null, tint = BrandAccentDark)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    "Smart Link Guard",
                    color = PrimaryTextDark,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "On-device URL inspection — no cloud, no logging.",
                    color = SecondaryTextDark,
                    fontSize = 12.sp
                )
            }
        }

        // Scanners card: QR button + paste-and-check row
        Card {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = { qrLauncher.launch(Unit) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = BrandCyanDark,
                        contentColor = Color.White
                    )
                ) {
                    Icon(Icons.Filled.QrCodeScanner, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Scan a QR code", fontWeight = FontWeight.SemiBold)
                }

                Divider(color = Color.White.copy(alpha = 0.06f))

                Text(
                    "Check a URL manually",
                    color = PrimaryTextDark,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
                OutlinedTextField(
                    value = manualInput,
                    onValueChange = { manualInput = it },
                    singleLine = true,
                    label = { Text("Paste or type a link") },
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(
                        onClick = {
                            val pasted = clipboard.getText()?.text.orEmpty()
                            if (pasted.isNotBlank()) manualInput = pasted
                        }
                    ) {
                        Icon(Icons.Filled.ContentPaste, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("Paste")
                    }
                    Button(
                        onClick = {
                            if (manualInput.isNotBlank()) {
                                val result = SmartLinkGuardRules.inspect(manualInput)
                                SmartLinkGuardPrefs.addScan(
                                    context,
                                    RecentScan(
                                        timestampMillis = System.currentTimeMillis(),
                                        url = result.url,
                                        verdict = result.verdict,
                                        source = RecentScan.Source.Manual,
                                        topReason = result.reasons.firstOrNull().orEmpty()
                                    )
                                )
                                refreshRecent()
                                activeResult = result
                            }
                        },
                        enabled = manualInput.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(containerColor = BrandAccentDark)
                    ) { Text("Check") }
                }
            }
        }

        // SMS toggle card
        Card {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            "Scan incoming SMS links",
                            color = PrimaryTextDark,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            "Flags risky URLs in texts and posts a system notification. SMS content never leaves the device.",
                            color = SecondaryTextDark,
                            fontSize = 12.sp
                        )
                    }
                    Switch(
                        checked = smsEnabled,
                        onCheckedChange = { want ->
                            if (want) {
                                val need = listOf(
                                    Manifest.permission.RECEIVE_SMS,
                                    Manifest.permission.READ_SMS
                                ).filter {
                                    ContextCompat.checkSelfPermission(context, it) !=
                                        PackageManager.PERMISSION_GRANTED
                                }
                                if (need.isEmpty()) {
                                    smsEnabled = true
                                    SmartLinkGuardPrefs.setSmsScanEnabled(context, true)
                                } else {
                                    smsPermissionsLauncher.launch(need.toTypedArray())
                                }
                            } else {
                                smsEnabled = false
                                SmartLinkGuardPrefs.setSmsScanEnabled(context, false)
                            }
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = BrandCyanDark
                        )
                    )
                }
                if (!smsEnabled) {
                    Text(
                        "Off — the receiver stays registered but will no-op until re-enabled.",
                        color = TertiaryTextDark,
                        fontSize = 11.sp
                    )
                }
            }
        }

        // Recent scans card
        Card {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "Recent scans",
                    color = PrimaryTextDark,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold
                )
                if (recent.isEmpty()) {
                    Text(
                        "Nothing inspected yet. Scan a QR code, paste a URL above, or wait for a flagged SMS link.",
                        color = SecondaryTextDark,
                        fontSize = 12.sp
                    )
                } else {
                    recent.forEach { scan ->
                        RecentScanRow(scan) { activeResult = SmartLinkGuardRules.inspect(scan.url) }
                        Divider(color = Color.White.copy(alpha = 0.04f))
                    }
                }
            }
        }

        // Explainer card
        Card {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Info, contentDescription = null, tint = BrandCyanDark)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "What we check",
                        color = PrimaryTextDark,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                RULE_EXPLAINERS.forEachIndexed { i, line ->
                    Row(verticalAlignment = Alignment.Top) {
                        Text(
                            "${i + 1}.",
                            color = BrandCyanDark,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.width(22.dp)
                        )
                        Text(line, color = SecondaryTextDark, fontSize = 12.sp)
                    }
                }
            }
        }
    }

    activeResult?.let { result ->
        VerdictDialog(
            result = result,
            onDismiss = { activeResult = null },
            onOpen = {
                val target = ensureScheme(result.url)
                runCatching {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(target))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(intent)
                }.onFailure { urlOpenError = it.message ?: "Could not open link." }
                activeResult = null
            }
        )
    }

    if (urlOpenError != null) {
        AlertDialog(
            onDismissRequest = { urlOpenError = null },
            confirmButton = {
                Button(onClick = { urlOpenError = null }) { Text("OK") }
            },
            title = { Text("Couldn't open link") },
            text = { Text(urlOpenError ?: "") },
            containerColor = MidnightElevated
        )
    }
}

@Composable
private fun RecentScanRow(scan: RecentScan, onTap: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        VerdictBadge(scan.verdict)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                scan.url,
                color = PrimaryTextDark,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1
            )
            Text(
                "${formatDate(scan.timestampMillis)} · ${scan.source.name}" +
                    if (scan.topReason.isNotBlank()) " · ${scan.topReason}" else "",
                color = SecondaryTextDark,
                fontSize = 11.sp,
                maxLines = 2
            )
        }
        OutlinedButton(onClick = onTap) { Text("View") }
    }
}

@Composable
private fun VerdictBadge(verdict: URLVerdict) {
    val color = when (verdict) {
        URLVerdict.Safe -> BrandAccentDark
        URLVerdict.Caution -> WarningDark
        URLVerdict.Danger -> DangerDark
    }
    val icon = when (verdict) {
        URLVerdict.Safe -> Icons.Filled.CheckCircle
        URLVerdict.Caution -> Icons.Filled.Warning
        URLVerdict.Danger -> Icons.Filled.LinkOff
    }
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = color.copy(alpha = 0.15f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.5f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(4.dp))
            Text(verdict.name, color = color, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun VerdictDialog(
    result: URLSafetyResult,
    onDismiss: () -> Unit,
    onOpen: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MidnightElevated,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                VerdictBadge(result.verdict)
                Spacer(Modifier.width(8.dp))
                Text(result.verdict.name, color = PrimaryTextDark, fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(result.url, color = PrimaryTextDark, fontSize = 13.sp)
                Divider(color = Color.White.copy(alpha = 0.08f))
                result.reasons.forEach { r ->
                    Text("• $r", color = SecondaryTextDark, fontSize = 12.sp)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onOpen,
                enabled = result.verdict != URLVerdict.Danger,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (result.verdict == URLVerdict.Danger)
                        DangerDark.copy(alpha = 0.4f) else BrandAccentDark
                )
            ) {
                Icon(Icons.Filled.OpenInNew, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text(if (result.verdict == URLVerdict.Danger) "Open blocked" else "Open")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun Card(
    borderColor: Color? = null,
    content: @Composable () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MidnightElevated,
        border = borderColor?.let { BorderStroke(1.dp, it) },
        modifier = Modifier.fillMaxWidth()
    ) { content() }
}

private fun ensureScheme(url: String): String =
    if (url.contains("://")) url else "https://$url"

private fun formatDate(millis: Long): String =
    SimpleDateFormat("MMM d, h:mm a", Locale.US).format(Date(millis))

private val RULE_EXPLAINERS: List<String> = listOf(
    "Scheme check — insecure http:// and ftp:// are flagged.",
    "Shortener check — bit.ly, t.co, tinyurl, etc. hide the real destination.",
    "Suspicious TLDs — .zip, .mov, .xyz, .top and other scam-heavy endings.",
    "Punycode / homograph — xn-- domains that look like real brands.",
    "Direct IP — real brands use their domain name, not a raw IP.",
    "Verified allowlist — ~200 top legit domains get a visible pass.",
    "Typosquat — Levenshtein distance 1-2 against the allowlist.",
    "Phishing paths — /login, /verify, /wallet, /paypal on unverified hosts."
)
