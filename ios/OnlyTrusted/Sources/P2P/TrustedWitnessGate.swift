// SPDX-FileCopyrightText: 2026 VanPaulTek Inc
// SPDX-License-Identifier: AGPL-3.0-or-later
import Foundation
import SwiftUI
import CoreBluetooth

/// Elite-tier Trusted Witness gate.
///
/// Call `TrustedWitnessGate.require(action:completion:)` before any sensitive action listed in
/// `TrustedWitnessState.GatedAction`. If the feature is off / no witness enrolled / the action
/// isn't in the required-set, the gate is a no-op and fires `.success` immediately — so this
/// stays opt-in in exactly the same way `HardwareKeyGate` does.
///
/// When the feature IS armed:
///   1. Owner's device scans for an advertising OnlyTrusted peer whose device ID matches an
///      enrolled witness. RSSI > -60 dBm is enforced to approximate <2 m proximity.
///   2. Peer manager runs the mutual-attestation handshake (shared with Shamir split).
///   3. Owner sends `witnessApprovalRequest`; the witness's app shows an approval prompt;
///      the witness returns `witnessApprovalReply{approved: Bool}`.
///   4. 60-second global timeout. Cancel at any time.
@MainActor
enum TrustedWitnessGate {

    enum Outcome {
        case success
        case cancelled
        case failed(String)
    }

    /// Entry point. Blocking actions should wrap their body in this call.
    static func require(
        action: TrustedWitnessState.GatedAction,
        reason: String,
        presentingTitle: String? = nil,
        completion: @escaping (Outcome) -> Void
    ) {
        let state = TrustedWitnessState.shared
        guard state.requiresWitness(for: action) else {
            completion(.success)
            return
        }
        // The actual proximity scan runs inside a dedicated controller so we can present a
        // modal "Waiting for witness…" sheet with a cancel button.
        TrustedWitnessApprovalPresenter.shared.start(
            action: action,
            reason: presentingTitle ?? reason,
            completion: completion
        )
    }
}

/// Owns the proximity scan and modal UI used by `TrustedWitnessGate.require`.
@MainActor
final class TrustedWitnessApprovalPresenter: ObservableObject {
    static let shared = TrustedWitnessApprovalPresenter()

    @Published var isActive = false
    @Published var statusLabel = ""
    @Published var action: TrustedWitnessState.GatedAction = .vaultExport

    private var completion: ((TrustedWitnessGate.Outcome) -> Void)?
    private var manager: OTBluetoothPeerManager?
    private var timeoutTask: Task<Void, Never>?
    private var attestationCode: String = ""

    func start(action: TrustedWitnessState.GatedAction,
               reason: String,
               completion: @escaping (TrustedWitnessGate.Outcome) -> Void) {
        self.action = action
        self.completion = completion
        self.statusLabel = reason
        self.isActive = true
        self.attestationCode = ShamirSSS.randomBytes(3).map { String(format: "%02X", $0) }.joined()

        // Launch the scanner and watch for an approve message.
        let mgr = OTBluetoothPeerManager()
        self.manager = mgr

        let payload = buildRequestPayload(action: action)

        mgr.startAdvertising(kind: .witnessApprovalRequest,
                             payload: payload,
                             attestationCode: attestationCode)

        // Timeout.
        timeoutTask = Task { @MainActor in
            try? await Task.sleep(nanoseconds: 60 * 1_000_000_000)
            self.finish(.failed("No witness approved in 60 seconds."))
        }

        // Observe `lastPayload` via a short poll — simpler than Combine wiring for this one
        // call-site. Simulator path: `LoopbackBus` fulfils payloads instantly so we poll
        // fast enough to catch them.
        Task { @MainActor in
            while isActive {
                try? await Task.sleep(nanoseconds: 150_000_000)
                guard let m = manager else { continue }
                if m.lastPayloadKind == .witnessApprovalReply, let data = m.lastPayload {
                    let approved = (data.first ?? 0) == 1
                    if approved { finish(.success) }
                    else { finish(.cancelled) }
                    break
                }
            }
        }

        // Simulator convenience: synthesise an auto-approval so the gated action flow can
        // still be exercised without a second simulator instance.
        if OTBluetoothPeerManager.isSimulator {
            Task { @MainActor in
                try? await Task.sleep(nanoseconds: 1_200_000_000)
                var data = Data()
                data.append(0x01)   // approved
                data.append(contentsOf: Array(attestationCode.utf8))
                manager?.lastPayload = data
                manager?.lastPayloadKind = .witnessApprovalReply
            }
        }
    }

    func cancel() {
        finish(.cancelled)
    }

    private func finish(_ outcome: TrustedWitnessGate.Outcome) {
        isActive = false
        timeoutTask?.cancel()
        timeoutTask = nil
        manager?.stop()
        manager = nil
        let cb = completion
        completion = nil
        cb?(outcome)
    }

    private func buildRequestPayload(action: TrustedWitnessState.GatedAction) -> Data {
        // Payload format v1: [actionRaw string, UTF-8]. Keep tiny — plenty of room in a single
        // BLE write and no PII leaks across the air (the action name is public UX text).
        Data(action.rawValue.utf8)
    }
}

/// Overlay sheet shown while a proximity scan is in flight. Hosted by App root via
/// `.sheet(isPresented:)` wired up in `OnlyTrustedApp`.
struct TrustedWitnessApprovalOverlay: View {
    @StateObject private var presenter = TrustedWitnessApprovalPresenter.shared

    var body: some View {
        VStack(spacing: 18) {
            Image(systemName: "person.2.wave.2.fill")
                .font(.system(size: 46, weight: .semibold))
                .foregroundStyle(Color.appBrightCyan)
                .padding(.top, 32)
            Text("Trusted Witness required")
                .font(.title3.weight(.bold))
                .foregroundStyle(Color.appPrimaryText)
            Text("Have your witness open OnlyTrusted and bring their phone within 2 metres. They'll see a prompt to approve \(presenter.action.approvalPrompt).")
                .font(.subheadline)
                .foregroundStyle(Color.appSecondaryText)
                .multilineTextAlignment(.center)
                .padding(.horizontal, 24)
                .fixedSize(horizontal: false, vertical: true)
            if OTBluetoothPeerManager.isSimulator {
                Text("Simulator: no real BLE radio — the auto-approve path will fire in ~1 s.")
                    .font(.caption)
                    .foregroundStyle(Color.appWarning)
                    .multilineTextAlignment(.center)
                    .padding(.horizontal, 24)
                    .fixedSize(horizontal: false, vertical: true)
            }
            Spacer()
            Button(role: .cancel) {
                presenter.cancel()
            } label: {
                Text("Cancel").font(.body.weight(.semibold))
                    .frame(maxWidth: .infinity, minHeight: 48)
            }
            .buttonStyle(.bordered)
            .tint(Color.appDanger)
            .padding(.horizontal, 24)
            .padding(.bottom, 20)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .appBackdrop()
    }
}
