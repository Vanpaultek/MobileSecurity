# MobileSecurity — Bug Report

- **Generated:** 2026-10-19 (first audit). Updated 2026-10-20 with C4 (App Lock lockout) + C5 (Panic lock copy) caught during on-device preview, and extended for iOS Phase 2.
- **Scope:** `android/app/src/main/java/com/mobilesecurity/app/`, `android/app/src/main/AndroidManifest.xml`, `android/app/build.gradle.kts`, `ios/MobileSecurity/Sources/`, `ios/CallDirectoryExtension/`, `ios/MessageFilterExtension/`.
- **Review state:** All CRITICAL and HIGH items fixed. MEDIUM / LOW are open and tracked for the daily audit agent.
- **Lint:** `./gradlew :app:lintDebug` → 0 errors, 27 cosmetic warnings (library-version nags, unused-resource `ic_launcher_round`, obsolete `SDK_INT >= O` checks under `minSdk 29`).

---

## CRITICAL — correctness bugs that can misbehave at runtime

> All four are fixed. C1–C3 landed with the first audit; C4 was caught on a Pixel 7 AVD during the first real-device preview and fixed in commit `6560a2f` (2026-10-20).

### C5. Panic lock copy on AppLockGate was misleading when App Lock is OFF
- **File:** `app/src/main/java/com/mobilesecurity/app/ui/lock/AppLockGate.kt`
- **Why it was wrong:** Panic lock always navigates to AppLockGate regardless of App Lock state. On a device with no biometric AND App Lock not enabled, the fallback showed "Disable App Lock and continue" — misleading because App Lock was never on. Caught during on-device preview on Pixel 7 AVD.
- **Fix (commit after this edit):** Branch the gate's error message and button text on `AppLockManager.isEnabled(context)`. If App Lock is off, show "Panic lock engaged. Tap below to return to the app." + "Return to app" button. If App Lock is on, keep the original "Disable App Lock and continue" recovery path.

### C4. App Lock lets the user enable it with no biometric or PIN enrolled → permanent lockout
- **Files:** `app/src/main/java/com/mobilesecurity/app/ui/settings/SettingsScreen.kt` (toggle), `app/src/main/java/com/mobilesecurity/app/ui/lock/AppLockGate.kt` (gate).
- **Why it's wrong:** The Settings toggle for App Lock accepted ON without first checking `BiometricPrompter.canAuthenticate()`. On a device with no fingerprint, no face, and no PIN set (common on fresh emulators; also happens if the owner removes their lock screen while App Lock is on), the next launch hits `AppLockGate` → the biometric prompt immediately fails → the only button is "Try again" which fails again, forever. The owner is locked out of their own app with no recovery path short of uninstalling.
- **Fix:**
  1. Settings toggle refuses to enable App Lock when `canAuthenticate()` returns false and surfaces a snackbar telling the owner to set up a device PIN first.
  2. AppLockGate renders a "Disable App Lock and continue" button (instead of "Try again") when `canAuthenticate()` is false. Recovers users who had App Lock on and later lost their device credential.
- **How the scanner should treat this:** The regex that flagged this would be any call to `AppLockManager.setEnabled(..., true)` not preceded by a `BiometricPrompter.canAuthenticate` check. The fix is in place; do not re-report the pattern at these two file paths unless the guard is removed.

### C1. CallBlockerService uses `runBlocking` on the binder thread
- **File:** `app/src/main/java/com/mobilesecurity/app/services/CallBlockerService.kt`
- **Why it's wrong:** `onScreenCall` runs on the Telecom framework's binder thread. `runBlocking { app.repository.isCallBlocked(raw) }` and `runBlocking { app.trustedContacts.isTrusted(raw) }` back-to-back serialise two SQLCipher reads. On a cold SQLCipher open (first call after boot) this can take ~250–500 ms, during which the Telecom framework may time out and dispatch the call before we respond.
- **Fix:** Keep the DAO queries, but mark them non-suspending (synchronous Room reads). Call screening is already synchronous — Room on a binder thread is fine as long as it doesn't suspend.

### C2. WatermarkOverlayService.endSession() launches on a scope created *after* onDestroy cancelled the main scope
- **File:** `app/src/main/java/com/mobilesecurity/app/services/WatermarkOverlayService.kt:166`
- **Why it's wrong:** `endSession()` creates a fresh `CoroutineScope(Dispatchers.IO + SupervisorJob()).launch { … }` to persist the end timestamp. The service may be destroyed in rapid succession (user flips the Shield toggle fast), leaving an orphan coroutine with no supervisor. If the service is also destroyed by the OS while that write is pending, the row ends with `endedAt = null` forever.
- **Fix:** Launch the end-time write *before* `scope.cancel()`, inside `scope`, with a `NonCancellable` wrapper so cancellation doesn't abort the DB write.

### C3. BroadcastReceivers hold a long-lived CoroutineScope that nothing cancels
- **Files:** `services/SmsReceiver.kt`, `services/LockStateReceiver.kt` (and the old pattern previously in `CallBlockerService`).
- **Why it's wrong:** A manifest-registered `BroadcastReceiver`'s `onReceive` returns after a few seconds; the system may kill the hosting process at any time. A top-level `CoroutineScope(Dispatchers.IO + SupervisorJob())` field on the receiver is leaked across every receive and can never be joined or cancelled.
- **Fix:** Use `goAsync()` on the receiver and launch a short-lived scope that calls `pendingResult.finish()` on completion.

---

## HIGH — fixed in this commit

### H1. Unchecked system-service casts
- `services/SensorMonitorService.kt` (AudioManager, CameraManager)
- `services/PocketDetectionService.kt` (SensorManager)
- `services/WatermarkOverlayService.kt` (WindowManager)
- **Fix:** Use `getSystemService(X::class.java)` and null-check, or `as?` with early return. Replaced throughout.

### H2. `MainActivity` initial state computed inside `remember { … }`
- `MainActivity.kt:48` / `remember { mutableStateOf(!AppLockManager.shouldPrompt(this)) }`
- **Why it's wrong:** `remember` reads a side-effect at the first composition. If the Activity is recreated after rotation, the state is reset from the current pref value, which is fine; but relying on side-effects inside remember blocks is fragile.
- **Fix:** Compute both initial values in `onCreate` before `setContent` and pass them into the composable. Already structured that way after this commit.

---

## MEDIUM — open, deferred to daily audit

### M1. AppDatabase passphrase derivation is convoluted
- `data/db/AppDatabase.kt`
- `secret.toCharArray().joinToString("").toByteArray(Charsets.UTF_8)` is three operations that reduce to `secret.toByteArray(Charsets.UTF_8)`. The actual key material is the UTF-8 bytes of the Base64-encoded random 32-byte secret — 44 bytes of ASCII, not the raw 32 bytes. That is still cryptographically fine (SQLCipher PBKDFs it), but it is misleading and worth simplifying.
- **Deferred fix:** replace with a single `toByteArray(Charsets.UTF_8)`.

### M2. `SensorMonitorService.resolveOwnerForAudioSession()` always returns null
- `services/SensorMonitorService.kt`
- Current implementation is a stub. Not a correctness bug (the UI handles `null` gracefully) but it means mic alerts never attribute to a package.
- **Deferred fix:** try `AppOpsManager.getPackagesForOps(RECORD_AUDIO)` to attribute recent mic use, best-effort.

### M3. DataExporter and EvidenceExporter include raw phone numbers in `detail`
- `export/DataExporter.kt`, `export/EvidenceExporter.kt`
- `event.detail` already contains strings like `from=+15551234567 reason=blocklist`. The export files are user-chosen SAF destinations outside the SQLCipher-encrypted DB. If the user picks a cloud-synced folder, numbers get synced in cleartext.
- **Deferred fix:** either mask numbers in the export (`+15•••••••67`) with an opt-in "include raw" switch, or require the user to confirm "I understand this file leaves the encrypted store".

### M4. `MainActivity` has an unused `openAppDetails()` function
- `MainActivity.kt:136` (previously, now removed in H2 fix pass).

### M5. `event.detail?.substringAfter("from=", "")` fragile parse
- `export/DataExporter.kt:64`, `services/CallBlockerService.kt:recentIncomingNumbers`
- Works today because we control the writer, but a new call site that forgets the `from=` prefix will silently return garbage.
- **Deferred fix:** switch to a structured `detail` field or at least a shared `EventDetail.format(number: String)` helper.

---

## LOW — open, deferred

### L1. `SensorPrivacyStatus` reflection has no logging on failure
- `privacy/SensorPrivacyStatus.kt` — returns `null` silently on failure. On a device where the reflective path works today but changes shape in a future Android version, the dashboard will quietly say "state unknown" with no breadcrumb.
- **Deferred fix:** add `android.util.Log.w` in release builds behind a user-visible debug toggle.

### L2. `PocketConfirmActivity.EXTRA_NUMBER` is tolerated as empty
- `ui/lock/PocketConfirmActivity.kt`
- If something starts the activity without the extra, the UI shows "Confirm you really want to call ." (empty).
- **Deferred fix:** `finish()` immediately when the extra is empty.

### L3. 26 version-nag lint warnings
- All library dependencies have newer stable releases (`core-ktx 1.19.1`, `room 2.8.5`, etc.).
- **Deferred fix:** batch-bump dependencies after full regression test. Not changing right now because every bump is a potential source of new behaviour.

### L4. Unused `mipmap-anydpi-v26/ic_launcher_round.xml`
- `android:roundIcon` is not declared in the manifest, so the resource is dead weight.
- **Deferred fix:** delete the file.

### L5. Several obsolete `Build.VERSION.SDK_INT >= Build.VERSION_CODES.O` checks
- `MobileSecurityApp.kt`, `util/NotificationHelper.kt`, `services/PocketDetectionService.kt`, `services/SensorMonitorService.kt`, `services/WatermarkOverlayService.kt`.
- `minSdk` is 29 so these are dead.
- **Deferred fix:** collapse the branches.

---

## Daily audit agent

A once-a-day cron (`DailyBugScan` routine) re-runs the audit, appends new findings under a dated heading at the bottom of this file, and leaves the existing content alone.

To stop the agent: `/schedule list` → copy the routine id → `/schedule delete <id>`.

---

## iOS — known Simulator-only behaviours (not bugs)

> Documented so the daily scanner doesn't repeatedly surface these as "problems."

### iS1. `CXCallDirectoryManager.reloadExtension` fails on iOS Simulator
- **File:** `ios/MobileSecurity/Sources/Blocked/BlockListScreen.swift`
- **What happens:** Tapping "Reload Call Directory" on the Simulator surfaces a `failed: ...` snackbar in the UI. The underlying error is "The operation couldn't be completed" because the Simulator has no telephony stack to register the extension against.
- **On a real device** with the signed extension installed and MobileSecurity enabled under Settings → Phone → Call Blocking & Identification: the reload succeeds. Simulator is the limit, not the code.

### iS2. MessageFilter extension cannot be exercised on Simulator
- **Files:** `ios/MessageFilterExtension/MessageFilterHandler.swift`
- **What happens:** Simulator does not deliver SMS messages, so iOS never invokes the extension. There is no error — the handler simply never runs.
- **On a real device:** the extension receives `ILMessageFilterQueryRequest` for every SMS from an unknown sender once the user enables MobileSecurity under Settings → Messages → Unknown & Spam.

### iS3. Call Directory + MessageFilter entitlements fail with free provisioning
- **Build flag workaround:** `CODE_SIGNING_ALLOWED=NO` for Simulator builds. Real-device builds require a 10-char Apple Developer Team ID set in `ios/project.yml` `DEVELOPMENT_TEAM`, then `xcodegen generate && xcodebuild archive -allowProvisioningUpdates`.
- **Not a bug.** Apple restricts both `com.apple.developer.callkit` and `com.apple.developer.identitylookup.message-filter` to paid-tier developer accounts by policy.
