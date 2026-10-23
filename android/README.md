# OnlyTrusted — Android

Jetpack Compose app, Material3, minSdk 26 / targetSdk 34. Gradle 8.x with the Android
Gradle Plugin.

## Build

```sh
cd android
./gradlew :app:assembleDebug
adb -s <device> install -r app/build/outputs/apk/debug/app-debug.apk
```

## Where things live

- `app/src/main/java/com/onlytrusted/app/ui/` — feature packages (compose screens +
  state + repositories)
- `app/src/main/java/com/onlytrusted/app/services/` — foreground + background services
  (CallBlocker, SmsReceiver, SensorMonitor, WatermarkOverlay, PocketDetection)
- `app/src/main/res/` — themes, drawables, strings, raw bundled catalogs
- `app/src/main/assets/` — JSON catalogs (breach prefixes, scam numbers, broker sites,
  threat intel) — copied into the APK at build time
- `app/src/main/AndroidManifest.xml` — permissions, service declarations, intent filters

## Privacy, IP, and legal docs

These are repo-level and cover **both** Android and iOS — they are not Android-specific:

- **[../docs/PRIVACY_ARCHITECTURE.md](../docs/PRIVACY_ARCHITECTURE.md)** — honest claims,
  per-platform data stores (EncryptedSharedPreferences, Room + SQLCipher, AndroidKeyStore
  on Android), what never leaves the device, the two opt-in network paths (Panic SOS
  backend + user-chosen alias relay), bundled catalog inventory, auditor hooks (grep
  for `INTERNET` permission, `OkHttp`, `WebView`, analytics SDKs).
- **[../docs/IP_REVIEW.md](../docs/IP_REVIEW.md)** — trademark knock-out status, SPDX /
  AGPL posture, patent-dense areas we shield behind OS-blessed APIs (CallScreeningService,
  BiometricPrompt, DeviceAdmin / Family Setup, Material Icons Extended), third-party
  license obligations (Jetpack Compose, AndroidX, Kotlin, Room — all Apache 2.0),
  breach-feed attribution requirements, Google Play policy mapping, export compliance,
  release checklist.
