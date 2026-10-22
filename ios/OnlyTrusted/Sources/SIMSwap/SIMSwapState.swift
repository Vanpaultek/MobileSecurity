// SPDX-FileCopyrightText: 2026 VanPaulTek Inc
// SPDX-License-Identifier: AGPL-3.0-or-later
import Foundation
import CryptoKit
import CoreTelephony
import SwiftUI
import Security

/// A snapshot of the SIM carrier identity used to detect SIM-swap attacks (Pro tier feature).
///
/// On iOS 16+ the private `CTCarrier` APIs that return the real carrier name / MCC / MNC
/// have been deprecated and return the "65535" sentinel + "--" string, so our fallback is
/// to hash whatever we can read and treat a change in that hash as a swap.
struct SIMSwapIdentity: Equatable {
    /// Human-readable carrier name (may be "--" on iOS 16+).
    let carrierName: String
    /// Mobile Country Code (may be "65535" on iOS 16+).
    let mcc: String
    /// Mobile Network Code (may be "65535" on iOS 16+).
    let mnc: String

    /// `true` when CoreTelephony reported sentinel values — meaning the API is no longer
    /// returning useful data on this device/OS. We still store the hash so a USER-TRIGGERED
    /// rebaseline works, but the alarm effectively becomes advisory.
    var isSentinel: Bool {
        mcc == "65535" || mnc == "65535" || carrierName == "--" || carrierName.isEmpty
    }

    /// Human-readable display string. Returns "Unavailable (iOS 16+ limitation)" when every
    /// field is sentinel-valued; otherwise formats as `carrierName · MCC-MNC`.
    var displayString: String {
        if isSentinel { return "Unavailable (iOS 16+ limitation)" }
        return "\(carrierName) · \(mcc)-\(mnc)"
    }

    /// Stable SHA-256 fingerprint of the identity. Fed to the Keychain for comparison on
    /// subsequent app launches.
    var hash: String {
        let material = "\(carrierName)|\(mcc)|\(mnc)"
        let digest = SHA256.hash(data: Data(material.utf8))
        return digest.map { String(format: "%02x", $0) }.joined()
    }

    static func current() -> SIMSwapIdentity {
        let info = CTTelephonyNetworkInfo()
        // Prefer the first entry from `serviceSubscriberCellularProviders` (iOS 12+ dict).
        let carrier: CTCarrier?
        if let providers = info.serviceSubscriberCellularProviders, let first = providers.values.first {
            carrier = first
        } else {
            carrier = nil
        }
        let name = carrier?.carrierName ?? ""
        let mcc = carrier?.mobileCountryCode ?? ""
        let mnc = carrier?.mobileNetworkCode ?? ""
        return SIMSwapIdentity(carrierName: name, mcc: mcc, mnc: mnc)
    }
}

/// Persisted + session state for the SIM-Swap Alarm.
///
/// Storage layout:
///   - Alarm enabled flag — `UserDefaults` (toggled from the setup screen).
///   - Baseline hash + metadata — `Keychain` (service `com.onlytrusted.app.simswap`), so a
///     plain filesystem copy of the app's prefs doesn't leak the carrier fingerprint.
///   - Last-seen session hash — in-memory only; used to avoid re-firing the alert if the
///     user taps "I did this swap on purpose" and immediately backgrounds the app.
@MainActor
final class SIMSwapState: ObservableObject {
    static let shared = SIMSwapState()

    /// Fires when a mismatch is detected at app launch (or on first unlock). The RootView
    /// observes this and renders the alarm sheet.
    @Published private(set) var pendingAlarm: Alarm?
    @Published private(set) var baseline: Baseline?
    @Published var isEnabled: Bool {
        didSet { UserDefaults.standard.set(isEnabled, forKey: Keys.enabled) }
    }
    /// Phone number the user wants dialed when they hit "Call my carrier". Defaults to the
    /// generic US short-code `611` when unset.
    @Published var carrierNumber: String {
        didSet { UserDefaults.standard.set(carrierNumber, forKey: Keys.carrierNumber) }
    }

    struct Alarm: Equatable {
        let detectedAt: Date
        let oldHashShort: String
        let newHashShort: String
    }

    struct Baseline: Equatable {
        let hash: String
        let display: String
        let capturedAt: Date
        let isSentinel: Bool
    }

    private enum Keys {
        static let enabled = "simswap.v1.enabled"
        static let carrierNumber = "simswap.v1.carrier_number"
        static let service = "com.onlytrusted.app.simswap"
        static let account = "baseline.v1"
    }

    private struct KeychainPayload: Codable {
        let hash: String
        let display: String
        let capturedAt: Date
        let isSentinel: Bool
    }

    private init() {
        let defaults = UserDefaults.standard
        isEnabled = defaults.bool(forKey: Keys.enabled)
        carrierNumber = defaults.string(forKey: Keys.carrierNumber) ?? "611"
        baseline = Self.loadKeychainBaseline()
    }

    // MARK: - Public API

    /// Captures (or re-captures) the current SIM identity as the baseline. Also enables
    /// the alarm. Called from the setup screen when the user taps "Capture baseline" or
    /// after they confirm "I did this swap on purpose" from the alarm sheet.
    @discardableResult
    func captureBaseline(now: Date = Date()) -> Baseline {
        let identity = SIMSwapIdentity.current()
        let b = Baseline(
            hash: identity.hash,
            display: identity.displayString,
            capturedAt: now,
            isSentinel: identity.isSentinel
        )
        baseline = b
        storeKeychainBaseline(b)
        pendingAlarm = nil
        return b
    }

    /// Clears everything. Called from the setup screen's "Reset baseline" row when the
    /// user wants to start over.
    func resetBaseline() {
        baseline = nil
        pendingAlarm = nil
        deleteKeychainBaseline()
    }

    /// Called from `RootView.onAppear` and whenever the app returns to the foreground.
    /// Compares the current carrier hash against the stored baseline and, if the alarm is
    /// armed, publishes `pendingAlarm` for the UI to display.
    func checkOnForeground() {
        guard isEnabled, let b = baseline else { return }
        let current = SIMSwapIdentity.current()
        if current.hash != b.hash {
            let alarm = Alarm(
                detectedAt: Date(),
                oldHashShort: String(b.hash.prefix(8)),
                newHashShort: String(current.hash.prefix(8))
            )
            pendingAlarm = alarm
            EventStore.shared.append(
                type: SecurityEventRecord.Kind.simSwap,
                detail: "old=\(alarm.oldHashShort)… new=\(alarm.newHashShort)… display=\(current.displayString)"
            )
        }
    }

    /// Dismisses the alarm when the user confirms "I did this swap on purpose" — the
    /// current identity becomes the new baseline.
    func acknowledgeAndRebaseline() {
        captureBaseline()
    }

    /// Clears the on-screen alarm without updating the baseline. Used after the user chose
    /// to lock the app or call the carrier; the alarm will fire again on next foreground
    /// until the user explicitly updates the baseline.
    func dismissAlarm() {
        pendingAlarm = nil
    }

    // MARK: - Keychain

    private static func loadKeychainBaseline() -> Baseline? {
        var query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: Keys.service,
            kSecAttrAccount as String: Keys.account,
            kSecReturnData as String: true,
            kSecMatchLimit as String: kSecMatchLimitOne
        ]
        var out: AnyObject?
        let status = SecItemCopyMatching(query as CFDictionary, &out)
        guard status == errSecSuccess, let data = out as? Data else { return nil }
        guard let payload = try? JSONDecoder().decode(KeychainPayload.self, from: data) else {
            return nil
        }
        return Baseline(
            hash: payload.hash,
            display: payload.display,
            capturedAt: payload.capturedAt,
            isSentinel: payload.isSentinel
        )
    }

    private func storeKeychainBaseline(_ b: Baseline) {
        let payload = KeychainPayload(
            hash: b.hash, display: b.display, capturedAt: b.capturedAt, isSentinel: b.isSentinel
        )
        guard let data = try? JSONEncoder().encode(payload) else { return }
        let base: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: Keys.service,
            kSecAttrAccount as String: Keys.account
        ]
        // Upsert: try update first, then add on itemNotFound.
        let attrs: [String: Any] = [
            kSecValueData as String: data,
            kSecAttrAccessible as String: kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
        ]
        let updateStatus = SecItemUpdate(base as CFDictionary, attrs as CFDictionary)
        if updateStatus == errSecItemNotFound {
            var add = base
            add[kSecValueData as String] = data
            add[kSecAttrAccessible as String] = kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
            SecItemAdd(add as CFDictionary, nil)
        }
    }

    private func deleteKeychainBaseline() {
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: Keys.service,
            kSecAttrAccount as String: Keys.account
        ]
        SecItemDelete(query as CFDictionary)
    }
}
