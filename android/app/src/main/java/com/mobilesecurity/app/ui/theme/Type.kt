// SPDX-FileCopyrightText: 2026 57Million LLC
// SPDX-License-Identifier: AGPL-3.0-or-later
package com.mobilesecurity.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.mobilesecurity.app.R

/**
 * Inter (SIL Open Font License 1.1 — bundled in res/font/) picked specifically because its
 * glyph widths, x-height and letter-spacing track SF Pro closely. We can't ship SF Pro on
 * Android — Apple's EULA forbids it — but this is the closest legal match.
 *
 * The type scale below is tuned to Apple HIG's Dynamic Type base values at Default size:
 * largeTitle 34, title1 28, title2 22, title3 20, headline 17/semibold, body 17, callout 16,
 * subheadline 15, footnote 13, caption1 12, caption2 11. Material 3's 15 slots don't line up
 * one-to-one with Apple's 11, so the mapping below picks the closest Material slot for each
 * Apple size the app actually uses.
 */
val Inter: FontFamily = FontFamily(
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_medium, FontWeight.Medium),
    Font(R.font.inter_semibold, FontWeight.SemiBold),
    Font(R.font.inter_bold, FontWeight.Bold)
)

val AppTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = Inter, fontWeight = FontWeight.Bold,
        fontSize = 34.sp, lineHeight = 41.sp, letterSpacing = 0.37.sp
    ),
    displayMedium = TextStyle(
        fontFamily = Inter, fontWeight = FontWeight.Bold,
        fontSize = 28.sp, lineHeight = 34.sp, letterSpacing = 0.36.sp
    ),
    headlineLarge = TextStyle(
        fontFamily = Inter, fontWeight = FontWeight.Bold,
        fontSize = 22.sp, lineHeight = 28.sp, letterSpacing = 0.35.sp
    ),
    headlineMedium = TextStyle(
        fontFamily = Inter, fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp, lineHeight = 25.sp, letterSpacing = 0.38.sp
    ),
    titleLarge = TextStyle(
        fontFamily = Inter, fontWeight = FontWeight.SemiBold,
        fontSize = 17.sp, lineHeight = 22.sp, letterSpacing = (-0.41).sp
    ),
    titleMedium = TextStyle(
        fontFamily = Inter, fontWeight = FontWeight.SemiBold,
        fontSize = 17.sp, lineHeight = 22.sp, letterSpacing = (-0.41).sp
    ),
    bodyLarge = TextStyle(
        fontFamily = Inter, fontWeight = FontWeight.Normal,
        fontSize = 17.sp, lineHeight = 22.sp, letterSpacing = (-0.41).sp
    ),
    bodyMedium = TextStyle(
        fontFamily = Inter, fontWeight = FontWeight.Normal,
        fontSize = 16.sp, lineHeight = 21.sp, letterSpacing = (-0.32).sp
    ),
    bodySmall = TextStyle(
        fontFamily = Inter, fontWeight = FontWeight.Normal,
        fontSize = 15.sp, lineHeight = 20.sp, letterSpacing = (-0.24).sp
    ),
    labelLarge = TextStyle(
        fontFamily = Inter, fontWeight = FontWeight.Medium,
        fontSize = 13.sp, lineHeight = 18.sp, letterSpacing = (-0.08).sp
    ),
    labelMedium = TextStyle(
        fontFamily = Inter, fontWeight = FontWeight.Normal,
        fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.sp
    ),
    labelSmall = TextStyle(
        fontFamily = Inter, fontWeight = FontWeight.Normal,
        fontSize = 11.sp, lineHeight = 13.sp, letterSpacing = 0.07.sp
    )
)
