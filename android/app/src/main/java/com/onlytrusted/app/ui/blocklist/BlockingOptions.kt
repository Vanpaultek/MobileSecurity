// SPDX-FileCopyrightText: 2026 57Million LLC
// SPDX-License-Identifier: AGPL-3.0-or-later
package com.onlytrusted.app.ui.blocklist

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Message
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.onlytrusted.app.ui.theme.BrandAccentDark
import com.onlytrusted.app.ui.theme.PrimaryTextDark
import com.onlytrusted.app.ui.theme.SecondaryTextDark
import com.onlytrusted.app.ui.theme.TertiaryTextDark

enum class CallHandlingMode(val id: String, val title: String, val detail: String) {
    Silence("silence", "Silence (recommended)", "Calls are silenced and logged."),
    Voicemail("voicemail", "Send to voicemail", "Send unknown calls to voicemail."),
    Block("block", "Block completely", "Reject calls from unknown numbers.");

    companion object {
        fun fromId(id: String?): CallHandlingMode = entries.firstOrNull { it.id == id } ?: Silence
    }
}

object BlockingPreferences {
    private const val PREFS = "blocking_options"
    private const val KEY_CALLERS = "callers_v1"
    private const val KEY_TEXTS = "texts_v1"
    private const val KEY_SPAM = "spam_v1"
    private const val KEY_CALL_HANDLING = "call_handling_v1"

    fun blockUnknownCallers(ctx: Context): Boolean = prefs(ctx).getBoolean(KEY_CALLERS, true)
    fun setBlockUnknownCallers(ctx: Context, v: Boolean) { prefs(ctx).edit().putBoolean(KEY_CALLERS, v).apply() }

    fun silenceUnknownTexts(ctx: Context): Boolean = prefs(ctx).getBoolean(KEY_TEXTS, true)
    fun setSilenceUnknownTexts(ctx: Context, v: Boolean) { prefs(ctx).edit().putBoolean(KEY_TEXTS, v).apply() }

    fun spamProtection(ctx: Context): Boolean = prefs(ctx).getBoolean(KEY_SPAM, true)
    fun setSpamProtection(ctx: Context, v: Boolean) { prefs(ctx).edit().putBoolean(KEY_SPAM, v).apply() }

    fun callHandling(ctx: Context): CallHandlingMode =
        CallHandlingMode.fromId(prefs(ctx).getString(KEY_CALL_HANDLING, null))
    fun setCallHandling(ctx: Context, mode: CallHandlingMode) {
        prefs(ctx).edit().putString(KEY_CALL_HANDLING, mode.id).apply()
    }

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}

@Composable
fun BlockingOptionsSection(modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    var blockCallers by remember { mutableStateOf(BlockingPreferences.blockUnknownCallers(ctx)) }
    var silenceTexts by remember { mutableStateOf(BlockingPreferences.silenceUnknownTexts(ctx)) }
    var spam by remember { mutableStateOf(BlockingPreferences.spamProtection(ctx)) }
    var callMode by remember { mutableStateOf(BlockingPreferences.callHandling(ctx)) }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SectionHeader(icon = Icons.Filled.Shield, title = "Blocking & Unknown")
                OptionToggleRow(
                    icon = Icons.Filled.Phone,
                    title = "Block unknown callers",
                    detail = "Silence calls from numbers not in your trusted list.",
                    checked = blockCallers
                ) {
                    blockCallers = it
                    BlockingPreferences.setBlockUnknownCallers(ctx, it)
                }
                HorizontalDivider(Modifier.padding(start = 48.dp))
                OptionToggleRow(
                    icon = Icons.Filled.Message,
                    title = "Silence unknown texts",
                    detail = "Move unknown messages to a separate folder.",
                    checked = silenceTexts
                ) {
                    silenceTexts = it
                    BlockingPreferences.setSilenceUnknownTexts(ctx, it)
                }
                HorizontalDivider(Modifier.padding(start = 48.dp))
                OptionToggleRow(
                    icon = Icons.Filled.Warning,
                    title = "Spam protection",
                    detail = "Block common spam patterns on this device (no cloud).",
                    checked = spam
                ) {
                    spam = it
                    BlockingPreferences.setSpamProtection(ctx, it)
                }
            }
        }
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SectionHeader(icon = Icons.Filled.Phone, title = "Call handling")
                CallHandlingMode.entries.forEachIndexed { idx, mode ->
                    CallHandlingRow(
                        mode = mode,
                        selected = callMode == mode,
                        onSelected = {
                            callMode = mode
                            BlockingPreferences.setCallHandling(ctx, mode)
                        }
                    )
                    if (idx < CallHandlingMode.entries.lastIndex) {
                        HorizontalDivider(Modifier.padding(start = 48.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(icon: ImageVector, title: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(36.dp)
                .background(BrandAccentDark.copy(alpha = 0.14f), RoundedCornerShape(10.dp))
        ) {
            Icon(icon, contentDescription = null, tint = BrandAccentDark)
        }
        Spacer(Modifier.width(12.dp))
        Text(
            title,
            color = PrimaryTextDark,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun OptionToggleRow(
    icon: ImageVector,
    title: String,
    detail: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(36.dp)
                .background(BrandAccentDark.copy(alpha = 0.14f), RoundedCornerShape(10.dp))
        ) {
            Icon(icon, contentDescription = null, tint = BrandAccentDark)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                title,
                color = PrimaryTextDark,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(detail, color = SecondaryTextDark, style = MaterialTheme.typography.bodySmall)
        }
        Spacer(Modifier.width(8.dp))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun CallHandlingRow(
    mode: CallHandlingMode,
    selected: Boolean,
    onSelected: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onSelected() }
            .padding(vertical = 6.dp)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(36.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .border(
                        width = 2.dp,
                        color = if (selected) BrandAccentDark else TertiaryTextDark,
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (selected) {
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .background(BrandAccentDark, CircleShape)
                    )
                }
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                mode.title,
                color = PrimaryTextDark,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(mode.detail, color = SecondaryTextDark, style = MaterialTheme.typography.bodySmall)
        }
    }
}
