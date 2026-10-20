// SPDX-FileCopyrightText: 2026 57Million LLC
// SPDX-License-Identifier: AGPL-3.0-or-later
import Foundation
import SwiftUI
import CryptoKit

/// Two exporters that match the Android schema:
///
/// - `DataExporter` — user-visible export. Phone numbers are masked. Can go to iCloud Drive
///   or any Files location without leaking raw numbers.
/// - `EvidenceExporter` — formatted for carrier / bank / law enforcement. Raw numbers,
///   signed with a SHA-256 of the pack contents.
enum DataExporter {

    struct Payload: Codable {
        let schema: String
        let exportedAtMillis: Int64
        let appVersionName: String
        let events: [EventDto]
        let blockedNumbers: [BlockedDto]
    }

    struct EventDto: Codable {
        let timestampMillis: Int64
        let type: String
        let detail: String?
        let prevHash: String?
        let rowHash: String
    }

    struct BlockedDto: Codable {
        let number: String
        let label: String?
        let addedAtMillis: Int64
        let blockCalls: Bool
        let blockSms: Bool
    }

    static func makeURL() -> URL {
        let ts = Int(Date().timeIntervalSince1970 * 1000)
        return FileManager.default.temporaryDirectory
            .appendingPathComponent("mobilesecurity-\(ts).json")
    }

    static func write() throws -> URL {
        let payload = Payload(
            schema: "mobilesecurity.export.v1",
            exportedAtMillis: Int64(Date().timeIntervalSince1970 * 1000),
            appVersionName: appVersion(),
            events: EventStore.shared.allRecent(limit: 10_000).map { ev in
                EventDto(
                    timestampMillis: ev.timestampMillis,
                    type: ev.type,
                    detail: ev.detail.map(maskNumbers),
                    prevHash: ev.prevHash,
                    rowHash: ev.rowHash
                )
            },
            blockedNumbers: BlockListStore.shared.allRecent().map { b in
                BlockedDto(
                    number: PhoneUtils.mask(b.normalisedNumber),
                    label: b.label,
                    addedAtMillis: Int64(b.addedAt.timeIntervalSince1970 * 1000),
                    blockCalls: b.blockCalls,
                    blockSms: b.blockSms
                )
            }
        )
        let encoder = JSONEncoder()
        encoder.outputFormatting = [.prettyPrinted, .sortedKeys]
        let data = try encoder.encode(payload)
        let url = makeURL()
        try data.write(to: url, options: [.atomic])
        return url
    }

    private static let numberRegex = try! NSRegularExpression(pattern: "\\+?\\d{7,}")

    private static func maskNumbers(_ input: String) -> String {
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
}

enum EvidenceExporter {

    struct EvidencePack: Codable {
        let schema: String
        let generatedAtMillis: Int64
        let appVersionName: String
        let device: String
        let osVersion: String
        let events: [DataExporter.EventDto]
        let integrity: Integrity

        struct Integrity: Codable {
            let chainVerified: Bool
            let eventCount: Int
            let packSha256: String
        }
    }

    @MainActor
    static func write() throws -> URL {
        let events = EventStore.shared.allRecent(limit: 20_000).map { ev in
            DataExporter.EventDto(
                timestampMillis: ev.timestampMillis,
                type: ev.type,
                detail: ev.detail,            // raw — intended for investigators
                prevHash: ev.prevHash,
                rowHash: ev.rowHash
            )
        }

        let device = UIDevice.current.model
        let os = "iOS \(UIDevice.current.systemVersion)"
        let chainOk = EventStore.shared.verifyChain()

        let draft = EvidencePack(
            schema: "mobilesecurity.evidence.v1",
            generatedAtMillis: Int64(Date().timeIntervalSince1970 * 1000),
            appVersionName: appVersion(),
            device: device,
            osVersion: os,
            events: events,
            integrity: .init(chainVerified: chainOk, eventCount: events.count, packSha256: "")
        )

        // Hash everything except the hash field itself.
        let encoder = JSONEncoder()
        encoder.outputFormatting = [.prettyPrinted, .sortedKeys]
        let preData = try encoder.encode(draft)
        let digest = SHA256.hash(data: preData).map { String(format: "%02x", $0) }.joined()
        let final = EvidencePack(
            schema: draft.schema,
            generatedAtMillis: draft.generatedAtMillis,
            appVersionName: draft.appVersionName,
            device: draft.device,
            osVersion: draft.osVersion,
            events: draft.events,
            integrity: .init(chainVerified: chainOk, eventCount: events.count, packSha256: digest)
        )
        let finalData = try encoder.encode(final)

        let ts = Int(Date().timeIntervalSince1970 * 1000)
        let url = FileManager.default.temporaryDirectory
            .appendingPathComponent("evidence-\(ts).json")
        try finalData.write(to: url, options: [.atomic])
        return url
    }
}

private func appVersion() -> String {
    (Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String) ?? "?"
}

/// SwiftUI share-sheet shim.
struct ShareSheet: UIViewControllerRepresentable {
    let activityItems: [Any]

    func makeUIViewController(context: Context) -> UIActivityViewController {
        UIActivityViewController(activityItems: activityItems, applicationActivities: nil)
    }

    func updateUIViewController(_ uiViewController: UIActivityViewController, context: Context) {}
}
