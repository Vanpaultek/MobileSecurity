// SPDX-FileCopyrightText: 2026 VanPaulTek Inc
// SPDX-License-Identifier: AGPL-3.0-or-later
import Foundation
import Security
import SwiftUI
import UserNotifications

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
    /// 2-letter ISO country code the action applies to, or "WW" for worldwide.
    let region: String
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
              region: "US",
              title: "Freeze credit at Equifax",
              organisation: "Equifax",
              timeEstimate: "~5 min",
              url: Self.url("https://www.equifax.com/personal/credit-report-services/credit-freeze/"),
              notes: "Free. Takes effect within one business day. Reversible with a PIN."),
        .init(id: "experian-freeze",
              phase: .urgentFreeze,
              region: "US",
              title: "Freeze credit at Experian",
              organisation: "Experian",
              timeEstimate: "~5 min",
              url: Self.url("https://www.experian.com/freeze/center.html"),
              notes: "Free. Must be done separately from the other two bureaus."),
        .init(id: "transunion-freeze",
              phase: .urgentFreeze,
              region: "US",
              title: "Freeze credit at TransUnion",
              organisation: "TransUnion",
              timeEstimate: "~5 min",
              url: Self.url("https://www.transunion.com/credit-freeze"),
              notes: "Free. Keep the PIN somewhere safer than this app's screen."),
        .init(id: "innovis-freeze",
              phase: .urgentFreeze,
              region: "US",
              title: "Freeze at Innovis (4th bureau)",
              organisation: "Innovis",
              timeEstimate: "~5 min",
              url: Self.url("https://www.innovis.com/securityFreeze/index"),
              notes: "Smaller bureau that some lenders still pull from. Skipped by most guides."),
        .init(id: "chex-freeze",
              phase: .urgentFreeze,
              region: "US",
              title: "Freeze at ChexSystems",
              organisation: "ChexSystems",
              timeEstimate: "~5 min",
              url: Self.url("https://www.chexsystems.com/security-freeze/place-freeze"),
              notes: "Blocks fraudulent checking/savings account openings in your name."),

        // --- Monitoring ---
        .init(id: "hibp-notify",
              phase: .monitoring,
              region: "US",
              title: "Enroll in Have I Been Pwned notifications",
              organisation: "Have I Been Pwned",
              timeEstimate: "~2 min",
              url: Self.url("https://haveibeenpwned.com/NotifyMe"),
              notes: "Separate from OnlyTrusted's own Breach Watch — gives you Troy Hunt's full feed with explicit breach names."),
        .init(id: "irs-ip-pin",
              phase: .monitoring,
              region: "US",
              title: "Get an IRS Identity Protection PIN",
              organisation: "IRS",
              timeEstimate: "~15 min",
              url: Self.url("https://www.irs.gov/identity-theft-fraud-scams/get-an-identity-protection-pin"),
              notes: "Blocks someone else filing a fraudulent tax return in your name. Needs ID.me verification."),
        .init(id: "ssa-mysocialsecurity",
              phase: .monitoring,
              region: "US",
              title: "Open your my Social Security account",
              organisation: "Social Security Administration",
              timeEstimate: "~15 min",
              url: Self.url("https://www.ssa.gov/myaccount/"),
              notes: "Prevents a thief from creating one in your name first and redirecting your benefits."),
        .init(id: "ssa-eservices-block",
              phase: .monitoring,
              region: "US",
              title: "Block online SSN changes (eServices block)",
              organisation: "Social Security Administration",
              timeEstimate: "~10 min (phone call)",
              url: Self.url("https://www.ssa.gov/pubs/EN-05-10220.pdf"),
              notes: "Call 1-800-772-1213 after you've opened the account above to lock out online changes."),
        .init(id: "annualcreditreport",
              phase: .monitoring,
              region: "US",
              title: "Pull free weekly credit reports",
              organisation: "AnnualCreditReport.com",
              timeEstimate: "~10 min",
              url: Self.url("https://www.annualcreditreport.com/"),
              notes: "The only federally authorised free report site. Weekly reports from all 3 bureaus are now permanent."),

        // --- Recovery ---
        .init(id: "ftc-identitytheft",
              phase: .recovery,
              region: "US",
              title: "File an FTC identity theft report",
              organisation: "FTC",
              timeEstimate: "~20 min",
              url: Self.url("https://www.identitytheft.gov/"),
              notes: "This report is what banks and lenders ask for to reverse fraudulent charges. Save the PDF."),
        .init(id: "fraud-alert-one-call",
              phase: .recovery,
              region: "US",
              title: "Place a 1-year fraud alert (one-call does all three bureaus)",
              organisation: "Equifax / Experian / TransUnion",
              timeEstimate: "~5 min",
              url: Self.url("https://www.equifax.com/personal/credit-report-services/credit-fraud-alerts/"),
              notes: "Lenders must verify your identity before opening new credit. One bureau notifies the others."),
        .init(id: "police-report",
              phase: .recovery,
              region: "US",
              title: "File a local police report",
              organisation: "Local law enforcement",
              timeEstimate: "~45 min",
              url: Self.url("https://www.usa.gov/local-governments"),
              notes: "Bring your FTC report. Some banks require a police report before refunding six-figure losses."),
        .init(id: "dmv-id-theft",
              phase: .recovery,
              region: "US",
              title: "Report ID theft to your state DMV",
              organisation: "State DMV",
              timeEstimate: "~15 min",
              url: Self.url("https://www.usa.gov/motor-vehicle-services"),
              notes: "Blocks someone replacing your driver's licence or state ID. Procedure varies by state.")
    ]

    /// Legacy call-site that returns the US catalog by default. Prefer `grouped(for:)`.
    static func grouped() -> [(IdentityActionPhase, [IdentityAction])] {
        grouped(for: "US")
    }

    /// Region-aware grouping. Returns the entries that apply to `countryCode` (2-letter
    /// ISO) plus any worldwide entries. Unknown codes fall back to the US catalog so the
    /// screen always has something to show.
    static func grouped(for countryCode: String) -> [(IdentityActionPhase, [IdentityAction])] {
        let region = countryCode.uppercased()
        let fallback = regionActions.keys.contains(region) ? region : "US"
        let applicable = (regionActions[fallback] ?? []) + (regionActions["WW"] ?? [])
        return IdentityActionPhase.allCases.map { phase in
            (phase, applicable.filter { $0.phase == phase })
        }
    }

    /// All US actions + India actions + any future per-region additions, keyed by
    /// 2-letter ISO code. "WW" is reserved for worldwide actions (currently empty).
    private static var regionActions: [String: [IdentityAction]] {
        [
            "US": all,
            "IN": indiaActions,
            "WW": []
        ]
    }

    /// India-specific identity-protection actions. Mirrors the US flow (freeze →
    /// monitor → recover) using Indian government / credit-bureau portals.
    static let indiaActions: [IdentityAction] = [
        // --- Urgent freeze (Aadhaar + credit bureau locks) ---
        .init(id: "aadhaar-biometric-lock", phase: .urgentFreeze, region: "IN",
              title: "Lock Aadhaar biometric at UIDAI",
              organisation: "UIDAI",
              timeEstimate: "~5 min",
              url: Self.url("https://resident.uidai.gov.in/aadhaar-lockunlock"),
              notes: "Prevents anyone from using your Aadhaar biometric for KYC until you unlock it. Free. Needs your Aadhaar number + registered mobile."),
        .init(id: "aadhaar-online-lock", phase: .urgentFreeze, region: "IN",
              title: "Lock Aadhaar online services",
              organisation: "UIDAI",
              timeEstimate: "~5 min",
              url: Self.url("https://myaadhaar.uidai.gov.in/"),
              notes: "Blocks e-KYC + Virtual ID requests until unlocked. Separate from biometric lock."),
        .init(id: "cibil-freeze", phase: .urgentFreeze, region: "IN",
              title: "Freeze CIBIL report",
              organisation: "TransUnion CIBIL",
              timeEstimate: "~15 min",
              url: Self.url("https://www.cibil.com/"),
              notes: "India's largest credit bureau. Freeze prevents new loan accounts in your name."),
        .init(id: "experian-india-freeze", phase: .urgentFreeze, region: "IN",
              title: "Freeze Experian India report",
              organisation: "Experian India",
              timeEstimate: "~15 min",
              url: Self.url("https://www.experian.in/"),
              notes: "Second-largest Indian credit bureau. Must freeze separately from CIBIL + CRIF + Equifax."),
        .init(id: "crif-highmark-freeze", phase: .urgentFreeze, region: "IN",
              title: "Freeze CRIF High Mark report",
              organisation: "CRIF High Mark",
              timeEstimate: "~15 min",
              url: Self.url("https://www.crifhighmark.com/"),
              notes: "Lender-focused bureau. Still used by many NBFCs and microfinance institutions."),
        .init(id: "equifax-india-freeze", phase: .urgentFreeze, region: "IN",
              title: "Freeze Equifax India report",
              organisation: "Equifax India",
              timeEstimate: "~15 min",
              url: Self.url("https://www.equifax.co.in/"),
              notes: "Fourth Indian bureau. Required for complete coverage."),

        // --- Monitoring (ongoing watchdog setups) ---
        .init(id: "hibp-notify-india", phase: .monitoring, region: "IN",
              title: "Enroll in Have I Been Pwned notifications",
              organisation: "Have I Been Pwned",
              timeEstimate: "~2 min",
              url: Self.url("https://haveibeenpwned.com/NotifyMe"),
              notes: "Global breach feed. Covers Indian breaches too (BigBasket, JustDial, Zomato, Dr Lal PathLabs, etc.)."),
        .init(id: "aadhaar-history", phase: .monitoring, region: "IN",
              title: "Review Aadhaar authentication history",
              organisation: "UIDAI",
              timeEstimate: "~10 min",
              url: Self.url("https://resident.uidai.gov.in/aadhaar-auth-history"),
              notes: "Shows every e-KYC + biometric auth against your Aadhaar in the last 6 months. Spot unexpected activity early."),
        .init(id: "trai-dnd-register", phase: .monitoring, region: "IN",
              title: "Register with DND (Do Not Disturb)",
              organisation: "TRAI",
              timeEstimate: "~5 min",
              url: Self.url("https://www.ncpr.dnd.gov.in/"),
              notes: "Blocks promotional calls + SMS. SMS 'START 0' to 1909 for full block."),
        .init(id: "income-tax-portal", phase: .monitoring, region: "IN",
              title: "Register / check your Income Tax e-filing account",
              organisation: "Income Tax Department",
              timeEstimate: "~15 min",
              url: Self.url("https://www.incometax.gov.in/"),
              notes: "Enables 2FA on your PAN. Prevents fraudulent returns in your name."),

        // --- Recovery (post-incident reports + helplines) ---
        .init(id: "cybercrime-india", phase: .recovery, region: "IN",
              title: "File a cybercrime report",
              organisation: "National Cyber Crime Reporting Portal",
              timeEstimate: "~20 min",
              url: Self.url("https://cybercrime.gov.in/"),
              notes: "Official MHA portal. Covers financial fraud, impersonation, cyber-stalking. Call 1930 for financial fraud < 24 hours old."),
        .init(id: "ncu-financial", phase: .recovery, region: "IN",
              title: "Call 1930 — Cyber financial fraud helpline",
              organisation: "I4C / Cyber Crime Coordination Centre",
              timeEstimate: "~5 min (call)",
              url: Self.url("https://cybercrime.gov.in/webform/helpline.aspx"),
              notes: "Freeze fraudulent UPI / net-banking transactions in minutes if you call within the golden hour (first 60 min)."),
        .init(id: "nchelpline", phase: .recovery, region: "IN",
              title: "Call 1800-11-4000 — National Consumer Helpline",
              organisation: "Department of Consumer Affairs",
              timeEstimate: "~15 min (call)",
              url: Self.url("https://consumerhelpline.gov.in/"),
              notes: "For non-fraud consumer disputes — unauthorized charges, service denial, warranty issues."),
        .init(id: "local-police-india", phase: .recovery, region: "IN",
              title: "File a local police FIR",
              organisation: "Local police",
              timeEstimate: "~60 min",
              url: Self.url("https://digitalpolice.gov.in/"),
              notes: "Bring your cybercrime.gov.in acknowledgement + bank statement. FIR is required for insurance / chargeback claims above ₹10k."),
    ]
}

// MARK: - Local "done" tracker

/// Tracks which actions the user has checked off. Stored as a comma-separated id list in
/// the Keychain so a filesystem copy of the app sandbox can't read the user's progress.
@MainActor
final class IdentityActionsProgress: ObservableObject {

    static let shared = IdentityActionsProgress()

    @Published private(set) var done: Set<String> = []

    /// Premium-only: per-action `[id: nextReminderDate]`. The reminder fires once per
    /// year and is rescheduled when the user re-arms it from the UI.
    @Published private(set) var reminders: [String: Date] = [:]

    static let reminderYears: TimeInterval = 1

    private let doneKey = "identity_actions.v1.done"
    private let remindersKey = "identity_actions.v1.reminders"

    private init() {
        if let raw = Keychain.get(doneKey) {
            done = Set(raw.split(separator: ",").map(String.init).filter { !$0.isEmpty })
        }
        if let raw = Keychain.get(remindersKey),
           let data = raw.data(using: .utf8),
           let decoded = try? JSONDecoder().decode([String: Date].self, from: data) {
            reminders = decoded
        }
    }

    func toggle(_ id: String) {
        if done.contains(id) { done.remove(id) } else { done.insert(id) }
        persistDone()
    }

    func isDone(_ id: String) -> Bool { done.contains(id) }

    /// Premium: schedule / cancel a yearly renewal reminder for an action.
    func setReminder(_ id: String, title: String, now: Date = Date()) {
        let fireAt = now.addingTimeInterval(Self.reminderYears * 365 * 86400)
        reminders[id] = fireAt
        persistReminders()
        let content = UNMutableNotificationContent()
        content.title = "Renew: \(title)"
        content.body = "It's been a year since you flagged this identity-protection step. Open OnlyTrusted → Identity Protection to re-verify."
        content.sound = .default
        let comps = Calendar.current.dateComponents([.year,.month,.day,.hour,.minute], from: fireAt)
        let trigger = UNCalendarNotificationTrigger(dateMatching: comps, repeats: false)
        let req = UNNotificationRequest(identifier: Self.notifID(id), content: content, trigger: trigger)
        UNUserNotificationCenter.current().add(req) { _ in }
    }

    func clearReminder(_ id: String) {
        reminders.removeValue(forKey: id)
        persistReminders()
        UNUserNotificationCenter.current().removePendingNotificationRequests(
            withIdentifiers: [Self.notifID(id)]
        )
    }

    func reminderDate(_ id: String) -> Date? { reminders[id] }

    func clearAll() {
        reminders.keys.forEach { clearReminder($0) }
        done = []
        reminders = [:]
        Keychain.delete(doneKey)
        Keychain.delete(remindersKey)
    }

    private static func notifID(_ id: String) -> String {
        "identity_actions.v1.reminder.\(id)"
    }

    private func persistDone() {
        let s = done.sorted().joined(separator: ",")
        Keychain.set(s, for: doneKey)
    }

    private func persistReminders() {
        let data = (try? JSONEncoder().encode(reminders)) ?? Data()
        let str = String(data: data, encoding: .utf8) ?? "{}"
        Keychain.set(str, for: remindersKey)
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
