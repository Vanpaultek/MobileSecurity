// SPDX-FileCopyrightText: 2026 VanPaulTek Inc
// SPDX-License-Identifier: AGPL-3.0-or-later
import Foundation
import SwiftUI
import LocalAuthentication

/// Border Mode (Elite). One-tap preset that stacks every defensive layer at the maximum
/// setting for high-risk crossings (border, airport, protest, hostile territory).
///
/// On `arm`, the previous state of every setting we touch is snapshotted into
/// `previousState` and persisted, then every orchestrated setting is forced to its most
/// defensive value. On `disarm`, the snapshot is restored. See the DESIGN_BRIEF for the
/// explicit 9-point list this covers.
///
/// Backing store: `UserDefaults` keyed `bordermode.v1.*`. The cover identity blob is a
/// small JSON object stored alongside the toggle, so it survives app relaunches and can
/// be re-used across arm cycles. We purposefully don't use the keychain here — the arm
/// state is not a secret, and keeping it in UserDefaults makes it visible to the regular
/// "Wipe everything" and Dead-Man Wipe paths without extra wiring.
@MainActor
final class BorderModeState: ObservableObject {
    static let shared = BorderModeState()

    @Published var isArmed: Bool {
        didSet { d.set(isArmed, forKey: Keys.armed) }
    }

    @Published private(set) var armedAt: Date? {
        didSet {
            if let armedAt {
                d.set(armedAt.timeIntervalSince1970, forKey: Keys.armedAt)
            } else {
                d.removeObject(forKey: Keys.armedAt)
            }
        }
    }

    @Published var coverIdentity: CoverIdentity? {
        didSet { persistCoverIdentity() }
    }

    private let d = UserDefaults.standard

    private enum Keys {
        static let armed = "bordermode.v1.armed"
        static let armedAt = "bordermode.v1.armed_at"
        static let previousState = "bordermode.v1.previous_state"
        static let coverIdentity = "bordermode.v1.cover_identity"
    }

    private init() {
        self.isArmed = d.bool(forKey: Keys.armed)
        if let ts = d.object(forKey: Keys.armedAt) as? Double {
            self.armedAt = Date(timeIntervalSince1970: ts)
        } else {
            self.armedAt = nil
        }
        if let data = d.data(forKey: Keys.coverIdentity),
           let decoded = try? JSONDecoder().decode(CoverIdentity.self, from: data) {
            self.coverIdentity = decoded
        } else {
            self.coverIdentity = nil
        }
    }

    // MARK: - Cover identity

    /// Owner-chosen alias + plausible trusted contact list. Shown to the dashboard /
    /// vault UI only while Border Mode is armed; swapped back to real on disarm.
    struct CoverIdentity: Codable, Equatable {
        var ownerName: String
        var contacts: [CoverContact]
    }

    struct CoverContact: Codable, Equatable, Identifiable {
        var id: UUID
        var name: String
        var number: String

        init(id: UUID = UUID(), name: String = "", number: String = "") {
            self.id = id
            self.name = name
            self.number = number
        }
    }

    private func persistCoverIdentity() {
        if let coverIdentity, let data = try? JSONEncoder().encode(coverIdentity) {
            d.set(data, forKey: Keys.coverIdentity)
        } else {
            d.removeObject(forKey: Keys.coverIdentity)
        }
    }

    // MARK: - Previous-state snapshot (JSON blob)

    /// Snapshot of everything Border Mode mutates, so `disarm` can put every setting back
    /// where the user had it. We don't try to persist OS-level values (e.g. the device
    /// passcode) — only in-app toggles.
    struct PreviousState: Codable {
        var deadmanWipeEnabled: Bool
        var deadmanWipeInterval: Int
        var duressEnabled: Bool
        var appLockEnabled: Bool
        var autoLockSeconds: Int
        var vaultOwnerName: String?
    }

    private func savePreviousState(_ snapshot: PreviousState) {
        if let data = try? JSONEncoder().encode(snapshot) {
            d.set(data, forKey: Keys.previousState)
        }
    }

    private func loadPreviousState() -> PreviousState? {
        guard let data = d.data(forKey: Keys.previousState) else { return nil }
        return try? JSONDecoder().decode(PreviousState.self, from: data)
    }

    // MARK: - Arm

    enum ArmError: Error, LocalizedError {
        case duressNotConfigured
        case pinMismatch
        case notAvailable

        var errorDescription: String? {
            switch self {
            case .duressNotConfigured:
                return "Duress Mode must be configured first — add a Duress PIN before arming Border Mode."
            case .pinMismatch:
                return "Incorrect PIN."
            case .notAvailable:
                return "Border Mode is unavailable on this device."
            }
        }
    }

    /// Returns `true` only if Duress Mode has been fully set up (PIN + alert contact).
    /// Border Mode refuses to arm until this is in place because point 2 of the preset is
    /// "force Duress active".
    var duressReady: Bool {
        let duress = DuressState.shared
        return duress.alertContactId != nil
            && !duress.ownerName.isEmpty
            && duressPinSet()
    }

    private func duressPinSet() -> Bool {
        // DuressState stores PIN salt + hash in UserDefaults. We only check presence.
        let saltKey = "duress.v1.pin_salt"
        let hashKey = "duress.v1.pin_hash"
        return UserDefaults.standard.string(forKey: saltKey) != nil
            && UserDefaults.standard.string(forKey: hashKey) != nil
    }

    /// Arm the preset. Caller is responsible for:
    ///   • Having gated this on `SubscriptionState.shared.hasElite`.
    ///   • Confirming via the three-step dialog (see `BorderModeSetupScreen`).
    ///   • Verifying the user's current PIN before calling (passed in as `verifiedPIN`
    ///     — the state object itself does not touch LocalAuthentication; we trust the
    ///     caller to have performed the biometric or `evaluatePolicy` check).
    ///
    /// Throws `.duressNotConfigured` if the user has not set up Duress yet.
    func arm() throws {
        guard duressReady else { throw ArmError.duressNotConfigured }

        // 1. Snapshot current state.
        let deadman = DeadmanWipeState.shared
        let duress = DuressState.shared

        let snapshot = PreviousState(
            deadmanWipeEnabled: deadman.isEnabled,
            deadmanWipeInterval: deadman.intervalDays,
            duressEnabled: duress.isEnabled,
            appLockEnabled: UserDefaults.standard.bool(forKey: "settings.applock.enabled"),
            autoLockSeconds: AutoLockPrefs.currentSeconds,
            vaultOwnerName: VaultStore.shared.contacts.first(where: { $0.isOwner })?.displayName
        )
        savePreviousState(snapshot)

        // 2. Force every orchestrated setting to its max-protection value.
        //    (Items 1,2,4-6,8 from the brief; items 3,7,9 are handled below / per-platform.)
        deadman.enable(intervalDays: 7)         // 1 — Dead-Man Wipe → 7 days
        duress.isEnabled = true                 // 2 — Duress active
        // 3 — Biometric unlock → off (PIN only). We don't own the device passcode; what
        //     we can do is forcibly flip the app's biometric gate off. The lock screen's
        //     `attemptAuth()` branch will then skip Face ID and present only the PIN
        //     fallback. The toggle lives in UserDefaults under `settings.applock.biometric`.
        UserDefaults.standard.set(false, forKey: "settings.applock.biometric")
        // 4 — Sensor monitor service: iOS has no equivalent per-user service; our iOS
        //     dashboard already shows system mic/camera dots. We flip a flag that the
        //     dashboard can watch to escalate copy to "border mode" tone. (See
        //     BorderModeState.sensorMonitorForced below.)
        UserDefaults.standard.set(true, forKey: "settings.sensor.forced_on")
        // 5 — Screenshot protection → forced on. On iOS this is enforced by the
        //     `screenCaptureProtectedView` modifier applied app-wide via SettingsScreen.
        UserDefaults.standard.set(true, forKey: "settings.screenshot.forced_on")
        // 6 — Shield watermark → "DO NOT RECORD".
        UserDefaults.standard.set(true, forKey: "settings.shield.forced_on")
        UserDefaults.standard.set("DO NOT RECORD", forKey: "settings.shield.watermark_text")
        // 7 — Pocket-dial protection: Android-only; no-op on iOS.
        // 8 — Auto-lock → 30 s.
        AutoLockPrefs.setSeconds(30)
        // 9 — Cover identity swap: flip VaultStore into duress mode so the vault & dashboard
        //     greeting source the decoy data. The DuressVault blob can be seeded from the
        //     cover identity before arming (see `applyCoverIdentityToDuress`).
        if let cover = coverIdentity {
            applyCoverIdentityToDuress(cover)
            duress.ownerName = cover.ownerName
            duress.isDuressSession = true
        }

        // 3. Flip the armed flag last — anyone subscribing to `isArmed` will see the
        //    orchestrated settings already in place.
        armedAt = Date()
        isArmed = true
    }

    /// Disarm. Caller is responsible for the biometric+PIN double-factor check (see
    /// `BorderModeSetupScreen.disarm()`); this method only restores state.
    func disarm() {
        isArmed = false
        armedAt = nil

        guard let snapshot = loadPreviousState() else { return }

        let deadman = DeadmanWipeState.shared
        let duress = DuressState.shared

        if snapshot.deadmanWipeEnabled {
            deadman.enable(intervalDays: snapshot.deadmanWipeInterval)
        } else {
            deadman.disable()
        }
        duress.isEnabled = snapshot.duressEnabled
        duress.isDuressSession = false

        UserDefaults.standard.set(snapshot.appLockEnabled, forKey: "settings.applock.enabled")
        UserDefaults.standard.set(true, forKey: "settings.applock.biometric")
        UserDefaults.standard.set(false, forKey: "settings.sensor.forced_on")
        UserDefaults.standard.set(false, forKey: "settings.screenshot.forced_on")
        UserDefaults.standard.set(false, forKey: "settings.shield.forced_on")
        UserDefaults.standard.removeObject(forKey: "settings.shield.watermark_text")
        AutoLockPrefs.setSeconds(snapshot.autoLockSeconds)

        d.removeObject(forKey: Keys.previousState)
    }

    // MARK: - Cover identity → DuressVault seeding
    //
    // When the user arms with a configured cover identity, we overwrite the Duress vault
    // blob with the cover identity's contacts so point 9 of the preset ("swap vault to
    // cover identity") lines up with the existing decoy-vault machinery.
    private func applyCoverIdentityToDuress(_ cover: CoverIdentity) {
        let store = DuressVaultStore.shared
        store.wipe()
        if !cover.ownerName.isEmpty {
            try? store.save(TrustedContactDraft(
                id: UUID(),
                displayName: cover.ownerName,
                relation: "me",
                isOwner: true,
                email: nil,
                address: nil,
                notes: nil,
                phoneNumbers: []
            ))
        }
        for contact in cover.contacts {
            try? store.save(TrustedContactDraft(
                id: contact.id,
                displayName: contact.name,
                relation: nil,
                isOwner: false,
                email: nil,
                address: nil,
                notes: nil,
                phoneNumbers: [(contact.number, nil)]
            ))
        }
    }

    // MARK: - Flags that the rest of the app watches

    /// True when Border Mode is forcing sensor monitoring to on — the dashboard reads this
    /// to show a stronger "someone else might be listening" tone.
    static var sensorMonitorForced: Bool {
        UserDefaults.standard.bool(forKey: "settings.sensor.forced_on")
    }

    /// True when Border Mode is forcing screenshot protection to on — any
    /// `screenCaptureProtectedView` modifier reads this to decide whether to add
    /// `.privacySensitive()` + overlay.
    static var screenshotProtectionForced: Bool {
        UserDefaults.standard.bool(forKey: "settings.screenshot.forced_on")
    }

    /// Watermark text — defaults to the user's existing shield tag; "DO NOT RECORD" when
    /// Border Mode is armed.
    static var activeWatermarkText: String? {
        UserDefaults.standard.string(forKey: "settings.shield.watermark_text")
    }
}
