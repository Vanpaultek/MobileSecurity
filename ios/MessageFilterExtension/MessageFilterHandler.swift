// SPDX-FileCopyrightText: 2026 VanPaulTek Inc
// SPDX-License-Identifier: AGPL-3.0-or-later
import Foundation
import IdentityLookup

/// iOS calls this whenever an SMS from an unknown sender arrives (iOS does NOT invoke
/// filter extensions for Contacts or prior conversations). Our job: decide whether the
/// message is .junk / .promotion / .transaction / .none.
///
/// Two inputs feed the classifier:
///   1. **Sender** — if on the user's block list with `blockSms` set, return .junk.
///   2. **Message body** — scan for URLs and run each through
///      `SmartLinkGuardRules.inspect(_:)`. If any URL comes back `Caution` or `Danger`,
///      return .junk. This is the Apple-approved on-device path for Smart Link Guard
///      (iOS does not expose a general SMS intercept API — the filter extension is the
///      only legitimate hook, and only for messages from unknown senders).
///
/// Budget: tight. Must return within a few seconds. No network. No UI.
final class MessageFilterHandler: ILMessageFilterExtension {}

extension MessageFilterHandler: ILMessageFilterQueryHandling {

    func handle(_ queryRequest: ILMessageFilterQueryRequest,
                context: ILMessageFilterExtensionContext,
                completion: @escaping (ILMessageFilterQueryResponse) -> Void) {

        let response = ILMessageFilterQueryResponse()
        response.action = classify(
            sender: queryRequest.sender,
            body: queryRequest.messageBody
        )
        completion(response)
    }

    private func classify(sender: String?, body: String?) -> ILMessageFilterAction {
        // Rule 1 — sender on user block list (SMS blocking enabled).
        if let sender, !sender.isEmpty {
            let normalised = PhoneUtils.normalise(sender)
            let hit = BlockListStore.shared.allSorted().first { $0.normalisedNumber == normalised }
            if let hit, hit.blockSms {
                return .junk
            }
        }

        // Rule 2 — Smart Link Guard body scan. Pull every URL in the message, run the
        // shared offline rules, and junk the message if any URL is Caution or Danger.
        if let body, !body.isEmpty, bodyHasRiskyURL(body) {
            return .junk
        }

        return .none
    }

    /// Returns `true` if the body contains an http(s) URL whose Smart Link Guard verdict
    /// is Caution or Danger. The scanner only runs when at least one URL is present, so
    /// the common "safe" case stays cheap.
    private func bodyHasRiskyURL(_ body: String) -> Bool {
        let urls = extractURLs(from: body)
        guard !urls.isEmpty else { return false }
        for url in urls {
            let result = SmartLinkGuardRules.inspect(url)
            if result.verdict == .caution || result.verdict == .danger {
                return true
            }
        }
        return false
    }

    /// Lightweight URL extractor using `NSDataDetector` — safe in app-extension budget,
    /// no network.
    private func extractURLs(from body: String) -> [String] {
        guard let detector = try? NSDataDetector(types: NSTextCheckingResult.CheckingType.link.rawValue) else {
            return []
        }
        let range = NSRange(body.startIndex..., in: body)
        var found: [String] = []
        detector.enumerateMatches(in: body, options: [], range: range) { match, _, _ in
            if let url = match?.url {
                let s = url.absoluteString
                if s.hasPrefix("http://") || s.hasPrefix("https://") {
                    found.append(s)
                }
            }
        }
        return found
    }
}
