# OnlyTrusted — Design Brief

**For:** external designers / design agencies evaluating or redesigning the OnlyTrusted UI.
**App owner:** 57Million LLC. Contact: `reddy@vanpaultek.com`.
**Platforms:** native Android (Jetpack Compose + Material 3) and native iOS (SwiftUI).
**Status:** functional product, both platforms building green, awaiting a professional visual pass.
**Repository:** https://github.com/Vanpaultek/OnlyTrusted (public, AGPL-3.0).

---

## 1. What this app is, in one paragraph

OnlyTrusted is a privacy-first mobile app for people who want tight control over who can reach them on their phone. The core use case: an owner (and their immediate family) curate a short "trusted" contact list. The app silences, blocks, or flags everything coming from outside that list, keeps a tamper-evident local audit log, and never sends any data off the device. It is explicitly not a general-purpose caller-ID / spam-blocker (no cloud reputation database); it is a serious tool for people who are being harassed, stalked, or otherwise harmed by unsolicited contact.

### Not the app

- Not a social app. Not a messaging app. Not a dating app.
- Not an entertainment app. Not a game.
- Not an advertising-supported app. Zero ads, zero analytics, zero cloud.
- Not a stalkerware tool. Explicitly refuses to be installed on someone else's phone without their knowledge.

### Audience

Adults who need a quiet phone and strong privacy guarantees — small business owners, journalists, domestic-abuse survivors, serious family-first users. Not a mass-market consumer product.

---

## 2. Brand constraints (what cannot change)

| Attribute | Value |
|---|---|
| **Product name** | `OnlyTrusted` (one word, two capitals; never `Only Trusted` or `OT`) |
| **Legal owner** | 57Million LLC |
| **Support email** | reddy@vanpaultek.com |
| **License** | GNU AGPL-3.0 (visible footer in About + README) |
| **Primary color token** | `appAccent` — teal, currently `#0F6A66` light / `#54C7BD` dark. WCAG AA 4.65:1 on white. Open to shade adjustment but it must remain a teal/green family and clear 4.5:1 for text on primary surfaces. |
| **Semantic colors** | `appSuccess` (green), `appWarning` (amber), `appDanger` (red). All WCAG AA-measured. |
| **Font (iOS)** | Apple SF Pro (system default). Must stay. |
| **Font (Android)** | Inter 4.x (SIL OFL licensed, bundled in `android/app/src/main/res/font/`). Open to replacement as long as the new font is SIL OFL / Apache 2.0 licensed and matches SF Pro's rhythm within reason. |
| **Icon set** | Apple SF Symbols (iOS) + Material Symbols (Android). Open to a custom glyph pack if designer provides it as an SVG set. |

**Do not propose:** dark-pattern UX, "upgrade to Pro" cross-sell screens, onboarding carousels that gate features behind account creation, splash-screen videos, Lottie animations in the critical path. These will be rejected.

---

## 3. Honesty principles (must survive any redesign)

These are copy and layout decisions, not optional:

1. **Every screen that implies a protective feature must disclose what the platform does NOT allow.** The Dashboard has a "Honest limits" card. The Legal screen has a tab called "Honest claims." The Onboarding screen gates entry on the user ticking "I have read the honest limits." Any redesign must retain visible, non-collapsible disclosure of these limits.
2. **No feature may suggest it does more than it does.** For example: iOS cannot monitor other apps' mic or camera use. We must not show a card that implies we do.
3. **No persuasive design to maximise consent or engagement.** No countdown timers, no "Only 2 slots left," no FOMO. Toggles start OFF where consent matters.
4. **Legal + policy copy is shipped in the binary**, not fetched from a server. Redesign may re-typeset but cannot change the words without owner approval.

---

## 4. Platform minima

| Platform | Minimum | Target | Why |
|---|---|---|---|
| iOS | 17.0 | 18.x | SwiftUI concurrency features we rely on |
| Android | API 29 (Android 10) | API 36 (Android 15) | CallScreeningService + SQLCipher + biometric parity |
| Dynamic Type / text scaling | Must respect user's system setting up to XXXL | Both | WCAG 2.1 AA requirement |
| Color contrast | ≥ 4.5:1 for body text, ≥ 3:1 for large text + icons | Both | WCAG 2.1 AA |
| Hit targets | ≥ 44pt iOS, ≥ 48dp Android | Both | Apple HIG + Material 3 |

---

## 5. Screen inventory

Every screen the user can reach, in approximate reading order. For each: purpose, what data it shows, what the user can do, states to design for, accessibility notes.

### 5.1 Onboarding (first-launch only)

- **Purpose.** Serious-user consent gate. Explain honestly what the app is and is not before any protection is enabled.
- **Shows.**
  - Hero: brand icon + "OnlyTrusted" + one-sentence subtitle.
  - "What this app does" card — 4 feature bullets.
  - "Honest limits" card — 4 platform-limitation bullets.
  - 3 consent toggles with inline sub-labels: adult, device-owner, understood-limits.
  - "I confirm and continue" CTA.
  - Legal footnote + 3 tappable links (Privacy policy / Terms of use / Honest claims) opening a sheet with the LegalScreen content pre-selected.
- **States.**
  - Default (all toggles off, CTA disabled).
  - Partial (1 or 2 toggles on, CTA still disabled).
  - Complete (all 3 toggles on, CTA enabled with brand tint).
  - Legal sheet open (modal bottom-sheet style on both platforms).
- **Accessibility.** Toggles must have VoiceOver / TalkBack labels combining the title AND sub-label. Hero icon is decorative (hidden from screen readers). CTA must be reachable at Dynamic Type XXXL (currently handled by a sticky bottom action bar on iOS).
- **Do not.** Add a carousel, add a video, hide the limits card behind a "Learn more" toggle, add a "Skip" button.

### 5.2 Dashboard (Shield tab)

- **Purpose.** At-a-glance status of protection + quick access to the most important actions.
- **Shows.**
  - Header card: "You're protected" / "Finish setup to be protected" (dynamic), one-sentence explanation, Panic Lock button (destructive red).
  - Quick Setup card: 2–4 checklist steps that disappear from the list as the user completes them (add vault entry, enable call-screener as default dialer app, turn on Trusted-only, enable App Lock). Disappears entirely when all steps done.
  - Trusted-only mode card: switch for calls, switch for SMS, count of trusted contacts, link to vault, warning text if list is empty.
  - Mic & camera ownership card: hardware mute status (muted / available / unknown), one-tap deep-link to system Privacy settings.
  - Apps that can use mic/camera card: counts + expandable list of apps holding permission + per-app deep-link to its system settings page (Android only — iOS version is a redirect card to Apple's Privacy settings).
  - 4 mini stat tiles: mic uses, camera uses, blocked calls, flagged SMS — each a count over the last 24h.
  - Audit chain integrity card: green "intact" or red "mismatch detected" with a "Re-check" button.
- **States.**
  - First-launch with Quick Setup visible.
  - Fully set up with Quick Setup hidden.
  - Trusted-only mode OFF.
  - Trusted-only mode ON.
  - Audit chain mismatch (should look alarming without being gaudy).
- **Density.** This screen is information-rich. Do not consolidate cards into tabs; the owner wants to scan everything on one scroll.

### 5.3 Family vault (Family tab)

- **Purpose.** Encrypted contact list of the people the owner trusts.
- **Shows.**
  - Empty state: illustration + "No trusted contacts yet" + instructions.
  - Populated: list of trusted-contact rows. Each row shows display name, relation (e.g. "Spouse", "Mother"), masked phone number(s) (`+15•••••••67`), masked email (`j•••@domain.com`), "Owner" chip if applicable.
  - FAB / "+" button to add a new contact.
  - Per-row: Edit button + Reveal (eye) button.
- **Reveal flow.** Tapping the eye requires Face ID / Touch ID / passcode (biometric or device credential). On success, a bottom-sheet or modal shows the full unmasked contact details. The bottom sheet times out automatically if the app is backgrounded.
- **Edit flow.** Full-screen or sheet form with Display name, Relation, Owner toggle, email, one-or-more phone numbers each with a label, address (encrypted), notes (encrypted). Save / Cancel / Delete (if editing existing). Phone number field uses numeric keyboard + length cap.
- **States.** Empty, 1 contact, many contacts (list scroll). Owner-labeled vs regular. Row with 1 number vs multiple numbers.
- **Honest limit.** Family data never leaves the device. Nothing on this screen should imply cloud sync or "share with family."

### 5.4 Events (Events tab)

- **Purpose.** Audit log of everything the app flagged. Lets the owner answer "what did my phone do last night while I slept?"
- **Shows.**
  - Audit chain integrity badge at the top (green = intact, red = tampered).
  - Reload button in nav bar.
  - Chronological list, newest first: event type, relative timestamp, optional detail. Event types: Call blocked, Call allowed, Spam SMS flagged, Call directory refreshed, Panic lock engaged, Vault edited, Data wiped.
  - Empty state: "No events yet" with explanation of what will appear here.
- **Density.** Many rows. Consider grouping by day with sticky section headers in the design.
- **Row structure.** Icon (type) + title + relative time + detail string in monospace secondary.

### 5.5 Blocked (Blocked tab)

- **Purpose.** Manage the per-number block list. Reload it to iOS when it changes.
- **Shows.**
  - "+" button to add a number.
  - List: masked number, optional label, chips for "Calls" / "SMS" block scope. Swipe-to-delete.
  - "Reload Call Directory" button (iOS) with status (idle / spinning / ok / failed message).
  - Deep-link buttons: "Enable blocking in Phone settings" + "Enable SMS filter in Messages settings."
  - Add-blocked bottom-sheet: Number field (numeric keyboard), Label field, Block incoming calls toggle, Flag incoming SMS toggle, Add / Cancel.
- **States.** Empty, with entries, after reload success, after reload failure (red footer).

### 5.6 Settings (Settings tab)

- **Purpose.** All toggles + data operations + navigation to deeper screens.
- **Shows (grouped sections).**
  - **Privacy** group: Mic + camera monitoring (Android only), Pocket-dial protection (Android only), Screenshot protection, App Lock toggle (with lockout-prevention guard), Shield watermark configuration (Android only).
  - **Owner tag** input (24 chars, used in Shield watermark).
  - **Data** group: Export my data as JSON, Export evidence pack for carrier/bank/police, Clear event history, Wipe everything (destructive confirmation dialog).
  - **More** group: Permission audit (Android only), Device posture check (Android only), Privacy / terms / honest claims, About + attributions, Shield session log (Android only).
  - **Footer.** © 2026 57Million LLC. AGPL-3.0. Support email.
- **State for App Lock toggle.** Must refuse to enable when the device has no biometric or passcode set, and show a snackbar explaining why. Already implemented; redesign must preserve.

### 5.7 Legal (reached from Settings → "Privacy, terms, and honest claims")

- **Purpose.** The full legal + honesty content, inside the binary, with a visible "last updated" date.
- **Shows.** 8 sections picker (Audience, Non-interference, Call encryption, Privacy policy, Terms of use, Honest claims, Data principles, Accessibility). Content area with headings, paragraphs, bulleted lists, bold emphasis.
- **Interactions.** iOS uses a Menu picker; Android uses a horizontal Tab row. Content is text-selectable (users want to copy clauses). Scrollable.
- **Design need.** Typography that holds up for ~2000-word legal text. Line length and leading matter. Consider pull-quote treatment for the "What we commit to" / "What we do not claim" sections.

### 5.8 About (reached from Settings → "About and attributions")

- **Purpose.** Version info + support contact + changelog + open-source attributions.
- **Shows.**
  - Version + build.
  - "A product of 57Million LLC." footer.
  - Support email with "Open email client" button.
  - Changelog (versioned list of bulleted features).
  - Open-source attributions list (AndroidX, Jetpack Compose, Room, SQLCipher, Kotlin, Swift/SwiftUI, CryptoKit, LocalAuthentication).

### 5.9 App Lock gate (modal, pre-content)

- **Purpose.** Biometric challenge before any content is visible.
- **Shows.** Lock icon, "Locked" heading, "Unlock with Face ID or Passcode" button. On error: error text + "Try again."
- **Fallback.** If the device has no biometric / passcode set, show "Disable App Lock and continue" OR "Return to app" button depending on whether App Lock is actually on (Panic Lock reaches this gate too).

### 5.10 Shield watermark overlay (Android only; iOS can't do this)

- **Purpose.** On-screen diagonal watermark showing owner tag + session code + timestamp so screenshots can be traced back.
- **Visual.** Semi-transparent repeating diagonal text, light + dark versions stacked for contrast against any underlying content. Session code is Crockford-base32 8 chars.
- **Design need.** Tune opacity, text size, repeat spacing, rotation angle. Must be visible to a camera photographing the phone's screen while NOT making normal app use annoying. Current implementation is `services/WatermarkView.kt`.

### 5.11 Pocket-dial confirmation (Android only)

- **Purpose.** Full-screen activity shown when the owner tries to place a call while sensors suggest the phone is in a pocket.
- **Shows.** Icon + "Pocket-dial guard" title + explanatory text + "Cancel" / "Place call" buttons.
- **Design need.** Immediate recognisability so a user knows what's happening even if their hand hit the screen by accident.

### 5.12 Permission audit (Android only, reached from Settings)

- **Purpose.** List installed apps that currently hold mic / camera / location / SMS / phone / contacts.
- **Shows.** Filter chip ("Showing mic/camera holders" vs "Showing all sensitive"). List of apps with name + package + permission badges + "Open in system settings" button per app.

### 5.13 Device posture check (Android only, reached from Settings)

- **Purpose.** Read-only health check of the device.
- **Shows.** USB debugging status, developer options, lock screen set, root status, unknown-sources permission. "Apps holding both mic and camera" list. Each with a Fix button that deep-links to the right system page.

### 5.14 Shield session log (Android only, reached from Settings)

- **Purpose.** Match a leaked screenshot's watermark code to the session that produced it.
- **Shows.** Scrolling list of session rows: code, owner tag, started, ended (or "still running"), optional note.

---

## 6. State enumeration (what to design for across screens)

For every screen that shows user data, include visual designs for:

- **Empty** state (no data yet).
- **Loading** state (if data load is slow — mostly only on first-launch cold DB open).
- **Partially filled** state (e.g. 1 contact in vault, 2 of 4 quick-setup steps complete).
- **Fully populated** state.
- **Error / problem** state (e.g. audit chain mismatch, call-directory reload failed, biometric check unavailable).
- **Reduced-visibility** state when iOS/Android system Privacy indicators show mic/camera is active.

Dark mode and Dynamic Type XXXL designs are required for every screen.

---

## 7. Navigation structure

```
App launch
├─ Onboarding (first launch only)
│   └─ Legal sheet (pre-selected tab)
├─ AppLockGate (if App Lock enabled, every cold start)
│   └─ (recovery) Disable App Lock button
└─ MainTabs (TabView / BottomNavigation)
    ├─ Shield (Dashboard)
    │   └─ Panic → re-enters AppLockGate
    ├─ Family (Vault)
    │   ├─ Add contact (sheet)
    │   ├─ Edit contact (sheet)
    │   └─ Reveal contact (bottom-sheet, biometric-gated)
    ├─ Events
    ├─ Blocked
    │   └─ Add blocked number (sheet)
    │   └─ (external) iOS Phone / Messages settings
    └─ Settings
        ├─ Permission audit (Android only)
        ├─ Device posture (Android only)
        ├─ Shield session log (Android only)
        ├─ Privacy / terms / honest claims → LegalScreen
        └─ About → AboutScreen
```

---

## 8. Current implementation + screenshot references

| Screen | iOS source | Android source |
|---|---|---|
| Onboarding | `ios/OnlyTrusted/Sources/Onboarding/OnboardingScreen.swift` | `android/app/src/main/java/com/onlytrusted/app/ui/onboarding/OnboardingScreen.kt` |
| Dashboard | `ios/OnlyTrusted/Sources/App/MainTabs.swift` → `DashboardScreen` | `android/.../ui/dashboard/DashboardScreen.kt` |
| Vault | `ios/.../Vault/VaultScreen.swift` + `VaultEditScreen.swift` | `android/.../ui/vault/VaultScreen.kt` + `VaultEditScreen.kt` |
| Events | `ios/.../Events/EventsScreen.swift` | `android/.../ui/events/EventsScreen.kt` |
| Blocked | `ios/.../Blocked/BlockListScreen.swift` | `android/.../ui/blocklist/BlockListScreen.kt` |
| Settings | `ios/.../App/SettingsScreen.swift` | `android/.../ui/settings/SettingsScreen.kt` |
| Legal | `ios/.../Legal/LegalScreen.swift` + `LegalContent.swift` | `android/.../ui/legal/LegalScreen.kt` + `LegalContent.kt` |
| About | `ios/.../About/AboutScreen.swift` | `android/.../ui/about/AboutScreen.kt` |
| AppLockGate | `ios/.../Lock/AppLockGate.swift` | `android/.../ui/lock/AppLockGate.kt` |
| Shield watermark | (not possible on iOS) | `android/.../services/WatermarkOverlayService.kt` + `WatermarkView.kt` |

Current screenshots (iOS simulator, light mode, default text size) can be reproduced via:

```
cd ios
xcodebuild -scheme OnlyTrusted \
  -destination 'platform=iOS Simulator,name=iPhone 17 Pro' \
  CODE_SIGNING_ALLOWED=NO build
xcrun simctl install "iPhone 17 Pro" build/Build/Products/Debug-iphonesimulator/OnlyTrusted.app
xcrun simctl launch "iPhone 17 Pro" com.onlytrusted.app
xcrun simctl io "iPhone 17 Pro" screenshot ~/Desktop/onlytrusted-$(date +%s).png
```

Current Android screenshots can be captured on a windowed AVD.

---

## 9. What we want from a designer

### Minimum deliverables (fixed-price project)

1. **Launcher icon** (Android adaptive icon 108×108 + iOS App Icon full matrix). Must respect the trademark concept of "trust / shield" without being a cliché padlock.
2. **Splash / launch screen** for both platforms. No animation — iOS defaults to a UILaunchScreen storyboard-equivalent, Android to windowBackground.
3. **Figma file** with every screen listed in §5 at default size + XXXL text + dark mode = **3 × 14 = 42 artboards** minimum.
4. **Color system** proposal — can keep the current teal `appAccent` or propose a replacement that meets WCAG AA. Must include semantic colors (success / warning / danger / neutral tiers).
5. **Typography scale** — can keep Inter on Android + SF Pro on iOS, or propose a licensed replacement. Must map to the current Material 3 / Apple HIG type slots (see `ui/theme/Type.kt` and iOS `Theme/Theme.swift` → `Font` extension).
6. **Illustration pack** for the 3 empty states (empty vault, no events, no blocked numbers). Must not look like generic SaaS vector art.
7. **Micro-copy pass** on all labels (checkbox labels, section headers, button text). Must preserve the honesty principles in §3.
8. **Deliver as:** Figma file with components / variants / tokens, exported SVG icon set, PNG @1x/2x/3x for anything that must be rasterised, a 1-page PDF design-system summary.

### Stretch deliverables (negotiable)

- Motion specification for the Panic Lock transition and Shield watermark fade-in.
- Haptic specification for the "chain tampered" alert.
- Localization-ready copy pass for Spanish, Hindi, French (strings externalisation already in place on Android via `strings.xml`; iOS currently has strings inline — would need `.strings` extraction).

### Explicitly out of scope

- Marketing website.
- Play Store / App Store listing graphics (handled separately by 57Million).
- Logo for 57Million LLC itself.
- Documentation / help centre.

---

## 10. Reference material

| | |
|---|---|
| Apple Human Interface Guidelines | https://developer.apple.com/design/human-interface-guidelines |
| Material 3 Design | https://m3.material.io/ |
| WCAG 2.1 AA quick reference | https://www.w3.org/WAI/WCAG21/quickref/?levels=aa |
| Apple SF Symbols library | https://developer.apple.com/sf-symbols/ |
| Material Symbols library | https://fonts.google.com/icons |

Current repository: https://github.com/Vanpaultek/OnlyTrusted

---

## 11. What the engineering side needs back from the designer

When redesign artwork is delivered, engineering needs:

1. Final `Color.kt` + `Theme.swift` **token values** (hex, in both light and dark pairs). Not just "primary = this teal" but every semantic role: onSurface, onSurfaceVariant, outline, surface, surfaceContainer, scrim, etc.
2. Final launcher icons at the right sizes (108dp adaptive-icon-foreground + background for Android; the 1024×1024 App Store icon + all iOS variants for iOS).
3. For any custom glyph, delivery as an SVG so we can import it as a vector drawable on Android and a Symbol Image on iOS.
4. Any new empty-state illustration delivered as SVG + PNG @3x.
5. A short written description per screen stating "if the state is X, show Y" — because screenshots alone miss edge cases.
6. Explicit statement of what fonts and licenses are being used, with license files included for anything redistributable.

---

## 12. How to quote this to a designer

Suggested one-paragraph briefing when posting on Dribbble / Fiverr / Behance:

> I'm looking for a native-mobile designer (iOS + Android) to redesign OnlyTrusted, a privacy-first phone-security app targeting serious adult users. The app has ~14 screens per platform, is fully functional, and needs a professional visual pass without compromising honesty-of-copy or WCAG 2.1 AA accessibility. Deliverables: Figma file with every screen in default + XXXL text + dark mode, color + typography system, launcher icons, 3 empty-state illustrations, SVG icon set. Honest-UX required — no dark patterns, no engagement maximisation. Fixed-price work. Full brief at https://github.com/Vanpaultek/OnlyTrusted/blob/main/DESIGN_BRIEF.md.

---

*Last updated: 2026-10-20. Owner approval required for any change to §2 (brand constraints) or §3 (honesty principles).*
