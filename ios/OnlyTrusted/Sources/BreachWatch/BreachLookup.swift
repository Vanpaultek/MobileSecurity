// SPDX-FileCopyrightText: 2026 VanPaulTek Inc
// SPDX-License-Identifier: AGPL-3.0-or-later
import Foundation
import CryptoKit

/// Pure-functional lookup for the Breach Watch feature (FREE tier).
///
/// Hashes the normalised email (lowercased, trimmed) with SHA-256 and tests whether the
/// first 8 hex characters appear in the bundled `breach_hashes.v1.txt` prefix list. The
/// shipped file keeps 10-hex-char prefixes for the Elite `ThreatIntelDatabase` already,
/// so this loader simply truncates each line to 8 chars before building the set — any
/// 10-char prefix is still a valid 8-char match. Zero network calls.
///
/// Reads the bundle once per process; subsequent calls hit the in-memory `Set<String>`.
enum BreachLookup {

    /// Lazily initialised prefix set so cold-start doesn't pay for file IO until the user
    /// opens Breach Watch (or Elite Threat Intel). `nonisolated(unsafe)` is sound because
    /// the `lazy` initialiser runs under the Swift runtime's dispatch_once protection and
    /// after assignment the `Set<String>` is read-only + value-type.
    nonisolated(unsafe) private static let prefixes: Set<String> = loadPrefixes()

    /// Returns `true` iff the SHA-256(lowercased, trimmed email) prefix is present in the
    /// bundled breach feed. Empty / nil-shaped inputs return `false`.
    static func isEmailBreached(_ email: String) -> Bool {
        let normalised = normalise(email)
        guard !normalised.isEmpty, normalised.contains("@") else { return false }
        guard let data = normalised.data(using: .utf8) else { return false }
        let digest = SHA256.hash(data: data)
        let hex = digest.map { String(format: "%02x", $0) }.joined()
        let needle = String(hex.prefix(8)).lowercased()
        return prefixes.contains(needle)
    }

    /// Trim + lowercase. Also strips any internal whitespace users occasionally paste in.
    static func normalise(_ email: String) -> String {
        email
            .trimmingCharacters(in: .whitespacesAndNewlines)
            .lowercased()
            .filter { !$0.isWhitespace }
    }

    // MARK: - Loader

    private static func loadPrefixes() -> Set<String> {
        let bundle = Bundle.main
        guard let url = bundle.url(forResource: "breach_hashes.v1", withExtension: "txt",
                                   subdirectory: "Data")
                ?? bundle.url(forResource: "breach_hashes.v1", withExtension: "txt"),
              let text = try? String(contentsOf: url, encoding: .utf8)
        else { return [] }

        var out: Set<String> = []
        for raw in text.split(whereSeparator: { $0 == "\n" || $0 == "\r" }) {
            let line = raw.trimmingCharacters(in: .whitespaces)
            guard !line.isEmpty, !line.hasPrefix("#") else { continue }
            let truncated = String(line.prefix(8)).lowercased()
            if truncated.count == 8 { out.insert(truncated) }
        }
        return out
    }
}
