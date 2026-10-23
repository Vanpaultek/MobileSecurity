// SPDX-FileCopyrightText: 2026 VanPaulTek Inc
// SPDX-License-Identifier: AGPL-3.0-or-later
package com.onlytrusted.app.ui.weeklydigest

import android.content.Context
import android.content.Intent
import android.graphics.Color as AColor
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
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
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.onlytrusted.app.OnlyTrustedApp
import com.onlytrusted.app.data.db.entities.SecurityEvent
import com.onlytrusted.app.ui.subscription.SubscriptionState
import com.onlytrusted.app.ui.theme.AppBackdropScaffold
import com.onlytrusted.app.ui.theme.BrandAccentDark
import com.onlytrusted.app.ui.theme.BrandCyanDark
import com.onlytrusted.app.ui.theme.MidnightElevated
import com.onlytrusted.app.ui.theme.PrimaryTextDark
import com.onlytrusted.app.ui.theme.SecondaryTextDark
import com.onlytrusted.app.ui.subscription.subGold
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Premium weekly digest (Android). Mirrors iOS `WeeklyDigestScreen`. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WeeklyDigestScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val isPremium = SubscriptionState.hasPremium(context)
    var counts by remember { mutableStateOf(WeeklyPDFDigestCounts()) }

    LaunchedEffect(Unit) {
        counts = WeeklyPDFDigestGenerator.compute(context)
    }

    AppBackdropScaffold(
        topBar = {
            TopAppBar(
                title = { Text("Weekly Digest", color = PrimaryTextDark) },
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
            CardBox {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    HeaderRow(Icons.Filled.Description, "Weekly Digest", BrandAccentDark)
                    Text(
                        "A one-page PDF summarising the last 7 days: blocked calls, filtered " +
                            "SMS, mic/cam alerts, breach checks. Generated on your device and " +
                            "shared via the system share sheet. Nothing is uploaded.",
                        fontSize = 12.sp, color = SecondaryTextDark
                    )
                }
            }

            CardBox {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    HeaderRow(Icons.Filled.BarChart, "This week at a glance", BrandAccentDark)
                    SummaryRow("Mic/camera alerts", counts.sensorAlerts)
                    SummaryRow("Blocked calls", counts.blockedCalls)
                    SummaryRow("Filtered SMS", counts.filteredSms)
                    SummaryRow("Panic / lockdown events", counts.panicEvents)
                    SummaryRow("Total audit events", counts.total)
                }
            }

            if (isPremium) {
                CardBox {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Export", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = SecondaryTextDark)
                        Button(
                            onClick = {
                                scope.launch {
                                    val uri = withContext(Dispatchers.IO) {
                                        WeeklyPDFDigestGenerator.generatePdf(context, counts)
                                    }
                                    if (uri != null) {
                                        val send = Intent(Intent.ACTION_SEND).apply {
                                            type = "application/pdf"
                                            putExtra(Intent.EXTRA_STREAM, uri)
                                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                        }
                                        context.startActivity(Intent.createChooser(send, "Share weekly digest"))
                                    } else {
                                        snackbar.showSnackbar("Export failed")
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = BrandAccentDark)
                        ) {
                            Icon(Icons.Filled.Share, null, tint = Color.White)
                            Spacer(Modifier.size(8.dp))
                            Text("Generate & share PDF", color = Color.White, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            } else {
                CardBox {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.WorkspacePremium, null, tint = subGold)
                            Spacer(Modifier.size(8.dp))
                            Text(
                                "Premium weekly digest",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = PrimaryTextDark
                            )
                        }
                        Text(
                            "Premium (\$4.99/yr) generates a one-page weekly PDF digest you can " +
                                "save or share. All counts come from your on-device audit log; " +
                                "nothing is uploaded.",
                            fontSize = 11.sp, color = SecondaryTextDark
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SummaryRow(label: String, value: Int) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontSize = 13.sp, color = SecondaryTextDark, modifier = Modifier.weight(1f))
        Text("$value", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = PrimaryTextDark)
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

data class WeeklyPDFDigestCounts(
    val sensorAlerts: Int = 0,
    val blockedCalls: Int = 0,
    val filteredSms: Int = 0,
    val panicEvents: Int = 0,
    val total: Int = 0
)

object WeeklyPDFDigestGenerator {

    suspend fun compute(ctx: Context, now: Long = System.currentTimeMillis()): WeeklyPDFDigestCounts {
        val cutoff = now - 7L * 24 * 60 * 60 * 1000
        return withContext(Dispatchers.IO) {
            val dao = (ctx.applicationContext as OnlyTrustedApp).database.eventDao()
            val sensor = dao.countOfTypeSinceOnce(SecurityEvent.TYPE_MIC, cutoff) +
                dao.countOfTypeSinceOnce(SecurityEvent.TYPE_CAMERA, cutoff)
            val blocked = dao.countOfTypeSinceOnce(SecurityEvent.TYPE_CALL_BLOCKED, cutoff) +
                dao.countOfTypeSinceOnce(SecurityEvent.TYPE_WANGIRI_BLOCKED, cutoff)
            val sms = dao.countOfTypeSinceOnce(SecurityEvent.TYPE_SMS_FLAGGED, cutoff) +
                dao.countOfTypeSinceOnce(SecurityEvent.TYPE_UNTRUSTED_SMS, cutoff)
            val panic = dao.countOfTypeSinceOnce(SecurityEvent.TYPE_PANIC_LOCK, cutoff) +
                dao.countOfTypeSinceOnce(SecurityEvent.TYPE_PANIC_LOCKDOWN_ACTIVATED, cutoff)
            val total = dao.countSinceOnce(cutoff)
            WeeklyPDFDigestCounts(sensor, blocked, sms, panic, total)
        }
    }

    /** Builds the one-page PDF in the app cache dir and returns a FileProvider URI. */
    fun generatePdf(ctx: Context, counts: WeeklyPDFDigestCounts, now: Long = System.currentTimeMillis()): android.net.Uri? {
        val pdf = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(612, 792, 1).create()  // US Letter 72dpi
        val page = pdf.startPage(pageInfo)
        val canvas = page.canvas

        val titlePaint = Paint().apply { color = AColor.BLACK; textSize = 28f; isFakeBoldText = true }
        val subPaint = Paint().apply { color = AColor.DKGRAY; textSize = 12f }
        val labelPaint = Paint().apply { color = AColor.BLACK; textSize = 14f }
        val valuePaint = Paint().apply { color = AColor.BLACK; textSize = 14f; isFakeBoldText = true }
        val footerPaint = Paint().apply { color = AColor.GRAY; textSize = 10f }
        val fmt = SimpleDateFormat("MMM d, yyyy", Locale.US)

        canvas.drawText("OnlyTrusted — Weekly Digest", 50f, 70f, titlePaint)
        val since = fmt.format(Date(now - 7L * 24 * 60 * 60 * 1000))
        val until = fmt.format(Date(now))
        canvas.drawText("Period: $since → $until. Generated on this device, no network.", 50f, 100f, subPaint)

        var y = 160f
        val entries = listOf(
            "Mic/camera alerts" to counts.sensorAlerts,
            "Blocked calls" to counts.blockedCalls,
            "Filtered SMS" to counts.filteredSms,
            "Panic / lockdown events" to counts.panicEvents,
            "Total audit events" to counts.total
        )
        for ((label, value) in entries) {
            canvas.drawText(label, 60f, y, labelPaint)
            canvas.drawText("$value", 500f, y, valuePaint)
            y += 28f
        }

        val footer = "Everything on this page is derived from your local, signed audit log. OnlyTrusted does not operate a backend; nothing on this page has been or will be transmitted off this device unless you tap Share and send it yourself."
        // Simple word-wrap at ~80 chars for footer.
        val lines = footer.chunked(90)
        var fy = 740f
        for (line in lines) {
            canvas.drawText(line, 50f, fy, footerPaint)
            fy += 14f
        }

        pdf.finishPage(page)
        val fname = "onlytrusted_weekly_${now / 1000}.pdf"
        val outDir = File(ctx.cacheDir, "digests").apply { mkdirs() }
        val outFile = File(outDir, fname)
        return runCatching {
            FileOutputStream(outFile).use { pdf.writeTo(it) }
            pdf.close()
            FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", outFile)
        }.getOrNull().also { if (it == null) pdf.close() }
    }
}
