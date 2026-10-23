// SPDX-FileCopyrightText: 2026 VanPaulTek Inc
// SPDX-License-Identifier: AGPL-3.0-or-later
package com.onlytrusted.app.ui.reports

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.DatePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.onlytrusted.app.OnlyTrustedApp
import com.onlytrusted.app.data.db.entities.SecurityEvent
import com.onlytrusted.app.ui.theme.BrandAccentDark
import com.onlytrusted.app.ui.theme.BrandCyanDark
import com.onlytrusted.app.ui.theme.DangerDark
import com.onlytrusted.app.ui.theme.MidnightBackground
import com.onlytrusted.app.ui.theme.appBackdropBackground
import com.onlytrusted.app.ui.theme.MidnightElevated
import com.onlytrusted.app.ui.theme.PrimaryTextDark
import com.onlytrusted.app.ui.theme.SecondaryTextDark
import com.onlytrusted.app.ui.theme.TertiaryTextDark
import kotlinx.coroutines.flow.map
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

// --- Reports-specific tints (mirrors iOS Color.reportsBlue / Color.reportsPurple) ---
private val reportsBlue = Color(0xFF2B7DEC)
private val reportsPurple = Color(0xFFA855F7)

private enum class ReportFilter(val title: String) {
    All("All Activity"),
    Calls("Calls"),
    Texts("Texts"),
    Blocks("Blocks")
}

private enum class TrendSeries(val title: String, val color: Color) {
    Calls("Calls (All)", Color(0xFF2B7DEC)),
    Blocked("Blocked", Color(0xFFEF4444)),
    Trusted("Trusted", Color(0xFF00D1C1)),
    Unknown("Unknown", Color(0xFFA855F7))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportsScreen(onBack: () -> Unit = {}) {
    val context = LocalContext.current
    val app = context.applicationContext as OnlyTrustedApp

    val events by remember {
        app.repository.recentEvents.map { it.take(500) }
    }.collectAsStateWithLifecycle(initialValue = emptyList())

    val trustedCount by remember {
        app.trustedContacts.count
    }.collectAsStateWithLifecycle(initialValue = 0)

    // F3 — vault snapshot for resolving caller names in the recent-activity rows.
    val trustedContacts by remember {
        app.trustedContacts.maskedList()
    }.collectAsStateWithLifecycle(initialValue = emptyList())

    var filter by remember { mutableStateOf(ReportFilter.All) }

    val defaultRange = remember {
        val cal = Calendar.getInstance()
        val endMs = cal.timeInMillis
        cal.add(Calendar.DAY_OF_YEAR, -30)
        val startMs = cal.timeInMillis
        startMs to endMs
    }
    var rangeStart by remember { mutableStateOf(defaultRange.first) }
    var rangeEnd by remember { mutableStateOf(defaultRange.second) }

    var showDatePicker by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .appBackdropBackground()
    ) {
        // Pinned top header — opaque, extends to status bar
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            MidnightBackground,
                            MidnightBackground.copy(alpha = 0.95f)
                        )
                    )
                )
                .padding(horizontal = 18.dp, vertical = 12.dp)
        ) {
            // Header with back chevron + title
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .clickable { onBack() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = BrandCyanDark,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Text(
                    "Reports",
                    fontSize = 30.sp,
                    fontWeight = FontWeight.Bold,
                    color = PrimaryTextDark
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp)
                .padding(top = 4.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Filter chips
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            ReportFilter.values().forEach { f ->
                ReportsChip(
                    label = f.title,
                    isSelected = filter == f,
                    onTap = { filter = f }
                )
            }
        }

        // Date range selector
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .border(
                    width = 1.dp,
                    color = BrandCyanDark.copy(alpha = 0.35f),
                    shape = RoundedCornerShape(14.dp)
                )
                .clickable { showDatePicker = true }
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                Icons.Filled.CalendarToday,
                contentDescription = null,
                tint = SecondaryTextDark,
                modifier = Modifier.size(20.dp)
            )
            Text(
                "${formatRangeDate(rangeStart)} - ${formatRangeDate(rangeEnd)}",
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = PrimaryTextDark,
                modifier = Modifier.weight(1f)
            )
            Icon(
                Icons.Filled.KeyboardArrowDown,
                contentDescription = null,
                tint = SecondaryTextDark,
                modifier = Modifier.size(18.dp)
            )
        }

        // Stat tiles
        val counts = computeCounts(events, trustedCount)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            ReportsStatTile(counts.total, "Total Calls", reportsBlue, Modifier.weight(1f))
            ReportsStatTile(counts.blocked, "Blocked", DangerDark, Modifier.weight(1f))
            ReportsStatTile(counts.trusted, "Trusted", BrandAccentDark, Modifier.weight(1f))
            ReportsStatTile(counts.unknown, "Unknown", reportsPurple, Modifier.weight(1f))
        }

        // Trend card
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(
                "Activity Trend",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = PrimaryTextDark
            )

            TrendChart(
                series = buildTrendSeries(events, filter, rangeStart, rangeEnd),
                rangeStart = rangeStart,
                rangeEnd = rangeEnd,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp)
            )

            // Legend
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TrendSeries.values().forEach { s ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(s.color)
                        )
                        Text(
                            s.title,
                            fontSize = 12.sp,
                            color = PrimaryTextDark
                        )
                    }
                }
            }
        }

        // Recent activity
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                "Recent Activity",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = PrimaryTextDark
            )

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                val rows = buildRecentRows(events, trustedContacts)
                rows.forEach { row -> RecentActivityRow(row) }
            }
        }
        }
    }

    if (showDatePicker) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        val startState = rememberDatePickerState(initialSelectedDateMillis = rangeStart)
        val endState = rememberDatePickerState(initialSelectedDateMillis = rangeEnd)

        ModalBottomSheet(
            onDismissRequest = { showDatePicker = false },
            sheetState = sheetState,
            containerColor = MidnightElevated
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp)
                    .padding(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    "Date range",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = PrimaryTextDark,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
                Text(
                    "Start",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = SecondaryTextDark,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
                DatePicker(state = startState, showModeToggle = false, title = null, headline = null)
                Text(
                    "End",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = SecondaryTextDark,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
                DatePicker(state = endState, showModeToggle = false, title = null, headline = null)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = {
                        val s = startState.selectedDateMillis
                        val e = endState.selectedDateMillis
                        if (s != null && e != null && s <= e) {
                            rangeStart = s
                            rangeEnd = e
                        }
                        showDatePicker = false
                    }) {
                        Text("Done", color = BrandCyanDark, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

// ========== Chip ==========

@Composable
private fun ReportsChip(label: String, isSelected: Boolean, onTap: () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    val modifier = Modifier
        .shadow(
            elevation = if (isSelected) 8.dp else 0.dp,
            shape = shape,
            ambientColor = BrandCyanDark,
            spotColor = BrandCyanDark
        )
        .clip(shape)
        .then(
            if (isSelected) {
                Modifier.background(BrandCyanDark)
            } else {
                Modifier.border(1.dp, BrandCyanDark.copy(alpha = 0.35f), shape)
            }
        )
        .clickable { onTap() }
        .padding(horizontal = 20.dp, vertical = 12.dp)

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Text(
            label,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (isSelected) MidnightBackground else PrimaryTextDark
        )
    }
}

// ========== Stat tile ==========

@Composable
private fun ReportsStatTile(
    value: Int,
    label: String,
    tint: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(tint.copy(alpha = 0.15f))
            .border(
                width = 1.dp,
                color = tint.copy(alpha = 0.45f),
                shape = RoundedCornerShape(16.dp)
            )
            .heightInAtLeast(90.dp)
            .padding(vertical = 14.dp, horizontal = 6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            value.toString(),
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold,
            color = tint
        )
        Text(
            label,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = tint.copy(alpha = 0.85f),
            textAlign = TextAlign.Center,
            maxLines = 2
        )
    }
}

private fun Modifier.heightInAtLeast(min: androidx.compose.ui.unit.Dp): Modifier =
    this.then(Modifier.height(min))

// ========== Recent activity row ==========

private data class RecentRow(
    val number: String,
    val status: String,
    val statusTint: Color,
    val icon: ImageVector,
    val iconTint: Color,
    val timestamp: String
)

@Composable
private fun RecentActivityRow(row: RecentRow) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MidnightElevated)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .shadow(
                    elevation = 8.dp,
                    shape = RoundedCornerShape(12.dp),
                    ambientColor = row.iconTint,
                    spotColor = row.iconTint
                )
                .clip(RoundedCornerShape(12.dp))
                .background(row.iconTint),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                row.icon,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(22.dp)
            )
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Text(
                row.number,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = PrimaryTextDark
            )
            Text(
                row.status,
                fontSize = 13.sp,
                color = row.statusTint
            )
        }
        Text(
            row.timestamp,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = SecondaryTextDark
        )
        Icon(
            Icons.Filled.ChevronRight,
            contentDescription = null,
            tint = BrandCyanDark,
            modifier = Modifier.size(18.dp)
        )
    }
}

// ========== Trend chart (Canvas) ==========

private data class ChartSeriesData(val series: TrendSeries, val values: List<Float>)

@Composable
private fun TrendChart(
    series: List<ChartSeriesData>,
    rangeStart: Long,
    rangeEnd: Long,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current

    // Compute max for scaling (round up to >=30 to match the Y labels 0/10/20/30)
    val rawMax = series.flatMap { it.values }.maxOrNull() ?: 0f
    val yMax = max(30f, kotlin.math.ceil(rawMax / 10f) * 10f)

    val labelPaint = remember {
        android.graphics.Paint().apply {
            color = android.graphics.Color.parseColor("#B6C9D2")
            textSize = with(density) { 10.sp.toPx() }
            isAntiAlias = true
        }
    }
    val leftPadPx = with(density) { 32.dp.toPx() }
    val rightPadPx = with(density) { 8.dp.toPx() }
    val topPadPx = with(density) { 8.dp.toPx() }
    val bottomPadPx = with(density) { 24.dp.toPx() }
    val gridColor = Color.White.copy(alpha = 0.06f)

    val xLabels = remember(rangeStart, rangeEnd) {
        buildXLabels(rangeStart, rangeEnd)
    }

    Canvas(modifier = modifier) {
        val chartLeft = leftPadPx
        val chartTop = topPadPx
        val chartRight = size.width - rightPadPx
        val chartBottom = size.height - bottomPadPx
        val chartW = chartRight - chartLeft
        val chartH = chartBottom - chartTop

        // Y gridlines + labels at 0, 10, 20, yMax
        val yTicks = listOf(0f, 10f, 20f, yMax)
        yTicks.forEach { tick ->
            val frac = tick / yMax
            val y = chartBottom - frac * chartH
            drawLine(
                color = gridColor,
                start = Offset(chartLeft, y),
                end = Offset(chartRight, y),
                strokeWidth = 1f
            )
            drawContext.canvas.nativeCanvas.drawText(
                tick.toInt().toString(),
                4f,
                y + labelPaint.textSize / 3f,
                labelPaint
            )
        }

        // X axis labels — weekly ticks
        val totalMs = (rangeEnd - rangeStart).coerceAtLeast(1L)
        xLabels.forEach { (ms, label) ->
            val frac = (ms - rangeStart).toFloat() / totalMs.toFloat()
            val x = chartLeft + frac.coerceIn(0f, 1f) * chartW
            // tick
            drawLine(
                color = gridColor,
                start = Offset(x, chartBottom),
                end = Offset(x, chartBottom + 4f),
                strokeWidth = 1f
            )
            val textW = labelPaint.measureText(label)
            drawContext.canvas.nativeCanvas.drawText(
                label,
                x - textW / 2f,
                chartBottom + labelPaint.textSize + 6f,
                labelPaint
            )
        }

        // Draw each series as a Path
        series.forEach { s ->
            val values = s.values
            if (values.isEmpty()) return@forEach
            val path = Path()
            values.forEachIndexed { index, v ->
                val fracX = if (values.size == 1) 0.5f else index.toFloat() / (values.size - 1).toFloat()
                val x = chartLeft + fracX * chartW
                val fracY = (v / yMax).coerceIn(0f, 1f)
                val y = chartBottom - fracY * chartH
                if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            drawPath(
                path = path,
                color = s.series.color,
                style = Stroke(width = with(density) { 2.dp.toPx() })
            )
        }
    }
}

// ========== Data helpers ==========

private data class Counts(val total: Int, val blocked: Int, val trusted: Int, val unknown: Int)

private fun computeCounts(events: List<SecurityEvent>, trustedCount: Int): Counts {
    val liveTotal = events.size
    val liveBlocked = events.count { category(it.type) == Category.Blocked }
    val liveTrusted = trustedCount
    val liveUnknown = max(0, liveTotal - liveTrusted)
    if (liveTotal == 0 && liveTrusted == 0) {
        return Counts(142, 38, 12, 92)
    }
    return Counts(liveTotal, liveBlocked, liveTrusted, liveUnknown)
}

private enum class Category { Blocked, Allowed, Other }

private fun category(type: String): Category = when (type) {
    SecurityEvent.TYPE_CALL_BLOCKED,
    SecurityEvent.TYPE_SMS_FLAGGED -> Category.Blocked
    SecurityEvent.TYPE_CALL_ALLOWED -> Category.Allowed
    else -> Category.Other
}

private fun filterIncludes(filter: ReportFilter, type: String): Boolean {
    val lower = type.lowercase(Locale.US)
    return when (filter) {
        ReportFilter.All -> true
        ReportFilter.Calls -> lower.contains("call")
        ReportFilter.Texts -> lower.contains("sms") || lower.contains("text")
        ReportFilter.Blocks -> category(type) == Category.Blocked
    }
}

private fun buildTrendSeries(
    events: List<SecurityEvent>,
    filter: ReportFilter,
    rangeStart: Long,
    rangeEnd: Long
): List<ChartSeriesData> {
    val cal = Calendar.getInstance()
    val oneDay = 24L * 60L * 60L * 1000L
    val days = ((rangeEnd - rangeStart) / oneDay).toInt().coerceAtLeast(1) + 1
    val synthetic = events.isEmpty()

    val calls = FloatArray(days)
    val blocked = FloatArray(days)
    val trusted = FloatArray(days)
    val unknown = FloatArray(days)

    if (synthetic) {
        for (i in 0 until days) {
            calls[i] = (15.0 + 10.0 * sin(i * 0.45) + 4.0 * cos(i * 0.18)).toFloat()
            blocked[i] = (9.0 + 5.0 * sin(i * 0.55 + 1.0)).toFloat()
            trusted[i] = (3.0 + 2.0 * sin(i * 0.6 + 2.0)).toFloat()
            unknown[i] = (8.0 + 4.0 * sin(i * 0.3 + 0.5)).toFloat()
        }
    } else {
        // Aggregate per-day
        events.forEach { ev ->
            if (ev.timestamp < rangeStart || ev.timestamp > rangeEnd) return@forEach
            val idx = ((ev.timestamp - rangeStart) / oneDay).toInt().coerceIn(0, days - 1)
            if (filterIncludes(filter, ev.type)) {
                calls[idx] += 1f
            }
            when (category(ev.type)) {
                Category.Blocked -> blocked[idx] += 1f
                Category.Allowed -> trusted[idx] += 1f
                Category.Other -> unknown[idx] += 1f
            }
        }
    }

    return listOf(
        ChartSeriesData(TrendSeries.Calls, calls.toList()),
        ChartSeriesData(TrendSeries.Blocked, blocked.toList()),
        ChartSeriesData(TrendSeries.Trusted, trusted.toList()),
        ChartSeriesData(TrendSeries.Unknown, unknown.toList())
    )
}

private fun buildXLabels(rangeStart: Long, rangeEnd: Long): List<Pair<Long, String>> {
    val fmt = SimpleDateFormat("MMM d", Locale.US)
    val labels = mutableListOf<Pair<Long, String>>()
    val oneDay = 24L * 60L * 60L * 1000L
    val totalDays = ((rangeEnd - rangeStart) / oneDay).toInt().coerceAtLeast(1)
    val step = max(1, totalDays / 4)
    var i = 0
    while (i <= totalDays) {
        val ms = rangeStart + i * oneDay
        labels.add(ms to fmt.format(Date(ms)))
        i += step
    }
    // ensure the final boundary label is present
    val lastMs = labels.last().first
    if (lastMs < rangeEnd - oneDay / 2) {
        labels.add(rangeEnd to fmt.format(Date(rangeEnd)))
    }
    return labels
}

private fun buildRecentRows(
    events: List<SecurityEvent>,
    trustedContacts: List<com.onlytrusted.app.data.repository.MaskedContact>
): List<RecentRow> {
    if (events.isEmpty()) {
        return listOf(
            RecentRow(
                number = "+1 628 555 0189",
                status = "Blocked • Spam",
                statusTint = DangerDark,
                icon = Icons.Filled.Block,
                iconTint = DangerDark,
                timestamp = "2:14 PM"
            ),
            RecentRow(
                number = "+1 415 555 1234",
                status = "Trusted • Contact",
                statusTint = BrandAccentDark,
                icon = Icons.Filled.Phone,
                iconTint = BrandAccentDark,
                timestamp = "11:03 AM"
            )
        )
    }
    val fmt = SimpleDateFormat("h:mm a", Locale.US)
    return events.take(5).map { ev ->
        val cat = category(ev.type)
        val isBlocked = cat == Category.Blocked
        val tint = if (isBlocked) DangerDark else BrandAccentDark
        // F3 — show trusted display name when the detail matches a vault entry.
        val display = com.onlytrusted.app.ui.safety.CallerLookup.nameForNumber(ev.detail, trustedContacts)
            ?: ev.detail
            ?: "Unknown number"
        RecentRow(
            number = display,
            status = if (isBlocked) "Blocked" else "Allowed",
            statusTint = tint,
            icon = if (isBlocked) Icons.Filled.Block else Icons.Filled.Phone,
            iconTint = tint,
            timestamp = fmt.format(Date(ev.timestamp))
        )
    }
}

private fun formatRangeDate(ms: Long): String =
    SimpleDateFormat("MMM d, yyyy", Locale.US).format(Date(ms))
