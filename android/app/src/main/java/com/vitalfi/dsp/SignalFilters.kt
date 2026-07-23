package com.vitalfi.dsp

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.round
import kotlin.math.sin
import kotlin.math.sqrt

object FftUtils {

    fun nextPowerOfTwo(n: Int): Int {
        var v = 1
        while (v < n) v = v shl 1
        return v
    }

    /** FFT radix-2 Cooley-Tukey. Entrada real, salida magnitudes por bin positivo. */
    fun magnitudeSpectrum(signal: DoubleArray, fs: Double): Pair<DoubleArray, DoubleArray> {
        val n = nextPowerOfTwo(signal.size)
        val re = DoubleArray(n)
        val im = DoubleArray(n)
        for (i in signal.indices) re[i] = signal[i]
        fftInPlace(re, im)

        val half = n / 2
        val freqs = DoubleArray(half) { k -> k * fs / n }
        val mags = DoubleArray(half) { k ->
            sqrt(re[k] * re[k] + im[k] * im[k])
        }
        return freqs to mags
    }

    private fun fftInPlace(re: DoubleArray, im: DoubleArray) {
        val n = re.size
        var j = 0
        for (i in 1 until n) {
            var bit = n shr 1
            while (j and bit != 0) {
                j = j xor bit
                bit = bit shr 1
            }
            j = j xor bit
            if (i < j) {
                re[i] = re[j].also { re[j] = re[i] }
                im[i] = im[j].also { im[j] = im[i] }
            }
        }

        var len = 2
        while (len <= n) {
            val ang = -2.0 * PI / len
            val wLenRe = cos(ang)
            val wLenIm = sin(ang)
            var i = 0
            while (i < n) {
                var wRe = 1.0
                var wIm = 0.0
                for (k in 0 until len / 2) {
                    val uRe = re[i + k]
                    val uIm = im[i + k]
                    val vRe = re[i + k + len / 2] * wRe - im[i + k + len / 2] * wIm
                    val vIm = re[i + k + len / 2] * wIm + im[i + k + len / 2] * wRe
                    re[i + k] = uRe + vRe
                    im[i + k] = uIm + vIm
                    re[i + k + len / 2] = uRe - vRe
                    im[i + k + len / 2] = uIm - vIm
                    val nextWRe = wRe * wLenRe - wIm * wLenIm
                    wIm = wRe * wLenIm + wIm * wLenRe
                    wRe = nextWRe
                }
                i += len
            }
            len = len shl 1
        }
    }

    fun dominantFrequency(
        signal: DoubleArray,
        fs: Double,
        band: Pair<Double, Double>,
    ): Pair<Double, Double> {
        val metrics = spectralMetrics(signal, fs, band)
        return metrics.dominantFreq to metrics.dominantMag
    }

    data class SpectralMetrics(
        val dominantFreq: Double,
        val dominantMag: Double,
        val spectralEnergy: Double,
        val snr: Double,
    )

    fun spectralMetrics(
        signal: DoubleArray,
        fs: Double,
        band: Pair<Double, Double>,
    ): SpectralMetrics {
        val (freqs, mags) = magnitudeSpectrum(signal, fs)
        var bestF = 0.0
        var bestM = 0.0
        var inBand = 0.0
        var outBand = 0.0
        var total = 0.0
        for (i in freqs.indices) {
            val e = mags[i] * mags[i]
            total += e
            if (freqs[i] in band.first..band.second) {
                inBand += e
                if (mags[i] > bestM) {
                    bestM = mags[i]
                    bestF = freqs[i]
                }
            } else {
                outBand += e
            }
        }
        val specEnergy = if (total < 1e-12) 0.0 else inBand / total
        val snr = if (outBand < 1e-12) 100.0 else inBand / outBand
        return SpectralMetrics(bestF, bestM, specEnergy, snr)
    }

    fun spectralEnergy(signal: DoubleArray, fs: Double, band: Pair<Double, Double>): Double =
        spectralMetrics(signal, fs, band).spectralEnergy

    fun signalSnr(signal: DoubleArray, fs: Double, band: Pair<Double, Double>): Double =
        spectralMetrics(signal, fs, band).snr
}

object SignalFilters {

    fun hampelFilter(signal: DoubleArray, windowSize: Int = 5, threshold: Double = 3.5): DoubleArray {
        val out = signal.copyOf()
        val k = 1.4826
        for (i in signal.indices) {
            val lo = max(0, i - windowSize)
            val hi = min(signal.size, i + windowSize + 1)
            val window = signal.slice(lo until hi).sorted()
            val median = window[window.size / 2]
            val mad = k * window.map { abs(it - median) }.sorted()[window.size / 2]
            // MAD=0 when the window is nearly constant: any distinct sample is an outlier.
            if (mad <= 0.0) {
                if (abs(signal[i] - median) > 0.0) out[i] = median
            } else if (abs(signal[i] - median) > threshold * mad) {
                out[i] = median
            }
        }
        return out
    }

    /** Bandpass aproximado: quita tendencia lenta + suavizado paso-bajo. */
    fun bandpass(signal: DoubleArray, lowHz: Double, highHz: Double, fs: Double): DoubleArray {
        val detrended = removeSlowTrend(signal, fs, max(5.0, 1.0 / lowHz))
        return lowPass(detrended, cutoffHz = highHz, fs = fs)
    }

    fun removeSlowTrend(signal: DoubleArray, fs: Double, windowSec: Double): DoubleArray {
        val win = max(3, (fs * windowSec).toInt())
        if (signal.size < win) return signal.copyOf()
        val out = signal.copyOf()
        val half = win / 2
        for (i in signal.indices) {
            val lo = max(0, i - half)
            val hi = min(signal.size, i + half + 1)
            val mean = signal.slice(lo until hi).average()
            out[i] -= mean
        }
        return out
    }

    private fun lowPass(signal: DoubleArray, cutoffHz: Double, fs: Double): DoubleArray {
        val rc = 1.0 / (2.0 * PI * cutoffHz)
        val dt = 1.0 / fs
        val alpha = dt / (rc + dt)
        val out = DoubleArray(signal.size)
        out[0] = signal[0]
        for (i in 1 until signal.size) {
            out[i] = out[i - 1] + alpha * (signal[i] - out[i - 1])
        }
        return out
    }

    fun preprocess(signal: DoubleArray, fs: Double, rubbleMode: Boolean): DoubleArray {
        val x = signal.map { it - signal.average() }.toDoubleArray()
        val d1 = DoubleArray(x.size) { i ->
            val v = if (i == 0) x[0] else x[i] - x[i - 1]
            v
        }
        val d1Mean = d1.average()
        for (i in d1.indices) d1[i] -= d1Mean

        if (!rubbleMode) {
            return DoubleArray(x.size) { i -> 0.65 * x[i] + 0.35 * d1[i] }
        }

        val d2 = DoubleArray(d1.size) { i ->
            val v = if (i == 0) d1[0] else d1[i] - d1[i - 1]
            v
        }
        val d2Mean = d2.average()
        for (i in d2.indices) d2[i] -= d2Mean

        val win = max(5, (fs * 15).toInt())
        var trendRemoved = x
        if (x.size >= win) {
            trendRemoved = DoubleArray(x.size)
            for (i in x.indices) {
                val lo = max(0, i - win / 2)
                val hi = min(x.size, i + win / 2 + 1)
                val mean = x.slice(lo until hi).average()
                trendRemoved[i] = x[i] - mean
            }
        }
        return DoubleArray(trendRemoved.size) { i ->
            0.45 * trendRemoved[i] + 0.35 * d1[i] + 0.20 * d2[i]
        }
    }

    fun respiratoryPeriodicity(filtered: DoubleArray, fs: Double): Double {
        if (filtered.size < max(30, (fs * 12).toInt())) return 0.0
        val mean = filtered.average()
        val centered = filtered.map { it - mean }.toDoubleArray()
        val ac = correlate(centered, centered)
        if (ac.isEmpty() || ac[0] <= 1e-12) return 0.0
        val norm = ac.map { it / ac[0] }
        val lagMin = max(1, (fs / 0.55).toInt())
        val lagMax = min(norm.size - 1, (fs / 0.06).toInt())
        if (lagMax <= lagMin) return 0.0
        return norm.subList(lagMin, lagMax).maxOrNull() ?: 0.0
    }

    private fun correlate(a: DoubleArray, b: DoubleArray): DoubleArray {
        val n = a.size
        val out = DoubleArray(2 * n - 1)
        for (i in out.indices) {
            var sum = 0.0
            for (j in a.indices) {
                val k = i - (n - 1) + j
                if (k in b.indices) sum += a[j] * b[k]
            }
            out[i] = sum
        }
        return out.slice(n - 1 until out.size).toDoubleArray()
    }

    fun uniqueRounded(signal: DoubleArray, decimals: Int = 2): Int {
        val factor = Math.pow(10.0, decimals.toDouble())
        return signal.map { kotlin.math.round(it * factor) / factor }.toSet().size
    }

    fun variance(signal: DoubleArray): Double {
        if (signal.isEmpty()) return 0.0
        val mean = signal.average()
        return signal.map { (it - mean) * (it - mean) }.average()
    }
}
