// SPDX-FileCopyrightText: 2026 57Million LLC
// SPDX-License-Identifier: AGPL-3.0-or-later
import Foundation
import CallKit

/// iOS instantiates this class whenever the user, or `CXCallDirectoryManager.reloadExtension`,
/// asks for the block list to be refreshed. The extension's job is to feed iOS a strictly
/// ascending list of E.164 phone numbers to block (and optionally identify). iOS caches the
/// result; nothing in here runs per-call. Budget: ~45 seconds, ~120 MB.
class CallDirectoryHandler: CXCallDirectoryProvider {

    override func beginRequest(with context: CXCallDirectoryExtensionContext) {
        context.delegate = self

        // Full reload vs incremental: we always do a full reload because our block list is
        // small (owner-curated, typically < 100 entries) and the full-reload path is simpler
        // and more robust than tracking deltas across main-app restarts.
        if context.isIncremental {
            // We don't support incremental; tell iOS and fall through to a full reload.
            context.removeAllBlockingEntries()
            context.removeAllIdentificationEntries()
        }

        // Load the shared block list. If the App Group isn't configured (misconfiguration or
        // extension running before main app has written anything), finish cleanly with an
        // empty list rather than erroring the whole reload.
        let records = BlockListStore.shared.allSorted()

        for record in records where record.blockCalls && record.e164Int64 > 0 {
            context.addBlockingEntry(withNextSequentialPhoneNumber: CXCallDirectoryPhoneNumber(record.e164Int64))
            if let label = record.label, !label.isEmpty {
                context.addIdentificationEntry(
                    withNextSequentialPhoneNumber: CXCallDirectoryPhoneNumber(record.e164Int64),
                    label: label
                )
            }
        }

        context.completeRequest()
    }
}

extension CallDirectoryHandler: CXCallDirectoryExtensionContextDelegate {
    func requestFailed(for extensionContext: CXCallDirectoryExtensionContext, withError error: Error) {
        // iOS calls this when our data is rejected (duplicates, out-of-order numbers, etc.).
        // We don't have a lot of recourse from inside the extension — the main app will see
        // the failure next time it polls `CXCallDirectoryManager.getEnabledStatusForExtension`.
    }
}
