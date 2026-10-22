// SPDX-FileCopyrightText: 2026 VanPaulTek Inc
// SPDX-License-Identifier: AGPL-3.0-or-later
import Foundation
import Security
import SwiftUI

/// Elite-tier (`SubscriptionState.hasElite`) feature: Face-Change Shoulder-Surfing Lock.
///
/// Keeps the enrolled owner-face embedding (3-sample average of 128-float descriptors) and
/// the three toggles that drive runtime behaviour:
///   * `lockWhenOwnerLeaves`  — 0 faces for >3s → blur
///   * `lockWhenStrangerJoins` — 1 non-owner face OR 2+ faces → instant lock
///   * `blurOnExit`           — apply blur overlay to sensitive screens
///
/// Storage: the embedding is a 128-float vector stored in the Keychain under
/// `com.onlytrusted.app.face-lock` with device-only accessibility. We never persist the raw
/// front-camera image — only the derived descriptor, in-memory everywhere else.
///
/// Simulator note: iOS Simulator has no camera, so enrollment surfaces a graceful inline
/// explainer and the runtime monitor short-circuits to no-op. See `FaceLockMonitor`.
@MainActor
final class FaceLockState: ObservableObject {
    static let shared = FaceLockState()

    /// Keychain service / account used to persist the owner embedding blob.
    private static let keychainService = "com.onlytrusted.app.face-lock"
    private static let keychainAccount = "owner-embedding.v1"

    private enum Keys {
        static let lockWhenOwnerLeaves  = "facelock.v1.lockWhenOwnerLeaves"
        static let lockWhenStrangerJoin = "facelock.v1.lockWhenStrangerJoins"
        static let blurOnExit           = "facelock.v1.blurOnExit"
        static let hasEnrollment        = "facelock.v1.hasEnrollment"
    }

    @Published private(set) var enrolledEmbedding: [Float]?
    @Published var lockWhenOwnerLeaves: Bool {
        didSet { d.set(lockWhenOwnerLeaves, forKey: Keys.lockWhenOwnerLeaves) }
    }
    @Published var lockWhenStrangerJoins: Bool {
        didSet { d.set(lockWhenStrangerJoins, forKey: Keys.lockWhenStrangerJoin) }
    }
    @Published var blurOnExit: Bool {
        didSet { d.set(blurOnExit, forKey: Keys.blurOnExit) }
    }

    private let d = UserDefaults.standard

    /// Minimum cosine similarity required to accept the live face as the enrolled owner.
    /// Spec value from the feature brief.
    nonisolated static let ownerSimilarityThreshold: Float = 0.70

    /// Grace period before "0 faces detected" counts as a walk-away blur event.
    nonisolated static let noFaceCountdownSeconds: Double = 3.0

    /// Sampling cadence for the runtime monitor. Battery-friendly per the brief.
    nonisolated static let sampleIntervalSeconds: Double = 2.0

    private init() {
        self.lockWhenOwnerLeaves = d.object(forKey: Keys.lockWhenOwnerLeaves) as? Bool ?? true
        self.lockWhenStrangerJoins = d.object(forKey: Keys.lockWhenStrangerJoin) as? Bool ?? true
        self.blurOnExit = d.object(forKey: Keys.blurOnExit) as? Bool ?? true
        self.enrolledEmbedding = Self.loadFromKeychain()
    }

    /// True when at least one owner face has been enrolled on this device.
    var isEnrolled: Bool { enrolledEmbedding != nil }

    // MARK: - Enroll / clear

    /// Store the three-angle average embedding (straight, slight-left, slight-right) as the
    /// canonical owner descriptor. Overwrites any previous enrollment.
    func setEnrollment(samples: [[Float]]) {
        guard let avg = Self.averageEmbedding(samples) else { return }
        enrolledEmbedding = avg
        d.set(true, forKey: Keys.hasEnrollment)
        Self.persistToKeychain(avg)
    }

    func clearEnrollment() {
        enrolledEmbedding = nil
        d.set(false, forKey: Keys.hasEnrollment)
        Self.deleteFromKeychain()
    }

    // MARK: - Matching

    /// Returns the cosine similarity between the enrolled owner embedding and a live
    /// descriptor. Returns `nil` if no owner embedding is enrolled.
    func similarity(for candidate: [Float]) -> Float? {
        guard let owner = enrolledEmbedding else { return nil }
        return Self.cosineSimilarity(owner, candidate)
    }

    /// Returns true when the candidate is close enough to the enrolled owner.
    func matchesOwner(_ candidate: [Float]) -> Bool {
        guard let sim = similarity(for: candidate) else { return false }
        return sim >= Self.ownerSimilarityThreshold
    }

    // MARK: - Math

    /// Element-wise average + L2-normalise. Returns nil if the input list is empty.
    nonisolated static func averageEmbedding(_ samples: [[Float]]) -> [Float]? {
        guard let first = samples.first, !first.isEmpty else { return nil }
        let dim = first.count
        var acc = [Float](repeating: 0, count: dim)
        for s in samples {
            guard s.count == dim else { continue }
            for i in 0..<dim { acc[i] += s[i] }
        }
        let count = Float(samples.count)
        guard count > 0 else { return nil }
        for i in 0..<dim { acc[i] /= count }
        return l2Normalised(acc)
    }

    nonisolated static func l2Normalised(_ v: [Float]) -> [Float] {
        let sumSq = v.reduce(Float(0)) { $0 + $1 * $1 }
        let norm = sqrtf(max(sumSq, 1e-9))
        return v.map { $0 / norm }
    }

    nonisolated static func cosineSimilarity(_ a: [Float], _ b: [Float]) -> Float {
        guard a.count == b.count else { return 0 }
        var dot: Float = 0
        var na: Float = 0
        var nb: Float = 0
        for i in 0..<a.count {
            dot += a[i] * b[i]
            na += a[i] * a[i]
            nb += b[i] * b[i]
        }
        let denom = sqrtf(max(na, 1e-9)) * sqrtf(max(nb, 1e-9))
        return denom == 0 ? 0 : dot / denom
    }

    // MARK: - Keychain

    private static func persistToKeychain(_ embedding: [Float]) {
        let data = embedding.withUnsafeBufferPointer { Data(buffer: $0) }
        let base: [CFString: Any] = [
            kSecClass: kSecClassGenericPassword,
            kSecAttrService: keychainService,
            kSecAttrAccount: keychainAccount
        ]
        SecItemDelete(base as CFDictionary)
        var insert = base
        insert[kSecValueData] = data
        insert[kSecAttrAccessible] = kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
        SecItemAdd(insert as CFDictionary, nil)
    }

    private static func loadFromKeychain() -> [Float]? {
        let query: [CFString: Any] = [
            kSecClass: kSecClassGenericPassword,
            kSecAttrService: keychainService,
            kSecAttrAccount: keychainAccount,
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

    private static func deleteFromKeychain() {
        let base: [CFString: Any] = [
            kSecClass: kSecClassGenericPassword,
            kSecAttrService: keychainService,
            kSecAttrAccount: keychainAccount
        ]
        SecItemDelete(base as CFDictionary)
    }

    /// True when the running binary targets the iOS Simulator — no front camera available.
    static var isRunningOnSimulator: Bool {
        #if targetEnvironment(simulator)
        return true
        #else
        return false
        #endif
    }
}
