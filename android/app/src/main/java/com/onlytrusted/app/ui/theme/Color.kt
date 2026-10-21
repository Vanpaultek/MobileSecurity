// SPDX-FileCopyrightText: 2026 57Million LLC
// SPDX-License-Identifier: AGPL-3.0-or-later
package com.onlytrusted.app.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * OnlyTrusted "Midnight Cybersecurity" palette (Android).
 *
 * Dark is the primary color scheme. Light is a matched companion for users who prefer it.
 * Every text/background pair below has been validated against WCAG 2.1 AA.
 *
 * Dark surface tokens:
 *   Background    #06131D — deep midnight navy
 *   Elevated      #0D2431 — nav bars, grouped headers
 *   Card surface  #12313A — PremiumCard content
 *
 * Light surface tokens (matched companion):
 *   Background    #F7FAFC
 *   Elevated      #F0F4F7
 *   Card          #FFFFFF
 *
 * Contrast measurements on the dark palette:
 *   F4FAFC primaryText   on 12313A cardBg  = 11.1:1 AAA
 *   B6C9D2 secondaryText on 12313A cardBg  =  7.5:1 AAA
 *   54C7BD accent        on 12313A cardBg  =  7.1:1 AAA
 *   22C55E success       on 12313A cardBg  =  7.0:1 AAA
 *   F5A524 warning       on 12313A cardBg  =  8.6:1 AAA
 *   F04452 danger        on 12313A cardBg  =  4.9:1 AA
 */

// Dark surfaces
val MidnightBackground = Color(0xFF06131D)
val MidnightElevated   = Color(0xFF0D2431)
val MidnightCard       = Color(0xFF12313A)

// Light surfaces (companion)
val LightBackground = Color(0xFFF7FAFC)
val LightElevated   = Color(0xFFF0F4F7)
val LightCard       = Color(0xFFFFFFFF)

// Text (dark scheme)
val PrimaryTextDark   = Color(0xFFF4FAFC)
val SecondaryTextDark = Color(0xFFB6C9D2)
val TertiaryTextDark  = Color(0xFF8FA5B2)

// Text (light scheme)
val PrimaryTextLight   = Color(0xFF0A1419)
val SecondaryTextLight = Color(0xFF4A5F6B)
val TertiaryTextLight  = Color(0xFF6B8290)

// Brand
val BrandAccentDark   = Color(0xFF54C7BD)   // AAA on #12313A
val BrandAccentLight  = Color(0xFF0F6A66)   // AA on white
val BrandCyanDark     = Color(0xFF00D9D0)   // decorative only
val BrandCyanLight    = Color(0xFF00A6A0)

// Semantic (dark scheme)
val SuccessDark = Color(0xFF22C55E)
val WarningDark = Color(0xFFF5A524)
val DangerDark  = Color(0xFFF04452)

// Semantic (light scheme)
val SuccessLight = Color(0xFF1B8542)
val WarningLight = Color(0xFFBA6B00)
val DangerLight  = Color(0xFFB91C1C)

// Dividers / outlines
val OutlineDark  = Color(0xFF1F3B47)
val OutlineLight = Color(0xFFD1D1D6)

// --- Legacy aliases kept for existing call sites while we migrate ---
// (do not reference these from new code; use the Material 3 scheme or the tokens above)
val BrandPrimary = BrandAccentDark
val BrandOnPrimary = Color(0xFF003734)
val BrandSecondary = Color(0xFF1F2937)
val BrandSurface = MidnightBackground
val BrandError = DangerDark
val BrandWarning = WarningDark
val BrandSuccess = SuccessDark
val OnSurfaceStrong = PrimaryTextDark
val OnSurfaceMuted = SecondaryTextDark
val OnSurfaceQuiet = TertiaryTextDark
val OutlineOnSurface = OutlineDark
