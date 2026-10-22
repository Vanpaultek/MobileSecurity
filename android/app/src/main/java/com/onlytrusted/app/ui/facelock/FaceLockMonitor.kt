// SPDX-FileCopyrightText: 2026 VanPaulTek Inc
// SPDX-License-Identifier: AGPL-3.0-or-later
package com.onlytrusted.app.ui.facelock

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.State
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetector
import com.google.mlkit.vision.face.FaceDetectorOptions
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicLong

/**
 * Runtime camera monitor for the Face-Change Shoulder-Surfing Lock (Elite). Mirrors the iOS
 * `FaceLockMonitor`:
 *   * 0 faces for >3 s                 → .WalkedAway (soft blur)
 *   * 1 face but not the owner          → .LockStranger
 *   * 2+ faces simultaneously           → .LockMultiple
 *   * otherwise                         → .Ok
 *
 * CameraX binds the front camera at a low resolution and feeds `ImageAnalysis` into ML Kit's
 * on-device face detector. The rate-limiter drops frames outside the 2 s cadence so the
 * detector only runs ~30 times/minute, keeping battery drain low.
 *
 * Not a long-lived singleton — one instance per sensitive-screen attachment. Call `stop()`
 * on dispose to tear down the CameraX binding and the ML Kit detector.
 */
class FaceLockMonitor(private val lifecycleOwner: LifecycleOwner) {

    enum class Decision { Idle, Ok, WalkedAway, LockStranger, LockMultiple }

    private val _decisionState = mutableStateOf(Decision.Idle)
    val decision: State<Decision> = _decisionState

    private val _runningState = mutableStateOf(false)
    val isRunning: State<Boolean> = _runningState

    private val detector: FaceDetector = run {
        val opts = FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
            .setContourMode(FaceDetectorOptions.CONTOUR_MODE_ALL)
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_NONE)
            .setMinFaceSize(0.15f)
            .build()
        FaceDetection.getClient(opts)
    }
    private val analysisExecutor = Executors.newSingleThreadExecutor()
    private val lastSampleAt = AtomicLong(0L)

    @Volatile private var provider: ProcessCameraProvider? = null
    @Volatile private var analysis: ImageAnalysis? = null

    @Volatile private var noFaceSinceMs: Long = 0L
    @Volatile private var consecutiveStranger: Int = 0
    @Volatile private var consecutiveMultiple: Int = 0

    @SuppressLint("MissingPermission")
    fun start(context: Context) {
        if (!FaceLockState.isEnrolled(context)) {
            _decisionState.value = Decision.Idle
            return
        }
        if (!FaceLockState.lockWhenOwnerLeaves(context) &&
            !FaceLockState.lockWhenStrangerJoins(context)) {
            _decisionState.value = Decision.Idle
            return
        }
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED) {
            _decisionState.value = Decision.Idle
            return
        }
        if (_runningState.value) return

        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            try {
                val cp = future.get()
                provider = cp
                val an = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build().also {
                        it.setAnalyzer(analysisExecutor) { proxy ->
                            analyze(context, proxy)
                        }
                    }
                analysis = an
                cp.unbindAll()
                cp.bindToLifecycle(
                    lifecycleOwner,
                    CameraSelector.DEFAULT_FRONT_CAMERA,
                    an
                )
                _runningState.value = true
            } catch (t: Throwable) {
                _decisionState.value = Decision.Idle
                _runningState.value = false
            }
        }, ContextCompat.getMainExecutor(context))
    }

    fun stop() {
        try { provider?.unbindAll() } catch (_: Throwable) {}
        provider = null
        analysis = null
        _runningState.value = false
        _decisionState.value = Decision.Idle
        noFaceSinceMs = 0L
        consecutiveStranger = 0
        consecutiveMultiple = 0
    }

    fun shutdown() {
        stop()
        runCatching { detector.close() }
        analysisExecutor.shutdown()
    }

    fun acknowledgeUnlock() {
        _decisionState.value = Decision.Ok
        noFaceSinceMs = 0L
        consecutiveStranger = 0
        consecutiveMultiple = 0
    }

    @SuppressLint("UnsafeOptInUsageError")
    private fun analyze(context: Context, proxy: ImageProxy) {
        val now = System.currentTimeMillis()
        val last = lastSampleAt.get()
        if (now - last < FaceLockState.SAMPLE_INTERVAL_MS) {
            proxy.close()
            return
        }
        lastSampleAt.set(now)

        val media = proxy.image
        if (media == null) {
            proxy.close()
            return
        }
        val img = InputImage.fromMediaImage(media, proxy.imageInfo.rotationDegrees)
        detector.process(img)
            .addOnSuccessListener { faces ->
                applyDecision(context, faces.size, faces)
            }
            .addOnFailureListener {
                // Vision failed — treat as 0 faces so the walk-away timer doesn't lock on a glitch.
                applyDecision(context, 0, emptyList())
            }
            .addOnCompleteListener { proxy.close() }
    }

    private fun applyDecision(context: Context,
                              count: Int,
                              faces: List<com.google.mlkit.vision.face.Face>) {
        val lockOwnerLeaves = FaceLockState.lockWhenOwnerLeaves(context)
        val lockStrangerJoins = FaceLockState.lockWhenStrangerJoins(context)

        if (count == 0) {
            consecutiveStranger = 0
            consecutiveMultiple = 0
            if (!lockOwnerLeaves) {
                _decisionState.value = Decision.Ok
                noFaceSinceMs = 0L
                return
            }
            if (noFaceSinceMs == 0L) {
                noFaceSinceMs = System.currentTimeMillis()
            } else if (System.currentTimeMillis() - noFaceSinceMs >= FaceLockState.NO_FACE_COUNTDOWN_MS) {
                if (_decisionState.value != Decision.WalkedAway) {
                    _decisionState.value = Decision.WalkedAway
                }
            }
            return
        }

        noFaceSinceMs = 0L

        if (count >= 2) {
            consecutiveStranger = 0
            if (!lockStrangerJoins) {
                _decisionState.value = Decision.Ok
                return
            }
            consecutiveMultiple++
            if (consecutiveMultiple >= CONSECUTIVE_LOCK_THRESHOLD) {
                _decisionState.value = Decision.LockMultiple
            }
            return
        }

        // Exactly 1 face.
        consecutiveMultiple = 0
        val embedding = FaceLockEmbedder.embed(faces[0])
        val ownerMatches = embedding != null && FaceLockState.matchesOwner(context, embedding)
        if (ownerMatches) {
            consecutiveStranger = 0
            _decisionState.value = Decision.Ok
            return
        }
        if (!lockStrangerJoins) {
            _decisionState.value = Decision.Ok
            return
        }
        consecutiveStranger++
        if (consecutiveStranger >= CONSECUTIVE_LOCK_THRESHOLD) {
            _decisionState.value = Decision.LockStranger
        }
    }

    companion object {
        private const val CONSECUTIVE_LOCK_THRESHOLD = 2
    }
}
