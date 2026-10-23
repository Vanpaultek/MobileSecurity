// SPDX-FileCopyrightText: 2026 VanPaulTek Inc
// SPDX-License-Identifier: AGPL-3.0-or-later
import Foundation
import Security
import SwiftUI

/// Elite-tier (`SubscriptionState.hasElite`) feature: Voice Biometric Trusted Contact
/// Verification.
///
/// Stores a 20-dim MFCC descriptor per enrolled trusted contact. The raw audio is NEVER
/// persisted — only the derived vector lives in the Keychain. See `MFCC.swift` for the
/// feature extraction + similarity math and `VoiceBioMonitor.swift` for the live-call
/// comparison hook.
///
/// Keychain layout: one generic-password item per enrollment, keyed by `voice-{contactID}`.
@MainActor
final class VoiceBioState: ObservableObject {
    static let shared = VoiceBioState()

    /// Keychain service used for every stored voice-print.
    nonisolated private static let keychainService = "com.onlytrusted.app.voice-bio"

    /// Minimum cosine similarity below which a caller is flagged as a possible deepfake.
    /// Spec value per the feature brief.
    nonisolated static let matchThreshold: Float = 0.6

    /// Enrollment clip length (seconds).
    nonisolated static let enrollmentDurationSeconds: Double = 10.0

    /// Live-call verification clip length (seconds).
    nonisolated static let verificationDurationSeconds: Double = 2.0

    private enum Keys {
        static let enabled = "voicebio.v1.enabled"
        static let enrolledIDs = "voicebio.v1.enrolled_ids"
    }

    @Published private(set) var enrolledContactIDs: Set<UUID>
    @Published var featureEnabled: Bool {
        didSet { d.set(featureEnabled, forKey: Keys.enabled) }
    }

    private let d = UserDefaults.standard

    private init() {
        self.featureEnabled = d.object(forKey: Keys.enabled) as? Bool ?? true
        if
            let data = d.data(forKey: Keys.enrolledIDs),
            let ids = try? JSONDecoder().decode([UUID].self, from: data)
        {
            self.enrolledContactIDs = Set(ids)
        } else {
            self.enrolledContactIDs = []
        }
    }

    // MARK: - Public API

    /// True if a voice-print is on file for the given contact.
    func isEnrolled(contactID: UUID) -> Bool {
        enrolledContactIDs.contains(contactID)
    }

    /// Persist a 20-dim MFCC descriptor for a contact. Overwrites any previous enrollment.
    func enroll(contactID: UUID, descriptor: [Float]) {
        guard descriptor.count == MFCC.coefficientCount else { return }
        Self.persistToKeychain(contactID: contactID, descriptor: descriptor)
        if !enrolledContactIDs.contains(contactID) {
            enrolledContactIDs.insert(contactID)
            persistIDs()
        }
    }

    /// Remove the stored voice-print for a contact. Idempotent.
    func clear(contactID: UUID) {
        Self.deleteFromKeychain(contactID: contactID)
        if enrolledContactIDs.contains(contactID) {
            enrolledContactIDs.remove(contactID)
            persistIDs()
        }
    }

    /// Nukes every voice-print on disk. Called by Wipe Everything.
    func clearAll() {
        for id in enrolledContactIDs {
            Self.deleteFromKeychain(contactID: id)
        }
        enrolledContactIDs = []
        persistIDs()
    }

    /// Look up the stored descriptor for a contact. Returns `nil` if nothing is enrolled.
    nonisolated static func descriptor(for contactID: UUID) -> [Float]? {
        loadFromKeychain(contactID: contactID)
    }

    /// True when the running binary targets the iOS Simulator (no mic hardware).
    static var isRunningOnSimulator: Bool {
        #if targetEnvironment(simulator)
        return true
        #else
        return false
        #endif
    }

    // MARK: - Persistence

    private func persistIDs() {
        let payload = try? JSONEncoder().encode(Array(enrolledContactIDs))
        d.set(payload, forKey: Keys.enrolledIDs)
    }

    nonisolated private static func account(for id: UUID) -> String { "voice-\(id.uuidString)" }

    nonisolated private static func persistToKeychain(contactID: UUID, descriptor: [Float]) {
        let data = descriptor.withUnsafeBufferPointer { Data(buffer: $0) }
        let base: [CFString: Any] = [
            kSecClass: kSecClassGenericPassword,
            kSecAttrService: keychainService,
            kSecAttrAccount: account(for: contactID)
        ]
        SecItemDelete(base as CFDictionary)
        var insert = base
        insert[kSecValueData] = data
        insert[kSecAttrAccessible] = kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
        SecItemAdd(insert as CFDictionary, nil)
    }

    nonisolated private static func loadFromKeychain(contactID: UUID) -> [Float]? {
        let query: [CFString: Any] = [
            kSecClass: kSecClassGenericPassword,
            kSecAttrService: keychainService,
            kSecAttrAccount: account(for: contactID),
            kSecReturnData: true,
            kSecMatchLimit: kSecMatchLimitOne
        ]
        var out: CFTypeRef?
        guard SecItemCopyMatching(query as CFDictionary, &out) == errSecSuccess,
              let data = out as? Data else { return nil }
        let count = data.count / MemoryLayout<Float>.size
        guard count > 0 else { return nil }
        return data.withUnsafeBytes { raw -> [Float] in
            let ptr = raw.bindMemory(to: Float.self)
            return Array(UnsafeBufferPointer(start: ptr.baseAddress, count: count))
        }
    }

    nonisolated private static func deleteFromKeychain(contactID: UUID) {
        let base: [CFString: Any] = [
            kSecClass: kSecClassGenericPassword,
            kSecAttrService: keychainService,
            kSecAttrAccount: account(for: contactID)
        ]
        SecItemDelete(base as CFDictionary)
    }
}

/// Extra event kinds introduced by the Elite batch (Voice Biometric / Rogue Scanner /
/// Threat Intel). Added as an extension so we don't touch the hash-chain generator in
/// `EventStore`.
extension SecurityEventRecord.Kind {
    /// Fired when VoiceBioMonitor flags a mismatch between the live caller and a stored
    /// trusted-contact voice-print. Elite tier.
    static let voiceDeepfake = "VOICE_DEEPFAKE_DETECTED"
    /// Fired when the Rogue Network Scanner detects an IMSI-catcher-shaped signal
    /// (forced 2G downgrade, implausible cell-ID jump). Elite tier.
    static let imsiCatcher = "IMSI_CATCHER_SUSPECTED"
    /// Fired when the Rogue Network Scanner flags a suspicious Wi-Fi network (fake AP,
    /// evil-twin BSSID collision, open network with a brand-matching SSID). Elite tier.
    static let rogueWifi = "ROGUE_WIFI_SUSPECTED"
    /// Fired by the Offline Threat Intel scan when a trusted contact or local file hits
    /// one of the bundled Bloom filters / IoC sets. Elite tier.
    static let threatIntelHit = "THREAT_INTEL_HIT"
}
