// SPDX-FileCopyrightText: 2026 VanPaulTek Inc
// SPDX-License-Identifier: AGPL-3.0-or-later
import AVFoundation
import SwiftUI
import UIKit

/// Smart Link Guard — iOS QR scanner view.
///
/// Wraps `AVCaptureSession` + `AVCaptureMetadataOutput` configured for
/// `AVMetadataObject.ObjectType.qr`. On the first QR with a URL value we fire
/// [onDetected] (which stops the session) and dismiss.
///
/// Permission string lives in Info.plist under `NSCameraUsageDescription`; without it
/// AVCaptureDevice will throw. We also gracefully degrade if the camera is unavailable
/// (simulator, device without a camera) by showing an inline hint instead of a crash.
struct SmartLinkGuardQRScanner: UIViewControllerRepresentable {
    let onDetected: (String) -> Void
    let onCancel: () -> Void

    func makeCoordinator() -> Coordinator {
        Coordinator(onDetected: onDetected, onCancel: onCancel)
    }

    func makeUIViewController(context: Context) -> QRScannerController {
        let vc = QRScannerController()
        vc.onDetected = { [weak coord = context.coordinator] value in
            coord?.deliver(value)
        }
        vc.onCancel = { [weak coord = context.coordinator] in
            coord?.cancel()
        }
        return vc
    }

    func updateUIViewController(_ uiViewController: QRScannerController, context: Context) {}

    @MainActor
    final class Coordinator {
        let onDetected: (String) -> Void
        let onCancel: () -> Void
        private var delivered = false

        init(onDetected: @escaping (String) -> Void, onCancel: @escaping () -> Void) {
            self.onDetected = onDetected
            self.onCancel = onCancel
        }

        func deliver(_ value: String) {
            guard !delivered else { return }
            delivered = true
            onDetected(value)
        }

        func cancel() {
            guard !delivered else { return }
            delivered = true
            onCancel()
        }
    }
}

final class QRScannerController: UIViewController, @preconcurrency AVCaptureMetadataOutputObjectsDelegate {

    var onDetected: ((String) -> Void)?
    var onCancel: (() -> Void)?

    private let session = AVCaptureSession()
    private var previewLayer: AVCaptureVideoPreviewLayer?
    /// Auto Privacy Shield handle. The `MainActor` registry owns teardown on scene
    /// transitions; `stopCapture()` here is the single teardown path and is idempotent.
    private var shieldHandle: CaptureRegistry.Handle?

    override func viewDidLoad() {
        super.viewDidLoad()
        view.backgroundColor = .black
        configureCamera()
        addCancelButton()
    }

    override func viewWillAppear(_ animated: Bool) {
        super.viewWillAppear(animated)
        if !session.isRunning {
            DispatchQueue.global(qos: .userInitiated).async { [weak self] in
                self?.session.startRunning()
                DispatchQueue.main.async { [weak self] in
                    guard let self, self.session.isRunning, self.shieldHandle == nil else { return }
                    // Register with the Auto Privacy Shield. Stop callback routes through
                    // `stopCapture` so a scene-background event tears down the preview too.
                    self.shieldHandle = CaptureRegistry.shared.register(
                        feature: "SmartLinkGuard QR",
                        kind: .camera
                    ) { [weak self] in self?.stopCapture() }
                }
            }
        }
    }

    override func viewWillDisappear(_ animated: Bool) {
        super.viewWillDisappear(animated)
        stopCapture()
    }

    /// Idempotent teardown. Called by `viewWillDisappear`, by the registry on background,
    /// and by the metadata-detect callback once we have a QR hit.
    private func stopCapture() {
        if session.isRunning { session.stopRunning() }
        if let handle = shieldHandle {
            shieldHandle = nil
            CaptureRegistry.shared.unregister(handle)
        }
    }

    override func viewDidLayoutSubviews() {
        super.viewDidLayoutSubviews()
        previewLayer?.frame = view.bounds
    }

    private func configureCamera() {
        guard let device = AVCaptureDevice.default(for: .video),
              let input = try? AVCaptureDeviceInput(device: device),
              session.canAddInput(input) else {
            showNoCameraMessage()
            return
        }
        session.addInput(input)

        let output = AVCaptureMetadataOutput()
        guard session.canAddOutput(output) else {
            showNoCameraMessage()
            return
        }
        session.addOutput(output)
        output.setMetadataObjectsDelegate(self, queue: .main)
        output.metadataObjectTypes = [.qr]

        let preview = AVCaptureVideoPreviewLayer(session: session)
        preview.videoGravity = .resizeAspectFill
        preview.frame = view.bounds
        view.layer.insertSublayer(preview, at: 0)
        previewLayer = preview
    }

    private func addCancelButton() {
        let button = UIButton(type: .system)
        button.setTitle("Cancel", for: .normal)
        button.setTitleColor(.white, for: .normal)
        button.backgroundColor = UIColor.black.withAlphaComponent(0.6)
        button.layer.cornerRadius = 20
        button.translatesAutoresizingMaskIntoConstraints = false
        button.contentEdgeInsets = UIEdgeInsets(top: 10, left: 24, bottom: 10, right: 24)
        button.addTarget(self, action: #selector(cancelTapped), for: .touchUpInside)
        view.addSubview(button)
        NSLayoutConstraint.activate([
            button.centerXAnchor.constraint(equalTo: view.centerXAnchor),
            button.bottomAnchor.constraint(equalTo: view.safeAreaLayoutGuide.bottomAnchor, constant: -24)
        ])
    }

    @objc private func cancelTapped() { onCancel?() }

    private func showNoCameraMessage() {
        let label = UILabel()
        label.text = "Camera unavailable. Try on a real device or paste the URL manually instead."
        label.textColor = .white
        label.textAlignment = .center
        label.numberOfLines = 0
        label.translatesAutoresizingMaskIntoConstraints = false
        view.addSubview(label)
        NSLayoutConstraint.activate([
            label.leadingAnchor.constraint(equalTo: view.leadingAnchor, constant: 24),
            label.trailingAnchor.constraint(equalTo: view.trailingAnchor, constant: -24),
            label.centerYAnchor.constraint(equalTo: view.centerYAnchor)
        ])
    }

    // MARK: - AVCaptureMetadataOutputObjectsDelegate

    func metadataOutput(
        _ output: AVCaptureMetadataOutput,
        didOutput metadataObjects: [AVMetadataObject],
        from connection: AVCaptureConnection
    ) {
        guard let first = metadataObjects.first as? AVMetadataMachineReadableCodeObject,
              let value = first.stringValue, !value.isEmpty else { return }
        stopCapture()
        onDetected?(value)
    }
}
