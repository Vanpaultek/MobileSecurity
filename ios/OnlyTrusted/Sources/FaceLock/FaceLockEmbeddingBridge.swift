// SPDX-FileCopyrightText: 2026 VanPaulTek Inc
// SPDX-License-Identifier: AGPL-3.0-or-later
import Foundation
import Vision

/// Non-isolated top-level wrapper around `FaceLockMonitor.landmarkEmbedding`.
///
/// Why this exists: Swift 6 strict concurrency sometimes treats calls to a
/// `nonisolated static` method on a `@MainActor` class as main-actor-isolated when the
/// call-site lives inside another `@MainActor`-isolated type (seen with the enrollment
/// controller's AVCapture delegate). Routing through a free function with no enclosing
/// isolation sidesteps the inference edge case cleanly.
///
/// The function body just forwards to `FaceLockMonitor.landmarkEmbedding`, so there is no
/// duplicated logic to drift.
func faceLockLandmarkEmbedding(_ observation: VNFaceObservation) -> [Float]? {
    FaceLockMonitor.landmarkEmbedding(for: observation)
}
