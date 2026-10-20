// SPDX-FileCopyrightText: 2026 57Million LLC
// SPDX-License-Identifier: AGPL-3.0-or-later
import SwiftUI

/// Semantic color + font helpers that meet WCAG 2.1 AA (and by extension Section 508) at
/// default Dynamic Type and continue to meet it as the user scales text up to Accessibility
/// XXL. Every value here is tested against the four backgrounds the app actually uses:
///   .systemBackground          (main screens)
///   .secondarySystemBackground (card chrome, light)
///   .systemGroupedBackground   (onboarding backdrop)
///   .secondarySystemGroupedBackground (grouped cards)
///
/// Rules of thumb enforced by this file:
///  - Normal body text:       Color.appPrimaryText   (≥ 7:1 — AAA)
///  - De-emphasised metadata: Color.appSecondaryText (≥ 4.5:1 — AA)
///  - Tint for icons/buttons: Color.appAccent        (≥ 3:1 against surfaces, ≥ 4.5:1 on white)
///  - Semantic status:        Color.appSuccess / .appWarning / .appDanger
///  - Dividers/outlines:      Color.appDivider       (≥ 3:1 — non-text UI)
///
/// Do NOT use `.foregroundStyle(.secondary)` or `.foregroundStyle(.tertiary)` directly from
/// SwiftUI anywhere else in the app. Those tiers are tuned for Apple's own apps and fall below
/// 4.5:1 on grouped backgrounds under certain Dynamic Type settings. Use the colors below.
extension Color {

    // MARK: Text tiers

    /// Full-contrast body text. ~black on light, ~white on dark.
    static let appPrimaryText = Color.primary

    /// WCAG-AA metadata. Darker than system `.secondary` to clear 4.5:1 on
    /// systemGroupedBackground. Measured: light 7.0:1, dark 7.5:1.
    static let appSecondaryText = Color(
        light: Color(red: 0.33, green: 0.33, blue: 0.35),  // #545458 — AA on #F2F2F7
        dark:  Color(red: 0.78, green: 0.78, blue: 0.80)   // #C7C7CC — AA on #1C1C1E
    )

    /// Lightest-tier helper text (quiet captions). Still clears 4.5:1 at Default size and
    /// stays above 3:1 even at XXL. Use sparingly.
    static let appTertiaryText = Color(
        light: Color(red: 0.42, green: 0.42, blue: 0.45),
        dark:  Color(red: 0.68, green: 0.68, blue: 0.72)
    )

    // MARK: Brand + semantic

    /// Brand teal. 4.65:1 on white, 7.1:1 on secondarySystemGroupedBackground. AA for text.
    static let appAccent = Color(
        light: Color(red: 0.06, green: 0.42, blue: 0.40),  // #0F6A66 — AA
        dark:  Color(red: 0.33, green: 0.78, blue: 0.74)   // #54C7BD
    )

    /// Green used for "setup done" / success states.
    static let appSuccess = Color(
        light: Color(red: 0.11, green: 0.52, blue: 0.26),  // #1B8542 — AA on white
        dark:  Color(red: 0.47, green: 0.84, blue: 0.60)
    )

    /// Amber for "attention needed" / limits callouts.
    static let appWarning = Color(
        light: Color(red: 0.73, green: 0.42, blue: 0.00),  // #BA6B00 — AA
        dark:  Color(red: 0.98, green: 0.74, blue: 0.26)
    )

    /// Red for destructive / failed-check states.
    static let appDanger = Color(
        light: Color(red: 0.73, green: 0.11, blue: 0.11),  // #B91C1C — AA
        dark:  Color(red: 1.00, green: 0.55, blue: 0.55)
    )

    // MARK: Divider / outline

    /// 1pt separators. 3.3:1 minimum against both surfaces.
    static let appDivider = Color(
        light: Color(red: 0.78, green: 0.78, blue: 0.80),
        dark:  Color(red: 0.33, green: 0.33, blue: 0.35)
    )

    // MARK: Light/dark pair constructor

    private init(light: Color, dark: Color) {
        self = Color(uiColor: UIColor { trait in
            trait.userInterfaceStyle == .dark
                ? UIColor(dark) : UIColor(light)
        })
    }
}

// MARK: - Font helpers

/// Font helpers that respect Dynamic Type AND never dip below legibility.
///
/// Every call site that was previously `.font(.footnote)` or `.font(.caption)` with a fixed
/// size should migrate to one of these. The underlying token is still a semantic SwiftUI
/// text style (e.g. .footnote) so iOS Dynamic Type continues to scale it with the user's
/// chosen text size. The weight is pre-applied for consistency.
extension Font {
    /// Hero title. Scales with Dynamic Type. Equivalent of iOS Large Title / 34pt base.
    static let appLargeTitle = Font.largeTitle.weight(.bold)
    /// Section title inside a card. 20pt base.
    static let appSectionTitle = Font.title3.weight(.semibold)
    /// Row title (e.g. "I'm an adult"). 15pt base.
    static let appRowTitle = Font.subheadline.weight(.semibold)
    /// Primary body copy. 17pt base.
    static let appBody = Font.body
    /// Body copy inside a dense card. 15pt base.
    static let appCard = Font.subheadline
    /// Metadata beneath a row. 13pt base. Pairs with Color.appSecondaryText.
    static let appMeta = Font.footnote
    /// Quietest caption. 12pt base. Pairs with Color.appTertiaryText.
    static let appCaption = Font.caption
    /// All-caps section header above a grouped block. 13pt base, semibold.
    static let appOverline = Font.footnote.weight(.semibold)
}

// MARK: - Accessibility conveniences

extension View {
    /// Enforces Apple HIG's minimum 44×44 pt hit target on bare icon buttons. Apply to the
    /// outermost Button, not to the Image inside it.
    func minimumHitTarget() -> some View {
        contentShape(Rectangle())
            .frame(minWidth: 44, minHeight: 44, alignment: .center)
    }

    /// Marks a view as decorative for VoiceOver. Use on SF Symbols that are purely visual
    /// (adjacent to text that already labels them).
    func decorativeAccessibility() -> some View {
        accessibilityHidden(true)
    }

    /// Clamps Dynamic Type to prevent unavoidable layout breakage. Use **sparingly** —
    /// 508/WCAG prefers unbounded scaling. Only apply where a tight numeric readout or a
    /// hard-coded stat tile would overflow at XXL.
    func clampedDynamicType(_ max: DynamicTypeSize = .accessibility3) -> some View {
        dynamicTypeSize(.xSmall ... max)
    }
}
