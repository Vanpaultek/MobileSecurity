// SPDX-FileCopyrightText: 2026 VanPaulTek Inc
// SPDX-License-Identifier: AGPL-3.0-or-later
import Foundation
@preconcurrency import AVFoundation

/// Lightweight audio recorder for Voice Biometric enrollment + verification. We use
/// `AVAudioEngine` (not `AVAudioRecorder`) so we can tap the input bus directly and build
/// an in-memory `Float` buffer — no file ever hits disk. Mono, 16 kHz, Float32.
///
/// Simulator note: there is no microphone on the simulator; `start` returns `.simulator`
/// and the caller shows an inline fallback explainer.
@MainActor
final class VoiceBioRecorder {

    enum StartError: Error, LocalizedError {
        case permissionDenied
        case simulator
        case engineFailed(String)

        var errorDescription: String? {
            switch self {
            case .permissionDenied:
                return "Microphone permission is required. Open iOS Settings → OnlyTrusted → Microphone."
            case .simulator:
                return "The iOS Simulator has no microphone, so voice enrollment can only be verified on a real device."
            case .engineFailed(let msg):
                return "Couldn't start the audio engine: \(msg)"
            }
        }
    }

    private let engine = AVAudioEngine()
    private var buffer: [Float] = []
    private var active = false
    private let targetSampleRate: Double = MFCC.sampleRate
    /// Auto Privacy Shield handle. Non-nil only while capture is running. Teardown goes
    /// through `stop()` so the engine, audio-session, and registry entry stay in sync.
    private var shieldHandle: CaptureRegistry.Handle?

    deinit {
        // Nothing to tear down from `deinit`; `stop()` on the main actor already removed
        // the tap and stopped the engine. Reaching `deinit` with `active == true` means
        // the SwiftUI view holding the recorder was dropped mid-recording, so the system
        // reclaims the engine. Explicit teardown would race with the main actor.
    }

    /// Request microphone permission (returns true if authorized).
    static func requestPermission() async -> Bool {
        await withCheckedContinuation { cont in
            AVAudioApplication.requestRecordPermission { granted in
                cont.resume(returning: granted)
            }
        }
    }

    /// Begin capturing audio into the in-memory buffer.
    func start() throws {
        #if targetEnvironment(simulator)
        throw StartError.simulator
        #else
        guard !active else { return }
        buffer.removeAll(keepingCapacity: true)

        let session = AVAudioSession.sharedInstance()
        try session.setCategory(.record, mode: .measurement, options: [.duckOthers])
        try session.setActive(true, options: .notifyOthersOnDeactivation)

        let input = engine.inputNode
        let format = input.inputFormat(forBus: 0)
        let sr = format.sampleRate
        let decimation = max(1, Int(sr / targetSampleRate))

        input.installTap(onBus: 0, bufferSize: 1024, format: format) { [weak self] buf, _ in
            guard let self else { return }
            guard let channelData = buf.floatChannelData else { return }
            let frames = Int(buf.frameLength)
            let ptr = channelData[0]
            var localCopy = [Float]()
            localCopy.reserveCapacity(frames / decimation + 1)
            var i = 0
            while i < frames {
                localCopy.append(ptr[i])
                i += decimation
            }
            Task { @MainActor [weak self] in
                self?.buffer.append(contentsOf: localCopy)
            }
        }

        do {
            try engine.start()
            active = true
        } catch {
            throw StartError.engineFailed(error.localizedDescription)
        }
        // Auto Privacy Shield: register the live mic so a background/lock transition
        // tears the engine down even if the enrollment countdown is mid-flight.
        shieldHandle = CaptureRegistry.shared.register(
            feature: "VoiceBio",
            kind: .microphone
        ) { [weak self] in _ = self?.stop() }
        #endif
    }

    /// Stop the engine and return the captured mono 16 kHz Float sample buffer.
    @discardableResult
    func stop() -> [Float] {
        // Idempotent. Called by the enrollment flow on normal completion, by the view on
        // cancel, and by the shield registry when the scene backgrounds.
        if let handle = shieldHandle {
            shieldHandle = nil
            CaptureRegistry.shared.unregister(handle)
        }
        guard active else { return buffer }
        engine.inputNode.removeTap(onBus: 0)
        engine.stop()
        active = false
        try? AVAudioSession.sharedInstance().setActive(false, options: .notifyOthersOnDeactivation)
        return buffer
    }
}
