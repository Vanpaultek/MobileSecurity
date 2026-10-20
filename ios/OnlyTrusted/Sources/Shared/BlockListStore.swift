// SPDX-FileCopyrightText: 2026 57Million LLC
// SPDX-License-Identifier: AGPL-3.0-or-later
import Foundation
import SwiftUI

/// A single blocked phone number.
///
/// `e164Int64` is the canonical form iOS expects from Call Directory Extensions —
/// a signed Int64 of the digits with the country-code sign flipped off. iOS requires
/// the array passed to `addBlockingEntry(withNextSequentialPhoneNumber:)` to be
/// strictly ascending, so we always sort on load.
struct BlockedNumberRecord: Codable, Identifiable, Hashable {
    let id: UUID
    var raw: String
    var normalisedNumber: String
    var e164Int64: Int64
    var label: String?
    var blockCalls: Bool
    var blockSms: Bool
    var addedAt: Date

    init(id: UUID = UUID(), raw: String, label: String? = nil,
         blockCalls: Bool = true, blockSms: Bool = true, addedAt: Date = Date()) {
        self.id = id
        self.raw = raw
        self.normalisedNumber = PhoneUtils.normalise(raw)
        self.e164Int64 = BlockedNumberRecord.toE164(self.normalisedNumber)
        self.label = label?.trimmingCharacters(in: .whitespacesAndNewlines).ifEmptyNil()
        self.blockCalls = blockCalls
        self.blockSms = blockSms
        self.addedAt = addedAt
    }

    /// Convert "+15551234567" or "15551234567" to 15551234567.
    /// Returns 0 for anything that doesn't look like a dialable number — those rows get
    /// filtered out before being handed to iOS.
    static func toE164(_ normalised: String) -> Int64 {
        let digits = normalised.filter { $0.isNumber }
        return Int64(digits) ?? 0
    }
}

/// Shared store, usable from the main app or either extension.
///
/// Backing store is a plain JSON file in the App Group container. We don't SQLCipher-encrypt
/// this one deliberately: extensions have severely restricted CPU/RAM budgets (CallDirectory
/// is capped at ~45 s / 120 MB) and the data is a block list, not sensitive family data.
final class BlockListStore: @unchecked Sendable {

    static let shared = BlockListStore()

    private let queue = DispatchQueue(label: "com.onlytrusted.app.blocklist", qos: .userInitiated)
    private let filename = "block-list.v1.json"

    private var fileURL: URL? {
        try? SharedContainer.fileURL(named: filename)
    }

    // MARK: - Read

    /// Returns records sorted by e164 ascending — the shape iOS requires for Call Directory feeds.
    func allSorted() -> [BlockedNumberRecord] {
        queue.sync {
            loadRaw().sorted { $0.e164Int64 < $1.e164Int64 }
        }
    }

    /// Returns records sorted by `addedAt` descending — the shape the UI wants.
    func allRecent() -> [BlockedNumberRecord] {
        queue.sync {
            loadRaw().sorted { $0.addedAt > $1.addedAt }
        }
    }

    func contains(normalised: String) -> Bool {
        queue.sync { loadRaw().contains(where: { $0.normalisedNumber == normalised }) }
    }

    // MARK: - Write

    @discardableResult
    func upsert(_ record: BlockedNumberRecord) throws -> BlockedNumberRecord {
        try queue.sync {
            var records = loadRaw()
            if let idx = records.firstIndex(where: { $0.id == record.id }) {
                records[idx] = record
            } else if let dupIdx = records.firstIndex(where: { $0.normalisedNumber == record.normalisedNumber }) {
                // Prevent duplicates by number — update the existing row instead.
                records[dupIdx] = record
            } else {
                records.append(record)
            }
            try persist(records)
            return record
        }
    }

    func remove(id: UUID) throws {
        try queue.sync {
            var records = loadRaw()
            records.removeAll { $0.id == id }
            try persist(records)
        }
    }

    func wipe() throws {
        try queue.sync {
            try persist([])
        }
    }

    // MARK: - Internal

    private func loadRaw() -> [BlockedNumberRecord] {
        guard let fileURL,
              let data = try? Data(contentsOf: fileURL),
              let decoded = try? JSONDecoder.blocklist.decode([BlockedNumberRecord].self, from: data)
        else { return [] }
        return decoded
    }

    private func persist(_ records: [BlockedNumberRecord]) throws {
        guard let fileURL else { throw SharedContainer.ContainerError.missingAppGroup }
        let data = try JSONEncoder.blocklist.encode(records)
        try data.write(to: fileURL, options: [.atomic, .completeFileProtectionUnlessOpen])
    }
}

private extension String {
    func ifEmptyNil() -> String? { isEmpty ? nil : self }
}

private extension JSONEncoder {
    static let blocklist: JSONEncoder = {
        let e = JSONEncoder()
        e.dateEncodingStrategy = .iso8601
        e.outputFormatting = [.sortedKeys]
        return e
    }()
}

private extension JSONDecoder {
    static let blocklist: JSONDecoder = {
        let d = JSONDecoder()
        d.dateDecodingStrategy = .iso8601
        return d
    }()
}
