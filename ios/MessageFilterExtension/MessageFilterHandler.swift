// SPDX-FileCopyrightText: 2026 57Million LLC
// SPDX-License-Identifier: AGPL-3.0-or-later
import Foundation
import IdentityLookup

/// iOS calls this whenever an SMS from an unknown sender arrives (iOS does NOT invoke
/// filter extensions for Contacts or prior conversations). Our job: decide whether the
/// message is .junk / .promotion / .transaction / .none based on the sender.
///
/// Budget: tight. Must return within a few seconds. No network. No UI.
final class MessageFilterHandler: ILMessageFilterExtension {}

extension MessageFilterHandler: ILMessageFilterQueryHandling {

    func handle(_ queryRequest: ILMessageFilterQueryRequest,
                context: ILMessageFilterExtensionContext,
                completion: @escaping (ILMessageFilterQueryResponse) -> Void) {

        let response = ILMessageFilterQueryResponse()
        response.action = classify(sender: queryRequest.sender)
        completion(response)
    }

    private func classify(sender: String?) -> ILMessageFilterAction {
        guard let sender, !sender.isEmpty else { return .none }
        let normalised = PhoneUtils.normalise(sender)

        // If the sender is on the block list AND the user asked us to flag SMS from them,
        // mark the message as junk — iOS routes it to the "Junk" tab in Messages and
        // suppresses the notification.
        let hit = BlockListStore.shared.allSorted().first { $0.normalisedNumber == normalised }
        if let hit, hit.blockSms {
            return .junk
        }
        return .none
    }
}
