// SPDX-FileCopyrightText: 2026 57Million LLC
// SPDX-License-Identifier: AGPL-3.0-or-later
import Foundation

/// Mirror of Android's PhoneUtils. Pure functions, no state, no I/O.
enum PhoneUtils {
    private static let allowed: Set<Character> = Set("+0123456789 -()")
    static let maxLength = 24

    static func sanitiseNumberInput(_ raw: String) -> String {
        String(raw.filter { allowed.contains($0) }.prefix(maxLength))
    }

    static func normalise(_ raw: String) -> String {
        let digitsAndPlus = raw.filter { $0.isNumber || $0 == "+" }
        if digitsAndPlus.hasPrefix("+") {
            return "+" + String(digitsAndPlus.dropFirst().drop(while: { $0 == "0" }))
        }
        return String(digitsAndPlus.drop(while: { $0 == "0" }))
    }

    /// Masks a number for display: keep the first 2 (3 with +) and last 2 digits.
    static func mask(_ raw: String) -> String {
        let digits = raw.filter { $0.isNumber || $0 == "+" }
        guard digits.count > 4 else { return "••••" }
        let frontLen = digits.hasPrefix("+") ? 3 : 2
        let front = String(digits.prefix(frontLen))
        let back = String(digits.suffix(2))
        let hidden = max(2, digits.count - frontLen - 2)
        return front + String(repeating: "•", count: hidden) + back
    }

    static func sanitiseOwnerTag(_ raw: String) -> String {
        String(raw.filter { ch in
            guard let scalar = ch.unicodeScalars.first else { return false }
            return scalar.value >= 0x20 && scalar.value <= 0x7E
        }.prefix(24))
    }
}
