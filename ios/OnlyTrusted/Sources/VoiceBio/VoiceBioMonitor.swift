// SPDX-FileCopyrightText: 2026 VanPaulTek Inc
// SPDX-License-Identifier: AGPL-3.0-or-later
import Foundation
import CallKit
import SwiftUI

/// Elite feature wiring: when CallKit reports an incoming call has connected AND the
/// user has an enrolled voice-print, capture ~2 seconds of mic audio, compute MFCC,
/// and compare against the enrolled vector. Below `VoiceBioState.matchThreshold` the
/// monitor publishes an `activeAlert` and the main UI shows a warning overlay.
///
/// Important iOS limitations:
///   * CallKit does not surface the caller's phone number for privacy reasons, so we
///     cannot auto-match the incoming call to a specific stored enrollment. We instead
///     compare against ALL enrolled vectors — the HIGHEST similarity wins. If every
///     enrollment similarity falls below threshold AND at least one contact is enrolled,
///     we show the deepfake alert.
///   * We cannot block or screen the call — iOS Elite feature is "passive". The UI only.
///   * `AVAudioEngine` is not permitted to tap the call audio stream directly; we record
///     the microphone side of the call, which is normally silent. This still catches a
///     spoofed caller held near the owner (speaker + mic open) or captures the owner
///     speaking back. In production the user is encouraged to put the call on speaker.
@MainActor
final class VoiceBioMonitor: NSObject, ObservableObject {

    static let shared = VoiceBioMonitor()

    struct Alert: Identifiable, Equatable {
        let id = UUID()
        let bestSimilarity: Float
        let bestContactID: UUID?
        let bestContactName: String?
        let at: Date
    }

    @Published var activeAlert: Alert?

    private let observer = CXCallObserver()
    private var isObserving = false
    private var armedCallID: UUID?
    private var recorder: VoiceBioRecorder?

    private override init() { super.init() }

    /// Start listening for incoming-call connects. Safe to call repeatedly.
    func startIfNeeded() {
        guard !isObserving else { return }
        guard VoiceBioState.shared.featureEnabled else { return }
        observer.setDelegate(self, queue: nil)
        isObserving = true
    }

    func stop() {
        guard isObserving else { return }
        observer.setDelegate(nil, queue: nil)
        isObserving = false
        armedCallID = nil
        _ = recorder?.stop()
        recorder = nil
    }

    func clearAlert() { activeAlert = nil }

    // MARK: - Internal: triggered when CallKit says a call has connected.

    fileprivate func onCallConnected(callID: UUID) {
        guard armedCallID == nil else { return }
        guard VoiceBioState.shared.featureEnabled else { return }
        guard !VoiceBioState.shared.enrolledContactIDs.isEmpty else { return }

        armedCallID = callID
        let recorder = VoiceBioRecorder()
        self.recorder = recorder

        Task { [weak self] in
            guard let self else { return }
            let granted = await VoiceBioRecorder.requestPermission()
            guard granted else { return }

            do { try recorder.start() } catch { return }

            let duration = VoiceBioState.verificationDurationSeconds
            try? await Task.sleep(nanoseconds: UInt64(duration * 1_000_000_000))

            let samples = recorder.stop()
            self.recorder = nil
            self.armedCallID = nil
            await MainActor.run { self.compareAndMaybeAlert(samples: samples) }
        }
    }

    fileprivate func onCallEnded(callID: UUID) {
        guard armedCallID == callID else { return }
        armedCallID = nil
        _ = recorder?.stop()
        recorder = nil
    }

    private func compareAndMaybeAlert(samples: [Float]) {
        guard samples.count >= 1600 else { return } // need > 100 ms of audio
        guard let liveDescriptor = MFCC.utteranceDescriptor(samples: samples) else { return }

        var best: (sim: Float, id: UUID?) = (-1, nil)
        for id in VoiceBioState.shared.enrolledContactIDs {
            guard let stored = VoiceBioState.descriptor(for: id) else { continue }
            let sim = MFCC.cosineSimilarity(stored, liveDescriptor)
            if sim > best.sim { best = (sim, id) }
        }

        guard let id = best.id else { return }
        if best.sim >= VoiceBioState.matchThreshold { return }

        let name = VaultStore.shared.contacts.first { $0.id == id }?.displayName
        let alert = Alert(bestSimilarity: best.sim, bestContactID: id, bestContactName: name, at: Date())
        self.activeAlert = alert

        EventStore.shared.append(
            type: SecurityEventRecord.Kind.voiceDeepfake,
            detail: String(format: "best similarity %.2f vs %@",
                           best.sim, name ?? id.uuidString)
        )
    }
}

extension VoiceBioMonitor: CXCallObserverDelegate {
    nonisolated func callObserver(_ observer: CXCallObserver, callChanged call: CXCall) {
        let id = call.uuid
        let outgoing = call.isOutgoing
        let hasConnected = call.hasConnected
        let hasEnded = call.hasEnded
        Task { @MainActor [weak self] in
            guard let self else { return }
            if outgoing { return }
            if hasEnded {
                self.onCallEnded(callID: id)
            } else if hasConnected {
                self.onCallConnected(callID: id)
            }
        }
    }
}
