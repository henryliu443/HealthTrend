package com.healthtrend.core.analytics.internal

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Shared numeric primitives for the engine.
 *
 * INTERNAL: not part of the public surface. Every function is pure and stateless.
 */
internal object NumericSupport {

    /** Arithmetic mean; `NaN` for an empty input. */
    fun mean(values: DoubleArray): Double {
        if (values.isEmpty()) return Double.NaN
        var sum = 0.0
        for (value in values) sum += value
        return sum / values.size
    }

    /** Sum of squared deviations from [center] — the second pass of the stable two-pass algorithm. */
    fun sumSquaredDeviations(values: DoubleArray, center: Double): Double {
        var acc = 0.0
        for (value in values) {
            val deviation = value - center
            acc += deviation * deviation
        }
        return acc
    }

    /**
     * `sqrt` with floating-point cancellation clamped away.
     *
     * A variance computed as a difference of large sums can come out marginally negative
     * (AGENTS.md §6.1 explicitly warns about this); `NaN` stays `NaN` so it propagates.
     */
    fun safeSqrt(value: Double): Double =
        if (value.isNaN()) Double.NaN else sqrt(max(0.0, value))

    fun sorted(values: DoubleArray): DoubleArray = values.copyOf().also { it.sort() }

    /**
     * Linear-interpolation quantile — numpy's default `percentile` method, i.e. Hyndman & Fan
     * type 7: `h = (n - 1) * p`, interpolated between `sorted[floor(h)]` and `sorted[ceil(h)]`.
     */
    fun quantileSorted(sortedValues: DoubleArray, p: Double): Double {
        if (sortedValues.isEmpty()) return Double.NaN
        if (sortedValues.size == 1) return sortedValues[0]
        val h = (sortedValues.size - 1) * p.coerceIn(0.0, 1.0)
        val lower = floor(h).toInt()
        val upper = ceil(h).toInt()
        if (lower == upper) return sortedValues[lower]
        return sortedValues[lower] + (h - lower) * (sortedValues[upper] - sortedValues[lower])
    }

    fun quantile(values: DoubleArray, p: Double): Double = quantileSorted(sorted(values), p)

    fun median(values: DoubleArray): Double = quantile(values, 0.5)

    /**
     * Raw median absolute deviation from [center].
     *
     * The 0.6745 consistency factor used by the robust z-score is applied by the caller
     * (AGENTS.md §6.7), so this stays a plain, comparable statistic.
     */
    fun medianAbsoluteDeviation(values: DoubleArray, center: Double): Double {
        if (values.isEmpty()) return Double.NaN
        val deviations = DoubleArray(values.size) { abs(values[it] - center) }
        return median(deviations)
    }

    /** Average ranks (tied values share the mean of their rank span) — the basis of Spearman's rho. */
    fun averageRanks(values: DoubleArray): DoubleArray {
        val order = values.indices.sortedBy { values[it] }
        val ranks = DoubleArray(values.size)
        var i = 0
        while (i < order.size) {
            var j = i
            while (j + 1 < order.size && values[order[j + 1]] == values[order[i]]) j++
            val averageRank = (i + j) / 2.0 + 1.0
            for (k in i..j) ranks[order[k]] = averageRank
            i = j + 1
        }
        return ranks
    }

    /**
     * Sample (n-1) or population (n) covariance; `NaN` when undefined for the requested flavour.
     */
    fun covariance(x: DoubleArray, y: DoubleArray, sample: Boolean = true): Double {
        require(x.size == y.size) { "covariance requires equally sized inputs (${x.size} vs ${y.size})" }
        val n = x.size
        if (n == 0) return Double.NaN
        if (sample && n < 2) return Double.NaN
        val meanX = mean(x)
        val meanY = mean(y)
        var acc = 0.0
        for (i in 0 until n) acc += (x[i] - meanX) * (y[i] - meanY)
        return acc / (if (sample) n - 1 else n)
    }

    /** Pearson product-moment correlation; `null` when undefined (degenerate spread or n < 2). */
    fun pearson(x: DoubleArray, y: DoubleArray): Double? {
        require(x.size == y.size) { "pearson requires equally sized inputs (${x.size} vs ${y.size})" }
        val n = x.size
        if (n < 2) return null
        val meanX = mean(x)
        val meanY = mean(y)
        var sumXy = 0.0
        var sumXx = 0.0
        var sumYy = 0.0
        for (i in 0 until n) {
            val dx = x[i] - meanX
            val dy = y[i] - meanY
            sumXy += dx * dy
            sumXx += dx * dx
            sumYy += dy * dy
        }
        val denominator = sqrt(sumXx * sumYy)
        if (denominator <= 0.0 || !denominator.isFinite()) return null
        return (sumXy / denominator).coerceIn(-1.0, 1.0)
    }

    /**
     * Ordinary-least-squares fit of `y = intercept + slope * x`.
     *
     * `null` when the fit is undefined (fewer than two points or no spread in `x`).
     */
    fun linearFit(x: DoubleArray, y: DoubleArray): LinearFit? {
        require(x.size == y.size) { "linearFit requires equally sized inputs (${x.size} vs ${y.size})" }
        val n = x.size
        if (n < 2) return null
        val meanX = mean(x)
        val meanY = mean(y)
        var sumXy = 0.0
        var sumXx = 0.0
        for (i in 0 until n) {
            val dx = x[i] - meanX
            sumXy += dx * (y[i] - meanY)
            sumXx += dx * dx
        }
        if (sumXx <= 0.0) return null
        val slope = sumXy / sumXx
        val intercept = meanY - slope * meanX
        var residualSumOfSquares = 0.0
        var totalSumOfSquares = 0.0
        for (i in 0 until n) {
            val predicted = intercept + slope * x[i]
            val residual = y[i] - predicted
            residualSumOfSquares += residual * residual
            val deviation = y[i] - meanY
            totalSumOfSquares += deviation * deviation
        }
        val rSquared = if (totalSumOfSquares <= 0.0) 0.0 else 1.0 - residualSumOfSquares / totalSumOfSquares
        return LinearFit(
            slope = slope,
            intercept = intercept,
            rSquared = rSquared,
            sumSquaredXDeviations = sumXx,
            residualSumOfSquares = residualSumOfSquares,
        )
    }

    /** Periodic Hann window of length [n]; a single sample degenerates to `1.0`. */
    fun hannWindow(n: Int): DoubleArray {
        if (n <= 0) return DoubleArray(0)
        if (n == 1) return doubleArrayOf(1.0)
        return DoubleArray(n) { i ->
            0.5 * (1.0 - kotlin.math.cos(2.0 * Math.PI * i / (n - 1)))
        }
    }

    /** Smallest power of two `>= n` (minimum 1). */
    fun nextPowerOfTwo(n: Int): Int {
        require(n > 0) { "nextPowerOfTwo requires n > 0, was $n" }
        var value = 1
        while (value < n) value = value shl 1
        return value
    }
}

/** Result of [NumericSupport.linearFit]. */
internal data class LinearFit(
    val slope: Double,
    val intercept: Double,
    val rSquared: Double,
    val sumSquaredXDeviations: Double,
    val residualSumOfSquares: Double,
)
