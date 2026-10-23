# OnlyTrusted — Privacy Architecture

**Owner:** VanPaulTek Inc
**Last updated:** 2026-10-23
**Scope:** iOS SwiftUI app (`ios/OnlyTrusted`) + Android Compose app (`android/app`).

## 1. Honest claims (the promises everything else protects)

The app ships with these claims surfaced in-UI and in-store copy. Every architectural
decision below exists so that these claims remain true.

1. **No cloud, no server.** OnlyTrusted does not operate any backend. There is no
   authentication server, telemetry pipeline, analytics, A/B service, feature flag host,
   or push-notification backend operated by VanPaulTek Inc for the app's core features.
2. **No account, no sign-up.** The app has no email/password login, no OAuth, no SSO.
   The device *is* the account.
3. **No tracking SDKs.** The build does not include Firebase Analytics, Google Analytics,
   Facebook SDK, Branch, AppsFlyer, Adjust, Mixpanel, Amplitude, or any ad SDK.
4. **No sync.** User data is never uploaded, mirrored, or synced to a VanPaulTek-operated
   endpoint. iCloud / Google backup is a user-controlled OS feature and is covered in §4.
5. **Everything runs on-device.** Every lookup (breach check, scam-number match, link
   risk score) uses bundled data compiled into the app binary.

Two deliberate exceptions are called out below (§6): opt-in Panic SOS Backend and the
user's own choice of relay provider for Private Aliases. Both require explicit opt-in and
are described in-UI the first time they're reached.

## 2. Where data lives on each platform

### iOS

| Store | Scope | Used for |
|---|---|---|
| **Keychain** (`kSecClassGenericPassword`, `kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly`) | Per-app, per-device, never iCloud-synced | Breach Watch email + hits, Alias list, Identity-action checkmarks, Face Lock state, PIN hashes, panic-contact bookkeeping |
| **App Group container** `group.com.onlytrusted.app` | Shared with Call Directory + Message Filter + Smart Link Guard extensions | Scam number list, block-list, SMS link-guard rules, extension-visible toggles |
| **File container** (`FileManager.default.urls(for: .applicationSupportDirectory)`) | Per-app sandbox | Encrypted vault payload, signed audit log, bundled catalogs copied in at first launch |
| **UserDefaults** | Per-app sandbox | Non-sensitive UI preferences only (last cadence picked on Upgrade, which tab was open). Never used for credentials or user data. |

### Android

| Store | Scope | Used for |
|---|---|---|
| **EncryptedSharedPreferences** (AES256_SIV keys + AES256_GCM values via Keystore master key) | Per-app, per-device | All sensitive feature state: Breach Watch, Aliases, Identity progress, Vault key material, Face Lock state, Border Mode state, Dead-Man Wipe config, SIM Swap baseline |
| **Room database** (sqlcipher-wrapped) | Per-app | Vault contacts, call-log events, SMS-filter log, blocked-numbers |
| **DataStore (plain)** | Per-app | Non-sensitive UI prefs |
| **System KeyStore** (`AndroidKeyStore`) | Per-app, hardware-backed where available (StrongBox on supported devices) | Master key material; derived keys never leave Keystore |

## 3. What never leaves the device

Explicit allow-list of data categories that are **never** written to any network socket:

- Contacts (vault, trusted-contacts, panic list)
- Call history
- SMS content (Message Filter sees it in-extension only; the app receives nothing)
- Camera / microphone frames or buffers (Privacy Shield blocks background apps; our own
  capture sessions register with CaptureRegistry and are torn down on lock)
- Biometric features (Face Lock keeps its template in Keystore / Secure Enclave)
- Vault contents (encrypted blobs stay in the file container)
- Signed audit log entries
- Breach Watch email (monitored locally against bundled hash prefixes)
- Alias list, labels, and enable/disable toggles
- Identity-action checkmarks
- Last location, geofence state

## 4. User-initiated boundary crossings

Three places a user action *can* cause data to leave the device. Each is explicit and
reversible.

1. **Encrypted vault backup to the user's own cloud (Elite).** The user picks an iCloud
   Drive / Files / Drive location via the system picker. The payload is encrypted with a
   backup passphrase the user types in; the raw bytes we write are AES-GCM ciphertext
   over a BackupPayload. We never see the passphrase and never upload on the user's
   behalf. See `ios/.../Backup/BackupPayload.swift` and `android/.../backup/BackupManager.kt`.
2. **Panic SOS Backend (opt-in, Elite).** If the user turns on backend-assisted panic, a
   short "I'm in trouble" webhook POST goes to the user's chosen URL (their family's
   responder service). The payload is minimal: timestamp, device nickname, optional
   coarse location. See `ios/.../Safety/PanicBackendScreen.swift` and the Android
   equivalent. Opt-in required, URL supplied by the user, nothing is remembered on our
   side because we don't operate a server.
3. **Deep-link out to third-party portals.** Data Removal Scan, Identity Protection,
   Private Aliases relay-provider "Open" buttons each launch Safari / Chrome at an
   external URL. The URL is public-record and does not include anything about the user.

## 5. Extension boundaries (iOS)

Three app extensions ship inside the main app:

- **CallDirectoryExtension (CXCallDirectoryProvider).** Reads the user's block list +
  bundled `scam_numbers.v1.txt` from the App Group container at reload time. Never
  writes. Has no network entitlement.
- **MessageFilterExtension (ILMessageFilterExtension).** Receives SMS body + sender from
  the OS, matches against bundled SmartLinkGuardRules + the user's filter list from the
  App Group container, returns a verdict. Never writes. Has no network entitlement.
- **SmartLinkGuardExtension (ILClassificationUIExtension).** QR scanner surface. Same
  posture.

All three App Group reads/writes are plain property-list files; nothing is encrypted at
the extension boundary because the Keychain scoping is already per-device. The extensions
are compiled with `com.apple.security.network.*` entitlements **absent** so they cannot
open sockets even if a crash caused them to try.

## 6. Opt-in network: Panic SOS Backend + Alias relay

The two code modules that *can* talk to a network are:

- **Panic SOS Backend** — single `POST` to the user-supplied webhook URL when the panic
  button is held. Disabled by default. See §4 point 2.
- **Private Aliases relay portal "Open" button** — launches the user's browser at the
  relay provider's own self-service URL. The app itself never calls the relay's API; the
  relay (iCloud Hide My Email, DuckDuckGo, SimpleLogin, Firefox Relay, or a custom
  domain) owns the actual forwarding. See `ios/.../PrivateAliases/AliasStore.swift` and
  `android/.../ui/aliases/AliasStore.kt`.

Both are described in-UI the first time they're reached. The Honest Claims card on each
screen states the boundary plainly.

## 7. Bundled catalogs (compiled into the binary)

Every "lookup" feature uses a static feed compiled into the app bundle at build time:

| Feed | File | Used by |
|---|---|---|
| Breach-hash prefix list | `OnlyTrusted/Data/breach_prefixes.v1.txt` + Android asset | Breach Watch |
| Scam number list | `OnlyTrusted/Data/scam_numbers.v1.txt` | Call Directory Extension + Android CallBlockerService |
| People-search site templates | `OnlyTrusted/Data/data_broker_sites.v1.json` + Android | Data Removal Scan |
| SmartLink rule set | `SmartLinkGuardRules.swift` / `.kt` constants | Smart Link Guard |
| Threat Intel feed | `OnlyTrusted/Data/threat_intel.v1.json` | Threat Intel (Elite) |
| Identity-protection action catalog | `IdentityActionsCatalog.swift` / `.kt` constants | Identity Protection |

Updating a feed requires shipping a new app version through Apple / Google. This is a
deliberate trade-off: it means we cannot push a feed update in response to an active
incident, but it also means no feed can be mutated post-install.

## 8. Platform-privilege boundaries we do **not** cross

- No background location updates when the user hasn't asked for geofencing.
- No microphone access outside the user's own voice-bio enrollment session.
- No camera access outside the user's own face-lock enrollment session.
- No contact-list read without the user tapping "import contacts" in the vault.
- No SMS read in the main app (handed off to the extension).
- No accessibility services on Android. The app is usable with TalkBack but does not
  itself consume accessibility events.
- No DeviceAdmin / DeviceOwner on Android unless the user explicitly provisions the
  device via factory-reset DPC. See AppShield setup screen for the Wellbeing hand-off.

## 9. Review hooks

If you are a reviewer or contractor auditing this architecture:

- iOS extension entitlements live at `ios/Entitlements/*.entitlements` — confirm no
  `com.apple.security.network.client` on the three extensions.
- Android manifest at `android/app/src/main/AndroidManifest.xml` — confirm no
  `android.permission.INTERNET` on any component except the main application (and even
  there it's only required for the opt-in Panic SOS webhook).
- Search for `URLSession`, `URLRequest`, `OkHttp`, `HttpURLConnection`, `WebView` across
  both source trees — only the two opt-in paths in §6 should surface.
- Search for `Firebase`, `GoogleAnalytics`, `Facebook`, `Branch`, `Adjust`, `AppsFlyer`,
  `Mixpanel`, `Amplitude` — zero hits expected.

## 10. Change control for this document

Any change that weakens or expands §3, §5, or §6 is a privacy-architecture change and
should land in a commit that updates this document in the same patch.
