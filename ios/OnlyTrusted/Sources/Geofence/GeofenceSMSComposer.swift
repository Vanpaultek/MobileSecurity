// SPDX-FileCopyrightText: 2026 VanPaulTek Inc
// SPDX-License-Identifier: AGPL-3.0-or-later
import SwiftUI
import MessageUI

/// Geofence-exit SMS composer. iOS has no public API for truly silent, unattended SMS send
/// (same iron rule as Duress Mode) — the best we can do is pre-fill the system Messages
/// composer with the alert body + recipient and present it as a sheet. The owner (or whoever
/// is holding the device) still has to tap Send.
///
/// On Android we fire the SOS via `SmsManager.sendTextMessage` without any user interaction.
struct GeofenceSMSComposer: UIViewControllerRepresentable {

    let recipients: [String]
    let body: String
    let onFinish: (MessageComposeResult) -> Void

    static var canSendText: Bool { MFMessageComposeViewController.canSendText() }

    func makeUIViewController(context: Context) -> MFMessageComposeViewController {
        let vc = MFMessageComposeViewController()
        vc.recipients = recipients
        vc.body = body
        vc.messageComposeDelegate = context.coordinator
        return vc
    }

    func updateUIViewController(_ uiViewController: MFMessageComposeViewController, context: Context) {}

    func makeCoordinator() -> Coordinator { Coordinator(onFinish: onFinish) }

    final class Coordinator: NSObject, MFMessageComposeViewControllerDelegate {
        let onFinish: (MessageComposeResult) -> Void
        init(onFinish: @escaping (MessageComposeResult) -> Void) {
            self.onFinish = onFinish
        }
        func messageComposeViewController(_ controller: MFMessageComposeViewController,
                                          didFinishWith result: MessageComposeResult) {
            controller.dismiss(animated: true)
            onFinish(result)
        }
    }
}
