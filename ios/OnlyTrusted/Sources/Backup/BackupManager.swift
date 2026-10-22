// SPDX-FileCopyrightText: 2026 VanPaulTek Inc
// SPDX-License-Identifier: AGPL-3.0-or-later
import Foundation
import UIKit

/// Builds + restores encrypted vault backups. Reaches directly into VaultStore / BlockListStore
/// / EventStore / preference singletons; nothing here touches the network.
///
/// Called from `EncryptedBackupScreen` (export) and `BackupRestoreScreen` (import).
@MainActor
enum BackupManager {

    enum BackupError: Error, LocalizedError {
        case passphraseWeak(String)
        case fileWriteFailed(String)
        case fileReadFailed(String)
        case decryptFailed
        case parseFailed

        var errorDescription: String? {
            switch self {
            case .passphraseWeak(let reason): return reason
            case .fileWriteFailed(let msg): return msg
            case .fileReadFailed(let msg): return msg
            case .decryptFailed: return "Backup could not be decrypted. Check your passphrase or try a different file."
            case .parseFailed: return "Backup file is corrupt or from an unsupported app version."
            }
        }
    }

    // MARK: - Export

    /// Serialise, encrypt and write a `.ot2bak` to the temp directory. Returns the file URL
    /// which the caller hands to `UIActivityViewController` so the user picks iCloud Drive /
    /// Files / wherever.
    static func createBackup(passphrase: String) throws -> URL {
        if let bad = BackupCrypto.validate(passphrase: passphrase) {
            throw BackupError.passphraseWeak(bad)
        }

        let payload = try snapshot()
        let encoder = JSONEncoder()
        encoder.outputFormatting = [.sortedKeys]
        let plaintext = try encoder.encode(payload)
        let encrypted = try BackupCrypto.encrypt(plaintext: plaintext, passphrase: passphrase)

        let f = DateFormatter()
        f.dateFormat = "yyyy-MM-dd-HHmmss"
        let filename = "OnlyTrusted-backup-\(f.string(from: Date())).ot2bak"
        let url = FileManager.default.temporaryDirectory.appendingPathComponent(filename)

        do {
            try encrypted.write(to: url, options: [.atomic])
        } catch {
            throw BackupError.fileWriteFailed(String(describing: error))
        }
        return url
    }

    // MARK: - Restore

    static func decryptBackup(fileURL: URL, passphrase: String) throws -> BackupPayload {
        let data: Data
        do {
            // Access + release the security-scoped resource since the user picked this URL
            // via the document picker.
            let gotAccess = fileURL.startAccessingSecurityScopedResource()
            defer { if gotAccess { fileURL.stopAccessingSecurityScopedResource() } }
            data = try Data(contentsOf: fileURL)
        } catch {
            throw BackupError.fileReadFailed(String(describing: error))
        }

        let plaintext: Data
        do {
            plaintext = try BackupCrypto.decrypt(fileBytes: data, passphrase: passphrase)
        } catch {
            throw BackupError.decryptFailed
        }

        do {
            return try JSONDecoder().decode(BackupPayload.self, from: plaintext)
        } catch {
            throw BackupError.parseFailed
        }
    }

    /// Overwrites every local store with the decoded backup. Caller is expected to have shown
    /// a confirmation dialog before calling this.
    static func applyRestore(_ payload: BackupPayload) throws {
        // Wipe first so stale rows that aren't present in the backup don't leak through.
        try? VaultStore.shared.wipe()
        try? BlockListStore.shared.wipe()
        EventStore.shared.wipe()

        // Reinstate contacts.
        try writeContacts(payload.contacts)

        // Reinstate blocked numbers.
        for row in payload.blockedNumbers {
            try? BlockListStore.shared.upsert(row)
        }

        // Reinstate events (append rebuilds the hash chain from scratch — intentional, so a
        // restore produces a fresh verifiable chain anchored at this moment).
        for ev in payload.events {
            EventStore.shared.append(type: ev.type, detail: ev.detail)
        }

        // Reinstate settings. All of these are singletons that write-through to UserDefaults.
        let d = UserDefaults.standard
        for (k, v) in payload.settings.notification { setDefault(d, k, v) }
        for (k, v) in payload.settings.blocking { setDefault(d, k, v) }
        d.set(payload.settings.autoLockSeconds, forKey: "autolock.v1.seconds")
        d.set(payload.settings.appLockEnabled, forKey: "applock.enabled")
        d.set(payload.settings.subscriptionTier, forKey: "subscription.v1.tier")
        d.set(payload.settings.subscriptionActive, forKey: "subscription.v1.active")

        EventStore.shared.append(type: SecurityEventRecord.Kind.vaultEdit, detail: "backup restored")
    }

    // MARK: - Snapshot

    private static func snapshot() throws -> BackupPayload {
        let contacts = try loadContactsRaw()
        let blocked = BlockListStore.shared.allRecent()
        let events = EventStore.shared.allRecent(limit: 10_000)

        let settings = BackupPayload.SettingsSnapshot(
            notification: readPrefs(prefix: "notif."),
            blocking: readPrefs(prefix: "blocking."),
            autoLockSeconds: UserDefaults.standard.integer(forKey: "autolock.v1.seconds"),
            appLockEnabled: UserDefaults.standard.bool(forKey: "applock.enabled"),
            subscriptionTier: UserDefaults.standard.string(forKey: "subscription.v1.tier") ?? "free",
            subscriptionActive: UserDefaults.standard.bool(forKey: "subscription.v1.active")
        )

        return BackupPayload(
            schema: BackupPayload.currentSchema,
            exportedAtMillis: Int64(Date().timeIntervalSince1970 * 1000),
            appVersion: appVersionString(),
            platform: "iOS \(UIDevice.current.systemVersion)",
            contacts: contacts,
            blockedNumbers: blocked,
            events: events,
            settings: settings
        )
    }

    private static func readPrefs(prefix: String) -> [String: JSONValue] {
        var out: [String: JSONValue] = [:]
        for (key, value) in UserDefaults.standard.dictionaryRepresentation() where key.hasPrefix(prefix) {
            out[key] = JSONValue.from(value)
        }
        return out
    }

    private static func setDefault(_ d: UserDefaults, _ key: String, _ v: JSONValue) {
        if let any = v.asAny {
            d.set(any, forKey: key)
        } else {
            d.removeObject(forKey: key)
        }
    }

    // MARK: - Vault file access
    //
    // VaultStore exposes a masked projection, but the backup needs the raw records (encrypted
    // address + notes blobs intact so restore round-trips cleanly). We read + write the backing
    // JSON file directly; this is co-located with VaultStore's own `storageURL`.

    private static func vaultFileURL() -> URL {
        let dir = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0]
            .appendingPathComponent("OnlyTrusted", isDirectory: true)
        try? FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
        return dir.appendingPathComponent("vault.v1.json")
    }

    private static func loadContactsRaw() throws -> [TrustedContactRecord] {
        let url = vaultFileURL()
        guard FileManager.default.fileExists(atPath: url.path) else { return [] }
        let data = try Data(contentsOf: url)
        let decoder = JSONDecoder()
        decoder.dateDecodingStrategy = .iso8601
        return (try? decoder.decode([TrustedContactRecord].self, from: data)) ?? []
    }

    private static func writeContacts(_ records: [TrustedContactRecord]) throws {
        let encoder = JSONEncoder()
        encoder.dateEncodingStrategy = .iso8601
        encoder.outputFormatting = [.sortedKeys]
        let data = try encoder.encode(records)
        try data.write(to: vaultFileURL(), options: [.atomic, .completeFileProtection])
        // Push a reload so the UI shows the restored contacts without a restart.
        Task { @MainActor in await VaultStore.shared.reload() }
    }

    private static func appVersionString() -> String {
        (Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String) ?? "?"
    }
}
