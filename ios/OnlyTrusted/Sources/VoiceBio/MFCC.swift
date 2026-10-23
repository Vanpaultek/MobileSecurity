// SPDX-FileCopyrightText: 2026 VanPaulTek Inc
// SPDX-License-Identifier: AGPL-3.0-or-later
import Foundation
import Accelerate

/// Mel-Frequency Cepstral Coefficients extractor. iOS ships no built-in MFCC API, so this
/// is a from-scratch implementation on top of `Accelerate.vDSP` primitives (FFT + pre-
/// emphasis + Mel filter bank + DCT). Shared by Voice Biometric enrollment and runtime
/// verification. 100% on-device, no model files.
///
/// Pipeline per frame:
///   1. Pre-emphasis `x[n] = s[n] - 0.97 * s[n-1]`
///   2. Hamming-windowed power spectrum via vDSP FFT
///   3. 26 triangular Mel-scaled filter banks → log-energies
///   4. Discrete Cosine Transform → first 20 coefficients
///
/// A 10-second clip at 16 kHz yields ~1000 frames (25 ms frame, 10 ms hop). The utterance
/// descriptor is the per-coefficient mean across frames, so the output is a stable 20-dim
/// vector regardless of utterance length.
enum MFCC {

    /// Sample rate assumed by the extractor. VoiceBio always records at 16 kHz mono —
    /// enough for human-voice MFCCs and small on disk.
    static let sampleRate: Double = 16_000

    /// Analysis frame = 25 ms = 400 samples at 16 kHz.
    static let frameSize: Int = 400

    /// Hop size = 10 ms = 160 samples at 16 kHz.
    static let hopSize: Int = 160

    /// FFT length — next power of two ≥ frameSize.
    static let fftLength: Int = 512

    /// Mel filter banks.
    static let melBankCount: Int = 26

    /// Output coefficient count — the first 20 Mel cepstral coefficients.
    static let coefficientCount: Int = 20

    /// Low / high Mel edges. 0 Hz → maxFreq/2 by default.
    static let lowFreqHz: Double = 20
    static let highFreqHz: Double = 7_600

    // MARK: - Public API

    /// Compute the mean 20-dim MFCC descriptor for a mono 16 kHz float sample array.
    /// Returns `nil` if the clip has fewer than two frames.
    static func utteranceDescriptor(samples: [Float]) -> [Float]? {
        guard samples.count >= frameSize else { return nil }

        let preEmph = preEmphasis(samples)
        let window = hammingWindow(size: frameSize)
        let melBanks = melFilterBanks()

        var accum = [Float](repeating: 0, count: coefficientCount)
        var frameCount = 0

        var start = 0
        while start + frameSize <= preEmph.count {
            let frame = Array(preEmph[start ..< start + frameSize])
            let windowed = multiply(frame, window)
            let power = powerSpectrum(windowed)
            let logEnergies = applyMelBanks(power, melBanks: melBanks)
            let coeffs = dct(logEnergies, count: coefficientCount)
            for i in 0..<coefficientCount {
                accum[i] += coeffs[i]
            }
            frameCount += 1
            start += hopSize
        }

        guard frameCount > 0 else { return nil }
        let inv = 1.0 / Float(frameCount)
        for i in 0..<coefficientCount { accum[i] *= inv }
        return accum
    }

    /// Cosine similarity in [-1, 1]. Returns 0 when either vector is empty / mismatched.
    static func cosineSimilarity(_ a: [Float], _ b: [Float]) -> Float {
        guard a.count == b.count, !a.isEmpty else { return 0 }
        var dot: Float = 0
        var na: Float = 0
        var nb: Float = 0
        vDSP_dotpr(a, 1, b, 1, &dot, vDSP_Length(a.count))
        vDSP_svesq(a, 1, &na, vDSP_Length(a.count))
        vDSP_svesq(b, 1, &nb, vDSP_Length(b.count))
        let denom = sqrtf(max(na, 1e-9)) * sqrtf(max(nb, 1e-9))
        return denom == 0 ? 0 : dot / denom
    }

    // MARK: - Stages

    private static func preEmphasis(_ x: [Float]) -> [Float] {
        var out = [Float](repeating: 0, count: x.count)
        out[0] = x[0]
        let alpha: Float = 0.97
        for i in 1..<x.count {
            out[i] = x[i] - alpha * x[i - 1]
        }
        return out
    }

    private static func hammingWindow(size: Int) -> [Float] {
        var win = [Float](repeating: 0, count: size)
        vDSP_hamm_window(&win, vDSP_Length(size), 0)
        return win
    }

    private static func multiply(_ a: [Float], _ b: [Float]) -> [Float] {
        var out = [Float](repeating: 0, count: a.count)
        vDSP_vmul(a, 1, b, 1, &out, 1, vDSP_Length(a.count))
        return out
    }

    /// Returns the power spectrum (|FFT|^2) truncated to `fftLength/2 + 1` bins.
    private static func powerSpectrum(_ frame: [Float]) -> [Float] {
        let n = fftLength
        var padded = frame
        if padded.count < n {
            padded.append(contentsOf: [Float](repeating: 0, count: n - padded.count))
        } else if padded.count > n {
            padded = Array(padded.prefix(n))
        }

        let log2n = vDSP_Length(log2(Double(n)).rounded())
        guard let setup = vDSP_create_fftsetup(log2n, FFTRadix(kFFTRadix2)) else {
            return [Float](repeating: 0, count: n / 2 + 1)
        }
        defer { vDSP_destroy_fftsetup(setup) }

        var real = [Float](repeating: 0, count: n / 2)
        var imag = [Float](repeating: 0, count: n / 2)
        var power = [Float](repeating: 0, count: n / 2 + 1)

        padded.withUnsafeBufferPointer { pBuf in
            pBuf.baseAddress!.withMemoryRebound(to: DSPComplex.self, capacity: n / 2) { typedPtr in
                real.withUnsafeMutableBufferPointer { realBuf in
                    imag.withUnsafeMutableBufferPointer { imagBuf in
                        var split = DSPSplitComplex(realp: realBuf.baseAddress!, imagp: imagBuf.baseAddress!)
                        vDSP_ctoz(typedPtr, 2, &split, 1, vDSP_Length(n / 2))
                        vDSP_fft_zrip(setup, &split, 1, log2n, FFTDirection(FFT_FORWARD))
                        for i in 0..<(n / 2) {
                            let r = realBuf[i]
                            let im = imagBuf[i]
                            power[i] = r * r + im * im
                        }
                        // Nyquist bin lives in imag[0] when using zrip.
                        power[n / 2] = imagBuf[0] * imagBuf[0]
                        // Scale (vDSP packs the real-only transform, see Accelerate docs).
                        let scale: Float = 1.0 / (4.0 * Float(n))
                        for i in 0...(n / 2) { power[i] *= scale }
                    }
                }
            }
        }
        return power
    }

    private static func melFilterBanks() -> [[Float]] {
        let bins = fftLength / 2 + 1
        let lowMel = hzToMel(lowFreqHz)
        let highMel = hzToMel(highFreqHz)
        let step = (highMel - lowMel) / Double(melBankCount + 1)
        var points = [Double]()
        for i in 0...(melBankCount + 1) {
            points.append(melToHz(lowMel + Double(i) * step))
        }
        let binFreqs = points.map { hz in
            min(Double(bins) - 1, (hz * Double(fftLength) / sampleRate).rounded(.toNearestOrEven))
        }

        var banks = [[Float]]()
        banks.reserveCapacity(melBankCount)
        for m in 1...melBankCount {
            var bank = [Float](repeating: 0, count: bins)
            let left = Int(binFreqs[m - 1])
            let center = Int(binFreqs[m])
            let right = Int(binFreqs[m + 1])
            if center > left {
                for k in left..<center {
                    bank[k] = Float(k - left) / Float(center - left)
                }
            }
            if right > center {
                for k in center..<right {
                    bank[k] = Float(right - k) / Float(right - center)
                }
            }
            banks.append(bank)
        }
        return banks
    }

    private static func applyMelBanks(_ power: [Float], melBanks: [[Float]]) -> [Float] {
        var logEnergies = [Float](repeating: 0, count: melBanks.count)
        for (i, bank) in melBanks.enumerated() {
            var e: Float = 0
            vDSP_dotpr(power, 1, bank, 1, &e, vDSP_Length(bank.count))
            // log(0) guard
            logEnergies[i] = logf(max(e, 1e-10))
        }
        return logEnergies
    }

    /// Discrete Cosine Transform II, keeping the first `count` coefficients.
    private static func dct(_ x: [Float], count: Int) -> [Float] {
        let n = x.count
        var out = [Float](repeating: 0, count: count)
        for k in 0..<count {
            var sum: Float = 0
            let kd = Double(k)
            for i in 0..<n {
                let angle = Double.pi * kd * (2 * Double(i) + 1) / (2.0 * Double(n))
                sum += x[i] * Float(cos(angle))
            }
            out[k] = sum
        }
        return out
    }

    private static func hzToMel(_ hz: Double) -> Double {
        1125.0 * log(1.0 + hz / 700.0)
    }

    private static func melToHz(_ mel: Double) -> Double {
        700.0 * (exp(mel / 1125.0) - 1.0)
    }
}
