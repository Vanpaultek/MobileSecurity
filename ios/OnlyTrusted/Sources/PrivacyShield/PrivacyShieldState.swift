// SPDX-FileCopyrightText: 2026 VanPaulTek Inc
// SPDX-License-Identifier: AGPL-3.0-or-later
import Foundation

/// Preferences for the Auto Privacy Shield dashboard. Two toggles today:
///   * `autoStopEnabled` — gates `CaptureRegistry.stopAll(reason: .background)`.
///     Default ON. If the user turns this off the registry still works for manual
///     teardown and `unregister`, but scene-phase transitions become no-ops.
///   * `requireManualRestart` — informational only in Phase 1. The registry never
///     auto-restarts regardless; this toggle is here so the UI can expose the
///     promise and we have a hook for Phase 2 (silent auto-resume on return).
///
/// Lives in `UserDefaults` with explicit keys so a storage migration later can read
/// them back. Not stored in the vault — these are UX preferences, not secrets.
@MainActor
final class PrivacyShieldState: ObservableObject {

    static let shared = PrivacyShieldState()

    @Published var autoStopEnabled: Bool {
        didSet { UserDefaults.standard.set(autoStopEnabled, forKey: Keys.autoStop) }
    }

    @Published var requireManualRestart: Bool {
        didSet { UserDefaults.standard.set(requireManualRestart, forKey: Keys.manualRestart) }
    }

    private init() {
        let d = UserDefaults.standard
        // Default both to ON so first-run users get the strict posture. The brief calls
        // this out explicitly: the shield is a free-tier default.
        if d.object(forKey: Keys.autoStop) == nil { d.set(true, forKey: Keys.autoStop) }
        if d.object(forKey: Keys.manualRestart) == nil { d.set(true, forKey: Keys.manualRestart) }
        self.autoStopEnabled = d.bool(forKey: Keys.autoStop)
        self.requireManualRestart = d.bool(forKey: Keys.manualRestart)
    }

    private enum Keys {
        static let autoStop = "privacyshield.autoStopEnabled"
        static let manualRestart = "privacyshield.requireManualRestart"
    }
}
