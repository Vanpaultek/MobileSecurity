package com.mobilesecurity.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightColors = lightColorScheme(
    primary = BrandPrimary,
    onPrimary = BrandOnPrimary,
    secondary = BrandSecondary,
    surface = BrandSurface,
    error = BrandError
)

private val DarkColors = darkColorScheme(
    primary = BrandPrimary,
    onPrimary = BrandOnPrimary,
    secondary = BrandSecondary,
    error = BrandError
)

@Composable
fun MobileSecurityTheme(
    useDark: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (useDark) DarkColors else LightColors,
        typography = AppTypography,
        content = content
    )
}
