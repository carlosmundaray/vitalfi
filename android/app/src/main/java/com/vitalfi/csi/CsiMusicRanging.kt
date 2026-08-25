package com.vitalfi.csi

import kotlin.math.*

/**
 * Estimador de distancia por MUSIC (MUltiple SIgnal Classification) adaptado
 * al dominio de frecuencia de subportadoras OFDM para CSI Wi-Fi.
 *
 * MUSIC es un algoritmo de super-resolución que descompone la señal recibida
 * en subespacio de señal (caminos multipath reales) y subespacio de ruido.
 * Al evaluar un pseudo-espectro sobre distancias candidatas, los picos
 * corresponden a las distancias reales de los reflectores/personas.
 *
 * Ventaja sobre phase slope lineal:
 * - Separa múltiples caminos (directo + reflexiones)
 * - Resolución sub-métrica incluso con pocas subportadoras
 * - Robusto a multipath: identifica el camino más corto (directo)
 *
 * Limitación: Necesita varias tramas (snapshots) para construir una
 * matriz de covarianza estable.
 */
class CsiMusicRanging(
    /** Número de snapshots (tramas) en la ventana de estimación. */
    private val windowSize: Int = 20,
    /** Número de fuentes (caminos multipath) a resolver. */
    private val numSources: Int = 3,
    /** Resolución del barrido de distancia (metros). */
    private val distanceResolution: Double = 0.05,
    /** Distancia máxima del barrido (metros). */
    private val maxDistance: Double = 12.0,
    /** Distancia mínima del barrido (metros). */
    private val minDistance: Double = 0.2,
) {
    // Buffer circular de snapshots CSI (cada snapshot = vector complejo de subportadoras)
    private val snapshotBufferReal = ArrayDeque<DoubleArray>()
    private val snapshotBufferImag = ArrayDeque<DoubleArray>()

    // Resultado cacheado
    private var lastEstimate: MusicResult? = null

    // Número de subportadoras del último snapshot
    private var numSubcarriers = 0

    data class MusicResult(
        /** Distancia estimada al reflector/persona más cercano (metros). */
        val primaryDistanceM: Double,
        /** Distancia del segundo camino más fuerte (metros), si hay. */
        val secondaryDistanceM: Double?,
        /** Número de caminos multipath detectados. */
        val multipathCount: Int,
        /** Pseudo-espectro de distancias (para visualización/debug). */
        val spectrum: DoubleArray,
        /** Distancias correspondientes al espectro. */
        val distancesAxis: DoubleArray,
        /** Confianza de la estimación (0.0 - 1.0). */
        val confidence: Double,
    )

    /**
     * Añade un snapshot CSI al buffer.
     *
     * @param amplitudes Amplitudes de las subportadoras.
     * @param phases Fases limpias de las subportadoras (ya procesadas por CsiPhaseProcessor).
     */
    fun addSnapshot(amplitudes: FloatArray, phases: DoubleArray) {
        val n = minOf(amplitudes.size, phases.size)
        if (n < 8) return

        numSubcarriers = n

        // Construir vector complejo: x[k] = A[k] * exp(j * phi[k])
        val real = DoubleArray(n) { amplitudes[it] * cos(phases[it]) }
        val imag = DoubleArray(n) { amplitudes[it] * sin(phases[it]) }

        snapshotBufferReal.addLast(real)
        snapshotBufferImag.addLast(imag)

        while (snapshotBufferReal.size > windowSize) {
            snapshotBufferReal.removeFirst()
            snapshotBufferImag.removeFirst()
        }
    }

    /**
     * Estima la distancia usando el algoritmo MUSIC.
     *
     * @return MusicResult con la distancia estimada, o null si no hay suficientes datos.
     */
    fun estimateDistance(): MusicResult? {
        val snapCount = snapshotBufferReal.size
        if (snapCount < 8 || numSubcarriers < 8) return lastEstimate

        val n = numSubcarriers
        val effectiveSources = minOf(numSources, n / 3)

        // 1. Construir matriz de covarianza R = (1/L) * Σ x * x^H
        val rReal = Array(n) { DoubleArray(n) }
        val rImag = Array(n) { DoubleArray(n) }

        for (s in 0 until snapCount) {
            val xr = snapshotBufferReal.elementAt(s)
            val xi = snapshotBufferImag.elementAt(s)
            // Solo usar las primeras n componentes
            val len = minOf(xr.size, n)
            for (i in 0 until len) {
                for (j in 0 until len) {
                    // R += x * x^H (producto exterior hermitiano)
                    rReal[i][j] += (xr[i] * xr[j] + xi[i] * xi[j]) / snapCount
                    rImag[i][j] += (xi[i] * xr[j] - xr[i] * xi[j]) / snapCount
                }
            }
        }

        // Regularización diagonal para estabilidad numérica
        val traceAvg = (0 until n).sumOf { rReal[it][it] } / n
        val reg = traceAvg * 0.01
        for (i in 0 until n) {
            rReal[i][i] += reg
        }

        // 2. Descomposición en eigenvalores (Power Iteration simplificada)
        // Para n grande usamos una versión truncada que solo necesita los primeros
        // eigenvalores para separar señal de ruido.
        val eigenvalues = DoubleArray(n)
        val eigenvectorsReal = Array(n) { DoubleArray(n) }
        val eigenvectorsImag = Array(n) { DoubleArray(n) }

        computeEigendecomposition(
            rReal, rImag, n,
            eigenvalues, eigenvectorsReal, eigenvectorsImag,
        )

        // 3. Separar subespacio de ruido (eigenvectores con eigenvalores pequeños)
        // Ordenar eigenvalores de mayor a menor
        val sortedIndices = eigenvalues.indices.sortedByDescending { eigenvalues[it] }

        // Los primeros 'effectiveSources' eigenvectores = subespacio de señal
        // El resto = subespacio de ruido
        val noiseStartIdx = effectiveSources

        // 4. Calcular pseudo-espectro MUSIC sobre distancias candidatas
        val numBins = ((maxDistance - minDistance) / distanceResolution).toInt() + 1
        val spectrum = DoubleArray(numBins)
        val distancesAxis = DoubleArray(numBins)

        for (b in 0 until numBins) {
            val dist = minDistance + b * distanceResolution
            distancesAxis[b] = dist

            // Steering vector para esta distancia
            // a(d) = [1, exp(-j*2π*d*Δf/c), exp(-j*2π*d*2Δf/c), ...]
            val aReal = DoubleArray(n)
            val aImag = DoubleArray(n)
            for (k in 0 until n) {
                val phase = -2.0 * PI * dist * k * CsiPhaseProcessor.SUBCARRIER_SPACING_HZ /
                    CsiPhaseProcessor.SPEED_OF_LIGHT
                aReal[k] = cos(phase)
                aImag[k] = sin(phase)
            }

            // P(d) = 1 / (a^H * En * En^H * a)
            // Donde En son los eigenvectores de ruido
            var denomReal = 0.0

            for (noiseIdx in noiseStartIdx until minOf(n, sortedIndices.size)) {
                val eIdx = sortedIndices[noiseIdx]
                // Producto a^H * e_noise
                var dotReal = 0.0
                var dotImag = 0.0
                for (k in 0 until n) {
                    // (a^H)[k] * e[k] = conj(a[k]) * e[k]
                    dotReal += aReal[k] * eigenvectorsReal[eIdx][k] + aImag[k] * eigenvectorsImag[eIdx][k]
                    dotImag += aReal[k] * eigenvectorsImag[eIdx][k] - aImag[k] * eigenvectorsReal[eIdx][k]
                }
                denomReal += dotReal * dotReal + dotImag * dotImag
            }

            spectrum[b] = if (denomReal > 1e-15) 1.0 / denomReal else 0.0
        }

        // 5. Encontrar picos en el pseudo-espectro
        val peaks = findPeaks(spectrum, distancesAxis)

        if (peaks.isEmpty()) {
            lastEstimate = null
            return null
        }

        // 6. El primer pico (más cercano) = camino directo = distancia a la persona
        val primary = peaks.first()
        val secondary = peaks.getOrNull(1)

        // Confianza basada en la separación entre eigenvalores de señal y ruido
        val signalEigenAvg = (0 until effectiveSources).map { eigenvalues[sortedIndices[it]] }.average()
        val noiseEigenAvg = (noiseStartIdx until n).map { eigenvalues[sortedIndices[it]] }.average()
        val eigenRatio = if (noiseEigenAvg > 1e-12) signalEigenAvg / noiseEigenAvg else 100.0
        val confidence = (1.0 - exp(-eigenRatio * 0.15)).coerceIn(0.0, 1.0)

        val result = MusicResult(
            primaryDistanceM = primary.coerceIn(minDistance, maxDistance),
            secondaryDistanceM = secondary?.coerceIn(minDistance, maxDistance),
            multipathCount = peaks.size,
            spectrum = spectrum,
            distancesAxis = distancesAxis,
            confidence = confidence,
        )

        lastEstimate = result
        return result
    }

    /**
     * Retorna el último resultado sin recalcular (para consultas de UI rápidas).
     */
    fun lastResult(): MusicResult? = lastEstimate

    /**
     * Resetea el buffer de snapshots y resultados.
     */
    fun reset() {
        snapshotBufferReal.clear()
        snapshotBufferImag.clear()
        lastEstimate = null
        numSubcarriers = 0
    }

    /**
     * Número de snapshots acumulados actualmente.
     */
    fun snapshotCount(): Int = snapshotBufferReal.size

    // ─── Álgebra lineal inline (sin dependencias externas) ───

    /**
     * Descomposición en eigenvalores simplificada para matrices hermitianas pequeñas.
     *
     * Usa el método de Power Iteration con deflación para extraer los
     * eigenvalores/eigenvectores dominantes. Para matrices de 30-56 subportadoras
     * esto es suficiente y corre en ~1-2ms en un smartphone.
     */
    private fun computeEigendecomposition(
        matReal: Array<DoubleArray>,
        matImag: Array<DoubleArray>,
        n: Int,
        eigenvalues: DoubleArray,
        eigenvectorsReal: Array<DoubleArray>,
        eigenvectorsImag: Array<DoubleArray>,
    ) {
        // Copiar la matriz para poder deflactar
        val wReal = Array(n) { matReal[it].copyOf() }
        val wImag = Array(n) { matImag[it].copyOf() }

        val maxEigens = minOf(n, numSources + 5) // Solo necesitamos señal + algunos de ruido

        for (e in 0 until maxEigens) {
            // Power iteration para el eigenvalor dominante
            var vr = DoubleArray(n) { if (it == e % n) 1.0 else 0.0 }
            var vi = DoubleArray(n) { 0.0 }
            var eigenvalue = 0.0

            for (iter in 0 until 30) {
                // w = A * v (multiplicación matriz-vector complejo)
                val wr = DoubleArray(n)
                val wi = DoubleArray(n)
                for (i in 0 until n) {
                    for (j in 0 until n) {
                        wr[i] += wReal[i][j] * vr[j] - wImag[i][j] * vi[j]
                        wi[i] += wReal[i][j] * vi[j] + wImag[i][j] * vr[j]
                    }
                }

                // Normalizar
                var norm = 0.0
                for (i in 0 until n) {
                    norm += wr[i] * wr[i] + wi[i] * wi[i]
                }
                norm = sqrt(norm)
                if (norm < 1e-15) break

                eigenvalue = norm
                for (i in 0 until n) {
                    vr[i] = wr[i] / norm
                    vi[i] = wi[i] / norm
                }
            }

            eigenvalues[e] = eigenvalue
            for (i in 0 until n) {
                eigenvectorsReal[e][i] = vr[i]
                eigenvectorsImag[e][i] = vi[i]
            }

            // Deflación: A = A - λ * v * v^H
            for (i in 0 until n) {
                for (j in 0 until n) {
                    // v * v^H = (vr + j*vi)(vr - j*vi)^T
                    wReal[i][j] -= eigenvalue * (vr[i] * vr[j] + vi[i] * vi[j])
                    wImag[i][j] -= eigenvalue * (vi[i] * vr[j] - vr[i] * vi[j])
                }
            }
        }

        // Llenar eigenvalores restantes con la traza residual
        if (maxEigens < n) {
            val residualTrace = (0 until n).sumOf { wReal[it][it] }
            val avgResidual = residualTrace / (n - maxEigens)
            for (e in maxEigens until n) {
                eigenvalues[e] = avgResidual.coerceAtLeast(0.0)
            }
        }
    }

    /**
     * Detección de picos en el pseudo-espectro MUSIC.
     *
     * Retorna las distancias de los picos ordenados por cercanía (más cercano primero).
     */
    private fun findPeaks(spectrum: DoubleArray, distances: DoubleArray): List<Double> {
        if (spectrum.size < 3) return emptyList()

        val peaks = mutableListOf<Pair<Double, Double>>() // (distancia, magnitud)

        // Umbral: al menos 20% del pico máximo
        val maxVal = spectrum.max()
        val threshold = maxVal * 0.20

        for (i in 1 until spectrum.size - 1) {
            if (spectrum[i] > spectrum[i - 1] &&
                spectrum[i] >= spectrum[i + 1] &&
                spectrum[i] > threshold
            ) {
                // Interpolación parabólica para precisión sub-bin
                val alpha = spectrum[i - 1]
                val beta = spectrum[i]
                val gamma = spectrum[i + 1]
                val denom = 2.0 * (2.0 * beta - alpha - gamma)
                val delta = if (abs(denom) > 1e-12) (alpha - gamma) / denom else 0.0
                val refinedDist = distances[i] + delta * distanceResolution

                peaks.add(refinedDist to spectrum[i])
            }
        }

        // Ordenar por distancia (más cercano primero) para seleccionar camino directo
        return peaks.sortedBy { it.first }.map { it.first }
    }
}
