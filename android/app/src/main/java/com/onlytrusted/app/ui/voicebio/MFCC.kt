// SPDX-FileCopyrightText: 2026 VanPaulTek Inc
// SPDX-License-Identifier: AGPL-3.0-or-later
package com.onlytrusted.app.ui.voicebio

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.log2
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Mel-Frequency Cepstral Coefficients extractor for Android. Direct port of the iOS
 * `MFCC.swift` implementation — same frame size, hop, Mel bank count, DCT, and output
 * dimension — so a descriptor enrolled on one platform compares meaningfully on the other.
 *
 * Pipeline per frame:
 *   1. Pre-emphasis `x[n] = s[n] - 0.97 * s[n-1]`
 *   2. Hamming-windowed power spectrum via a hand-rolled Cooley–Tukey radix-2 FFT
 *   3. 26 triangular Mel-scaled filter banks → log-energies
 *   4. Discrete Cosine Transform II → first 20 coefficients
 *
 * The utterance descriptor is the per-coefficient mean across all frames, yielding a
 * stable 20-dim vector regardless of utterance length. No external deps — no TFLite,
 * no JTransforms. Pure Kotlin.
 */
object MFCC {

    /** VoiceBio always records 16 kHz mono — enough for human voice MFCCs. */
    const val SAMPLE_RATE: Int = 16_000

    /** Analysis frame = 25 ms = 400 samples at 16 kHz. */
    const val FRAME_SIZE: Int = 400

    /** Hop size = 10 ms = 160 samples at 16 kHz. */
    const val HOP_SIZE: Int = 160

    /** FFT length — next power of two >= FRAME_SIZE. */
    const val FFT_LENGTH: Int = 512

    /** Mel filter banks. */
    const val MEL_BANK_COUNT: Int = 26

    /** Output coefficient count — the first 20 Mel cepstral coefficients. */
    const val COEFFICIENT_COUNT: Int = 20

    const val LOW_FREQ_HZ: Double = 20.0
    const val HIGH_FREQ_HZ: Double = 7_600.0

    /**
     * Compute the mean 20-dim MFCC descriptor for a mono 16 kHz float sample array.
     * Returns `null` if the clip has fewer than one full frame of audio.
     */
    fun utteranceDescriptor(samples: FloatArray): FloatArray? {
        if (samples.size < FRAME_SIZE) return null

        val preEmph = preEmphasis(samples)
        val window = hammingWindow(FRAME_SIZE)
        val melBanks = melFilterBanks()

        val accum = FloatArray(COEFFICIENT_COUNT)
        var frameCount = 0

        var start = 0
        while (start + FRAME_SIZE <= preEmph.size) {
            val frame = FloatArray(FRAME_SIZE) { i -> preEmph[start + i] * window[i] }
            val power = powerSpectrum(frame)
            val logEnergies = applyMelBanks(power, melBanks)
            val coeffs = dct(logEnergies, COEFFICIENT_COUNT)
            for (i in 0 until COEFFICIENT_COUNT) accum[i] += coeffs[i]
            frameCount += 1
            start += HOP_SIZE
        }

        if (frameCount == 0) return null
        val inv = 1.0f / frameCount
        for (i in 0 until COEFFICIENT_COUNT) accum[i] *= inv
        return accum
    }

    /** Cosine similarity in [-1, 1]. Returns 0 when either vector is empty or mismatched. */
    fun cosineSimilarity(a: FloatArray, b: FloatArray): Float {
        if (a.size != b.size || a.isEmpty()) return 0f
        var dot = 0f
        var na = 0f
        var nb = 0f
        for (i in a.indices) {
            dot += a[i] * b[i]
            na += a[i] * a[i]
            nb += b[i] * b[i]
        }
        val denom = sqrt(max(na, 1e-9f)) * sqrt(max(nb, 1e-9f))
        return if (denom == 0f) 0f else dot / denom
    }

    // ---- Stages ------------------------------------------------------------

    private fun preEmphasis(x: FloatArray): FloatArray {
        if (x.isEmpty()) return x
        val out = FloatArray(x.size)
        out[0] = x[0]
        val alpha = 0.97f
        for (i in 1 until x.size) out[i] = x[i] - alpha * x[i - 1]
        return out
    }

    private fun hammingWindow(size: Int): FloatArray {
        val w = FloatArray(size)
        val denom = (size - 1).toDouble()
        for (i in 0 until size) {
            w[i] = (0.54 - 0.46 * cos(2.0 * PI * i / denom)).toFloat()
        }
        return w
    }

    /** Returns the power spectrum |FFT|^2 truncated to FFT_LENGTH/2 + 1 bins. */
    private fun powerSpectrum(frame: FloatArray): FloatArray {
        val n = FFT_LENGTH
        val padded = FloatArray(n)
        val len = min(frame.size, n)
        for (i in 0 until len) padded[i] = frame[i]

        val real = DoubleArray(n) { padded[it].toDouble() }
        val imag = DoubleArray(n)
        fftRadix2(real, imag)

        val bins = n / 2 + 1
        val power = FloatArray(bins)
        for (i in 0 until bins) {
            val r = real[i]
            val im = imag[i]
            power[i] = (r * r + im * im).toFloat()
        }
        // Match the Accelerate-style scale from the iOS implementation so descriptors
        // stay comparable in magnitude. (Pure cosine similarity is scale-invariant,
        // but keeping the scale aligned helps future debugging.)
        val scale = 1.0f / (4.0f * n)
        for (i in 0 until bins) power[i] *= scale
        return power
    }

    /** In-place iterative Cooley–Tukey radix-2 FFT. `n` must be a power of two. */
    private fun fftRadix2(real: DoubleArray, imag: DoubleArray) {
        val n = real.size
        require(n > 0 && (n and (n - 1)) == 0) { "FFT length must be a power of two" }
        val levels = log2(n.toDouble()).toInt()

        // Bit reversal permutation.
        var j = 0
        for (i in 1 until n) {
            var bit = n shr 1
            while (j and bit != 0) {
                j = j xor bit
                bit = bit shr 1
            }
            j = j or bit
            if (i < j) {
                var tmp = real[i]; real[i] = real[j]; real[j] = tmp
                tmp = imag[i]; imag[i] = imag[j]; imag[j] = tmp
            }
        }

        var size = 2
        for (level in 1..levels) {
            val halfSize = size / 2
            val tableStep = n / size
            var i = 0
            while (i < n) {
                var k = 0
                for (jj in i until i + halfSize) {
                    val angle = -2.0 * PI * k / size
                    val cosA = cos(angle)
                    val sinA = kotlin.math.sin(angle)
                    val tRe = real[jj + halfSize] * cosA - imag[jj + halfSize] * sinA
                    val tIm = real[jj + halfSize] * sinA + imag[jj + halfSize] * cosA
                    real[jj + halfSize] = real[jj] - tRe
                    imag[jj + halfSize] = imag[jj] - tIm
                    real[jj] = real[jj] + tRe
                    imag[jj] = imag[jj] + tIm
                    k += tableStep
                }
                i += size
            }
            size = size shl 1
        }
    }

    private fun melFilterBanks(): Array<FloatArray> {
        val bins = FFT_LENGTH / 2 + 1
        val lowMel = hzToMel(LOW_FREQ_HZ)
        val highMel = hzToMel(HIGH_FREQ_HZ)
        val step = (highMel - lowMel) / (MEL_BANK_COUNT + 1)
        val points = DoubleArray(MEL_BANK_COUNT + 2) { i ->
            melToHz(lowMel + i * step)
        }
        val binFreqs = IntArray(points.size) { i ->
            min(bins - 1.0, Math.round(points[i] * FFT_LENGTH / SAMPLE_RATE).toDouble()).toInt()
        }

        return Array(MEL_BANK_COUNT) { m0 ->
            val m = m0 + 1
            val bank = FloatArray(bins)
            val left = binFreqs[m - 1]
            val center = binFreqs[m]
            val right = binFreqs[m + 1]
            if (center > left) {
                for (k in left until center) {
                    bank[k] = (k - left).toFloat() / (center - left).toFloat()
                }
            }
            if (right > center) {
                for (k in center until right) {
                    bank[k] = (right - k).toFloat() / (right - center).toFloat()
                }
            }
            bank
        }
    }

    private fun applyMelBanks(power: FloatArray, banks: Array<FloatArray>): FloatArray {
        val out = FloatArray(banks.size)
        for (b in banks.indices) {
            val bank = banks[b]
            var e = 0f
            val count = min(power.size, bank.size)
            for (k in 0 until count) e += power[k] * bank[k]
            out[b] = ln(max(e, 1e-10f))
        }
        return out
    }

    /** Discrete Cosine Transform II, keeping the first [count] coefficients. */
    private fun dct(x: FloatArray, count: Int): FloatArray {
        val n = x.size
        val out = FloatArray(count)
        for (k in 0 until count) {
            var sum = 0.0
            for (i in 0 until n) {
                val angle = PI * k * (2.0 * i + 1.0) / (2.0 * n)
                sum += x[i] * cos(angle)
            }
            out[k] = sum.toFloat()
        }
        return out
    }

    private fun hzToMel(hz: Double): Double = 1125.0 * ln(1.0 + hz / 700.0)
    private fun melToHz(mel: Double): Double = 700.0 * (exp(mel / 1125.0) - 1.0)
}
