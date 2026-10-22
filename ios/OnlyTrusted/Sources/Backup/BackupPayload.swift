// SPDX-FileCopyrightText: 2026 VanPaulTek Inc
// SPDX-License-Identifier: AGPL-3.0-or-later
import Foundation

/// Serialisable snapshot of everything OnlyTrusted holds locally. Encoded as JSON and then
/// AES-256-GCM encrypted via `BackupCrypto` into a `.ot2bak` file.
///
/// Keep this schema versioned via `schema`; bump the number if we rename or remove fields so
/// old backups can still be decoded by a newer app.
struct BackupPayload: Codable {
    static let currentSchema = "onlytrusted.backup.v1"

    let schema: String
    let exportedAtMillis: Int64
    let appVersion: String
    let platform: String

    let contacts: [TrustedContactRecord]
    let blockedNumbers: [BlockedNumberRecord]
    let events: [SecurityEventRecord]
    let settings: SettingsSnapshot

    struct SettingsSnapshot: Codable {
        let notification: [String: JSONValue]
        let blocking: [String: JSONValue]
        let autoLockSeconds: Int
        let appLockEnabled: Bool
        let subscriptionTier: String
        let subscriptionActive: Bool
    }
}

/// Small JSON-value helper so we can round-trip heterogenous preference dictionaries without
/// pulling in a third-party JSON library.
enum JSONValue: Codable {
    case string(String)
    case int(Int)
    case double(Double)
    case bool(Bool)
    case null

    init(from decoder: Decoder) throws {
        let c = try decoder.singleValueContainer()
        if c.decodeNil() { self = .null; return }
        if let v = try? c.decode(Bool.self) { self = .bool(v); return }
        if let v = try? c.decode(Int.self) { self = .int(v); return }
        if let v = try? c.decode(Double.self) { self = .double(v); return }
        if let v = try? c.decode(String.self) { self = .string(v); return }
        self = .null
    }

    func encode(to encoder: Encoder) throws {
        var c = encoder.singleValueContainer()
        switch self {
        case .string(let v): try c.encode(v)
        case .int(let v): try c.encode(v)
        case .double(let v): try c.encode(v)
        case .bool(let v): try c.encode(v)
        case .null: try c.encodeNil()
        }
    }

    var asAny: Any? {
        switch self {
        case .string(let v): return v
        case .int(let v): return v
        case .double(let v): return v
        case .bool(let v): return v
        case .null: return nil
        }
    }

    static func from(_ any: Any) -> JSONValue {
        switch any {
        case let v as Bool:   return .bool(v)
        case let v as Int:    return .int(v)
        case let v as Double: return .double(v)
        case let v as String: return .string(v)
        default: return .null
        }
    }
}
