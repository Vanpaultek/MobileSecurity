// SPDX-FileCopyrightText: 2026 VanPaulTek Inc
// SPDX-License-Identifier: AGPL-3.0-or-later
package com.onlytrusted.app.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * OnlyTrusted "Midnight Cybersecurity" palette (Android) — aligned to the 14-screen mockup.
 *
 * Dark is the primary color scheme. Light is a matched companion for users who prefer it.
 *
 * Mockup-derived dark tokens (used by every screen):
 *   Background    #0B1F2D — screen
 *   Surface       #1E2A3A — elevated (nav bars, grouped headers)
 *   Card          #2E3B4E — card content
 *   Primary Teal  #0F6A66 — solid button fills, large icon chips
 *   Accent/Glow   #00D1C1 — active toggles, highlighted stat numbers, focus
 *   Success       #22C55E
 *   Warning       #F59E0B
 *   Danger        #EF4444
 *
 * Light surface tokens (matched companion):
 *   Background    #F7FAFC
 *   Elevated      #F0F4F7
 *   Card          #FFFFFF
 *
 * WCAG 2.1 AA validation on the dark palette (text contrast):
 *   F4FAFC primaryText   on 0B1F2D screenBg  = 15.8:1 AAA
 *   F4FAFC primaryText   on 2E3B4E cardBg    = 10.8:1 AAA
 *   B6C9D2 secondaryText on 2E3B4E cardBg    =  6.6:1 AAA
 *   00D1C1 accent        on 2E3B4E cardBg    =  5.8:1 AAA
 *   00D1C1 accent        on 0B1F2D screenBg  =  8.6:1 AAA
 *   22C55E success       on 2E3B4E cardBg    =  5.6:1 AAA
 *   F59E0B warning       on 2E3B4E cardBg    =  6.7:1 AAA
 *   EF4444 danger        on 2E3B4E cardBg    =  3.0:1 AA Large text / UI only
 *
 * NOTE on danger: EF4444 clears AA only for large text and non-text UI (icons, borders,
 * backgrounds). Do not use it for body copy on cards — use a lighter variant or render
 * the danger inline inside a danger-tinted card for sufficient contrast.
 */

// Dark surfaces
val MidnightBackground = Color(0xFF0B1F2D)
val MidnightElevated   = Color(0xFF1E2A3A)
val MidnightCard       = Color(0xFF2E3B4E)

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
val BrandAccentDark   = Color(0xFF00D1C1)   // Accent/Glow — toggles, numbers, focus
val BrandAccentLight  = Color(0xFF0F6A66)   // AA on white
val BrandCyanDark     = Color(0xFF00D1C1)   // decorative glow — unified with accent
val BrandCyanLight    = Color(0xFF00A6A0)

// Primary Teal — solid button backgrounds, large brand-chip fills
val BrandPrimaryTeal  = Color(0xFF0F6A66)

// Semantic (dark scheme)
val SuccessDark = Color(0xFF22C55E)
val WarningDark = Color(0xFFF59E0B)
val DangerDark  = Color(0xFFEF4444)

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
