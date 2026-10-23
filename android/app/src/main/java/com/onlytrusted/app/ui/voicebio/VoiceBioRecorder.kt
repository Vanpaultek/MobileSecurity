// SPDX-FileCopyrightText: 2026 VanPaulTek Inc
// SPDX-License-Identifier: AGPL-3.0-or-later
package com.onlytrusted.app.ui.voicebio

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.core.content.ContextCompat
import com.onlytrusted.app.ui.privacyshield.CaptureRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 16 kHz mono PCM recorder for Voice Biometric enrollment and verification.
 *
 * We intentionally use `AudioRecord` (not `MediaRecorder`) because MFCC extraction needs
 * raw float samples, and `MediaRecorder` only emits AAC/AMR-NB containers. `AudioRecord`
 * writes 16-bit PCM straight into a short[] buffer which we convert to float[-1, 1].
 *
 * No file is ever written to disk — the samples live only in RAM for the lifetime of the
 * recorder, then feed MFCC.utteranceDescriptor(). This matches the "audio is never stored"
 * promise in the user-facing copy.
 */
class VoiceBioRecorder(private val context: Context) {

    sealed class StartError : Throwable() {
        object PermissionDenied : StartError() {
            private fun readResolve(): Any = PermissionDenied
            override val message: String =
                "Microphone permission is required. Grant it in Settings → Apps → OnlyTrusted → Permissions."
        }
        data class Hardware(val detail: String) : StartError() {
            override val message: String = "Couldn't open the microphone: $detail"
        }
    }

    @Volatile private var record: AudioRecord? = null
    @Volatile private var captureBuffer: FloatArray = FloatArray(0)
    /** Auto Privacy Shield handle. Non-null only while `AudioRecord` is live. */
    @Volatile private var shieldHandle: CaptureRegistry.Handle? = null

    /** True if RECORD_AUDIO is already granted. Callers should request it via ActivityResult. */
    fun hasPermission(): Boolean = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.RECORD_AUDIO
    ) == PackageManager.PERMISSION_GRANTED

    /**
     * Record for [durationSeconds] and return the captured float samples. Suspends on the
     * IO dispatcher so the UI stays responsive. The resulting array is at ~16 kHz mono,
     * values in roughly [-1, 1].
     */
    @SuppressLint("MissingPermission")
    suspend fun record(durationSeconds: Int): FloatArray = withContext(Dispatchers.IO) {
        if (!hasPermission()) throw StartError.PermissionDenied

        val sampleRate = MFCC.SAMPLE_RATE
        val channelConfig = AudioFormat.CHANNEL_IN_MONO
        val encoding = AudioFormat.ENCODING_PCM_16BIT

        val minBuffer = AudioRecord.getMinBufferSize(sampleRate, channelConfig, encoding)
        if (minBuffer <= 0) throw StartError.Hardware("getMinBufferSize=$minBuffer")

        // 2x min buffer gives us headroom against brief dispatcher stalls.
        val bufferSize = minBuffer * 2

        val rec = try {
            AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                sampleRate,
                channelConfig,
                encoding,
                bufferSize
            )
        } catch (t: Throwable) {
            throw StartError.Hardware(t.message ?: t::class.java.simpleName)
        }
        if (rec.state != AudioRecord.STATE_INITIALIZED) {
            rec.release()
            throw StartError.Hardware("AudioRecord did not initialize")
        }
        record = rec

        val totalSamples = sampleRate * durationSeconds
        val outBuffer = FloatArray(totalSamples)
        val chunk = ShortArray(2048)
        var written = 0
        // Auto Privacy Shield: register once the AudioRecord is initialised but before
        // we actually start capture. Stop closure routes through `cancel()` so a
        // background/screen-off event stops the hardware mid-read.
        shieldHandle = CaptureRegistry.register(
            feature = "VoiceBio",
            kind = CaptureRegistry.Kind.MICROPHONE
        ) { cancel() }
        CaptureRegistry.maybeArmScreenOffReceiver(context.applicationContext)
        try {
            rec.startRecording()
            while (written < totalSamples) {
                val toRead = minOf(chunk.size, totalSamples - written)
                val got = rec.read(chunk, 0, toRead)
                if (got <= 0) break
                for (i in 0 until got) {
                    outBuffer[written + i] = chunk[i] / 32768.0f
                }
                written += got
            }
        } finally {
            runCatching { rec.stop() }
            rec.release()
            record = null
            shieldHandle?.let { h ->
                shieldHandle = null
                CaptureRegistry.unregister(h)
            }
        }
        if (written == 0) throw StartError.Hardware("No audio captured")
        captureBuffer = outBuffer.copyOf(written)
        captureBuffer
    }

    /**
     * Cancel any in-flight recording. Safe to call multiple times, including from the
     * Auto Privacy Shield registry on a background / screen-off transition — releasing
     * [AudioRecord] from a different thread while the IO coroutine is mid-[AudioRecord.read]
     * is safe (`read` returns -1 and the finally block above completes normally).
     */
    fun cancel() {
        val rec = record ?: run {
            // Still unregister so the dashboard doesn't show a stale phantom handle.
            shieldHandle?.let { h ->
                shieldHandle = null
                CaptureRegistry.unregister(h)
            }
            return
        }
        runCatching { rec.stop() }
        rec.release()
        record = null
    }
}
