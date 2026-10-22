// SPDX-FileCopyrightText: 2026 VanPaulTek Inc
// SPDX-License-Identifier: AGPL-3.0-or-later
import Foundation
import UIKit

/// Glues `StegoCrypto` to the app's vault snapshot. The stego vault reuses the Elite B1
/// `BackupPayload` snapshot structure so loading a stego vault round-trips the exact same
/// contact / blocked-number / event / settings data as an encrypted `.ot2bak`.
///
/// Called by `StegoVaultScreen`:
///   - `createStego(format:carrierBytes:passphrase:)` → encrypted stego file bytes + filename.
///   - `openStego(from:passphrase:)` → decoded `BackupPayload` ready for the restore path.
@MainActor
enum StegoVaultManager {

    struct CreationResult {
        let bytes: Data
        let suggestedFilename: String
    }

    enum StegoVaultError: Error, LocalizedError {
        case passphraseWeak(String)
        case snapshotFailed(String)
        case carrierTooSmall(Int, Int)
        case embedFailed(String)
        case extractFailed(String)
        case parseFailed

        var errorDescription: String? {
            switch self {
            case .passphraseWeak(let r): return r
            case .snapshotFailed(let r): return r
            case .carrierTooSmall(let have, let need):
                return "Carrier is too small — need \(need) bytes capacity, have \(have). Pick a larger image."
            case .embedFailed(let r): return r
            case .extractFailed(let r): return r
            case .parseFailed:
                return "No vault found in this file. Check your passphrase or try a different carrier."
            }
        }
    }

    // MARK: - Create

    /// Produces a stego-encoded carrier file. `carrierBytes` is either a PNG or PDF; the
    /// `format` arg must match. The returned bytes get handed to a share sheet so the user
    /// chooses the save destination (Files, iCloud, etc.).
    static func createStego(
        format: StegoCrypto.CarrierFormat,
        carrierBytes: Data,
        passphrase: String
    ) throws -> CreationResult {

        if let bad = BackupCrypto.validate(passphrase: passphrase) {
            throw StegoVaultError.passphraseWeak(bad)
        }

        let plaintext: Data
        do {
            plaintext = try snapshotPlaintext()
        } catch {
            throw StegoVaultError.snapshotFailed(String(describing: error))
        }

        let payload: Data
        do {
            payload = try StegoCrypto.makeOT3SGPayload(plaintext: plaintext, passphrase: passphrase)
        } catch let e as StegoCrypto.StegoError {
            throw StegoVaultError.embedFailed(e.localizedDescription)
        }

        let outBytes: Data
        do {
            switch format {
            case .png: outBytes = try StegoCrypto.embedIntoPNG(carrierPNG: carrierBytes, payload: payload)
            case .pdf: outBytes = try StegoCrypto.embedIntoPDF(carrierPDF: carrierBytes, payload: payload)
            }
        } catch let e as StegoCrypto.StegoError {
            if case .carrierTooSmall(let have, let need) = e {
                throw StegoVaultError.carrierTooSmall(have, need)
            }
            throw StegoVaultError.embedFailed(e.localizedDescription)
        }

        let f = DateFormatter()
        f.dateFormat = "yyyy-MM-dd-HHmmss"
        let extName = format == .png ? "png" : "pdf"
        let filename = "memento-\(f.string(from: Date())).\(extName)"
        return CreationResult(bytes: outBytes, suggestedFilename: filename)
    }

    // MARK: - Open

    /// Reads the file at `url`, detects PNG vs PDF, extracts the OT3SG payload, decrypts with
    /// `passphrase`, and returns the decoded `BackupPayload`. Caller still has to confirm
    /// before applying to the live stores.
    static func openStego(from url: URL, passphrase: String) throws -> BackupPayload {
        let gotAccess = url.startAccessingSecurityScopedResource()
        defer { if gotAccess { url.stopAccessingSecurityScopedResource() } }
        let fileData: Data
        do {
            fileData = try Data(contentsOf: url)
        } catch {
            throw StegoVaultError.extractFailed(String(describing: error))
        }

        let payload: Data
        do {
            if looksLikePNG(fileData) {
                payload = try StegoCrypto.extractFromPNG(stegoPNG: fileData)
            } else if looksLikePDF(fileData) {
                payload = try StegoCrypto.extractFromPDF(stegoPDF: fileData)
            } else {
                throw StegoVaultError.parseFailed
            }
        } catch let e as StegoCrypto.StegoError {
            throw StegoVaultError.extractFailed(e.localizedDescription)
        }

        let plaintext: Data
        do {
            plaintext = try StegoCrypto.decryptOT3SGPayload(payload: payload, passphrase: passphrase)
        } catch {
            throw StegoVaultError.parseFailed
        }

        do {
            return try JSONDecoder().decode(BackupPayload.self, from: plaintext)
        } catch {
            throw StegoVaultError.parseFailed
        }
    }

    // MARK: - Vault snapshot

    private static func snapshotPlaintext() throws -> Data {
        // Reuse the same snapshot shape as the Elite B1 encrypted backup. Keeps the restore
        // path identical.
        let contacts = try loadContactsRaw()
        let blocked = BlockListStore.shared.allRecent()
        let events = EventStore.shared.allRecent(limit: 10_000)
        let settings = BackupPayload.SettingsSnapshot(
            notification: [:],
            blocking: [:],
            autoLockSeconds: UserDefaults.standard.integer(forKey: "autolock.v1.seconds"),
            appLockEnabled: UserDefaults.standard.bool(forKey: "applock.enabled"),
            subscriptionTier: UserDefaults.standard.string(forKey: "subscription.v1.tier") ?? "free",
            subscriptionActive: UserDefaults.standard.bool(forKey: "subscription.v1.active")
        )
        let payload = BackupPayload(
            schema: BackupPayload.currentSchema,
            exportedAtMillis: Int64(Date().timeIntervalSince1970 * 1000),
            appVersion: (Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String) ?? "?",
            platform: "iOS \(UIDevice.current.systemVersion)",
            contacts: contacts,
            blockedNumbers: blocked,
            events: events,
            settings: settings
        )
        let encoder = JSONEncoder()
        encoder.outputFormatting = [.sortedKeys]
        return try encoder.encode(payload)
    }

    private static func loadContactsRaw() throws -> [TrustedContactRecord] {
        let dir = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0]
            .appendingPathComponent("OnlyTrusted", isDirectory: true)
        let url = dir.appendingPathComponent("vault.v1.json")
        guard FileManager.default.fileExists(atPath: url.path) else { return [] }
        let data = try Data(contentsOf: url)
        let decoder = JSONDecoder()
        decoder.dateDecodingStrategy = .iso8601
        return (try? decoder.decode([TrustedContactRecord].self, from: data)) ?? []
    }

    // MARK: - Sniffers

    private static let pngSig: [UInt8] = [0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A]
    private static let pdfSig: [UInt8] = [0x25, 0x50, 0x44, 0x46] // "%PDF"

    private static func looksLikePNG(_ d: Data) -> Bool {
        guard d.count >= pngSig.count else { return false }
        for (i, b) in pngSig.enumerated() where d[d.startIndex + i] != b { return false }
        return true
    }

    private static func looksLikePDF(_ d: Data) -> Bool {
        guard d.count >= pdfSig.count else { return false }
        for (i, b) in pdfSig.enumerated() where d[d.startIndex + i] != b { return false }
        return true
    }
}
