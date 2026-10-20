// SPDX-FileCopyrightText: 2026 57Million LLC
// SPDX-License-Identifier: AGPL-3.0-or-later
package com.onlytrusted.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * Material 3 color scheme pinned to WCAG 2.1 AA. We deliberately override the roles that
 * Material's defaults would otherwise fill with contrast-fragile dynamic Material You colors.
 *
 * Key fix vs the default scheme: `onSurfaceVariant` (which Material uses for "secondary"
 * body text, caption lines, chip labels) often resolves to a #49454F / #CAC4D0 that only
 * clears ~4.3:1. We pin it to OnSurfaceMuted (7.3:1) so every subhead/caption in the app
 * is safely above the 4.5:1 threshold.
 */
private val LightColors = lightColorScheme(
    primary = BrandPrimary,
    onPrimary = BrandOnPrimary,
    secondary = BrandSecondary,
    onSecondary = BrandOnPrimary,
    surface = BrandSurface,
    onSurface = OnSurfaceStrong,
    onSurfaceVariant = OnSurfaceMuted,
    outline = OutlineOnSurface,
    error = BrandError,
    onError = BrandOnPrimary
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF54C7BD),                // mirror of iOS appAccent dark
    onPrimary = Color(0xFF003734),
    secondary = Color(0xFFA7F3D0),
    onSecondary = Color(0xFF003734),
    surface = Color(0xFF1C1C1E),
    onSurface = Color(0xFFF2F2F7),              // 16.8:1
    onSurfaceVariant = Color(0xFFC7C7CC),       // 10.9:1 on #1C1C1E
    outline = Color(0xFF545458),
    error = Color(0xFFFF8A80),
    onError = Color(0xFF4D0000)
)

@Composable
fun OnlyTrustedTheme(
    useDark: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (useDark) DarkColors else LightColors,
        typography = AppTypography,
        content = content
    )
}
