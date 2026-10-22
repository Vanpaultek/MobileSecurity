// SPDX-FileCopyrightText: 2026 VanPaulTek Inc
// SPDX-License-Identifier: AGPL-3.0-or-later
import SwiftUI
import MessageUI

/// iOS limitation: there is no public API for silent, unattended SMS send on iOS. Any claim
/// to the contrary is false. The best we can do is pre-fill the system Messages composer with
/// the SOS text + recipient and present it; the owner (or coerced owner) taps Send. The
/// coercion scenario still works because the composer appears automatically, the message is
/// pre-written, and the recipient is pre-filled.
///
/// On Android we use `SmsManager.sendTextMessage` for a truly silent send (see
/// `DuressSMSSender.kt`).
struct DuressSMSComposer: UIViewControllerRepresentable {

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

enum DuressSOS {
    /// Standard SOS text — kept short because iOS messages have no body length limits that
    /// matter here, but SMS fallback over cellular may split long bodies into multiple parts.
    static func body(ownerName: String, at date: Date = Date()) -> String {
        let name = ownerName.isEmpty ? "an OnlyTrusted user" : ownerName
        let f = DateFormatter()
        f.dateStyle = .short
        f.timeStyle = .short
        let stamp = f.string(from: date)
        return "\u{26A0}\u{FE0F} OnlyTrusted DURESS ALERT from \(name): duress unlock triggered at \(stamp). If you weren't expecting this, call them or 911."
    }
}
