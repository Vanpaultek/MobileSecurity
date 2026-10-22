// SPDX-FileCopyrightText: 2026 VanPaulTek Inc
// SPDX-License-Identifier: AGPL-3.0-or-later
import AVFoundation
import SwiftUI
import Vision

/// Face-Change Shoulder-Surfing Lock (Elite) — Settings → Security & data entry point.
///
/// Caller gates on `SubscriptionState.hasElite`; this screen assumes the user is on Elite.
/// Three-angle enrollment (straight / slight-left / slight-right) averages into the owner
/// embedding stored in Keychain by `FaceLockState`.
struct FaceLockSetupScreen: View {
    @Environment(\.dismiss) private var dismiss
    @StateObject private var state = FaceLockState.shared

    @State private var isEnrolling = false
    @State private var showClearConfirm = false

    var body: some View {
        ScrollView {
            VStack(spacing: 18) {
                headerCard

                PremiumCard {
                    VStack(alignment: .leading, spacing: 12) {
                        CardHeader(icon: "face.dashed", title: "Owner enrollment")
                        if FaceLockState.isRunningOnSimulator {
                            simulatorNoticeView
                        } else if state.isEnrolled {
                            enrolledSummary
                        } else {
                            Text("Enroll your face so OnlyTrusted can tell when the person in front of the camera isn't you.")
                                .font(.footnote)
                                .foregroundStyle(Color.appSecondaryText)
                                .fixedSize(horizontal: false, vertical: true)
                            Button {
                                isEnrolling = true
                            } label: {
                                Text("Enroll your face")
                                    .font(.body.weight(.semibold))
                                    .frame(maxWidth: .infinity, minHeight: 48)
                            }
                            .buttonStyle(.borderedProminent)
                            .tint(Color.appAccent)
                        }
                    }
                }

                PremiumCard {
                    VStack(alignment: .leading, spacing: 10) {
                        CardHeader(icon: "slider.horizontal.3", title: "What triggers the lock")
                        Toggle(isOn: $state.lockWhenOwnerLeaves) {
                            VStack(alignment: .leading, spacing: 2) {
                                Text("Lock when owner leaves frame")
                                    .font(.subheadline.weight(.semibold))
                                    .foregroundStyle(Color.appPrimaryText)
                                Text("Hide content after 3 seconds with no face in view.")
                                    .font(.caption)
                                    .foregroundStyle(Color.appSecondaryText)
                                    .fixedSize(horizontal: false, vertical: true)
                            }
                        }
                        .tint(Color.appAccent)

                        Toggle(isOn: $state.lockWhenStrangerJoins) {
                            VStack(alignment: .leading, spacing: 2) {
                                Text("Lock when stranger joins frame")
                                    .font(.subheadline.weight(.semibold))
                                    .foregroundStyle(Color.appPrimaryText)
                                Text("Instant lock + alert if a non-owner or extra face is seen.")
                                    .font(.caption)
                                    .foregroundStyle(Color.appSecondaryText)
                                    .fixedSize(horizontal: false, vertical: true)
                            }
                        }
                        .tint(Color.appAccent)

                        Toggle(isOn: $state.blurOnExit) {
                            VStack(alignment: .leading, spacing: 2) {
                                Text("Blur sensitive screens on exit")
                                    .font(.subheadline.weight(.semibold))
                                    .foregroundStyle(Color.appPrimaryText)
                                Text("Overlay a soft blur instead of a hard lock when you walk away.")
                                    .font(.caption)
                                    .foregroundStyle(Color.appSecondaryText)
                                    .fixedSize(horizontal: false, vertical: true)
                            }
                        }
                        .tint(Color.appAccent)
                    }
                }

                if state.isEnrolled {
                    Button(role: .destructive) {
                        showClearConfirm = true
                    } label: {
                        Text("Remove enrollment")
                            .font(.body.weight(.semibold))
                            .frame(maxWidth: .infinity, minHeight: 48)
                    }
                    .buttonStyle(.bordered)
                    .tint(Color.appDanger)
                    .padding(.horizontal, 4)
                }
            }
            .padding(20)
        }
        .appBackdrop()
        .navigationTitle("Face-Change Lock")
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .topBarTrailing) {
                Button("Done") { dismiss() }.tint(Color.appBrightCyan)
            }
        }
        .fullScreenCover(isPresented: $isEnrolling) {
            FaceLockEnrollmentFlow(onDone: { isEnrolling = false })
        }
        .confirmationDialog("Remove enrollment?", isPresented: $showClearConfirm, titleVisibility: .visible) {
            Button("Remove", role: .destructive) { state.clearEnrollment() }
            Button("Cancel", role: .cancel) {}
        } message: {
            Text("You'll need to re-enroll your face before OnlyTrusted can tell you apart from strangers again.")
        }
    }

    private var headerCard: some View {
        PremiumCard {
            HStack(alignment: .top, spacing: 14) {
                ZStack {
                    RoundedRectangle(cornerRadius: 14, style: .continuous)
                        .fill(
                            LinearGradient(colors: [Color.appAccent, Color.appAccent.opacity(0.7)],
                                           startPoint: .topLeading, endPoint: .bottomTrailing)
                        )
                        .shadow(color: Color.appAccent.opacity(0.5), radius: 10)
                    Image(systemName: "face.dashed")
                        .font(.system(size: 24, weight: .semibold))
                        .foregroundStyle(.white)
                }
                .frame(width: 54, height: 54)
                VStack(alignment: .leading, spacing: 6) {
                    Text("Elite feature")
                        .font(.caption.weight(.semibold))
                        .foregroundStyle(Color.subGold)
                    Text("Guards against over-the-shoulder peeks")
                        .font(.subheadline.weight(.semibold))
                        .foregroundStyle(Color.appPrimaryText)
                    Text("While sensitive screens are open, the front camera watches for faces: it blurs when you walk away and locks the moment someone else looks at your screen. Images are processed in memory and never stored.")
                        .font(.footnote)
                        .foregroundStyle(Color.appSecondaryText)
                        .fixedSize(horizontal: false, vertical: true)
                }
            }
        }
    }

    private var enrolledSummary: some View {
        VStack(alignment: .leading, spacing: 10) {
            Label {
                Text("Owner face enrolled")
                    .font(.subheadline.weight(.semibold))
                    .foregroundStyle(Color.appPrimaryText)
            } icon: {
                Image(systemName: "checkmark.seal.fill")
                    .foregroundStyle(Color.appSuccess)
            }
            Button {
                isEnrolling = true
            } label: {
                Text("Re-enroll")
                    .font(.body.weight(.semibold))
                    .frame(maxWidth: .infinity, minHeight: 44)
            }
            .buttonStyle(.bordered)
            .tint(Color.appAccent)
        }
    }

    private var simulatorNoticeView: some View {
        VStack(alignment: .leading, spacing: 10) {
            Label {
                Text("Camera unavailable in Simulator")
                    .font(.subheadline.weight(.semibold))
                    .foregroundStyle(Color.appPrimaryText)
            } icon: {
                Image(systemName: "exclamationmark.triangle.fill")
                    .foregroundStyle(Color.appWarning)
            }
            Text("Camera-based detection isn't available on the iOS Simulator. Test on a physical device.")
                .font(.footnote)
                .foregroundStyle(Color.appSecondaryText)
                .fixedSize(horizontal: false, vertical: true)
        }
    }
}

// MARK: - Enrollment flow

/// Three-angle enrollment capture (straight / slight-left / slight-right). Each stage waits
/// for a stable Vision face detection and extracts a landmark embedding; the three samples
/// are averaged and persisted by `FaceLockState.setEnrollment`.
private struct FaceLockEnrollmentFlow: View {
    let onDone: () -> Void
    @StateObject private var controller = FaceLockEnrollmentController()

    var body: some View {
        ZStack {
            Color.black.ignoresSafeArea()

            if FaceLockState.isRunningOnSimulator {
                VStack(spacing: 16) {
                    Image(systemName: "exclamationmark.triangle.fill")
                        .font(.system(size: 46, weight: .semibold))
                        .foregroundStyle(Color.appWarning)
                    Text("Camera unavailable in Simulator")
                        .font(.title3.weight(.bold))
                        .foregroundStyle(Color.appPrimaryText)
                    Text("Camera-based detection isn't available on the iOS Simulator. Run OnlyTrusted on a physical device to enroll.")
                        .font(.footnote)
                        .foregroundStyle(Color.appSecondaryText)
                        .multilineTextAlignment(.center)
                        .padding(.horizontal, 32)
                    Button("Close", action: onDone)
                        .buttonStyle(.borderedProminent)
                        .tint(Color.appAccent)
                        .padding(.top, 8)
                }
                .padding()
            } else {
                FaceLockCameraPreview(controller: controller)
                    .ignoresSafeArea()
                    .allowsHitTesting(false)

                VStack {
                    Spacer()
                    VStack(spacing: 12) {
                        Text(controller.promptTitle)
                            .font(.title3.weight(.bold))
                            .foregroundStyle(.white)
                            .multilineTextAlignment(.center)
                        Text(controller.promptDetail)
                            .font(.footnote)
                            .foregroundStyle(.white.opacity(0.85))
                            .multilineTextAlignment(.center)
                            .padding(.horizontal, 20)
                        ProgressView(value: Double(controller.stageIndex),
                                     total: Double(FaceLockEnrollmentController.totalStages))
                            .tint(Color.appAccent)
                            .padding(.horizontal, 24)
                        if controller.finished {
                            Button {
                                controller.commit()
                                onDone()
                            } label: {
                                Text("Save enrollment")
                                    .font(.body.weight(.semibold))
                                    .frame(maxWidth: .infinity, minHeight: 48)
                            }
                            .buttonStyle(.borderedProminent)
                            .tint(Color.appAccent)
                            .padding(.horizontal, 24)
                        } else {
                            Button("Cancel") {
                                controller.stop()
                                onDone()
                            }
                            .font(.footnote.weight(.semibold))
                            .foregroundStyle(.white.opacity(0.85))
                        }
                    }
                    .padding(.vertical, 24)
                    .padding(.horizontal, 12)
                    .background(Color.black.opacity(0.55))
                    .padding(.bottom, 24)
                }
            }
        }
        .onAppear { controller.start() }
        .onDisappear { controller.stop() }
    }
}

/// Drives the three-angle capture state machine. Each sample is captured once a face has
/// been visible stably for a short window; after three samples, `finished` flips and the
/// user commits.
@MainActor
private final class FaceLockEnrollmentController: ObservableObject {
    static let totalStages = 3
    static let stableSamplesRequired = 3

    @Published private(set) var stageIndex: Int = 0
    @Published private(set) var finished: Bool = false

    private var samples: [[Float]] = []
    private var stableHits: Int = 0
    private let session = AVCaptureSession()
    private let output = AVCaptureVideoDataOutput()
    private let sampleQueue = DispatchQueue(label: "com.onlytrusted.facelock.enroll")
    private var running = false

    var promptTitle: String {
        switch stageIndex {
        case 0: return "Look straight at the camera"
        case 1: return "Turn your head slightly left"
        case 2: return "Turn your head slightly right"
        default: return "Ready to save"
        }
    }

    var promptDetail: String {
        finished
        ? "Three angles captured. Tap Save to store your enrollment."
        : "Keep your face centered in the frame. Hold the pose for about a second."
    }

    func start() {
        guard !FaceLockState.isRunningOnSimulator, !running else { return }
        running = true
        sampleQueue.async { [weak self] in self?.configureSession() }
    }

    func stop() {
        running = false
        sampleQueue.async { [weak self] in
            guard let self else { return }
            if self.session.isRunning { self.session.stopRunning() }
        }
    }

    func commit() {
        FaceLockState.shared.setEnrollment(samples: samples)
        stop()
    }

    // MARK: AVCapture plumbing

    func previewLayer() -> AVCaptureVideoPreviewLayer {
        AVCaptureVideoPreviewLayer(session: session)
    }

    private func configureSession() {
        session.beginConfiguration()
        session.sessionPreset = .medium
        guard let device = AVCaptureDevice.default(.builtInWideAngleCamera, for: .video, position: .front),
              let input = try? AVCaptureDeviceInput(device: device),
              session.canAddInput(input) else {
            session.commitConfiguration()
            return
        }
        session.addInput(input)
        output.alwaysDiscardsLateVideoFrames = true
        output.videoSettings = [kCVPixelBufferPixelFormatTypeKey as String:
                                NSNumber(value: kCVPixelFormatType_32BGRA)]
        output.setSampleBufferDelegate(delegate, queue: sampleQueue)
        if session.canAddOutput(output) { session.addOutput(output) }
        if let connection = output.connection(with: .video) {
            connection.videoOrientation = .portrait
            if connection.isVideoMirroringSupported {
                connection.automaticallyAdjustsVideoMirroring = false
                connection.isVideoMirrored = true
            }
        }
        session.commitConfiguration()
        session.startRunning()
    }

    private lazy var delegate: Delegate = Delegate(controller: self)

    fileprivate func ingest(embedding: [Float]) {
        guard !finished else { return }
        guard stageIndex < Self.totalStages else { return }
        stableHits += 1
        guard stableHits >= Self.stableSamplesRequired else { return }
        samples.append(embedding)
        stableHits = 0
        stageIndex += 1
        if stageIndex >= Self.totalStages {
            finished = true
        }
    }

    fileprivate func resetStableHits() { stableHits = 0 }

    // Separate delegate target lets us keep MainActor isolation on the controller while
    // the capture callback runs off-main.
    fileprivate final class Delegate: NSObject, AVCaptureVideoDataOutputSampleBufferDelegate {
        private weak var controller: FaceLockEnrollmentController?
        private var lastSample: Date = .distantPast
        init(controller: FaceLockEnrollmentController) { self.controller = controller }

        nonisolated func captureOutput(_ output: AVCaptureOutput,
                                       didOutput sampleBuffer: CMSampleBuffer,
                                       from connection: AVCaptureConnection) {
            // Rate-limit — once every ~0.4 s is plenty for a stability counter.
            // `lastSample` is mutated from the capture queue only, which is a serial queue,
            // so we don't need extra synchronization.
            let now = Date()
            guard now.timeIntervalSince(lastSample) >= 0.4 else { return }
            lastSample = now
            guard let pixelBuffer = CMSampleBufferGetImageBuffer(sampleBuffer) else { return }
            let request = VNDetectFaceLandmarksRequest()
            let handler = VNImageRequestHandler(cvPixelBuffer: pixelBuffer,
                                                orientation: .leftMirrored, options: [:])
            do { try handler.perform([request]) } catch { return }
            let obs = (request.results ?? []) as [VNFaceObservation]
            guard obs.count == 1 else {
                DispatchQueue.main.async { [weak controller] in controller?.resetStableHits() }
                return
            }
            // Call the nonisolated static embedding helper directly so Swift 6 strict
            // concurrency doesn't reach a @MainActor-isolated version through the
            // enclosing class's isolation inference.
            guard let embedding = FaceLockMonitor.landmarkEmbedding(for: obs[0]) else {
                DispatchQueue.main.async { [weak controller] in controller?.resetStableHits() }
                return
            }
            DispatchQueue.main.async { [weak controller] in
                controller?.ingest(embedding: embedding)
            }
        }
    }
}

/// `UIViewRepresentable` wrapper that attaches an `AVCaptureVideoPreviewLayer` for the
/// enrollment controller's session.
private struct FaceLockCameraPreview: UIViewRepresentable {
    let controller: FaceLockEnrollmentController

    func makeUIView(context: Context) -> PreviewView {
        let v = PreviewView()
        v.previewLayer.session = controller.previewLayer().session
        v.previewLayer.videoGravity = .resizeAspectFill
        return v
    }

    func updateUIView(_ uiView: PreviewView, context: Context) {}

    final class PreviewView: UIView {
        override class var layerClass: AnyClass { AVCaptureVideoPreviewLayer.self }
        var previewLayer: AVCaptureVideoPreviewLayer { layer as! AVCaptureVideoPreviewLayer }
    }
}

// MARK: - Upsell sheet (non-Elite users)

/// Elite upsell shown when a non-Elite user taps the Face-Change Lock row in Settings.
struct FaceLockUpsellSheet: View {
    let onUpgrade: () -> Void
    let onDismiss: () -> Void

    var body: some View {
        VStack(spacing: 20) {
            Image(systemName: "face.dashed")
                .font(.system(size: 46, weight: .semibold))
                .foregroundStyle(Color.subGold)
                .padding(.top, 28)
            Text("Face-Change Shoulder-Surfing Lock")
                .font(.title2.weight(.bold))
                .foregroundStyle(Color.appPrimaryText)
                .multilineTextAlignment(.center)
                .padding(.horizontal, 24)
            Text("The front camera watches for faces while you're on sensitive screens: it blurs when you walk away and locks the moment someone else looks at your phone. All processing is on-device — no images are stored or uploaded. Elite tier.")
                .font(.subheadline)
                .foregroundStyle(Color.appSecondaryText)
                .multilineTextAlignment(.center)
                .padding(.horizontal, 32)
            Spacer(minLength: 0)
            Button(action: onUpgrade) {
                Text("Upgrade to Elite")
                    .font(.body.weight(.semibold))
                    .frame(maxWidth: .infinity, minHeight: 48)
            }
            .buttonStyle(.borderedProminent)
            .tint(Color.subGold)
            .padding(.horizontal, 24)
            Button("Not now", action: onDismiss)
                .foregroundStyle(Color.appSecondaryText)
                .padding(.bottom, 20)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .appBackdrop()
    }
}
