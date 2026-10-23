// SPDX-FileCopyrightText: 2026 VanPaulTek Inc
// SPDX-License-Identifier: AGPL-3.0-or-later
import Foundation
import CallKit

/// iOS instantiates this class whenever the user, or `CXCallDirectoryManager.reloadExtension`,
/// asks for the block list to be refreshed. The extension's job is to feed iOS a strictly
/// ascending list of E.164 phone numbers to block (and optionally identify). iOS caches the
/// result; nothing in here runs per-call. Budget: ~45 seconds, ~120 MB.
///
/// Sources merged for the block list:
///   1. **BlockListStore** — the user's manual block list from the main app (via App Group
///      shared UserDefaults). Includes Wangiri Protection auto-blocked numbers because the
///      Wangiri service writes directly into BlockListStore when it detects a one-ring
///      hangup.
///   2. **Bundled scam-number threat intel** — a shipped list of known scam phone
///      prefixes (FTC/FCC/ACCC public datasets). File `scam_numbers.v1.txt` in the
///      extension's resource bundle.
class CallDirectoryHandler: CXCallDirectoryProvider {

    override func beginRequest(with context: CXCallDirectoryExtensionContext) {
        context.delegate = self

        if context.isIncremental {
            context.removeAllBlockingEntries()
            context.removeAllIdentificationEntries()
        }

        // Build the union of (user block list ∪ bundled scam intel), de-dupe, sort
        // ascending — CallKit requires strictly ascending numbers.
        var blocking: [Int64] = []

        for record in BlockListStore.shared.allSorted()
            where record.blockCalls && record.e164Int64 > 0 {
            blocking.append(record.e164Int64)
        }

        for scam in Self.loadBundledScamNumbers() {
            blocking.append(scam)
        }

        let unique = Array(Set(blocking)).sorted()
        for number in unique where number > 0 {
            context.addBlockingEntry(withNextSequentialPhoneNumber: CXCallDirectoryPhoneNumber(number))
        }

        // Identification labels only come from the user's manual list — we don't want
        // to label every bundled scam number as "scam" because the strings are placeholder
        // ranges and may collide with legit callers on your network.
        for record in BlockListStore.shared.allSorted()
            where record.blockCalls && record.e164Int64 > 0 {
            if let label = record.label, !label.isEmpty {
                context.addIdentificationEntry(
                    withNextSequentialPhoneNumber: CXCallDirectoryPhoneNumber(record.e164Int64),
                    label: label
                )
            }
        }

        context.completeRequest()
    }

    /// Reads `scam_numbers.v1.txt` from the extension bundle (shipped via project.yml
    /// `resources`). Returns E.164 integers; empty on parse failure. Safe at launch —
    /// file is tiny today (~40 lines) and the real feed will fit well inside the 120 MB
    /// extension budget.
    private static func loadBundledScamNumbers() -> [Int64] {
        guard let url = Bundle(for: CallDirectoryHandler.self)
                .url(forResource: "scam_numbers.v1", withExtension: "txt"),
              let text = try? String(contentsOf: url, encoding: .utf8)
        else { return [] }

        var out: [Int64] = []
        for line in text.split(separator: "\n") {
            let trimmed = line.trimmingCharacters(in: .whitespaces)
            if trimmed.isEmpty || trimmed.hasPrefix("#") { continue }
            let digits = trimmed.filter(\.isNumber)
            if digits.count >= 7, let n = Int64(digits) {
                out.append(n)
            }
        }
        return out
    }
}

extension CallDirectoryHandler: CXCallDirectoryExtensionContextDelegate {
    func requestFailed(for extensionContext: CXCallDirectoryExtensionContext, withError error: Error) {
        // iOS calls this when our data is rejected (duplicates, out-of-order numbers, etc.).
        // We don't have a lot of recourse from inside the extension — the main app will see
        // the failure next time it polls `CXCallDirectoryManager.getEnabledStatusForExtension`.
    }
}
