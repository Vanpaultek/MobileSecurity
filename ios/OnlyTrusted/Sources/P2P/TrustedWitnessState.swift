// SPDX-FileCopyrightText: 2026 VanPaulTek Inc
// SPDX-License-Identifier: AGPL-3.0-or-later
import Foundation
import SwiftUI
import Security

/// Elite-tier Trusted Witness local state.
///
/// Stores up to a handful of enrolled witness device IDs + per-action requirement flags.
/// All data lives in Keychain-backed JSON — a filesystem copy is not useful without the
/// device-only Keychain.
@MainActor
final class TrustedWitnessState: ObservableObject {
    static let shared = TrustedWitnessState()

    enum GatedAction: String, CaseIterable, Identifiable, Codable {
        case vaultExport
        case wipeEverything
        case disarmDuress
        case disableDeadmanWipe
        case changeMasterPIN

        var id: String { rawValue }

        var displayName: String {
            switch self {
            case .vaultExport:         return "Vault export"
            case .wipeEverything:      return "Wipe everything"
            case .disarmDuress:        return "Disarm Duress Mode"
            case .disableDeadmanWipe:  return "Disable Dead-Man Wipe"
            case .changeMasterPIN:     return "Change master PIN"
            }
        }

        /// Short human sentence sent to the witness device during an approval request.
        var approvalPrompt: String {
            switch self {
            case .vaultExport:         return "export the encrypted vault"
            case .wipeEverything:      return "wipe everything on this device"
            case .disarmDuress:        return "disarm Duress Mode"
            case .disableDeadmanWipe:  return "disable the Dead-Man Wipe"
            case .changeMasterPIN:     return "change the master PIN"
            }
        }
    }

    struct Witness: Identifiable, Codable, Equatable {
        let id: UUID
        /// Nickname the owner typed during enrollment.
        var displayName: String
        /// Opaque device ID shown by the witness during pairing. Not a MAC address — just a
        /// stable random handle both apps keep for later re-identification.
        let deviceID: String
        let enrolledAt: Date
    }

    @Published private(set) var witnesses: [Witness] = []
    @Published var featureEnabled: Bool {
        didSet { d.set(featureEnabled, forKey: Keys.enabled) }
    }
    @Published var requiredActions: Set<GatedAction> {
        didSet {
            let arr = requiredActions.map(\.rawValue)
            d.set(arr, forKey: Keys.required)
        }
    }

    private let d = UserDefaults.standard
    private enum Keys {
        static let enabled = "trusted_witness.v1.enabled"
        static let required = "trusted_witness.v1.required"
        static let kcAccount = "trusted-witness-witnesses-v1"
    }

    private init() {
        featureEnabled = d.bool(forKey: Keys.enabled)
        let saved = d.stringArray(forKey: Keys.required) ?? GatedAction.allCases.map(\.rawValue)
        requiredActions = Set(saved.compactMap(GatedAction.init(rawValue:)))
        witnesses = Self.loadWitnesses()
    }

    func addWitness(displayName: String, deviceID: String) {
        let w = Witness(id: UUID(),
                        displayName: displayName,
                        deviceID: deviceID,
                        enrolledAt: Date())
        witnesses.append(w)
        persistWitnesses()
    }

    func removeWitness(id: UUID) {
        witnesses.removeAll { $0.id == id }
        persistWitnesses()
    }

    func clearAll() {
        witnesses = []
        persistWitnesses()
    }

    /// Does the current state require a witness for this action? Honored by `TrustedWitnessGate`.
    func requiresWitness(for action: GatedAction) -> Bool {
        guard featureEnabled, !witnesses.isEmpty else { return false }
        return requiredActions.contains(action)
    }

    // MARK: - Persistence

    private func persistWitnesses() {
        guard let data = try? JSONEncoder().encode(witnesses) else { return }
        let base: [CFString: Any] = [
            kSecClass: kSecClassGenericPassword,
            kSecAttrService: "com.onlytrusted.app.trusted-witness",
            kSecAttrAccount: Keys.kcAccount
        ]
        SecItemDelete(base as CFDictionary)
        var add = base
        add[kSecValueData] = data
        add[kSecAttrAccessible] = kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
        SecItemAdd(add as CFDictionary, nil)
    }

    private static func loadWitnesses() -> [Witness] {
        let query: [CFString: Any] = [
            kSecClass: kSecClassGenericPassword,
            kSecAttrService: "com.onlytrusted.app.trusted-witness",
            kSecAttrAccount: Keys.kcAccount,
            kSecReturnData: true,
            kSecMatchLimit: kSecMatchLimitOne
        ]
        var out: CFTypeRef?
        guard SecItemCopyMatching(query as CFDictionary, &out) == errSecSuccess,
              let data = out as? Data,
              let list = try? JSONDecoder().decode([Witness].self, from: data) else {
            return []
        }
        return list
    }
}
