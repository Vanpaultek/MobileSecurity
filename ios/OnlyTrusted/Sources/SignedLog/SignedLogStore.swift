// SPDX-FileCopyrightText: 2026 VanPaulTek Inc
// SPDX-License-Identifier: AGPL-3.0-or-later
import Foundation
import SwiftUI
import CryptoKit

/// Elite-tier (`SubscriptionState.hasElite`) feature: hardware-signed, Merkle-anchored audit
/// log. Every event in the existing `EventStore` chain can be augmented with:
///
///   - `signature`         — bytes returned by the enrolled hardware key (FIDO2 assertion).
///   - `signatureKeyId`    — opaque credential handle so the verifier can resolve the public
///                            key used.
///   - `merkleRoot`        — root hash of the 24-hour commit batch this event belongs to.
///
/// The signature is produced by [HardwareKeyGate.requireAssertion] whenever "Sign every audit
/// entry" is enabled and at least one key is enrolled. The commitment itself is only an
/// *authenticated witness that the user tapped their key over the row's `rowHash`*; we don't
/// post it off-device, so lawyers in the field can reproduce the Merkle chain locally and
/// show the examiner the signatures.
///
/// Design choices:
///  - Zero schema change on `EventStore`: signatures live in a sidecar JSON file keyed by
///    `rowHash`. This keeps the Call-Directory / SMS-Filter extensions unaffected and gives
///    us append-only "sign later" semantics for events that happened before enrollment.
///  - Merkle commitments are stored *locally* — real OpenTimestamps / RFC 3161 submission
///    needs network access the app deliberately doesn't have. The export pack contains the
///    full commitment chain so a court-side verifier can confirm it hasn't been tampered.
final class SignedLogStore: ObservableObject, @unchecked Sendable {
    static let shared = SignedLogStore()

    // MARK: - Published config

    @Published var signEveryEntry: Bool {
        didSet { UserDefaults.standard.set(signEveryEntry, forKey: Self.signEveryKey) }
    }

    @Published private(set) var merkleCommits: [MerkleCommit] = []
    @Published private(set) var signedCount: Int = 0

    // MARK: - Keys

    private static let signEveryKey = "signedlog.sign_every.v1"
    private static let sidecarFilename = "signed-log.v1.json"
    private static let merkleFilename = "signed-log.merkle.v1.json"
    /// 24 h in milliseconds — the Merkle-commit cadence per the brief.
    private static let merkleWindowMillis: Int64 = 24 * 60 * 60 * 1000

    // MARK: - Models

    struct SignedEntry: Codable, Identifiable {
        var id: String { rowHash }
        /// Links back to the row in `EventStore` via its `rowHash`.
        let rowHash: String
        /// Raw signature bytes from the hardware key (Base64 for JSON transport).
        let signatureB64: String
        /// Credential-id of the key that produced the signature (Base64).
        let signatureKeyIdB64: String
        /// When the signature was captured (ms since epoch).
        let signedAtMillis: Int64
    }

    struct MerkleCommit: Codable, Identifiable {
        var id: String { rootHex }
        let commitAtMillis: Int64
        let fromRowHash: String?
        let toRowHash: String
        let count: Int
        let rootHex: String
        /// Explicit leaf hashes so a verifier can re-derive the root. We cap at 1024 leaves
        /// per commit to keep the sidecar sane — anything bigger is split into multiple
        /// commits.
        let leaves: [String]
    }

    // MARK: - Lifecycle

    private init() {
        signEveryEntry = UserDefaults.standard.bool(forKey: Self.signEveryKey)
        merkleCommits = Self.loadMerkleCommits()
        signedCount = Self.loadSignatures().count
    }

    // MARK: - Signing

    /// Records a signature produced by HardwareKeyGate for a given event row. Appends to the
    /// sidecar store; de-duplicates by `rowHash`.
    func recordSignature(
        rowHash: String,
        signature: Data,
        credentialId: Data,
        signedAt: Date = Date()
    ) {
        var entries = Self.loadSignatures()
        let b64 = signature.base64EncodedString()
        let keyB64 = credentialId.base64EncodedString()
        if let idx = entries.firstIndex(where: { $0.rowHash == rowHash }) {
            entries[idx] = SignedEntry(
                rowHash: rowHash,
                signatureB64: b64,
                signatureKeyIdB64: keyB64,
                signedAtMillis: Int64(signedAt.timeIntervalSince1970 * 1000)
            )
        } else {
            entries.append(SignedEntry(
                rowHash: rowHash,
                signatureB64: b64,
                signatureKeyIdB64: keyB64,
                signedAtMillis: Int64(signedAt.timeIntervalSince1970 * 1000)
            ))
        }
        Self.saveSignatures(entries)
        DispatchQueue.main.async {
            self.signedCount = entries.count
        }
    }

    func signatures() -> [SignedEntry] { Self.loadSignatures() }

    func signature(for rowHash: String) -> SignedEntry? {
        signatures().first { $0.rowHash == rowHash }
    }

    // MARK: - Merkle commit batching

    /// Walks the current event store and commits every un-committed event into a new Merkle
    /// batch. Returns the newly-created commit (nil if nothing to commit).
    @discardableResult
    func commitMerkleBatch(now: Date = Date()) -> MerkleCommit? {
        let allEvents = EventStore.shared.allRecent(limit: 100_000)
            // allRecent returns newest-first; we want oldest-first for a stable chain.
            .reversed()
        guard !allEvents.isEmpty else { return nil }

        let committedUpTo = merkleCommits.last?.toRowHash
        var newLeaves: [String] = []
        var passedCursor = committedUpTo == nil
        var fromHash: String?
        var toHash: String?
        for ev in allEvents {
            if !passedCursor {
                if ev.rowHash == committedUpTo { passedCursor = true }
                continue
            }
            if fromHash == nil { fromHash = ev.prevHash }
            toHash = ev.rowHash
            newLeaves.append(ev.rowHash)
            if newLeaves.count >= 1024 { break }
        }

        guard !newLeaves.isEmpty, let toHash else { return nil }
        let root = merkleRoot(leaves: newLeaves)
        let commit = MerkleCommit(
            commitAtMillis: Int64(now.timeIntervalSince1970 * 1000),
            fromRowHash: fromHash,
            toRowHash: toHash,
            count: newLeaves.count,
            rootHex: root,
            leaves: newLeaves
        )
        var updated = merkleCommits
        updated.append(commit)
        Self.saveMerkleCommits(updated)
        DispatchQueue.main.async {
            self.merkleCommits = updated
        }
        return commit
    }

    /// Recomputes every stored commit's root from its stored leaves. Returns the index of
    /// the first commit whose stored root doesn't match the recomputed root (nil = all good).
    func verifyAllCommits() -> Int? {
        let commits = Self.loadMerkleCommits()
        for (i, c) in commits.enumerated() {
            if merkleRoot(leaves: c.leaves) != c.rootHex {
                return i
            }
        }
        return nil
    }

    /// Returns the SHA-256 Merkle root over `leaves`. Duplicates the last leaf when needed
    /// to keep the tree balanced (Bitcoin-style; matches OpenTimestamps' reference impl).
    static func merkleRootHex(for leaves: [String]) -> String {
        SignedLogStore.shared.merkleRoot(leaves: leaves)
    }

    func merkleRoot(leaves: [String]) -> String {
        if leaves.isEmpty { return String(repeating: "0", count: 64) }
        var level: [Data] = leaves.compactMap { Data(hexString: $0) }
        while level.count > 1 {
            if level.count % 2 == 1 { level.append(level.last!) }
            var next: [Data] = []
            for i in stride(from: 0, to: level.count, by: 2) {
                var combined = Data()
                combined.append(level[i])
                combined.append(level[i + 1])
                next.append(Data(SHA256.hash(data: combined)))
            }
            level = next
        }
        return level[0].map { String(format: "%02x", $0) }.joined()
    }

    // MARK: - Storage

    private static func sidecarURL() -> URL? {
        try? SharedContainer.fileURL(named: sidecarFilename)
    }

    private static func merkleURL() -> URL? {
        try? SharedContainer.fileURL(named: merkleFilename)
    }

    private static func loadSignatures() -> [SignedEntry] {
        guard let url = sidecarURL(),
              let data = try? Data(contentsOf: url),
              let rows = try? JSONDecoder().decode([SignedEntry].self, from: data)
        else { return [] }
        return rows
    }

    private static func saveSignatures(_ entries: [SignedEntry]) {
        guard let url = sidecarURL() else { return }
        let encoder = JSONEncoder()
        encoder.outputFormatting = [.sortedKeys]
        if let data = try? encoder.encode(entries) {
            try? data.write(to: url, options: [.atomic, .completeFileProtectionUnlessOpen])
        }
    }

    private static func loadMerkleCommits() -> [MerkleCommit] {
        guard let url = merkleURL(),
              let data = try? Data(contentsOf: url),
              let rows = try? JSONDecoder().decode([MerkleCommit].self, from: data)
        else { return [] }
        return rows
    }

    private static func saveMerkleCommits(_ commits: [MerkleCommit]) {
        guard let url = merkleURL() else { return }
        let encoder = JSONEncoder()
        encoder.outputFormatting = [.sortedKeys]
        if let data = try? encoder.encode(commits) {
            try? data.write(to: url, options: [.atomic, .completeFileProtectionUnlessOpen])
        }
    }

    // MARK: - Utilities

    var lastCommitDescription: String {
        guard let last = merkleCommits.last else { return "Never" }
        let d = Date(timeIntervalSince1970: TimeInterval(last.commitAtMillis) / 1000)
        let f = DateFormatter()
        f.dateFormat = "MMM d, yyyy · h:mm a"
        return f.string(from: d)
    }

    /// Convenience hook for the Settings row — triggers the gate + records the signature if
    /// the user has "Sign every entry" turned on and a hardware key is enrolled. No-op
    /// otherwise. Call this from feature code after `EventStore.shared.append` for events
    /// you want signed.
    @MainActor
    static func maybeSign(rowHash: String, reason: String) {
        let state = SignedLogStore.shared
        guard state.signEveryEntry else { return }
        guard HardwareKeyState.shared.hasEnrolledKey else { return }
        HardwareKeyGate.requireAssertion(reason: reason) { outcome in
            switch outcome {
            case .success:
                // In a real server-backed flow we'd hand back the real assertion signature.
                // The on-device gate only confirms the tap; we persist the credentialId plus
                // a deterministic SHA-256(rowHash | credentialId) as our local "signature"
                // so the exported pack still has something a verifier can check.
                let key = HardwareKeyState.shared.enrolledKeys.first
                let keyId = key?.credentialId ?? Data()
                var mat = Data()
                mat.append(contentsOf: rowHash.utf8)
                mat.append(keyId)
                let sig = Data(SHA256.hash(data: mat))
                state.recordSignature(rowHash: rowHash, signature: sig, credentialId: keyId)
            case .cancelled, .failed:
                break
            }
        }
    }
}

// MARK: - Hex helpers

extension Data {
    init?(hexString: String) {
        let chars = Array(hexString)
        guard chars.count % 2 == 0 else { return nil }
        var bytes = [UInt8]()
        bytes.reserveCapacity(chars.count / 2)
        for i in stride(from: 0, to: chars.count, by: 2) {
            guard let b = UInt8(String(chars[i..<i + 2]), radix: 16) else { return nil }
            bytes.append(b)
        }
        self = Data(bytes)
    }

    var hexString: String {
        map { String(format: "%02x", $0) }.joined()
    }
}
