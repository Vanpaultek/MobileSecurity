# MobileSecurity

A privacy-focused Android app that gives you visibility and control over who touches your microphone, camera, calls, and texts — with everything stored encrypted on your device.

## What it does (and what it doesn't)

**What it does**

- Alerts you (notification + vibration) whenever another app starts using the microphone or camera, and logs the event to an encrypted local database.
- Blocks incoming calls and silently drops SMS from any numbers in your personal block list (via `CallScreeningService` and a priority SMS receiver).
- Detects when the phone is in your pocket or face-down using the proximity sensor + accelerometer (pocket-dial protection).
- Protects this app's own screens from screenshots and screen recording via `FLAG_SECURE`.
- Keeps every event and the block list encrypted at rest with SQLCipher + an EncryptedSharedPreferences-managed key. No network calls, no analytics, no cloud sync.
- Shows a dashboard with 24-hour counts and a scrollable event history.

**What it cannot do** (do not advertise these — they are not technically possible from a third-party app)

- Prevent other apps from using the mic/camera at a hardware level without root.
- Stop a person on the other end of a video call from screen-recording their own device.
- Block screenshots of the home screen or other apps.
- Make the phone untrackable by your carrier, ISP, or OS vendor.

## Modules

| Module                                   | Role                                                           |
| ---------------------------------------- | -------------------------------------------------------------- |
| `services/SensorMonitorService.kt`       | Foreground service — audio & camera callbacks + 1.5s poller.   |
| `services/CallBlockerService.kt`         | `CallScreeningService` — blocks/silences numbers in DB.        |
| `services/SmsReceiver.kt`                | Priority SMS receiver — flags senders in DB.                   |
| `services/PocketDetectionService.kt`     | Proximity + accelerometer pocket detection.                    |
| `services/LockStateReceiver.kt`          | Logs screen on/off/unlock events.                              |
| `services/BootReceiver.kt`               | Restarts foreground services after reboot.                     |
| `data/db/AppDatabase.kt`                 | Room DB with SQLCipher factory; key stored in EncryptedPrefs.  |
| `ui/`                                    | Jetpack Compose + Material3 (Dashboard, Events, BlockList, Settings). |

## Build

Requires Android Studio Hedgehog (or newer) and JDK 17.

```
./gradlew :app:assembleDebug
```

The project uses:

- Kotlin 1.9.24, Compose BOM 2024.09.02
- Room 2.6.1 + KSP
- SQLCipher 4.5.4
- androidx.security.crypto 1.1.0-alpha06
- `compileSdk` 34, `minSdk` 26

## Runtime permissions

On first launch the app requests: `RECORD_AUDIO`, `CAMERA`, `READ_PHONE_STATE`, `READ_CALL_LOG`, `RECEIVE_SMS`, `READ_SMS`, and (on API 33+) `POST_NOTIFICATIONS`.

Call screening also requires the user to pick MobileSecurity as the default **Caller ID & spam** app in system settings — Settings → Apps → Default apps.

## Notes on honest marketing

Several claims commonly made by "security" apps are false and will get a listing rejected or taken down. The dashboard includes a visible "can / cannot" card so users set realistic expectations. Keep it.

## Non-interference pledge

The app is read-only against the system and never acts on anyone else's device:

- No system settings are flipped programmatically — we deep-link into system UI where the user flips the switch.
- No other apps are killed, modified, or stripped of permissions. We refuse Device Admin / Device Owner escalation.
- No third-party scanning, OSINT, "reverse lookup" or hack-back. If the user wants an attacker identified, they export the signed Evidence Pack and hand it to a carrier/bank/law-enforcement.
- No network at all — the manifest does not request internet permission.

The Non-interference tab inside the Legal screen states this to the user in plain language. Do not weaken it.
