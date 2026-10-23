# OnlyTrusted — iOS

SwiftUI app, iOS 17+, Swift 6 with strict concurrency. Generated with xcodegen from
`project.yml`.

## Build

```sh
cd ios
xcodegen generate
open OnlyTrusted.xcodeproj
```

Or from the command line:

```sh
xcodebuild -scheme OnlyTrusted \
  -sdk iphonesimulator \
  -destination 'generic/platform=iOS Simulator' \
  -derivedDataPath build \
  CODE_SIGNING_ALLOWED=NO build
```

## Where things live

- `OnlyTrusted/Sources/` — app code, grouped by feature folder
- `OnlyTrusted/Data/` — bundled catalogs (breach prefixes, scam numbers, broker sites,
  threat intel) — copied into the app bundle at build time
- `OnlyTrusted/Assets.xcassets/` — icons, splash hero, backdrop
- `OnlyTrusted/Entitlements/` — main app + 3 extension entitlements
- `CallDirectoryExtension/`, `MessageFilterExtension/`, `SmartLinkGuardExtension/` —
  app extensions (deliberately network-less, see privacy doc)

## Privacy, IP, and legal docs

These are repo-level and cover **both** iOS and Android — they are not iOS-specific:

- **[../docs/PRIVACY_ARCHITECTURE.md](../docs/PRIVACY_ARCHITECTURE.md)** — honest claims,
  per-platform data stores (Keychain / App Group / file container on iOS), what never
  leaves the device, the two opt-in network paths (Panic SOS backend + user-chosen alias
  relay), extension entitlement posture, bundled catalog inventory, auditor hooks.
- **[../docs/IP_REVIEW.md](../docs/IP_REVIEW.md)** — trademark knock-out status, SPDX /
  AGPL posture, patent-dense areas we shield behind OS-blessed APIs
  (CXCallDirectoryProvider, ILMessageFilterExtension, LocalAuthentication, Family
  Controls), third-party license obligations, breach-feed attribution requirements,
  Apple App Store policy mapping, export compliance, release checklist.
