// SPDX-FileCopyrightText: 2026 VanPaulTek Inc
// SPDX-License-Identifier: AGPL-3.0-or-later
package com.onlytrusted.app.ui.events

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.automirrored.filled.PhoneCallback
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.PhonePaused
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.onlytrusted.app.OnlyTrustedApp
import com.onlytrusted.app.data.db.entities.SecurityEvent
import com.onlytrusted.app.ui.theme.BrandAccentDark
import com.onlytrusted.app.ui.theme.BrandCyanDark
import com.onlytrusted.app.ui.theme.DangerDark
import com.onlytrusted.app.ui.theme.MidnightBackground
import com.onlytrusted.app.ui.theme.MidnightElevated
import com.onlytrusted.app.ui.theme.PrimaryTextDark
import com.onlytrusted.app.ui.theme.SecondaryTextDark
import com.onlytrusted.app.ui.theme.TertiaryTextDark
import com.onlytrusted.app.ui.theme.WarningDark
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

// MARK: - Filters

private enum class EventFilter(val label: String) {
    All("All"),
    Calls("Calls"),
    Texts("Texts"),
    Blocked("Blocked");

    fun matches(event: SecurityEvent): Boolean {
        val t = event.type.lowercase(Locale.US)
        return when (this) {
            All -> true
            Calls -> t.contains("call")
            Texts -> t.contains("sms") || t.contains("text")
            Blocked -> EventCategory.of(event.type) == EventCategory.Blocked
        }
    }
}

// MARK: - Semantic category

private enum class EventCategory {
    Blocked, Allowed, Silenced, System;

    companion object {
        fun of(type: String): EventCategory {
            val t = type.lowercase(Locale.US)
            return when {
                t.contains("silenced") -> Silenced
                t.contains("block") || t.contains("flag") || t == "wipe" ||
                    t.contains("panic") || t.contains("untrusted") -> Blocked
                t.contains("allow") -> Allowed
                else -> System
            }
        }
    }
}

// MARK: - Day grouping

private enum class ActivityGroup(val title: String) {
    Today("Today"),
    Yesterday("Yesterday"),
    ThisWeek("This week"),
    Earlier("Earlier");

    companion object {
        fun bucket(timestampMillis: Long): ActivityGroup {
            val todayStart = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis
            val rowStart = Calendar.getInstance().apply {
                timeInMillis = timestampMillis
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis
            val dayDiff = ((todayStart - rowStart) / (24L * 60L * 60L * 1000L)).toInt()
            return when {
                dayDiff <= 0 -> Today
                dayDiff == 1 -> Yesterday
                dayDiff < 7 -> ThisWeek
                else -> Earlier
            }
        }
    }
}

// MARK: - Screen

@Composable
fun EventsScreen() {
    val context = LocalContext.current
    val app = context.applicationContext as OnlyTrustedApp
    val vm: EventsViewModel = viewModel(factory = viewModelFactory {
        initializer { EventsViewModel(app) }
    })
    val events by vm.events.collectAsStateWithLifecycle()
    var filter by remember { mutableStateOf(EventFilter.All) }
    var chainOk by remember { mutableStateOf<Boolean?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        chainOk = app.repository.verifyChain()
    }

    val filtered = remember(events, filter) { events.filter { filter.matches(it) } }
    val grouped = remember(filtered) { groupByDay(filtered) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MidnightBackground)
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
            HeaderRow(onReload = {
                scope.launch { chainOk = app.repository.verifyChain() }
            })
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp)
                .padding(top = 4.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            FilterChipRow(selected = filter, onSelect = { filter = it })

            if (chainOk == false) {
                ChainTamperedCard()
            }

            if (filtered.isEmpty()) {
                EmptyState(filter = filter)
            } else {
                grouped.forEach { (group, rows) ->
                    DaySection(title = group.title, rows = rows)
                }
            }
        }
    }
}

private fun groupByDay(events: List<SecurityEvent>): List<Pair<ActivityGroup, List<SecurityEvent>>> {
    if (events.isEmpty()) return emptyList()
    val bucketed = events.groupBy { ActivityGroup.bucket(it.timestamp) }
    return ActivityGroup.entries.mapNotNull { group ->
        val rows = bucketed[group]?.sortedByDescending { it.timestamp }
        if (rows.isNullOrEmpty()) null else group to rows
    }
}

// MARK: - Header

@Composable
private fun HeaderRow(onReload: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "Activity Log",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = PrimaryTextDark,
            modifier = Modifier.weight(1f)
        )
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(MidnightElevated)
                .clickable { onReload() },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.Refresh,
                contentDescription = "Reload event log",
                tint = BrandCyanDark,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

// MARK: - Filter chips

@Composable
private fun FilterChipRow(selected: EventFilter, onSelect: (EventFilter) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        EventFilter.entries.forEach { f ->
            FilterChip(
                label = f.label,
                isSelected = selected == f,
                onTap = { onSelect(f) }
            )
        }
    }
}

@Composable
private fun FilterChip(label: String, isSelected: Boolean, onTap: () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    val textColor = if (isSelected) MidnightBackground else PrimaryTextDark

    val baseModifier = Modifier
        .then(
            if (isSelected)
                Modifier.shadow(10.dp, shape, clip = false, ambientColor = BrandCyanDark, spotColor = BrandCyanDark)
            else Modifier
        )
        .clip(shape)
        .then(
            if (isSelected)
                Modifier.background(BrandCyanDark)
            else
                Modifier.border(1.dp, BrandCyanDark.copy(alpha = 0.35f), shape)
        )
        .clickable { onTap() }
        .padding(horizontal = 22.dp, vertical = 12.dp)

    Box(modifier = baseModifier) {
        Text(
            text = label,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            color = textColor
        )
    }
}

// MARK: - Chain tampered alert

@Composable
private fun ChainTamperedCard() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MidnightElevated)
            .border(1.dp, DangerDark.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(DangerDark.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.Warning,
                contentDescription = null,
                tint = DangerDark,
                modifier = Modifier.size(22.dp)
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                "Audit chain tampered",
                color = PrimaryTextDark,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                "The event log hash chain is broken. An event may have been edited.",
                color = SecondaryTextDark,
                fontSize = 12.sp
            )
        }
    }
}

// MARK: - Empty state

@Composable
private fun EmptyState(filter: EventFilter) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Spacer(Modifier.height(32.dp))
        Box(
            modifier = Modifier
                .size(88.dp)
                .clip(CircleShape)
                .background(BrandAccentDark.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.History,
                contentDescription = null,
                tint = BrandAccentDark,
                modifier = Modifier.size(36.dp)
            )
        }
        Text(
            text = "No events to show",
            color = PrimaryTextDark,
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            text = if (filter == EventFilter.All)
                "Blocked calls, flagged SMS, panic locks, and vault edits will appear here as they happen."
            else
                "No events match the \"${filter.label}\" filter yet.",
            color = SecondaryTextDark,
            fontSize = 14.sp,
            modifier = Modifier.padding(horizontal = 20.dp)
        )
    }
}

// MARK: - Day section (large header + standalone row cards)

@Composable
private fun DaySection(title: String, rows: List<SecurityEvent>) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = title,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = PrimaryTextDark
        )
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            rows.forEach { event ->
                EventRow(event = event)
            }
        }
    }
}

// MARK: - Event row (standalone surface, solid-filled icon chip, cyan timestamp)

@Composable
private fun EventRow(event: SecurityEvent) {
    val category = EventCategory.of(event.type)
    val tint = tintFor(event.type, category)
    val icon = iconFor(event.type, category)
    val timeFormatter = remember { SimpleDateFormat("h:mm a", Locale.getDefault()) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MidnightElevated)
            .border(1.dp, BrandCyanDark.copy(alpha = 0.1f), RoundedCornerShape(16.dp))
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .shadow(10.dp, RoundedCornerShape(12.dp), clip = false, ambientColor = tint, spotColor = tint)
                .clip(RoundedCornerShape(12.dp))
                .background(tint),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(20.dp)
            )
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = humanLabel(event.type),
                color = PrimaryTextDark,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold
            )
            val subtitle = event.detail?.takeIf { it.isNotBlank() }
                ?: event.packageName?.takeIf { it.isNotBlank() }
                ?: "Unknown number"
            Text(
                text = subtitle,
                color = SecondaryTextDark,
                fontSize = 13.sp,
                maxLines = 1
            )
        }

        Spacer(Modifier.width(8.dp))

        Text(
            text = timeFormatter.format(Date(event.timestamp)),
            color = BrandCyanDark,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

private val AllowedTextBlue = Color(0xFF3890F2)

private fun tintFor(type: String, category: EventCategory): Color {
    val t = type.lowercase(Locale.US)
    if (t.contains("silenced")) return WarningDark
    if (category == EventCategory.Blocked) return DangerDark
    if (category == EventCategory.Allowed) {
        if (t.contains("sms") || t.contains("text")) return AllowedTextBlue
        return BrandAccentDark
    }
    return BrandAccentDark
}

private fun iconFor(type: String, category: EventCategory): ImageVector = when (type) {
    SecurityEvent.TYPE_CALL_BLOCKED -> Icons.Filled.Block
    SecurityEvent.TYPE_CALL_ALLOWED -> Icons.AutoMirrored.Filled.PhoneCallback
    SecurityEvent.TYPE_SMS_FLAGGED -> Icons.AutoMirrored.Filled.Message
    SecurityEvent.TYPE_UNTRUSTED_CALL -> Icons.Filled.Block
    SecurityEvent.TYPE_UNTRUSTED_SMS -> Icons.Filled.Block
    SecurityEvent.TYPE_PANIC_LOCK -> Icons.Filled.Lock
    SecurityEvent.TYPE_POCKET_LOCK -> Icons.Filled.Lock
    SecurityEvent.TYPE_SCREEN_LOCKED -> Icons.Filled.Lock
    SecurityEvent.TYPE_SCREEN_UNLOCKED -> Icons.Filled.Shield
    SecurityEvent.TYPE_MIC -> Icons.Filled.NotificationsOff
    SecurityEvent.TYPE_CAMERA -> Icons.Filled.NotificationsOff
    SecurityEvent.TYPE_SENSOR_PRIVACY_ON -> Icons.Filled.Shield
    SecurityEvent.TYPE_SENSOR_PRIVACY_OFF -> Icons.Filled.Shield
    else -> when (category) {
        EventCategory.Blocked -> Icons.Filled.Block
        EventCategory.Allowed -> Icons.Filled.CheckCircle
        EventCategory.Silenced -> Icons.Filled.PhonePaused
        EventCategory.System -> Icons.Filled.Info
    }
}

private fun humanLabel(type: String): String = when (type) {
    SecurityEvent.TYPE_MIC -> "Microphone used"
    SecurityEvent.TYPE_CAMERA -> "Camera used"
    SecurityEvent.TYPE_CALL_BLOCKED -> "Blocked call"
    SecurityEvent.TYPE_CALL_ALLOWED -> "Allowed call"
    SecurityEvent.TYPE_SMS_FLAGGED -> "Blocked text"
    SecurityEvent.TYPE_UNTRUSTED_CALL -> "Blocked call"
    SecurityEvent.TYPE_UNTRUSTED_SMS -> "Blocked text"
    SecurityEvent.TYPE_POCKET_LOCK -> "Pocket detected"
    SecurityEvent.TYPE_PANIC_LOCK -> "Panic lock engaged"
    SecurityEvent.TYPE_SCREEN_LOCKED -> "Screen locked"
    SecurityEvent.TYPE_SCREEN_UNLOCKED -> "Screen unlocked"
    SecurityEvent.TYPE_SENSOR_PRIVACY_ON -> "Sensors paused"
    SecurityEvent.TYPE_SENSOR_PRIVACY_OFF -> "Sensors resumed"
    else -> type.replace('_', ' ').lowercase(Locale.US)
        .replaceFirstChar { it.uppercase(Locale.US) }
}
