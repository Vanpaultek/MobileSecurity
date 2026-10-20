// SPDX-FileCopyrightText: 2026 57Million LLC
// SPDX-License-Identifier: AGPL-3.0-or-later
import Foundation
import SwiftUI

struct TrustedPhone: Codable, Identifiable, Hashable {
    let id: UUID
    let label: String?
    let normalisedNumber: String
    let maskedForDisplay: String

    init(id: UUID = UUID(), label: String?, raw: String) {
        self.id = id
        self.label = label?.trimmingCharacters(in: .whitespacesAndNewlines)
            .nilIfEmpty
        self.normalisedNumber = PhoneUtils.normalise(raw)
        self.maskedForDisplay = PhoneUtils.mask(raw)
    }
}

/// Persisted record. Address + notes are stored as AES-GCM ciphertext (base64).
struct TrustedContactRecord: Codable, Identifiable {
    var id: UUID
    var displayName: String
    var relation: String?
    var isOwner: Bool
    var maskedEmail: String?
    var addressEncrypted: String?
    var notesEncrypted: String?
    var numbers: [TrustedPhone]
    var createdAt: Date
    var updatedAt: Date
}

/// View-level, masked projection of a record. The reveal flow swaps this for a plain-text copy.
struct MaskedContact: Identifiable {
    let id: UUID
    let displayName: String
    let relation: String?
    let isOwner: Bool
    let maskedEmail: String?
    let maskedNumbers: [String]
}

struct RevealedContact {
    let id: UUID
    let displayName: String
    let relation: String?
    let isOwner: Bool
    let addressPlain: String?
    let notesPlain: String?
    let numbers: [TrustedPhone]
}

/// Draft used by the edit form before persistence.
struct TrustedContactDraft {
    var id: UUID?
    var displayName: String
    var relation: String?
    var isOwner: Bool
    var email: String?
    var address: String?
    var notes: String?
    /// (raw number input, optional label)
    var phoneNumbers: [(String, String?)]
}

@MainActor
final class VaultStore: ObservableObject {
    static let shared = VaultStore()

    @Published private(set) var contacts: [MaskedContact] = []

    private let storageURL: URL

    init(storageURL: URL? = nil) {
        if let storageURL {
            self.storageURL = storageURL
        } else {
            let dir = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0]
                .appendingPathComponent("OnlyTrusted", isDirectory: true)
            try? FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
            self.storageURL = dir.appendingPathComponent("vault.v1.json")
        }
    }

    func reload() async {
        contacts = loadRecords().map { $0.toMasked() }
    }

    func reveal(id: UUID) throws -> RevealedContact? {
        guard let record = loadRecords().first(where: { $0.id == id }) else { return nil }
        return RevealedContact(
            id: record.id,
            displayName: record.displayName,
            relation: record.relation,
            isOwner: record.isOwner,
            addressPlain: try FieldCrypto.decrypt(record.addressEncrypted),
            notesPlain: try FieldCrypto.decrypt(record.notesEncrypted),
            numbers: record.numbers
        )
    }

    func save(_ draft: TrustedContactDraft) throws {
        var records = loadRecords()
        let now = Date()
        let id = draft.id ?? UUID()

        let numbers: [TrustedPhone] = draft.phoneNumbers
            .map { (raw, label) -> (String, String?) in
                (raw.trimmingCharacters(in: .whitespacesAndNewlines), label?.trimmingCharacters(in: .whitespacesAndNewlines))
            }
            .filter { !$0.0.isEmpty }
            .map { TrustedPhone(label: $0.1?.nilIfEmpty, raw: $0.0) }

        let record = TrustedContactRecord(
            id: id,
            displayName: draft.displayName.trimmingCharacters(in: .whitespacesAndNewlines),
            relation: draft.relation?.trimmingCharacters(in: .whitespacesAndNewlines).nilIfEmpty,
            isOwner: draft.isOwner,
            maskedEmail: maskEmail(draft.email),
            addressEncrypted: try FieldCrypto.encrypt(draft.address?.trimmingCharacters(in: .whitespacesAndNewlines)),
            notesEncrypted: try FieldCrypto.encrypt(draft.notes?.trimmingCharacters(in: .whitespacesAndNewlines)),
            numbers: numbers,
            createdAt: records.first(where: { $0.id == id })?.createdAt ?? now,
            updatedAt: now
        )

        if let idx = records.firstIndex(where: { $0.id == id }) {
            records[idx] = record
        } else {
            records.append(record)
        }
        try persist(records)
        contacts = records.map { $0.toMasked() }
    }

    func delete(id: UUID) throws {
        var records = loadRecords()
        records.removeAll { $0.id == id }
        try persist(records)
        contacts = records.map { $0.toMasked() }
    }

    func wipe() throws {
        try? FileManager.default.removeItem(at: storageURL)
        contacts = []
    }

    // MARK: - Persistence

    private func loadRecords() -> [TrustedContactRecord] {
        guard let data = try? Data(contentsOf: storageURL) else { return [] }
        return (try? JSONDecoder.vault.decode([TrustedContactRecord].self, from: data)) ?? []
    }

    private func persist(_ records: [TrustedContactRecord]) throws {
        let sorted = records.sorted { lhs, rhs in
            if lhs.isOwner != rhs.isOwner { return lhs.isOwner }
            return lhs.displayName.lowercased() < rhs.displayName.lowercased()
        }
        let data = try JSONEncoder.vault.encode(sorted)
        try data.write(to: storageURL, options: [.atomic, .completeFileProtection])
    }

    private func maskEmail(_ raw: String?) -> String? {
        guard let raw, !raw.isEmpty, let at = raw.firstIndex(of: "@") else {
            return raw?.isEmpty == true ? nil : "••••"
        }
        let local = String(raw[..<at])
        let domain = String(raw[at...])
        guard local.count > 2 else { return "••" + domain }
        return String(local.first!) + String(repeating: "•", count: local.count - 2) + String(local.last!) + domain
    }
}

private extension TrustedContactRecord {
    func toMasked() -> MaskedContact {
        MaskedContact(
            id: id,
            displayName: displayName,
            relation: relation,
            isOwner: isOwner,
            maskedEmail: maskedEmail,
            maskedNumbers: numbers.map { $0.maskedForDisplay }
        )
    }
}

private extension JSONEncoder {
    static let vault: JSONEncoder = {
        let e = JSONEncoder()
        e.dateEncodingStrategy = .iso8601
        e.outputFormatting = [.sortedKeys]
        return e
    }()
}

private extension JSONDecoder {
    static let vault: JSONDecoder = {
        let d = JSONDecoder()
        d.dateDecodingStrategy = .iso8601
        return d
    }()
}

private extension String {
    var nilIfEmpty: String? { isEmpty ? nil : self }
}
