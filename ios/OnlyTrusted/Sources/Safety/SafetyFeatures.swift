// SPDX-FileCopyrightText: 2026 VanPaulTek Inc
// SPDX-License-Identifier: AGPL-3.0-or-later
import Foundation
import SwiftUI
import MessageUI

// MARK: - F3 Known-contact caller name

/// Looks up a phone number against the trusted-contact vault and returns the display
/// name if a trusted contact owns it. Matching is tolerant: it compares both the raw
/// string and a normalised (E.164-style, leading-zero-stripped) form so free-form input
/// such as "(628) 555-0189" or "+1 628-555-0189" collapses onto the same key as the
/// vault's stored canonical form.
enum CallerLookup {
    /// Returns the trusted contact's display name for a phone-number-like string, or nil.
    /// The input may be a free-form phone number, a label embedded with the number, or any
    /// detail-string pulled from an EventStore row. We extract a `+?\d{7,}` substring first
    /// and compare that against the vault.
    @MainActor
    static func contactNameForNumber(_ phoneLike: String?) -> String? {
        guard let phoneLike, !phoneLike.isEmpty else { return nil }

        // Pull a dialable candidate out of the string. Event detail strings are sometimes
        // "+15551234567" and sometimes "incoming: +15551234567 (US)" — both need to resolve.
        let candidate = extractDialable(from: phoneLike) ?? phoneLike
        let target = PhoneUtils.normalise(candidate)
        guard !target.isEmpty else { return nil }

        for contact in VaultStore.shared.contacts {
            if contact.normalisedNumbers.contains(where: { $0 == target }) {
                return contact.displayName
            }
            // Last-6-digit fallback: handles the case where the vault number lacks a country
            // code but the incoming event has one (very common with US +1 traffic).
            let tail = String(target.suffix(6))
            if tail.count == 6, contact.normalisedNumbers.contains(where: { $0.hasSuffix(tail) }) {
                return contact.displayName
            }
        }
        return nil
    }

    // Pattern is statically valid; the ?? sentinel makes this crash-free even if a
    // future edit breaks the pattern — we just stop finding dialable numbers.
    private static let numberRegex: NSRegularExpression? =
        (try? NSRegularExpression(pattern: "\\+?\\d{7,}"))

    private static func extractDialable(from text: String) -> String? {
        let ns = text as NSString
        let range = NSRange(location: 0, length: ns.length)
        guard let numberRegex,
              let match = numberRegex.firstMatch(in: text, range: range) else { return nil }
        return ns.substring(with: match.range)
    }
}

// MARK: - F2 Auto-lock after inactivity

/// Stores the auto-lock inactivity window and tracks the last background timestamp.
/// We never network: everything is UserDefaults + a wall-clock Date.
enum AutoLockPrefs {
    static let secondsKey = "settings.autolock.seconds"
    static let lastBackgroundedAtKey = "settings.autolock.lastBackgroundedAt"

    /// 0 means "Never" — don't auto-lock on background.
    static let options: [Int] = [30, 60, 300, 900, 0]

    static func label(for seconds: Int) -> String {
        switch seconds {
        case 0: return "Never"
        case ..<60: return "\(seconds) seconds"
        case 60: return "1 minute"
        case 300: return "5 minutes"
        case 900: return "15 minutes"
        default: return "\(seconds / 60) minutes"
        }
    }

    static var currentSeconds: Int {
        let v = UserDefaults.standard.integer(forKey: secondsKey)
        // Default: Never. First-run writes 0 so this stays off until the user opts in.
        return v
    }

    static func setSeconds(_ seconds: Int) {
        UserDefaults.standard.set(seconds, forKey: secondsKey)
    }

    static func markBackgroundedNow() {
        UserDefaults.standard.set(Date().timeIntervalSince1970, forKey: lastBackgroundedAtKey)
    }

    static func elapsedSinceBackground() -> TimeInterval? {
        let raw = UserDefaults.standard.double(forKey: lastBackgroundedAtKey)
        guard raw > 0 else { return nil }
        return Date().timeIntervalSince1970 - raw
    }

    static func clearBackgroundTimestamp() {
        UserDefaults.standard.removeObject(forKey: lastBackgroundedAtKey)
    }
}

// MARK: - F4 Weekly digest + F5 Honest Limits refresher state

/// Tracks the "last time we showed the weekly digest banner" and "last calendar month
/// we showed the honest-limits refresher." Both banners are strictly local, dismissable,
/// and keyed off wall-clock dates persisted in UserDefaults.
enum HomeBannerPrefs {
    private static let digestKey = "home.banner.weeklyDigest.lastShownYMD"
    private static let refresherKey = "home.banner.honestLimitsRefresher.lastShownYM"

    static func shouldShowWeeklyDigest(now: Date = Date()) -> Bool {
        let cal = Calendar.current
        // Only Monday first-open per week. Also require not shown this ISO week.
        let weekday = cal.component(.weekday, from: now) // 2 = Monday
        guard weekday == 2 else { return false }
        let key = isoWeekKey(for: now, cal: cal)
        return UserDefaults.standard.string(forKey: digestKey) != key
    }

    static func markWeeklyDigestShown(now: Date = Date()) {
        let cal = Calendar.current
        UserDefaults.standard.set(isoWeekKey(for: now, cal: cal), forKey: digestKey)
    }

    static func shouldShowHonestLimitsRefresher(now: Date = Date()) -> Bool {
        let key = yearMonthKey(for: now)
        return UserDefaults.standard.string(forKey: refresherKey) != key
    }

    static func markHonestLimitsRefresherShown(now: Date = Date()) {
        UserDefaults.standard.set(yearMonthKey(for: now), forKey: refresherKey)
    }

    private static func isoWeekKey(for date: Date, cal: Calendar) -> String {
        let comps = cal.dateComponents([.yearForWeekOfYear, .weekOfYear], from: date)
        return "\(comps.yearForWeekOfYear ?? 0)-W\(comps.weekOfYear ?? 0)"
    }

    private static func yearMonthKey(for date: Date) -> String {
        let f = DateFormatter()
        f.dateFormat = "yyyy-MM"
        return f.string(from: date)
    }
}

/// Pre-computed counts for the weekly digest. Pulled from EventStore on demand.
struct WeeklyDigestCounts {
    let callsBlocked: Int
    let textsBlocked: Int

    @MainActor
    static func compute() -> WeeklyDigestCounts {
        let now = Date()
        let cal = Calendar.current
        let startOfWeek = cal.date(byAdding: .day, value: -7, to: cal.startOfDay(for: now)) ?? now
        let threshold = Int64(startOfWeek.timeIntervalSince1970 * 1000)
        let rows = EventStore.shared.allRecent(limit: 10_000).filter { $0.timestampMillis >= threshold }
        var calls = 0, texts = 0
        for row in rows {
            switch row.type {
            case SecurityEventRecord.Kind.callBlocked: calls += 1
            case SecurityEventRecord.Kind.smsFlagged:  texts += 1
            default: continue
            }
        }
        return WeeklyDigestCounts(callsBlocked: calls, textsBlocked: texts)
    }
}

// MARK: - F7 Report-scam share text

enum ReportScam {
    static func buildMessage(number: String, timestamp: Int64) -> String {
        let date = Date(timeIntervalSince1970: Double(timestamp) / 1000.0)
        let f = DateFormatter()
        f.dateStyle = .medium
        f.timeStyle = .short
        let human = f.string(from: date)
        let numberOut = number.isEmpty ? "an unknown number" : number
        return "Blocked suspected scam call from \(numberOut) at \(human). Reported via OnlyTrusted."
    }

    /// US carriers accept SMS spam reports forwarded to 7726 ("SPAM").
    static let usSmsShortcode = "7726"
}

// MARK: - F8 Carrier spam report shortcuts

enum CarrierSpamReport {
    enum Carrier: String, CaseIterable, Identifiable {
        case tmobile = "T-Mobile Scam Shield"
        case att = "AT&T ActiveArmor"
        case verizon = "Verizon Call Filter"
        case other = "Other / Learn more"
        var id: String { rawValue }

        /// First-choice deep link into the installed carrier app (if it exposes a scheme),
        /// used by `UIApplication.canOpenURL` to decide whether to launch the app or the
        /// App Store page. Scheme values follow each carrier's public documentation; if the
        /// app isn't installed we fall back to `fallbackURL`.
        var deepLinkURL: URL? {
            switch self {
            case .tmobile: return URL(string: "callerid://")
            case .att: return URL(string: "com.att.mobilesecurity://")
            case .verizon: return URL(string: "vzcallfilter://")
            case .other: return URL(string: "https://www.ftc.gov/faq/consumer-protection/report-unwanted-calls")
            }
        }

        /// App Store (iOS) landing page for the carrier's spam-report app.
        var fallbackURL: URL? {
            switch self {
            case .tmobile: return URL(string: "https://apps.apple.com/app/id1372064399")
            case .att: return URL(string: "https://apps.apple.com/app/id1292652788")
            case .verizon: return URL(string: "https://apps.apple.com/app/id1288605803")
            case .other: return URL(string: "https://www.ftc.gov/faq/consumer-protection/report-unwanted-calls")
            }
        }
    }

    /// Chooses the best URL to open: prefer the deep link if the carrier app is installed,
    /// otherwise fall back to the App Store. If neither can be opened (dev simulator w/o
    /// store) we silently no-op.
    @MainActor
    static func open(_ carrier: Carrier) {
        if let deep = carrier.deepLinkURL, UIApplication.shared.canOpenURL(deep) {
            UIApplication.shared.open(deep)
            return
        }
        if let fallback = carrier.fallbackURL {
            UIApplication.shared.open(fallback)
        }
    }
}

// MARK: - F1 Panic contact helpers

enum PanicContact {
    /// Opens the tel: URL for the user's first trusted contact. Returns false if no
    /// trusted contact exists so the caller can show a prompt instead.
    @MainActor
    @discardableResult
    static func dialFirstTrustedContact() -> Bool {
        guard let number = VaultStore.shared.contacts
            .flatMap({ $0.normalisedNumbers })
            .first,
            let url = URL(string: "tel:\(number)")
        else { return false }
        UIApplication.shared.open(url)
        return true
    }

    @MainActor
    static func firstTrustedContactName() -> String? {
        VaultStore.shared.contacts.first?.displayName
    }

    @MainActor
    static func firstTrustedContactNumber() -> String? {
        VaultStore.shared.contacts
            .flatMap({ $0.normalisedNumbers })
            .first
    }
}

// MARK: - MFMessageComposeViewController wrapper
//
// iOS limitation: there is no "silent SMS" API. `MFMessageComposeViewController` presents
// a pre-filled Messages composer; the user must tap "Send" themselves. We expose it both
// for the panic flow (F1) and the scam-report flow (F7).

struct MessageComposerView: UIViewControllerRepresentable {
    let recipients: [String]
    let body: String
    let onFinish: ((MessageComposeResult) -> Void)?

    static var canSendText: Bool { MFMessageComposeViewController.canSendText() }

    func makeCoordinator() -> Coordinator { Coordinator(onFinish: onFinish) }

    func makeUIViewController(context: Context) -> MFMessageComposeViewController {
        let vc = MFMessageComposeViewController()
        vc.recipients = recipients
        vc.body = body
        vc.messageComposeDelegate = context.coordinator
        return vc
    }

    func updateUIViewController(_ uiViewController: MFMessageComposeViewController, context: Context) {}

    final class Coordinator: NSObject, MFMessageComposeViewControllerDelegate {
        let onFinish: ((MessageComposeResult) -> Void)?
        init(onFinish: ((MessageComposeResult) -> Void)?) {
            self.onFinish = onFinish
        }
        func messageComposeViewController(_ controller: MFMessageComposeViewController,
                                          didFinishWith result: MessageComposeResult) {
            controller.dismiss(animated: true)
            onFinish?(result)
        }
    }
}
