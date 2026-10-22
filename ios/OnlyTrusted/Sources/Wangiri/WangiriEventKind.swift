// SPDX-FileCopyrightText: 2026 VanPaulTek Inc
// SPDX-License-Identifier: AGPL-3.0-or-later
import Foundation

/// Extra event kinds introduced by the Pro batch (Wangiri Protection + Safety Check-In).
/// Added as a separate extension so we don't touch the hash-chain-generating EventStore.
extension SecurityEventRecord.Kind {
    /// A short-ring hangup that `WangiriWatcher` classified as a callback-trap scam.
    static let wangiriBlocked = "WANGIRI_BLOCKED"
    /// Owner tapped the "I'm safe" button and dispatched a check-in SMS.
    static let safetyCheckInSent = "SAFETY_CHECKIN_SENT"
}
