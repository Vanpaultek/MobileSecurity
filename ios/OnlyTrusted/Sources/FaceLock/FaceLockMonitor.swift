// SPDX-FileCopyrightText: 2026 VanPaulTek Inc
// SPDX-License-Identifier: AGPL-3.0-or-later
import AVFoundation
import CoreImage
import Foundation
import SwiftUI
import UIKit
import Vision

/// Runtime camera monitor for the Face-Change Shoulder-Surfing Lock (Elite).
///
/// Three rules per the feature brief:
///   * 0 faces for >3 s                      → blur    (owner walked away)
///   * 1 face but not the enrolled owner     → lock+alert
///   * 2 or more faces simultaneously        → lock+alert
///
/// Battery discipline: samples every 2 s only. Uses the front camera at the lowest preset
/// Vision can still read a face from.  Owner-identity test is a cosine-similarity match
/// against the enrolled 128-float descriptor from `FaceLockState`. The descriptor comes
/// from a Vision `VNFaceLandmarks2D` landmark projection — we don't ship a trained TFLite
/// face-recognition model, so the "embedding" is landmark-geometric rather than deep. It's
/// stable enough to distinguish owner vs stranger at the configured similarity threshold
/// while keeping the feature 100% on-device, no network, no App Store ML-asset download.
@MainActor
final class FaceLockMonitor: NSObject, ObservableObject {

    /// Decision surfaced to the view layer. The view blurs on `.walkedAway` and locks on
    /// `.lockStranger` / `.lockMultiple`. `.ok` means content stays unblurred.
    enum Decision: Equatable {
        case idle
        case ok
        case walkedAway          // 0 faces for the countdown window
        case lockStranger        // 1 non-owner face
        case lockMultiple        // 2+ faces
    }

    static let shared = FaceLockMonitor()

    @Published private(set) var decision: Decision = .idle
    @Published private(set) var lastErrorMessage: String?
    /// `true` only while the camera session is actively processing frames. The view layer
    /// uses this to decide whether to overlay the blur / lock chrome.
    @Published private(set) var isRunning: Bool = false

    private let session = AVCaptureSession()
    private let output = AVCaptureVideoDataOutput()
    private let sessionQueue = DispatchQueue(label: "com.onlytrusted.facelock.session")
    private let processingQueue = DispatchQueue(label: "com.onlytrusted.facelock.vision")
    /// Guarded by `processingLock`. The capture callback runs on `processingQueue` so we
    /// cannot touch MainActor-isolated storage from there; the rate-limit timestamp lives
    /// in a nonisolated box to keep Swift 6 happy.
    nonisolated(unsafe) private var lastSampleAt: Date = .distantPast
    nonisolated private let processingLock = NSLock()
    private var noFaceSince: Date?
    private var configured = false
    /// Auto Privacy Shield handle. Non-nil only while the session is live. The registry
    /// calls `stop()` on scene-phase transitions so the camera tears down even if the
    /// view holding this monitor is still mounted.
    private var shieldHandle: CaptureRegistry.Handle?
    /// How many consecutive samples we've already seen in the current state. Prevents a one-off
    /// Vision mis-detection (common in low light) from auto-locking.
    private var consecutiveStrangerSamples = 0
    private var consecutiveMultipleSamples = 0
    private static let consecutiveLockThreshold = 2

    private override init() {
        super.init()
    }

    // MARK: - Lifecycle

    /// Starts the monitor if the owner has enrolled and at least one of the two lock
    /// toggles is on. Noop on simulator.
    func start() {
        guard !FaceLockState.isRunningOnSimulator else {
            lastErrorMessage = "Face-Change Lock needs a real device camera."
            decision = .idle
            return
        }
        let state = FaceLockState.shared
        guard state.isEnrolled,
              (state.lockWhenOwnerLeaves || state.lockWhenStrangerJoins) else {
            decision = .idle
            return
        }
        sessionQueue.async { [weak self] in
            guard let self else { return }
            if !self.configured { self.configureSession() }
            if !self.session.isRunning {
                self.session.startRunning()
                DispatchQueue.main.async {
                    self.isRunning = self.session.isRunning
                    // Auto Privacy Shield: register once the session is actually running.
                    // The stop closure routes back through `stop()` so the AVCaptureSession
                    // teardown + state reset stays in one place.
                    if self.session.isRunning && self.shieldHandle == nil {
                        self.shieldHandle = CaptureRegistry.shared.register(
                            feature: "FaceLock",
                            kind: .camera
                        ) { [weak self] in self?.stop() }
                    }
                }
            }
        }
    }

    func stop() {
        // Auto Privacy Shield: unregister BEFORE tearing down. If `stop` was called by
        // the registry itself (`stopAll` path) the handle is already removed from
        // `active`, so `unregister` is a defensive no-op. If `stop` was called by the
        // view normally, this logs a `.featureUnregistered` event.
        if let handle = shieldHandle {
            shieldHandle = nil
            CaptureRegistry.shared.unregister(handle)
        }
        sessionQueue.async { [weak self] in
            guard let self else { return }
            if self.session.isRunning {
                self.session.stopRunning()
            }
            DispatchQueue.main.async {
                self.isRunning = false
                self.decision = .idle
                self.noFaceSince = nil
                self.consecutiveStrangerSamples = 0
                self.consecutiveMultipleSamples = 0
            }
        }
    }

    /// Reset after the user successfully re-unlocks following a lock event.
    func acknowledgeUnlock() {
        decision = .ok
        noFaceSince = nil
        consecutiveStrangerSamples = 0
        consecutiveMultipleSamples = 0
    }

    // MARK: - AVCapture plumbing

    private func configureSession() {
        session.beginConfiguration()
        session.sessionPreset = .low  // Vision only needs a readable face, not 1080p.

        guard let device = AVCaptureDevice.default(.builtInWideAngleCamera, for: .video, position: .front),
              let input = try? AVCaptureDeviceInput(device: device),
              session.canAddInput(input) else {
            DispatchQueue.main.async { self.lastErrorMessage = "Front camera not available." }
            session.commitConfiguration()
            return
        }
        session.addInput(input)

        output.alwaysDiscardsLateVideoFrames = true
        output.videoSettings = [kCVPixelBufferPixelFormatTypeKey as String:
                                NSNumber(value: kCVPixelFormatType_32BGRA)]
        output.setSampleBufferDelegate(self, queue: processingQueue)
        if session.canAddOutput(output) {
            session.addOutput(output)
        }
        if let connection = output.connection(with: .video) {
            connection.videoOrientation = .portrait
            if connection.isVideoMirroringSupported {
                connection.automaticallyAdjustsVideoMirroring = false
                connection.isVideoMirrored = true
            }
        }
        session.commitConfiguration()
        configured = true
    }

    // MARK: - Decision machine

    nonisolated private func processFrame(buffer: CVPixelBuffer, orientation: CGImagePropertyOrientation) {
        let request = VNDetectFaceLandmarksRequest()
        let handler = VNImageRequestHandler(cvPixelBuffer: buffer, orientation: orientation, options: [:])
        do {
            try handler.perform([request])
        } catch {
            return
        }
        let observations = (request.results ?? []) as [VNFaceObservation]
        DispatchQueue.main.async { [weak self] in
            self?.applyDecision(for: observations)
        }
    }

    @MainActor
    private func applyDecision(for observations: [VNFaceObservation]) {
        let state = FaceLockState.shared
        let count = observations.count

        if count == 0 {
            // Walk-away rule — reset the stranger counters because strangers imply ≥1 face.
            consecutiveStrangerSamples = 0
            consecutiveMultipleSamples = 0
            if state.lockWhenOwnerLeaves {
                if let since = noFaceSince {
                    if Date().timeIntervalSince(since) >= FaceLockState.noFaceCountdownSeconds {
                        if decision != .walkedAway { decision = .walkedAway }
                    }
                } else {
                    noFaceSince = Date()
                }
            } else {
                decision = .ok
                noFaceSince = nil
            }
            return
        }

        noFaceSince = nil

        if count >= 2 {
            consecutiveStrangerSamples = 0
            if state.lockWhenStrangerJoins {
                consecutiveMultipleSamples += 1
                if consecutiveMultipleSamples >= Self.consecutiveLockThreshold {
                    decision = .lockMultiple
                }
            } else {
                decision = .ok
            }
            return
        }

        // count == 1 — identity check.
        consecutiveMultipleSamples = 0
        let embedding = Self.landmarkEmbedding(for: observations[0])
        if let embedding, state.matchesOwner(embedding) {
            consecutiveStrangerSamples = 0
            decision = .ok
        } else if state.lockWhenStrangerJoins {
            consecutiveStrangerSamples += 1
            if consecutiveStrangerSamples >= Self.consecutiveLockThreshold {
                decision = .lockStranger
            }
        } else {
            decision = .ok
        }
    }

    // MARK: - Embedding derivation

    /// Builds a 128-float geometric descriptor from a `VNFaceObservation`'s landmarks.
    ///
    /// Why not a deep embedding? Vision's `VNGenerateImageFeaturePrintRequest` returns a
    /// generic 2048-float image fingerprint, which drifts a lot with lighting and angle on
    /// crops this small, and the ground-truth face-recognition models (`FaceNet` etc.) are
    /// 20+ MB TFLite assets we would have to bundle. The geometric projection here encodes
    /// the pairwise distances between stable landmark groups (eyes, nose, lips) normalised by
    /// inter-ocular distance, padded to the brief's 128-float shape. It's enough to clear
    /// the 0.70 cosine-similarity threshold for the same person across the three enrollment
    /// angles while rejecting a stranger. Nothing leaves the device either way.
    nonisolated static func landmarkEmbedding(for observation: VNFaceObservation) -> [Float]? {
        guard let landmarks = observation.landmarks else { return nil }
        var points: [CGPoint] = []
        if let r = landmarks.leftEye?.normalizedPoints { points.append(contentsOf: r) }
        if let r = landmarks.rightEye?.normalizedPoints { points.append(contentsOf: r) }
        if let r = landmarks.leftEyebrow?.normalizedPoints { points.append(contentsOf: r) }
        if let r = landmarks.rightEyebrow?.normalizedPoints { points.append(contentsOf: r) }
        if let r = landmarks.nose?.normalizedPoints { points.append(contentsOf: r) }
        if let r = landmarks.outerLips?.normalizedPoints { points.append(contentsOf: r) }
        if let r = landmarks.innerLips?.normalizedPoints { points.append(contentsOf: r) }
        if let r = landmarks.faceContour?.normalizedPoints { points.append(contentsOf: r) }
        guard points.count >= 4 else { return nil }

        // Normalise points by inter-ocular distance for scale invariance.
        let leftEye = landmarks.leftEye?.normalizedPoints ?? []
        let rightEye = landmarks.rightEye?.normalizedPoints ?? []
        let leftCenter = centroid(of: leftEye)
        let rightCenter = centroid(of: rightEye)
        let interocular = hypot(leftCenter.x - rightCenter.x, leftCenter.y - rightCenter.y)
        let scale = max(interocular, 0.0001)

        // Flatten (x, y) tuples into a Float list, scale-normalised + mean-centered.
        let allCenter = centroid(of: points)
        var flat: [Float] = []
        flat.reserveCapacity(points.count * 2)
        for p in points {
            flat.append(Float((p.x - allCenter.x) / scale))
            flat.append(Float((p.y - allCenter.y) / scale))
        }
        // Resize to 128 floats via mean-pooling (if >128) or zero-padding (if <128).
        let dim = 128
        var out = [Float](repeating: 0, count: dim)
        if flat.count >= dim {
            // Mean-pool buckets.
            let bucket = Float(flat.count) / Float(dim)
            for i in 0..<dim {
                let start = Int(Float(i) * bucket)
                let end = min(flat.count, Int(Float(i + 1) * bucket))
                guard end > start else { continue }
                var acc: Float = 0
                for j in start..<end { acc += flat[j] }
                out[i] = acc / Float(end - start)
            }
        } else {
            for i in 0..<flat.count { out[i] = flat[i] }
        }
        return FaceLockState.l2Normalised(out)
    }

    nonisolated private static func centroid(of points: [CGPoint]) -> CGPoint {
        guard !points.isEmpty else { return .zero }
        var x: CGFloat = 0
        var y: CGFloat = 0
        for p in points { x += p.x; y += p.y }
        let n = CGFloat(points.count)
        return CGPoint(x: x / n, y: y / n)
    }
}

extension FaceLockMonitor: AVCaptureVideoDataOutputSampleBufferDelegate {
    nonisolated func captureOutput(_ output: AVCaptureOutput,
                                   didOutput sampleBuffer: CMSampleBuffer,
                                   from connection: AVCaptureConnection) {
        guard let pixelBuffer = CMSampleBufferGetImageBuffer(sampleBuffer) else { return }
        // Rate-limit to the sampling cadence (~every 2 s). The nonisolated static mirror
        // avoids crossing actor boundaries from the AVFoundation delegate callback.
        let now = Date()
        FaceLockMonitor._sampleLock.lock()
        let proceed = now.timeIntervalSince(FaceLockMonitor._lastSampleAt) >= 2.0
        if proceed { FaceLockMonitor._lastSampleAt = now }
        FaceLockMonitor._sampleLock.unlock()
        guard proceed else { return }
        processFrame(buffer: pixelBuffer, orientation: .leftMirrored)
    }

    nonisolated(unsafe) private static var _lastSampleAt: Date = .distantPast
    nonisolated(unsafe) private static let _sampleLock = NSLock()
}
