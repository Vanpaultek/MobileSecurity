// SPDX-FileCopyrightText: 2026 VanPaulTek Inc
// SPDX-License-Identifier: AGPL-3.0-or-later
import Foundation

/// CSV export of events + trusted contacts + blocked numbers.
///
/// Columns match the Android F6 spec (Type, Timestamp, Number, Name, Label, Action, Notes).
/// Numbers are MASKED in the CSV because this file goes wherever the user shares it
/// (iCloud Drive, email, Files). The JSON exporter already applies the same policy.
enum CSVExporter {
    @MainActor
    static func write() throws -> URL {
        var rows: [[String]] = []
        rows.append(["Type", "Timestamp", "Number", "Name", "Label", "Action", "Notes"])

        let iso: ISO8601DateFormatter = {
            let f = ISO8601DateFormatter()
            f.formatOptions = [.withInternetDateTime]
            return f
        }()

        // Trusted contacts (one row per phone number so analytics tools can group by name).
        for contact in VaultStore.shared.contacts {
            if contact.maskedNumbers.isEmpty {
                rows.append([
                    "Trusted contact",
                    "",
                    "",
                    contact.displayName,
                    contact.relation ?? "",
                    "trusted",
                    ""
                ])
            } else {
                for masked in contact.maskedNumbers {
                    rows.append([
                        "Trusted contact",
                        "",
                        masked,
                        contact.displayName,
                        contact.relation ?? "",
                        "trusted",
                        ""
                    ])
                }
            }
        }

        // Blocked numbers.
        for b in BlockListStore.shared.allRecent() {
            let actions: [String] = {
                var a: [String] = []
                if b.blockCalls { a.append("calls") }
                if b.blockSms { a.append("sms") }
                return a
            }()
            rows.append([
                "Blocked number",
                iso.string(from: b.addedAt),
                PhoneUtils.mask(b.normalisedNumber),
                "",
                b.label ?? "",
                "block:" + actions.joined(separator: "+"),
                ""
            ])
        }

        // Events — detail numbers are masked via the same regex that DataExporter uses.
        for ev in EventStore.shared.allRecent(limit: 10_000) {
            let date = Date(timeIntervalSince1970: Double(ev.timestampMillis) / 1000.0)
            rows.append([
                humanType(ev.type),
                iso.string(from: date),
                maskNumbersInDetail(ev.detail ?? ""),
                "",
                "",
                humanAction(ev.type),
                ""
            ])
        }

        let text = rows.map { line in
            line.map(csvEscape).joined(separator: ",")
        }.joined(separator: "\n") + "\n"

        let ts = Int(Date().timeIntervalSince1970 * 1000)
        let url = FileManager.default.temporaryDirectory
            .appendingPathComponent("onlytrusted-\(ts).csv")
        try Data(text.utf8).write(to: url, options: [.atomic])
        return url
    }

    // MARK: - Helpers

    // Pattern is statically valid; the ?? sentinel makes this crash-free even if a
    // future edit breaks the pattern — we just skip masking.
    private static let numberRegex: NSRegularExpression? =
        (try? NSRegularExpression(pattern: "\\+?\\d{7,}"))

    private static func maskNumbersInDetail(_ input: String) -> String {
        guard !input.isEmpty else { return "" }
        guard let numberRegex else { return input }
        let ns = input as NSString
        let range = NSRange(location: 0, length: ns.length)
        var result = ""
        var cursor = 0
        for match in numberRegex.matches(in: input, range: range) {
            result += ns.substring(with: NSRange(location: cursor, length: match.range.location - cursor))
            result += PhoneUtils.mask(ns.substring(with: match.range))
            cursor = match.range.location + match.range.length
        }
        result += ns.substring(from: cursor)
        return result
    }

    private static func csvEscape(_ field: String) -> String {
        if field.contains(",") || field.contains("\"") || field.contains("\n") || field.contains("\r") {
            return "\"" + field.replacingOccurrences(of: "\"", with: "\"\"") + "\""
        }
        return field
    }

    private static func humanType(_ type: String) -> String {
        switch type {
        case SecurityEventRecord.Kind.callBlocked: return "Blocked call"
        case SecurityEventRecord.Kind.callAllowed: return "Allowed call"
        case SecurityEventRecord.Kind.smsFlagged:  return "Blocked text"
        case SecurityEventRecord.Kind.callDirectoryReloaded: return "Call directory refreshed"
        case SecurityEventRecord.Kind.panicLock:   return "Panic lock"
        case SecurityEventRecord.Kind.vaultEdit:   return "Vault edit"
        case SecurityEventRecord.Kind.wipe:        return "Wipe"
        default: return type
        }
    }

    private static func humanAction(_ type: String) -> String {
        switch type {
        case SecurityEventRecord.Kind.callBlocked, SecurityEventRecord.Kind.smsFlagged: return "blocked"
        case SecurityEventRecord.Kind.callAllowed: return "allowed"
        case SecurityEventRecord.Kind.panicLock: return "locked"
        case SecurityEventRecord.Kind.wipe: return "wiped"
        case SecurityEventRecord.Kind.vaultEdit: return "edited"
        case SecurityEventRecord.Kind.callDirectoryReloaded: return "refreshed"
        default: return ""
        }
    }
}
