// SPDX-FileCopyrightText: 2026 VanPaulTek Inc
// SPDX-License-Identifier: AGPL-3.0-or-later
import Foundation

/// Persisted state for Smart Link Guard (Pro).
///
/// Stores up to 20 recent URL inspections (SMS via the share extension, QR scans, and
/// manual pastes) in UserDefaults as a JSON blob. The Share Extension writes to the
/// app-group UserDefaults so the main app can show the entry when the owner next opens
/// Smart Link Guard; the main app writes to the standard suite.
public enum SmartLinkGuardScanSource: String, Codable, Sendable {
    case manual = "Manual"
    case qr = "QR"
    case sms = "SMS"
}

public struct SmartLinkGuardScan: Codable, Identifiable, Sendable, Equatable {
    public let id: UUID
    public let timestamp: Date
    public let url: String
    public let verdictRaw: Int
    public let source: SmartLinkGuardScanSource
    public let topReason: String

    public var verdict: URLVerdict { URLVerdict(rawValue: verdictRaw) ?? .caution }

    public init(url: String, verdict: URLVerdict, source: SmartLinkGuardScanSource, topReason: String) {
        self.id = UUID()
        self.timestamp = Date()
        self.url = url
        self.verdictRaw = verdict.rawValue
        self.source = source
        self.topReason = topReason
    }
}

@MainActor
public final class SmartLinkGuardState: ObservableObject {
    public static let shared = SmartLinkGuardState()

    @Published public private(set) var recent: [SmartLinkGuardScan] = []

    private let d = UserDefaults.standard
    private let key = "smartlinkguard.v1.recent"
    private let maxEntries = 20

    private init() {
        reload()
    }

    public func reload() {
        guard let raw = d.data(forKey: key) else {
            recent = []
            return
        }
        if let decoded = try? JSONDecoder().decode([SmartLinkGuardScan].self, from: raw) {
            recent = decoded
        } else {
            recent = []
        }
    }

    public func record(_ scan: SmartLinkGuardScan) {
        var next = recent
        next.insert(scan, at: 0)
        if next.count > maxEntries { next = Array(next.prefix(maxEntries)) }
        recent = next
        persist()
    }

    public func clear() {
        recent = []
        d.removeObject(forKey: key)
    }

    private func persist() {
        guard let data = try? JSONEncoder().encode(recent) else { return }
        d.set(data, forKey: key)
    }
}
