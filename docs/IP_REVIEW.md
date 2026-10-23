# OnlyTrusted — Copyright, Trademark & Licensing Risk Register

**Owner:** VanPaulTek Inc
**Last reviewed:** 2026-10-23
**Status:** Internal working document. **This is not a legal opinion.** A qualified IP
lawyer should sign off before each commercial release.

## 0. Reader orientation

This file tracks IP risk across six categories: trademark, copyright on code/UI/art,
patent exposure, third-party license obligations, data-feed licensing, and platform-store
policy. Each row names the asset, the risk, the current state, and the owner of the
next action.

## 1. Trademark — "OnlyTrusted" / "VanPaulTek"

| Item | State | Risk | Next action |
|---|---|---|---|
| USPTO search for "OnlyTrusted" (word mark + logo) | **Not performed** | High — unknown prior registrations in classes 9 (software), 42 (SaaS), 45 (security services) | Hire IP counsel for a knock-out search before marketing spend. |
| USPTO search for "VanPaulTek" | **Not performed** | Moderate — unusual mark, lower collision risk, but still unverified | Same as above. |
| EUIPO + CIPO searches | **Not performed** | Moderate — only relevant if we ship outside US | Defer until launch plan includes EU/CA. |
| Common-law trademark search (web, App Store, Play Store) | **Not performed** | High — a competitor using "OnlyTrusted" in commerce without USPTO reg can still enforce in their geographic market | Hire IP counsel. |
| Logo / wordmark design — in-house commission vs third-party stock | Design authored in-house by VanPaulTek for this project. SVGs live under `ios/.../Assets.xcassets/` and `android/.../res/drawable/`. | Low | Keep the originator chain of custody documented (invoice / contract). |

## 2. Copyright — our own source code

| Item | State | Risk | Next action |
|---|---|---|---|
| SPDX headers on every source file (`SPDX-FileCopyrightText: 2026 VanPaulTek Inc` + `SPDX-License-Identifier: AGPL-3.0-or-later`) | Shipped. Run `grep -rL "SPDX-FileCopyrightText"` across `ios/` and `android/` to spot gaps. | Low | Add header to any new file in review. |
| AGPL-3.0-or-later as the project license | Chosen. `LICENSE` at repo root (confirm present). | Low — copyleft; downstream forks must publish source. | Confirm the file exists and matches the SPDX declarations. |
| Prior-art provenance — code copied from other repos? | **Audit not performed**. Nothing has been copied knowingly, but there is no automated check. | Moderate — accidental copy from Stack Overflow / tutorial sites can import GPLv2 or unlicensed code. | Run FOSSology (or similar) before each release. |
| AI-assisted code generation | Portions were written with Claude Code as a pair-programming assistant. | Low in US (Thaler v. Perlmutter clarifies: AI-only output = no copyright, but human-authored + AI-assisted = authored by the human). Caveat: a court could still see memorised code snippets. | Keep a note in the release checklist that AI assist was used. |

## 3. Copyright — UI, iconography, imagery

| Item | State | Risk | Next action |
|---|---|---|---|
| App icon | In-house | Low | Keep source files (figma / original PSD) under version control. |
| Splash hero (`SplashHero.png`) | Commissioned / generated in-house for this project | Low — but confirm no model-trained-on-copyrighted-art issues if the asset was generated via a model | Keep provenance notes. |
| AppBackdrop (`AppBackdrop.png` — alpine lake + neon shield) | Same as above | Low | Same. |
| SF Symbols (iOS) | Apple SDK, licensed via developer agreement for use in iOS apps only | Low — standard SDK usage | Do not ship outside iOS. |
| Material Symbols (Android) | Apache 2.0, used via Material Icons extended | Low | Keep attribution file if we ever ship a web build. |
| Any third-party stock photo / icon | **None used.** Everything is SF/Material or commissioned. | Low | Reject new third-party stock in review. |

## 4. Patent exposure

| Area | Known risk | Posture |
|---|---|---|
| Device-level app blocking / parental controls | Unknown. Multiple patents in the "Family Controls / Screen Time" space. | We use **Apple's Family Controls framework**, which is Apple's own IP. If the underlying patent landscape ever threatens Family Controls, it threatens all iOS parental-control apps equally. |
| Call blocking / scam-call ID | Multiple patents held by Hiya, YouMail, TrueCaller, Nomorobo. | We use **CXCallDirectoryProvider** (Apple-blessed API) + CallScreeningService (Google). We do not use any proprietary scoring algorithm from a vendor. |
| Breach monitoring | Have I Been Pwned's work is published under broad terms; the API itself has terms of service. See §5. | We ship a bundled hash-prefix feed; no live HIBP API call. |
| Biometric face lock | Many patents on liveness detection. | We use **LocalAuthentication (iOS) / BiometricPrompt (Android)**. Vendor liveness. No in-house ML. |
| Dead-man wipe / cross-device wipe | Patents around enterprise MDM wipe. | We trigger OS-level wipe through official APIs (DeviceAdmin / Family Setup); no custom erase path. |
| Panic SOS / duress | Some patents around hidden gestures. | We implement standard touch gestures; no secret biometric key. |
| **Overall posture** | | We rely on OS-blessed APIs for every patent-dense area. A patent-clearance search before each commercial release is still warranted. |

## 5. Third-party licenses — runtime dependencies

Audit these before each release. Current known entries:

| Dependency | License | Obligation |
|---|---|---|
| Jetpack Compose, AndroidX libraries | Apache 2.0 | Include NOTICE file in app. |
| EncryptedSharedPreferences / Security Crypto | Apache 2.0 | Same. |
| Kotlin stdlib / Coroutines | Apache 2.0 | Same. |
| Material Icons Extended | Apache 2.0 | Same. |
| Room / SQLite | Apache 2.0 | Same. |
| SwiftUI, Foundation, Security framework (iOS) | Apple SDK license | Standard; no attribution required beyond SDK terms. |
| Bundled breach-hash list | **Source of this file determines the license.** If the list is derived from Have I Been Pwned's downloadable password hashes: HIBP terms require attribution + commercial use notice. | Confirm where the current `breach_prefixes.v1.txt` came from. If HIBP: add attribution to the Breach Watch screen footer and to the App Store description. If another source: document it here. |
| Bundled scam-number list | **Source determines the license.** US FTC Do-Not-Call data is public domain. Private feeds (Hiya, Nomorobo, YouMail) are proprietary. | Confirm source of `scam_numbers.v1.txt`. If derived from a paid feed: delete from the build. |
| People-search site URL templates | Templates generated by observing public URL patterns. The URL shape is not copyrightable; the site names and logos are trademarks owned by those sites. | Only use text, never site logos. Current implementation complies. |

## 6. Breach-database and intelligence feeds (singled out)

Have I Been Pwned's bulk password-hash corpus is governed by terms at
https://haveibeenpwned.com/API/v3 and https://haveibeenpwned.com/Passwords. Commercial
use permitted with attribution; attribution should name Troy Hunt / HIBP visibly.

**Action:** confirm the source of `breach_prefixes.v1.txt` and, if HIBP-derived, add an
"includes data from Have I Been Pwned (CC-BY-4.0)" attribution to:

- The Breach Watch screen footer (iOS + Android)
- The App Store / Play Store listing description
- An "Acknowledgments" section inside the About screen

If the feed came from another source (SpyCloud, Enzoic, Constella, 4iQ): remove it from
the bundled app — these are paid feeds and redistribution is a breach of their license.

## 7. Platform store policy — Apple

| Policy | How we comply |
|---|---|
| **4.3 Design — Spam/Copycat** | OnlyTrusted is a *combination* product (family vault + call shield + SMS filter + breach monitor + border mode + face lock + etc). No single competitor combines these features. We should still cite the differentiating features in the submission. |
| **5.1 Privacy — Data Collection and Storage** | We collect nothing. Privacy nutrition label = "Data Not Collected". The two opt-in network paths (§6 of PRIVACY_ARCHITECTURE.md) stay with the user and don't need declaration under the current App Store rules. |
| **5.1.5 Location Services** | Only when the user enables Geofence Setup. "While using the app" level only. |
| **5.1.4 Camera / Microphone usage strings** | Set in Info.plist. Confirm each string names the specific feature. |
| **2.3 Accurate Metadata** | Honest Claims card content must match screenshot captions in the listing. |
| **3.1.1 In-App Purchase** | Subscription uses StoreKit. Confirm non-restoreable items are flagged correctly. |

## 8. Platform store policy — Google

| Policy | How we comply |
|---|---|
| **Impersonation** | OnlyTrusted / VanPaulTek is our mark. Not similar to any known competitor. |
| **Permissions** | Each permission (SMS, Call Screening, Device Admin for AppShield) is justified by a specific user-facing feature; show the rationale sheet before requesting. |
| **Call Log + Contacts** | Call Log use declared under "Call-screening apps" category. Contacts use scoped to the Trusted Contacts feature. |
| **Family Policy** | We do not target children. Avoid any "under 13" marketing. |
| **Data Safety form** | "No data collected" for the base app. Panic SOS backend path needs a conditional disclosure describing the user-supplied webhook. |

## 9. Export compliance

| Item | State | Next action |
|---|---|---|
| Encryption export declaration (US BIS) | We use AES-256-GCM, HKDF, SHA-256 via platform SDKs. Qualifies for the TSU exception (publicly available) + mass-market §740.17. | Fill out annual self-classification report before first commercial sale. |
| ITAR concerns | None — this is consumer privacy software, no defence articles. | None. |

## 10. Release checklist (short form)

Before each public release:

- [ ] Trademark knock-out search current within 12 months
- [ ] FOSSology scan clean, or any findings resolved
- [ ] SPDX headers on all new files
- [ ] NOTICE file regenerated and bundled
- [ ] Breach-feed attribution present and matches actual source
- [ ] Store privacy labels match PRIVACY_ARCHITECTURE.md
- [ ] IP counsel sign-off on marketing copy
- [ ] Patent-clearance search for any new user-facing feature with ML / crypto novelty
