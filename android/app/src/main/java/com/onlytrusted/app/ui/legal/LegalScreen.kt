// SPDX-FileCopyrightText: 2026 VanPaulTek Inc
// SPDX-License-Identifier: AGPL-3.0-or-later
package com.onlytrusted.app.ui.legal

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.onlytrusted.app.ui.theme.BrandCyanDark
import com.onlytrusted.app.ui.theme.MidnightBackground
import com.onlytrusted.app.ui.theme.MidnightElevated
import com.onlytrusted.app.ui.theme.PrimaryTextDark
import com.onlytrusted.app.ui.theme.SecondaryTextDark
import com.onlytrusted.app.ui.theme.WarningDark

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LegalScreen(
    initialSectionTitle: String? = null,
    onBack: () -> Unit = {}
) {
    val sections = LegalContent.sections
    val initial = sections.indexOfFirst { it.title.equals(initialSectionTitle, ignoreCase = true) }
        .takeIf { it >= 0 } ?: 0
    var selected by remember { mutableIntStateOf(initial) }
    var showFullDetails by remember { mutableStateOf(false) }

    val isHonestClaims = sections[selected].title.equals("Honest claims", ignoreCase = true)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MidnightBackground)
    ) {
        // Pinned top: back + title AND section chips — opaque, extends to status bar
        Column(
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
                .padding(horizontal = 18.dp)
                .padding(top = 10.dp, bottom = 10.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Header: back link on the left, centered title
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onBack() },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                        contentDescription = "Back",
                        tint = BrandCyanDark,
                        modifier = Modifier.size(22.dp)
                    )
                    Text(
                        "Settings",
                        color = BrandCyanDark,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.weight(1f))
                }
                Text(
                    "Legal & Privacy",
                    color = PrimaryTextDark,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Default
                )
            }

            // Horizontally scrollable section chips (pinned)
            Row(
                modifier = Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(vertical = 4.dp, horizontal = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                sections.forEachIndexed { index, section ->
                    LegalSectionChip(
                        title = titleCase(section.title),
                        selected = index == selected,
                        onClick = { selected = index }
                    )
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp)
                .padding(top = 10.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            if (isHonestClaims) {
                // Honest claims — special layout
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "Honest Claims",
                        color = PrimaryTextDark,
                        fontSize = 30.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "OnlyTrusted is a powerful tool, but there are important limits to what it can do. We believe in being clear and honest about them.",
                        color = SecondaryTextDark,
                        fontSize = 14.sp,
                        lineHeight = 20.sp
                    )
                }

                HonestClaimsCard()

                ReadFullDetailsCard(onClick = { showFullDetails = true })
            } else {
                // Any other section — title + formatted body (paragraphs / bullets / numbered)
                Text(
                    titleCase(sections[selected].title),
                    color = PrimaryTextDark,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold
                )
                FormattedLegalText(raw = sections[selected].body)
            }
        }
    }

    if (showFullDetails) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        val honestBody = LegalContent.sections.firstOrNull {
            it.title.equals("Honest claims", ignoreCase = true)
        }?.body ?: ""
        ModalBottomSheet(
            onDismissRequest = { showFullDetails = false },
            sheetState = sheetState,
            containerColor = MidnightElevated
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Honest claims — full details",
                        color = PrimaryTextDark,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = { showFullDetails = false }) {
                        Text("Done", color = BrandCyanDark)
                    }
                }
                Text(
                    honestBody,
                    color = PrimaryTextDark,
                    fontSize = 14.sp,
                    lineHeight = 20.sp
                )
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun LegalSectionChip(
    title: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val fill = if (selected) BrandCyanDark else Color.Transparent
    val textColor = if (selected) MidnightBackground else PrimaryTextDark
    val borderColor = if (selected) Color.Transparent else BrandCyanDark.copy(alpha = 0.35f)
    val glow = if (selected) BrandCyanDark.copy(alpha = 0.45f) else Color.Transparent

    Box(
        modifier = Modifier
            .shadow(
                elevation = if (selected) 10.dp else 0.dp,
                shape = RoundedCornerShape(14.dp),
                ambientColor = glow,
                spotColor = glow
            )
            .background(fill, RoundedCornerShape(14.dp))
            .border(BorderStroke(1.dp, borderColor), RoundedCornerShape(14.dp))
            .clickable { onClick() }
            .padding(horizontal = 18.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = title,
            color = textColor,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun HonestClaimsCard() {
    val bullets = listOf(
        "Cannot monitor other apps' microphone or camera use.",
        "Cannot see messages in other apps.",
        "Cannot block calls on all device types or carriers.",
        "Cannot provide a cloud-based spam database.",
        "Cannot guarantee 100% blocking of unknown contacts."
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                WarningDark.copy(alpha = 0.06f),
                RoundedCornerShape(18.dp)
            )
            .border(
                BorderStroke(1.dp, WarningDark.copy(alpha = 0.5f)),
                RoundedCornerShape(18.dp)
            )
            .padding(18.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.Top
    ) {
        // Amber circle with warning triangle + glow
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .shadow(
                    elevation = 12.dp,
                    shape = CircleShape,
                    ambientColor = WarningDark.copy(alpha = 0.6f),
                    spotColor = WarningDark.copy(alpha = 0.6f)
                )
                .size(56.dp)
                .background(WarningDark, CircleShape)
        ) {
            Icon(
                imageVector = Icons.Filled.Warning,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(26.dp)
            )
        }

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                "What this app cannot do",
                color = WarningDark,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
            Column {
                bullets.forEachIndexed { idx, text ->
                    Row(
                        modifier = Modifier.padding(vertical = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Box(
                            modifier = Modifier
                                .padding(top = 7.dp)
                                .size(7.dp)
                                .background(WarningDark, CircleShape)
                        )
                        Text(
                            text,
                            color = PrimaryTextDark,
                            fontSize = 14.sp,
                            lineHeight = 20.sp,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    if (idx < bullets.size - 1) {
                        HorizontalDivider(
                            color = WarningDark.copy(alpha = 0.2f),
                            thickness = 1.dp,
                            modifier = Modifier.padding(start = 22.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ReadFullDetailsCard(onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MidnightElevated, RoundedCornerShape(16.dp))
            .border(
                BorderStroke(1.dp, BrandCyanDark.copy(alpha = 0.3f)),
                RoundedCornerShape(16.dp)
            )
            .clickable { onClick() }
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Cyan doc icon chip
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(46.dp)
                .background(BrandCyanDark.copy(alpha = 0.14f), RoundedCornerShape(12.dp))
                .border(
                    BorderStroke(1.dp, BrandCyanDark.copy(alpha = 0.5f)),
                    RoundedCornerShape(12.dp)
                )
        ) {
            Icon(
                imageVector = Icons.Filled.Description,
                contentDescription = null,
                tint = BrandCyanDark,
                modifier = Modifier.size(20.dp)
            )
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                "Read the full details",
                color = PrimaryTextDark,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                "View complete platform limitations and disclosures.",
                color = SecondaryTextDark,
                fontSize = 12.sp,
                lineHeight = 16.sp
            )
        }
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = BrandCyanDark,
            modifier = Modifier.size(20.dp)
        )
    }
}

/** "Terms of use" → "Terms of Use"; "Honest claims" → "Honest Claims". */
private fun titleCase(s: String): String {
    val smallWords = setOf("of", "and", "the", "for", "to")
    return s.split(" ").mapIndexed { idx, word ->
        if (idx > 0 && smallWords.contains(word.lowercase())) {
            word.lowercase()
        } else {
            word.replaceFirstChar { it.uppercase() }
        }
    }.joinToString(" ")
}

// Renders paragraphs, bullet lists ("• …"), and numbered lists ("1. …") with hanging
// indentation so wrapped lines align with the content, not the bullet/number. Mirrors
// the iOS `FormattedLegalText` so bodies read the same on both platforms.
@Composable
private fun FormattedLegalText(raw: String) {
    val blocks = remember(raw) { parseLegalBlocks(raw) }
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        blocks.forEach { block ->
            when (block) {
                is LegalBlock.Paragraph ->
                    Text(
                        block.text,
                        color = PrimaryTextDark,
                        fontSize = 14.sp,
                        lineHeight = 20.sp
                    )
                is LegalBlock.Bullets ->
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        block.items.forEach { item ->
                            BulletRow(marker = "•", text = item, markerWidth = 16.dp)
                        }
                    }
                is LegalBlock.Numbered ->
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        block.items.forEach { (num, text) ->
                            BulletRow(marker = "$num.", text = text, markerWidth = 24.dp)
                        }
                    }
            }
        }
    }
}

@Composable
private fun BulletRow(marker: String, text: String, markerWidth: androidx.compose.ui.unit.Dp) {
    Row(verticalAlignment = Alignment.Top) {
        Text(
            marker,
            color = BrandCyanDark,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            lineHeight = 20.sp,
            modifier = Modifier.width(markerWidth)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text,
            color = PrimaryTextDark,
            fontSize = 14.sp,
            lineHeight = 20.sp,
            modifier = Modifier.weight(1f)
        )
    }
}

private sealed class LegalBlock {
    data class Paragraph(val text: String) : LegalBlock()
    data class Bullets(val items: List<String>) : LegalBlock()
    data class Numbered(val items: List<Pair<String, String>>) : LegalBlock()
}

private fun parseLegalBlocks(raw: String): List<LegalBlock> {
    val lines = raw.split("\n").map { it.trim() }
    val numberedPrefix = Regex("""^(\d+)\.\s(.+)$""")

    fun kind(line: String): String = when {
        line.startsWith("• ") -> "bullet"
        numberedPrefix.matches(line) -> "numbered"
        else -> "paragraph"
    }

    val result = mutableListOf<LegalBlock>()
    val buffer = mutableListOf<String>()
    var bufferKind = "paragraph"

    fun flush() {
        if (buffer.isEmpty()) return
        when (bufferKind) {
            "bullet" ->
                result.add(LegalBlock.Bullets(buffer.map { it.removePrefix("• ") }))
            "numbered" -> {
                val items = buffer.mapNotNull { line ->
                    numberedPrefix.matchEntire(line)?.let { m ->
                        m.groupValues[1] to m.groupValues[2]
                    }
                }
                result.add(LegalBlock.Numbered(items))
            }
            else ->
                result.add(LegalBlock.Paragraph(buffer.joinToString("\n")))
        }
        buffer.clear()
    }

    for (line in lines) {
        if (line.isEmpty()) { flush(); continue }
        val k = kind(line)
        if (buffer.isNotEmpty() && k != bufferKind) flush()
        bufferKind = k
        buffer.add(line)
    }
    flush()
    return result
}
