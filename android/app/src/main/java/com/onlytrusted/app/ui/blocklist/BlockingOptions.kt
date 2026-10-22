// SPDX-FileCopyrightText: 2026 VanPaulTek Inc
// SPDX-License-Identifier: AGPL-3.0-or-later
package com.onlytrusted.app.ui.blocklist

import android.content.Context
import android.content.Intent
import android.provider.Settings
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
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Voicemail
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import com.onlytrusted.app.ui.theme.BrandAccentDark
import com.onlytrusted.app.ui.theme.DangerDark
import com.onlytrusted.app.ui.theme.MidnightBackground
import com.onlytrusted.app.ui.theme.MidnightElevated
import com.onlytrusted.app.ui.theme.PrimaryTextDark
import com.onlytrusted.app.ui.theme.SecondaryTextDark
import com.onlytrusted.app.ui.theme.TertiaryTextDark

enum class CallHandlingMode(
    val id: String,
    val title: String,
    val detail: String,
    val icon: ImageVector,
    val tint: Color
) {
    Silence(
        "silence",
        "Silence (recommended)",
        "Calls are silenced and logged.",
        Icons.Filled.Phone,
        Color(0xFF3890F2)
    ),
    Voicemail(
        "voicemail",
        "Send to voicemail",
        "Send unknown calls to voicemail.",
        Icons.Filled.Voicemail,
        TertiaryTextDark
    ),
    Block(
        "block",
        "Block completely",
        "Reject calls from unknown numbers.",
        Icons.Filled.Block,
        DangerDark
    );

    companion object {
        fun fromId(id: String?): CallHandlingMode = entries.firstOrNull { it.id == id } ?: Silence
    }
}

enum class TextHandlingMode(
    val id: String,
    val title: String,
    val detail: String,
    val icon: ImageVector,
    val tint: Color
) {
    SeparateFolder(
        "separateFolder",
        "Move to separate folder (recommended)",
        "Keep unknown messages out of your main inbox.",
        Icons.AutoMirrored.Filled.Message,
        Color(0xFFA855F7)
    ),
    DeleteAutomatically(
        "deleteAutomatically",
        "Delete automatically",
        "Remove unknown messages without notification.",
        Icons.Filled.Delete,
        DangerDark
    ),
    SilenceButKeep(
        "silenceButKeep",
        "Silence but keep",
        "Keep in a separate folder without alerts.",
        Icons.Filled.VisibilityOff,
        TertiaryTextDark
    );

    companion object {
        fun fromId(id: String?): TextHandlingMode =
            entries.firstOrNull { it.id == id } ?: SeparateFolder
    }
}

object BlockingPreferences {
    private const val PREFS = "onlytrusted_prefs"
    private const val KEY_CALLERS = "blocking.block_unknown_callers"
    private const val KEY_TEXTS = "blocking.silence_unknown_texts"
    private const val KEY_SPAM = "blocking.spam_protection"
    private const val KEY_CALL_HANDLING = "blocking.call_handling_mode"
    private const val KEY_TEXT_HANDLING = "blocking.text_handling_mode"

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

    fun textHandling(ctx: Context): TextHandlingMode =
        TextHandlingMode.fromId(prefs(ctx).getString(KEY_TEXT_HANDLING, null))
    fun setTextHandling(ctx: Context, mode: TextHandlingMode) {
        prefs(ctx).edit().putString(KEY_TEXT_HANDLING, mode.id).apply()
    }

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}

// Palette extras used only on this screen (matches iOS blocking colors)
private val BlockingOrange = Color(0xFFF59E0B)
private val BlockingPurple = Color(0xFFA855F7)
private val BlockingAmber = Color(0xFFEAB308)

@Composable
fun BlockingOptionsSection(modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    var blockCallers by remember { mutableStateOf(BlockingPreferences.blockUnknownCallers(ctx)) }
    var silenceTexts by remember { mutableStateOf(BlockingPreferences.silenceUnknownTexts(ctx)) }
    var spam by remember { mutableStateOf(BlockingPreferences.spamProtection(ctx)) }
    var callMode by remember { mutableStateOf(BlockingPreferences.callHandling(ctx)) }
    var textMode by remember { mutableStateOf(BlockingPreferences.textHandling(ctx)) }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(20.dp)) {
        // Three stacked toggle cards (each in its own rounded surface)
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            ToggleCard(
                icon = Icons.Filled.Shield,
                tint = BlockingOrange,
                title = "Block unknown callers",
                detail = "Silence calls from numbers not in your trusted list.",
                checked = blockCallers,
                onChange = {
                    blockCallers = it
                    BlockingPreferences.setBlockUnknownCallers(ctx, it)
                }
            )
            ToggleCard(
                icon = Icons.AutoMirrored.Filled.Message,
                tint = BlockingPurple,
                title = "Silence unknown texts",
                detail = "Move unknown messages to a separate folder.",
                checked = silenceTexts,
                onChange = {
                    silenceTexts = it
                    BlockingPreferences.setSilenceUnknownTexts(ctx, it)
                }
            )
            ToggleCard(
                icon = Icons.Filled.PrivacyTip,
                tint = BlockingAmber,
                title = "Spam protection",
                detail = "Block common spam patterns on this device (no cloud).",
                checked = spam,
                onChange = {
                    spam = it
                    BlockingPreferences.setSpamProtection(ctx, it)
                }
            )
        }

        // Call handling radio section
        RadioGroupSection(
            title = "Call handling",
            subtitle = "Choose how to handle calls from unknown numbers.",
            options = CallHandlingMode.entries.map { mode ->
                RadioRowSpec(
                    id = mode.id,
                    icon = mode.icon,
                    tint = mode.tint,
                    title = mode.title,
                    detail = mode.detail,
                    onTap = {
                        callMode = mode
                        BlockingPreferences.setCallHandling(ctx, mode)
                    }
                )
            },
            selectedId = callMode.id,
            footnote = "Android applies these preferences via the default caller-ID & spam app. Carrier-level blocks are outside any app's control."
        )

        // Text handling radio section
        RadioGroupSection(
            title = "Text handling",
            subtitle = "Choose how to handle text messages from unknown numbers.",
            options = TextHandlingMode.entries.map { mode ->
                RadioRowSpec(
                    id = mode.id,
                    icon = mode.icon,
                    tint = mode.tint,
                    title = mode.title,
                    detail = mode.detail,
                    onTap = {
                        textMode = mode
                        BlockingPreferences.setTextHandling(ctx, mode)
                    }
                )
            },
            selectedId = textMode.id,
            footnote = null
        )

        MutedExtensionRow(
            label = "Default caller-ID app needed",
            value = "Set in Android Settings",
            onClick = {
                val intent = Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                runCatching { ctx.startActivity(intent) }
            }
        )
    }
}

// MARK: - Toggle card

@Composable
private fun ToggleCard(
    icon: ImageVector,
    tint: Color,
    title: String,
    detail: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MidnightElevated)
            .border(1.dp, BrandAccentDark.copy(alpha = 0.12f), RoundedCornerShape(16.dp))
            .padding(14.dp)
    ) {
        IconChip(icon = icon, tint = tint)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                title,
                color = PrimaryTextDark,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(3.dp))
            Text(
                detail,
                color = SecondaryTextDark,
                fontSize = 12.sp
            )
        }
        Spacer(Modifier.width(8.dp))
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = BrandAccentDark,
                checkedBorderColor = BrandAccentDark,
                uncheckedThumbColor = SecondaryTextDark,
                uncheckedTrackColor = MidnightBackground,
                uncheckedBorderColor = TertiaryTextDark.copy(alpha = 0.5f)
            )
        )
    }
}

// MARK: - Radio group section

private data class RadioRowSpec(
    val id: String,
    val icon: ImageVector,
    val tint: Color,
    val title: String,
    val detail: String,
    val onTap: () -> Unit
)

@Composable
private fun RadioGroupSection(
    title: String,
    subtitle: String,
    options: List<RadioRowSpec>,
    selectedId: String,
    footnote: String?
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            title,
            color = PrimaryTextDark,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            subtitle,
            color = SecondaryTextDark,
            fontSize = 13.sp
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(MidnightElevated)
                .border(1.dp, BrandAccentDark.copy(alpha = 0.12f), RoundedCornerShape(16.dp))
                .padding(vertical = 4.dp)
        ) {
            options.forEachIndexed { idx, spec ->
                RadioRow(spec = spec, isSelected = spec.id == selectedId)
                if (idx < options.lastIndex) {
                    HorizontalDivider(
                        modifier = Modifier.padding(start = 76.dp),
                        color = TertiaryTextDark.copy(alpha = 0.15f)
                    )
                }
            }
        }
        if (footnote != null) {
            Text(
                footnote,
                color = SecondaryTextDark,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
    }
}

@Composable
private fun RadioRow(spec: RadioRowSpec, isSelected: Boolean) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { spec.onTap() }
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        // Radio circle
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(36.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .then(
                        if (isSelected)
                            Modifier.shadow(6.dp, CircleShape, clip = false, ambientColor = BrandAccentDark, spotColor = BrandAccentDark)
                        else Modifier
                    )
                    .border(
                        width = 2.dp,
                        color = if (isSelected) BrandAccentDark else TertiaryTextDark.copy(alpha = 0.5f),
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (isSelected) {
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .background(BrandAccentDark, CircleShape)
                    )
                }
            }
        }
        Spacer(Modifier.width(14.dp))
        IconChip(icon = spec.icon, tint = spec.tint)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                spec.title,
                color = PrimaryTextDark,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(3.dp))
            Text(
                spec.detail,
                color = SecondaryTextDark,
                fontSize = 12.sp
            )
        }
    }
}

// MARK: - Icon chip (gradient tint with drop shadow, white icon)

@Composable
private fun IconChip(icon: ImageVector, tint: Color) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(44.dp)
            .shadow(8.dp, RoundedCornerShape(11.dp), clip = false, ambientColor = tint, spotColor = tint)
            .clip(RoundedCornerShape(11.dp))
            .background(
                Brush.verticalGradient(
                    colors = listOf(tint, tint.copy(alpha = 0.75f))
                )
            )
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(20.dp)
        )
    }
}

// MARK: - Muted "needs system setup" row

@Composable
private fun MutedExtensionRow(label: String, value: String, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 10.dp)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(36.dp)
                .background(TertiaryTextDark.copy(alpha = 0.14f), RoundedCornerShape(10.dp))
        ) {
            Icon(Icons.Filled.Settings, contentDescription = null, tint = TertiaryTextDark)
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                label,
                color = SecondaryTextDark,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(value, color = TertiaryTextDark, fontSize = 12.sp)
        }
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = TertiaryTextDark
        )
    }
}
