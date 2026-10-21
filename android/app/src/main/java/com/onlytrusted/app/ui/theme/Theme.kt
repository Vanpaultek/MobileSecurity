// SPDX-FileCopyrightText: 2026 57Million LLC
// SPDX-License-Identifier: AGPL-3.0-or-later
package com.onlytrusted.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Local Color-literal constants (needed because darkColorScheme/lightColorScheme want
// androidx.compose.ui.graphics.Color values).
private val Color003734 = Color(0xFF003734)
private val Color002B29 = Color(0xFF002B29)
private val Color003C38 = Color(0xFF003C38)
private val ColorB2F2EC = Color(0xFFB2F2EC)
private val Color600000 = Color(0xFF600000)

/**
 * Dark-first OnlyTrusted theme. The midnight palette is the primary identity; a matched light
 * scheme is provided for completeness but by default we force dark through `useDark = true`.
 */
private val DarkColors = darkColorScheme(
    primary = BrandAccentDark,
    onPrimary = Color003734,
    primaryContainer = Color003C38,
    onPrimaryContainer = ColorB2F2EC,

    secondary = BrandCyanDark,
    onSecondary = Color002B29,

    background = MidnightBackground,
    onBackground = PrimaryTextDark,

    surface = MidnightCard,
    onSurface = PrimaryTextDark,
    surfaceContainer = MidnightElevated,
    surfaceContainerHigh = MidnightElevated,
    surfaceContainerHighest = MidnightElevated,
    onSurfaceVariant = SecondaryTextDark,

    outline = OutlineDark,
    outlineVariant = OutlineDark,

    error = DangerDark,
    onError = Color600000,
)

private val LightColors = lightColorScheme(
    primary = BrandAccentLight,
    onPrimary = Color.White,
    primaryContainer = ColorB2F2EC,
    onPrimaryContainer = Color003C38,

    secondary = BrandCyanLight,
    onSecondary = Color.White,

    background = LightBackground,
    onBackground = PrimaryTextLight,

    surface = LightCard,
    onSurface = PrimaryTextLight,
    surfaceContainer = LightElevated,
    surfaceContainerHigh = LightElevated,
    surfaceContainerHighest = LightElevated,
    onSurfaceVariant = SecondaryTextLight,

    outline = OutlineLight,
    outlineVariant = OutlineLight,

    error = DangerLight,
    onError = Color.White,
)

@Composable
fun OnlyTrustedTheme(
    // Dark is the default so the OnlyTrusted brand lands first. Pass `useDark = false` to
    // opt into the matched light scheme (e.g. for a future user setting).
    useDark: Boolean = true,
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (useDark) DarkColors else LightColors,
        typography = AppTypography,
        content = content
    )
}
