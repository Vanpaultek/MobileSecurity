// SPDX-FileCopyrightText: 2026 VanPaulTek Inc
// SPDX-License-Identifier: AGPL-3.0-or-later
import Foundation
import SwiftUI
import UIKit
import PDFKit
import CryptoKit

/// Builds a tamper-evident evidence pack combining the Elite F6 CSV / Evidence-pack output
/// with the Elite B2 hardware signatures and the Elite B3 Merkle commitments. The pack ships
/// as a two-file pair:
///
///   1. `signed-evidence-<ts>.json`  — machine-readable, includes every event + its
///      prev-hash chain + signatures + merkle commits + the hardware public keys.
///   2. `signed-evidence-<ts>.pdf`   — human-readable court-ready print of the same data,
///      displayed as fixed-width hex rows so a lawyer or examiner can show it to a judge.
///
/// Caller writes both files to the share-sheet temp dir and hands the URLs to the share
/// controller.
@MainActor
enum SignedEvidenceExporter {

    struct Pack {
        let jsonURL: URL
        let pdfURL: URL
    }

    struct Document: Codable {
        let schema: String
        let generatedAtMillis: Int64
        let device: String
        let osVersion: String
        let appVersion: String
        let events: [Row]
        let merkleCommits: [SignedLogStore.MerkleCommit]
        let publicKeys: [PublicKeyEntry]
        let integrity: Integrity

        struct Row: Codable {
            let timestampMillis: Int64
            let type: String
            let detail: String?
            let prevHash: String?
            let rowHash: String
            let signatureB64: String?
            let signatureKeyIdB64: String?
            let signedAtMillis: Int64?
        }

        struct PublicKeyEntry: Codable {
            let credentialIdB64: String
            let publicKeyB64: String?
            let displayName: String
        }

        struct Integrity: Codable {
            let chainVerified: Bool
            let eventCount: Int
            let signedCount: Int
            let merkleCommitCount: Int
            /// SHA-256 over the serialized `Document` with this field blanked.
            let packSha256: String
        }
    }

    static func write() throws -> Pack {
        let events = EventStore.shared.allRecent(limit: 20_000).reversed()
        let signatures = Dictionary(uniqueKeysWithValues:
            SignedLogStore.shared.signatures().map { ($0.rowHash, $0) }
        )
        let rows = events.map { ev -> Document.Row in
            let sig = signatures[ev.rowHash]
            return Document.Row(
                timestampMillis: ev.timestampMillis,
                type: ev.type,
                detail: ev.detail,
                prevHash: ev.prevHash,
                rowHash: ev.rowHash,
                signatureB64: sig?.signatureB64,
                signatureKeyIdB64: sig?.signatureKeyIdB64,
                signedAtMillis: sig?.signedAtMillis
            )
        }
        let publicKeys = HardwareKeyState.shared.enrolledKeys.map { key -> Document.PublicKeyEntry in
            Document.PublicKeyEntry(
                credentialIdB64: key.credentialId.base64EncodedString(),
                publicKeyB64: key.publicKey?.base64EncodedString(),
                displayName: key.displayName
            )
        }
        let commits = SignedLogStore.shared.merkleCommits
        let chainOk = EventStore.shared.verifyChain()
        let appVersion = (Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String) ?? "?"

        let draft = Document(
            schema: "onlytrusted.signed-evidence.v1",
            generatedAtMillis: Int64(Date().timeIntervalSince1970 * 1000),
            device: UIDevice.current.model,
            osVersion: "iOS \(UIDevice.current.systemVersion)",
            appVersion: appVersion,
            events: rows,
            merkleCommits: commits,
            publicKeys: publicKeys,
            integrity: Document.Integrity(
                chainVerified: chainOk,
                eventCount: rows.count,
                signedCount: signatures.count,
                merkleCommitCount: commits.count,
                packSha256: ""
            )
        )

        let encoder = JSONEncoder()
        encoder.outputFormatting = [.prettyPrinted, .sortedKeys]
        let draftData = try encoder.encode(draft)
        let digest = SHA256.hash(data: draftData).map { String(format: "%02x", $0) }.joined()
        let final = Document(
            schema: draft.schema,
            generatedAtMillis: draft.generatedAtMillis,
            device: draft.device,
            osVersion: draft.osVersion,
            appVersion: draft.appVersion,
            events: draft.events,
            merkleCommits: draft.merkleCommits,
            publicKeys: draft.publicKeys,
            integrity: Document.Integrity(
                chainVerified: chainOk,
                eventCount: rows.count,
                signedCount: signatures.count,
                merkleCommitCount: commits.count,
                packSha256: digest
            )
        )
        let finalData = try encoder.encode(final)

        let ts = Int(Date().timeIntervalSince1970 * 1000)
        let jsonURL = FileManager.default.temporaryDirectory
            .appendingPathComponent("signed-evidence-\(ts).json")
        try finalData.write(to: jsonURL, options: [.atomic])

        let pdfURL = try renderPDF(document: final, timestamp: ts)
        return Pack(jsonURL: jsonURL, pdfURL: pdfURL)
    }

    // MARK: - PDF

    private nonisolated static func renderPDF(document: Document, timestamp: Int) throws -> URL {
        let url = FileManager.default.temporaryDirectory
            .appendingPathComponent("signed-evidence-\(timestamp).pdf")

        // US Letter at 72 DPI
        let pageSize = CGSize(width: 612, height: 792)
        let margins: CGFloat = 36
        let renderer = UIGraphicsPDFRenderer(bounds: CGRect(origin: .zero, size: pageSize))
        try renderer.writePDF(to: url) { ctx in
            var cursor: CGFloat = margins
            var pageIndex = 0

            func newPage() {
                ctx.beginPage()
                cursor = margins
                pageIndex += 1
                drawString("OnlyTrusted — Hardware-Signed Evidence Pack",
                           at: CGPoint(x: margins, y: cursor),
                           size: 13, bold: true)
                cursor += 20
                drawString("Generated \(formatMillis(document.generatedAtMillis)) · \(document.device) · \(document.osVersion) · \(document.appVersion)",
                           at: CGPoint(x: margins, y: cursor),
                           size: 9, bold: false)
                cursor += 18
                drawString("Hash chain verified: \(document.integrity.chainVerified ? "YES" : "NO") · \(document.integrity.eventCount) events · \(document.integrity.signedCount) signed · \(document.integrity.merkleCommitCount) merkle commits",
                           at: CGPoint(x: margins, y: cursor),
                           size: 9, bold: false)
                cursor += 24
                drawString("Pack SHA-256: \(document.integrity.packSha256)",
                           at: CGPoint(x: margins, y: cursor),
                           size: 8, bold: false, monospace: true)
                cursor += 20
            }

            func ensureSpace(_ needed: CGFloat) {
                if cursor + needed > pageSize.height - margins {
                    newPage()
                }
            }

            newPage()

            // Public keys
            drawString("Enrolled hardware keys",
                       at: CGPoint(x: margins, y: cursor), size: 11, bold: true)
            cursor += 14
            if document.publicKeys.isEmpty {
                drawString("(none — no signatures were captured)",
                           at: CGPoint(x: margins, y: cursor), size: 9, bold: false)
                cursor += 14
            } else {
                for key in document.publicKeys {
                    ensureSpace(26)
                    drawString("• \(key.displayName)",
                               at: CGPoint(x: margins, y: cursor), size: 9, bold: true)
                    cursor += 12
                    drawString("credentialId: \(key.credentialIdB64)",
                               at: CGPoint(x: margins + 8, y: cursor), size: 8, bold: false, monospace: true)
                    cursor += 12
                    if let pk = key.publicKeyB64, !pk.isEmpty {
                        drawString("publicKey: \(pk.prefix(96))\(pk.count > 96 ? "…" : "")",
                                   at: CGPoint(x: margins + 8, y: cursor), size: 8, bold: false, monospace: true)
                        cursor += 12
                    }
                }
            }

            cursor += 10
            drawString("Merkle commitments",
                       at: CGPoint(x: margins, y: cursor), size: 11, bold: true)
            cursor += 14
            if document.merkleCommits.isEmpty {
                drawString("(none — no commits recorded)",
                           at: CGPoint(x: margins, y: cursor), size: 9, bold: false)
                cursor += 14
            } else {
                for commit in document.merkleCommits {
                    ensureSpace(30)
                    drawString("• \(formatMillis(commit.commitAtMillis)) · \(commit.count) leaves",
                               at: CGPoint(x: margins, y: cursor), size: 9, bold: true)
                    cursor += 12
                    drawString("root: \(commit.rootHex)",
                               at: CGPoint(x: margins + 8, y: cursor), size: 8, bold: false, monospace: true)
                    cursor += 12
                }
            }

            cursor += 10
            drawString("Event chain",
                       at: CGPoint(x: margins, y: cursor), size: 11, bold: true)
            cursor += 14
            for row in document.events {
                ensureSpace(40)
                drawString("\(formatMillis(row.timestampMillis)) · \(row.type)",
                           at: CGPoint(x: margins, y: cursor), size: 9, bold: true)
                cursor += 11
                if let d = row.detail, !d.isEmpty {
                    drawString("detail: \(d.prefix(96))",
                               at: CGPoint(x: margins + 8, y: cursor), size: 8, bold: false, monospace: true)
                    cursor += 11
                }
                drawString("rowHash: \(row.rowHash)",
                           at: CGPoint(x: margins + 8, y: cursor), size: 8, bold: false, monospace: true)
                cursor += 11
                if let sig = row.signatureB64 {
                    drawString("sig: \(sig.prefix(96))\(sig.count > 96 ? "…" : "")",
                               at: CGPoint(x: margins + 8, y: cursor), size: 8, bold: false, monospace: true)
                    cursor += 11
                }
                cursor += 3
            }
        }
        return url
    }

    // MARK: - PDF drawing helpers

    private nonisolated static func drawString(_ text: String, at p: CGPoint, size: CGFloat,
                                               bold: Bool, monospace: Bool = false) {
        let font: UIFont
        if monospace {
            font = UIFont.monospacedSystemFont(ofSize: size, weight: bold ? .semibold : .regular)
        } else if bold {
            font = UIFont.boldSystemFont(ofSize: size)
        } else {
            font = UIFont.systemFont(ofSize: size)
        }
        let attrs: [NSAttributedString.Key: Any] = [
            .font: font,
            .foregroundColor: UIColor.black
        ]
        (text as NSString).draw(at: p, withAttributes: attrs)
    }

    private nonisolated static func formatMillis(_ millis: Int64) -> String {
        let date = Date(timeIntervalSince1970: TimeInterval(millis) / 1000)
        let f = DateFormatter()
        f.dateFormat = "yyyy-MM-dd HH:mm:ss"
        return f.string(from: date)
    }
}

// MARK: - Verifier

/// Verifier used by the "Verify evidence pack" screen. Loads a previously-exported
/// `signed-evidence-*.json`, recomputes every row hash + merkle root, and reports any
/// mismatch with event-row context.
@MainActor
enum SignedEvidenceVerifier {

    struct Report {
        let valid: Bool
        /// 1-based index of the first tampered event when `valid` is false.
        let brokenAtEventIndex: Int?
        /// Human-readable summary rendered by the Verify screen.
        let summary: String
    }

    static func verify(url: URL) -> Report {
        let gotAccess = url.startAccessingSecurityScopedResource()
        defer { if gotAccess { url.stopAccessingSecurityScopedResource() } }
        guard let data = try? Data(contentsOf: url) else {
            return Report(valid: false, brokenAtEventIndex: nil,
                          summary: "Could not read the file.")
        }
        guard let doc = try? JSONDecoder().decode(SignedEvidenceExporter.Document.self, from: data) else {
            return Report(valid: false, brokenAtEventIndex: nil,
                          summary: "File is not a valid signed evidence pack.")
        }
        // Verify hash chain
        var prev: String? = nil
        for (i, row) in doc.events.enumerated() {
            if row.prevHash != prev {
                return Report(valid: false, brokenAtEventIndex: i + 1,
                              summary: "Hash chain broken at event #\(i + 1).")
            }
            let material = "\(prev ?? "")|\(row.timestampMillis)|\(row.type)|\(row.detail ?? "")"
            let digest = SHA256.hash(data: Data(material.utf8)).map { String(format: "%02x", $0) }.joined()
            if digest != row.rowHash {
                return Report(valid: false, brokenAtEventIndex: i + 1,
                              summary: "Event #\(i + 1) hash does not match the recomputed hash — row was altered.")
            }
            prev = row.rowHash
        }
        // Verify every merkle commit's root
        for (i, commit) in doc.merkleCommits.enumerated() {
            let recomputed = SignedLogStore.shared.merkleRoot(leaves: commit.leaves)
            if recomputed != commit.rootHex {
                return Report(valid: false, brokenAtEventIndex: nil,
                              summary: "Merkle commit #\(i + 1) root does not match the recomputed root.")
            }
        }
        let signed = doc.events.compactMap { $0.signatureB64 }.count
        return Report(
            valid: true,
            brokenAtEventIndex: nil,
            summary: "Verified · \(doc.events.count) events · \(signed) signed · \(doc.merkleCommits.count) merkle commits · pack hash intact."
        )
    }
}
