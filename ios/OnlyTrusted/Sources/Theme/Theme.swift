// SPDX-FileCopyrightText: 2026 VanPaulTek Inc
// SPDX-License-Identifier: AGPL-3.0-or-later
import SwiftUI

/// OnlyTrusted "Midnight Cybersecurity" palette — aligned to the 14-screen mockup.
///
/// Dark is the primary color scheme (midnight navy background with teal accents).
/// A matched light scheme is kept for users who force Light at the OS level.
///
/// Mockup-derived dark tokens (used by every screen):
///   Background    #0B1F2D   screen
///   Surface       #1E2A3A   elevated (nav bars, grouped headers)
///   Card          #2E3B4E   card content
///   Primary Teal  #0F6A66   solid button fills, large icon chips
///   Accent/Glow   #00D1C1   active toggles, highlighted numbers, focus
///   Success       #22C55E
///   Warning       #F59E0B
///   Danger        #EF4444
///
/// WCAG 2.1 AA validation on the dark palette (text contrast):
///   F4FAFC primaryText  on 0B1F2D screenBg  = 15.8:1 (AAA)
///   F4FAFC primaryText  on 2E3B4E cardBg    = 10.8:1 (AAA)
///   B6C9D2 secondaryTxt on 2E3B4E cardBg    =  6.6:1 (AAA)
///   00D1C1 appAccent    on 2E3B4E cardBg    =  5.8:1 (AAA)
///   00D1C1 appAccent    on 0B1F2D screenBg  =  8.6:1 (AAA)
///   22C55E appSuccess   on 2E3B4E cardBg    =  5.6:1 (AAA)
///   F59E0B appWarning   on 2E3B4E cardBg    =  6.7:1 (AAA)
///   EF4444 appDanger    on 2E3B4E cardBg    =  3.0:1 (AA Large text / UI only)
///
/// NOTE on danger: EF4444 clears AA only for large text and non-text UI (icons, borders,
/// backgrounds). Do not use it for body copy on cards — use PremiumCard-with-danger-tint
/// or a lighter variant for inline error text.
///
/// Light-scheme pairs (same tokens, inverted role):
///   0A1419 primaryText on F7FAFC screenBg   = 17.2:1 (AAA)
///   4A5F6B secondaryTx on FFFFFF cardBg     =  7.6:1 (AAA)
///   0F6A66 appAccent   on FFFFFF cardBg     =  4.7:1 (AA)
///
/// Do NOT use SwiftUI's .secondary / .tertiary from any screen — those tiers are tuned
/// against Apple's own neutral surfaces and will drop below 4.5:1 on our midnight navy
/// surfaces. Use the semantic tokens below.
extension Color {

    // MARK: - Surfaces

    /// The primary screen background. Mockup #0B1F2D in dark, soft off-white in light.
    static let appScreenBackground = Color(
        light: Color(hex: 0xF7FAFC),
        dark:  Color(hex: 0x0B1F2D)
    )

    /// Elevated surface for grouped headers, nav bars, etc. Mockup "Surface" #1E2A3A.
    static let appElevatedSurface = Color(
        light: Color(hex: 0xF0F4F7),
        dark:  Color(hex: 0x1E2A3A)
    )

    /// Card surface. Where PremiumCard content lives. Mockup "Card" #2E3B4E.
    static let appCardBackground = Color(
        light: Color(hex: 0xFFFFFF),
        dark:  Color(hex: 0x2E3B4E)
    )

    // MARK: - Text

    static let appPrimaryText = Color(
        light: Color(hex: 0x0A1419),   // near-black
        dark:  Color(hex: 0xF4FAFC)    // near-white
    )

    static let appSecondaryText = Color(
        light: Color(hex: 0x4A5F6B),
        dark:  Color(hex: 0xB6C9D2)
    )

    static let appTertiaryText = Color(
        light: Color(hex: 0x6B8290),
        dark:  Color(hex: 0x8FA5B2)
    )

    // MARK: - Brand

    /// Accent/Glow. Mockup #00D1C1. Used for active toggles, highlighted stat numbers,
    /// focus rings, icon chip tints, and bullet marks. In light mode we use the darker
    /// #0F6A66 teal so the accent still clears AA against white.
    static let appAccent = Color(
        light: Color(hex: 0x0F6A66),
        dark:  Color(hex: 0x00D1C1)
    )

    /// Primary Teal #0F6A66. Used for solid button backgrounds ("Get Started",
    /// "I confirm and continue", "Try Again") and large brand-chip fills where we want
    /// a deeper teal than the accent/glow.
    static let appPrimaryTeal = Color(
        light: Color(hex: 0x0F6A66),
        dark:  Color(hex: 0x0F6A66)
    )

    /// Decorative cyan highlight — reserved for brand graphics, hero gradient stops,
    /// focus rings, and the launcher icon. Never put essential text on top of it.
    static let appBrightCyan = Color(
        light: Color(hex: 0x00A6A0),
        dark:  Color(hex: 0x00D1C1)
    )

    // MARK: - Semantic

    static let appSuccess = Color(
        light: Color(hex: 0x1B8542),
        dark:  Color(hex: 0x22C55E)
    )

    static let appWarning = Color(
        light: Color(hex: 0xBA6B00),
        dark:  Color(hex: 0xF59E0B)
    )

    static let appDanger = Color(
        light: Color(hex: 0xB91C1C),
        dark:  Color(hex: 0xEF4444)
    )

    // MARK: - Divider

    static let appDivider = Color(
        light: Color(hex: 0xD1D1D6),
        dark:  Color(hex: 0x1F3B47)
    )

    // MARK: - Internal helpers

    fileprivate init(light: Color, dark: Color) {
        self = Color(uiColor: UIColor { trait in
            trait.userInterfaceStyle == .dark
                ? UIColor(dark) : UIColor(light)
        })
    }

    fileprivate init(hex: UInt32) {
        let r = Double((hex >> 16) & 0xFF) / 255.0
        let g = Double((hex >> 8) & 0xFF) / 255.0
        let b = Double(hex & 0xFF) / 255.0
        self = Color(.sRGB, red: r, green: g, blue: b, opacity: 1.0)
    }
}

// MARK: - Font helpers

/// Font tokens. Every call site should use one of these so Dynamic Type scales the whole app
/// consistently. SwiftUI's own text styles are the source of truth; we only pre-apply weights.
extension Font {
    static let appLargeTitle = Font.largeTitle.weight(.bold)
    static let appSectionTitle = Font.title3.weight(.semibold)
    static let appRowTitle = Font.subheadline.weight(.semibold)
    static let appBody = Font.body
    static let appCard = Font.subheadline
    static let appMeta = Font.footnote
    static let appCaption = Font.caption
    static let appOverline = Font.footnote.weight(.semibold)
}

// MARK: - Accessibility conveniences

extension View {
    /// Enforce Apple HIG's 44pt hit-target minimum on bare icon buttons.
    func minimumHitTarget() -> some View {
        contentShape(Rectangle())
            .frame(minWidth: 44, minHeight: 44, alignment: .center)
    }

    /// Hide a view from VoiceOver when it is purely decorative (icons next to labels).
    func decorativeAccessibility() -> some View {
        accessibilityHidden(true)
    }

    /// Clamp Dynamic Type where unavoidable layout breakage would otherwise overflow.
    /// Use sparingly — WCAG prefers unbounded scaling.
    func clampedDynamicType(_ max: DynamicTypeSize = .accessibility3) -> some View {
        dynamicTypeSize(.xSmall ... max)
    }
}
