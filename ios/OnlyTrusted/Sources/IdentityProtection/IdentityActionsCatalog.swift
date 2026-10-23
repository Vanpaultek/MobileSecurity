// SPDX-FileCopyrightText: 2026 VanPaulTek Inc
// SPDX-License-Identifier: AGPL-3.0-or-later
import Foundation
import Security
import SwiftUI

/// Static catalog of identity-protection actions. All entries deep-link to official
/// self-service portals (US bureaus and federal agencies). OnlyTrusted does **not**
/// contact any bureau or agency on the user's behalf — the screen is a visibility and
/// shortcut tool. "Done" state is tracked locally in the Keychain.
///
/// Grouped into three phases:
///  * urgentFreeze — things to do first the moment you suspect fraud (credit freeze +
///    fraud alert).
///  * monitoring  — ongoing watchdog setups (HIBP, IRS PIN, SSA account block).
///  * recovery    — what to do after a confirmed incident (FTC IdentityTheft.gov, local
///    police report, DMV record flag).
enum IdentityActionPhase: String, Codable, CaseIterable, Identifiable {
    case urgentFreeze
    case monitoring
    case recovery

    var id: String { rawValue }

    var displayName: String {
        switch self {
        case .urgentFreeze: return "Freeze & alert — do first"
        case .monitoring:   return "Ongoing monitoring"
        case .recovery:     return "If fraud has already happened"
        }
    }

    var subtitle: String {
        switch self {
        case .urgentFreeze: return "Free with all three bureaus. Blocks new credit lines opened in your name."
        case .monitoring:   return "Set once, run forever. Catches issues early."
        case .recovery:     return "Formal reports that unlock bank reimbursements and new credit lines."
        }
    }
}

struct IdentityAction: Identifiable, Equatable {
    let id: String
    let phase: IdentityActionPhase
    let title: String
    let organisation: String
    let timeEstimate: String
    let url: URL
    let notes: String
}

enum IdentityActionsCatalog {

    /// Safe URL builder for the hardcoded action list. If a URL string is ever edited to
    /// become malformed, we fall back to `about:blank` so the row still renders — far
    /// better than an in-production crash from a `URL(string:)!` force unwrap.
    fileprivate static func url(_ str: String) -> URL {
        // URL(fileURLWithPath:) never fails, so the fallback is crash-free even if the
        // string ever becomes malformed. Fallback will no-op in UIApplication.open.
        URL(string: str) ?? URL(fileURLWithPath: "/")
    }

    static let all: [IdentityAction] = [
        // --- Urgent freeze ---
        .init(id: "equifax-freeze",
              phase: .urgentFreeze,
              title: "Freeze credit at Equifax",
              organisation: "Equifax",
              timeEstimate: "~5 min",
              url: Self.url("https://www.equifax.com/personal/credit-report-services/credit-freeze/"),
              notes: "Free. Takes effect within one business day. Reversible with a PIN."),
        .init(id: "experian-freeze",
              phase: .urgentFreeze,
              title: "Freeze credit at Experian",
              organisation: "Experian",
              timeEstimate: "~5 min",
              url: Self.url("https://www.experian.com/freeze/center.html"),
              notes: "Free. Must be done separately from the other two bureaus."),
        .init(id: "transunion-freeze",
              phase: .urgentFreeze,
              title: "Freeze credit at TransUnion",
              organisation: "TransUnion",
              timeEstimate: "~5 min",
              url: Self.url("https://www.transunion.com/credit-freeze"),
              notes: "Free. Keep the PIN somewhere safer than this app's screen."),
        .init(id: "innovis-freeze",
              phase: .urgentFreeze,
              title: "Freeze at Innovis (4th bureau)",
              organisation: "Innovis",
              timeEstimate: "~5 min",
              url: Self.url("https://www.innovis.com/securityFreeze/index"),
              notes: "Smaller bureau that some lenders still pull from. Skipped by most guides."),
        .init(id: "chex-freeze",
              phase: .urgentFreeze,
              title: "Freeze at ChexSystems",
              organisation: "ChexSystems",
              timeEstimate: "~5 min",
              url: Self.url("https://www.chexsystems.com/security-freeze/place-freeze"),
              notes: "Blocks fraudulent checking/savings account openings in your name."),

        // --- Monitoring ---
        .init(id: "hibp-notify",
              phase: .monitoring,
              title: "Enroll in Have I Been Pwned notifications",
              organisation: "Have I Been Pwned",
              timeEstimate: "~2 min",
              url: Self.url("https://haveibeenpwned.com/NotifyMe"),
              notes: "Separate from OnlyTrusted's own Breach Watch — gives you Troy Hunt's full feed with explicit breach names."),
        .init(id: "irs-ip-pin",
              phase: .monitoring,
              title: "Get an IRS Identity Protection PIN",
              organisation: "IRS",
              timeEstimate: "~15 min",
              url: Self.url("https://www.irs.gov/identity-theft-fraud-scams/get-an-identity-protection-pin"),
              notes: "Blocks someone else filing a fraudulent tax return in your name. Needs ID.me verification."),
        .init(id: "ssa-mysocialsecurity",
              phase: .monitoring,
              title: "Open your my Social Security account",
              organisation: "Social Security Administration",
              timeEstimate: "~15 min",
              url: Self.url("https://www.ssa.gov/myaccount/"),
              notes: "Prevents a thief from creating one in your name first and redirecting your benefits."),
        .init(id: "ssa-eservices-block",
              phase: .monitoring,
              title: "Block online SSN changes (eServices block)",
              organisation: "Social Security Administration",
              timeEstimate: "~10 min (phone call)",
              url: Self.url("https://www.ssa.gov/pubs/EN-05-10220.pdf"),
              notes: "Call 1-800-772-1213 after you've opened the account above to lock out online changes."),
        .init(id: "annualcreditreport",
              phase: .monitoring,
              title: "Pull free weekly credit reports",
              organisation: "AnnualCreditReport.com",
              timeEstimate: "~10 min",
              url: Self.url("https://www.annualcreditreport.com/"),
              notes: "The only federally authorised free report site. Weekly reports from all 3 bureaus are now permanent."),

        // --- Recovery ---
        .init(id: "ftc-identitytheft",
              phase: .recovery,
              title: "File an FTC identity theft report",
              organisation: "FTC",
              timeEstimate: "~20 min",
              url: Self.url("https://www.identitytheft.gov/"),
              notes: "This report is what banks and lenders ask for to reverse fraudulent charges. Save the PDF."),
        .init(id: "fraud-alert-one-call",
              phase: .recovery,
              title: "Place a 1-year fraud alert (one-call does all three bureaus)",
              organisation: "Equifax / Experian / TransUnion",
              timeEstimate: "~5 min",
              url: Self.url("https://www.equifax.com/personal/credit-report-services/credit-fraud-alerts/"),
              notes: "Lenders must verify your identity before opening new credit. One bureau notifies the others."),
        .init(id: "police-report",
              phase: .recovery,
              title: "File a local police report",
              organisation: "Local law enforcement",
              timeEstimate: "~45 min",
              url: Self.url("https://www.usa.gov/local-governments"),
              notes: "Bring your FTC report. Some banks require a police report before refunding six-figure losses."),
        .init(id: "dmv-id-theft",
              phase: .recovery,
              title: "Report ID theft to your state DMV",
              organisation: "State DMV",
              timeEstimate: "~15 min",
              url: Self.url("https://www.usa.gov/motor-vehicle-services"),
              notes: "Blocks someone replacing your driver's licence or state ID. Procedure varies by state.")
    ]

    static func grouped() -> [(IdentityActionPhase, [IdentityAction])] {
        IdentityActionPhase.allCases.map { phase in
            (phase, all.filter { $0.phase == phase })
        }
    }
}

// MARK: - Local "done" tracker

/// Tracks which actions the user has checked off. Stored as a comma-separated id list in
/// the Keychain so a filesystem copy of the app sandbox can't read the user's progress.
@MainActor
final class IdentityActionsProgress: ObservableObject {

    static let shared = IdentityActionsProgress()

    @Published private(set) var done: Set<String> = []

    private let key = "identity_actions.v1.done"

    private init() {
        if let raw = Keychain.get(key) {
            done = Set(raw.split(separator: ",").map(String.init).filter { !$0.isEmpty })
        }
    }

    func toggle(_ id: String) {
        if done.contains(id) { done.remove(id) } else { done.insert(id) }
        persist()
    }

    func isDone(_ id: String) -> Bool { done.contains(id) }

    func clearAll() {
        done = []
        Keychain.delete(key)
    }

    private func persist() {
        let s = done.sorted().joined(separator: ",")
        Keychain.set(s, for: key)
    }
}

// Minimal Keychain shim (duplicated across features so each stays self-contained — see
// BreachWatchState.swift for the same pattern).
private enum Keychain {
    static func set(_ value: String, for key: String) {
        let data = Data(value.utf8)
        let q: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrAccount as String: key
        ]
        let attrs: [String: Any] = [
            kSecValueData as String: data,
            kSecAttrAccessible as String: kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
        ]
        if SecItemUpdate(q as CFDictionary, attrs as CFDictionary) == errSecItemNotFound {
            var insert = q
            insert[kSecValueData as String] = data
            insert[kSecAttrAccessible as String] = kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
            SecItemAdd(insert as CFDictionary, nil)
        }
    }

    static func get(_ key: String) -> String? {
        let q: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrAccount as String: key,
            kSecReturnData as String: true,
            kSecMatchLimit as String: kSecMatchLimitOne
        ]
        var out: AnyObject?
        guard SecItemCopyMatching(q as CFDictionary, &out) == errSecSuccess,
              let d = out as? Data else { return nil }
        return String(data: d, encoding: .utf8)
    }

    static func delete(_ key: String) {
        let q: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrAccount as String: key
        ]
        SecItemDelete(q as CFDictionary)
    }
}
