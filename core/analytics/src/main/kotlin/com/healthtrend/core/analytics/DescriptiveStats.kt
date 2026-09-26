package com.healthtrend.core.analytics

import com.healthtrend.core.analytics.internal.NumericSupport
import com.healthtrend.core.analytics.model.RawDataPoint
import com.healthtrend.core.analytics.model.TimeSeries
import kotlin.math.sqrt

/**
 * Descriptive statistics of a sample (AGENTS.md §6.1).
 *
 * `NaN` marks "undefined for this sample size" rather than an error, so a caller can render a
 * partial dashboard without special-casing. The rules are:
 *
 * | field | undefined when |
 * |---|---|
 * | `sampleVariance`, `sampleStandardDeviation`, `standardErrorOfMean` | `count < 2` |
 * | `skewness` | `count < 3` |
 * | `kurtosis` (excess) | `count < 4` |
 *
 * Both are `0.0` — not `NaN` — when the spread is exactly zero, as mandated by AGENTS.md §6.1.
 */
data class DescriptiveStatistics(
    val count: Int,
    val sum: Double,
    val mean: Double,
    val min: Double,
    val max: Double,
    val range: Double,
    val median: Double,
    val firstQuartile: Double,
    val thirdQuartile: Double,
    val interquartileRange: Double,
    /** Unbiased sample variance (`1 / (n - 1)`); `NaN` when `count < 2`. */
    val sampleVariance: Double,
    /** Population variance (`1 / n`). */
    val populationVariance: Double,
    val sampleStandardDeviation: Double,
    val populationStandardDeviation: Double,
    val standardErrorOfMean: Double,
    /** Unbiased (bias-corrected) Fisher–Pearson sample skewness. */
    val skewness: Double,
    /** Unbiased excess kurtosis (Fisher definition: a normal sample is `0.0`). */
    val kurtosis: Double,
)

/**
 * Descriptive statistics — AGENTS.md §6.1.
 *
 * Two-pass mean/variance: the mean is computed first and deviations are squared against it in a
 * second pass. This avoids the catastrophic cancellation of the naive `E[x²] - E[x]²` formula.
 */
object DescriptiveStats {

    /** Empty-input result: `count = 0`, everything else `NaN`. */
    val EMPTY: DescriptiveStatistics = fromValues(DoubleArray(0))

    fun calculate(values: DoubleArray): DescriptiveStatistics = fromValues(values)

    fun calculate(points: List<RawDataPoint>): DescriptiveStatistics =
        fromValues(DoubleArray(points.size) { points[it].value })

    fun calculate(series: TimeSeries): DescriptiveStatistics = calculate(series.points)

    private fun fromValues(values: DoubleArray): DescriptiveStatistics {
        val n = values.size
        if (n == 0) {
            return DescriptiveStatistics(
                count = 0,
                sum = Double.NaN,
                mean = Double.NaN,
                min = Double.NaN,
                max = Double.NaN,
                range = Double.NaN,
                median = Double.NaN,
                firstQuartile = Double.NaN,
                thirdQuartile = Double.NaN,
                interquartileRange = Double.NaN,
                sampleVariance = Double.NaN,
                populationVariance = Double.NaN,
                sampleStandardDeviation = Double.NaN,
                populationStandardDeviation = Double.NaN,
                standardErrorOfMean = Double.NaN,
                skewness = Double.NaN,
                kurtosis = Double.NaN,
            )
        }

        var sum = 0.0
        for (value in values) sum += value

        val mean = NumericSupport.mean(values)
        val sorted = NumericSupport.sorted(values)
        val median = NumericSupport.quantileSorted(sorted, 0.5)
        val q1 = NumericSupport.quantileSorted(sorted, 0.25)
        val q3 = NumericSupport.quantileSorted(sorted, 0.75)

        val sumSquaredDeviations = NumericSupport.sumSquaredDeviations(values, mean)
        val populationVariance = sumSquaredDeviations / n
        val sampleVariance = if (n >= 2) sumSquaredDeviations / (n - 1) else Double.NaN
        val populationStandardDeviation = NumericSupport.safeSqrt(populationVariance)
        val sampleStandardDeviation = if (n >= 2) NumericSupport.safeSqrt(sampleVariance) else Double.NaN
        val standardErrorOfMean = if (n >= 2) sampleStandardDeviation / sqrt(n.toDouble()) else Double.NaN

        // Third and fourth central moments (population form) are only meaningful with spread.
        val m2 = populationVariance
        var m3 = 0.0
        var m4 = 0.0
        if (m2 > 0.0) {
            for (value in values) {
                val d = value - mean
                val d2 = d * d
                m3 += d2 * d
                m4 += d2 * d2
            }
            m3 /= n
            m4 /= n
        }

        val skewness = when {
            m2 == 0.0 -> 0.0
            n < 3 -> Double.NaN
            else -> {
                // Unbiased Fisher–Pearson: sqrt(n(n-1)) / (n-2) * g1.
                val g1 = m3 / (m2 * sqrt(m2))
                sqrt(n.toDouble() * (n - 1)) / (n - 2) * g1
            }
        }

        val kurtosis = when {
            m2 == 0.0 -> 0.0
            n < 4 -> Double.NaN
            else -> {
                // Unbiased excess kurtosis: (n-1) / ((n-2)(n-3)) * ((n+1) g2 + 6).
                val g2 = m4 / (m2 * m2) - 3.0
                ((n + 1) * g2 + 6.0) * (n - 1) / ((n - 2).toDouble() * (n - 3))
            }
        }

        return DescriptiveStatistics(
            count = n,
            sum = sum,
            mean = mean,
            min = sorted.first(),
            max = sorted.last(),
            range = sorted.last() - sorted.first(),
            median = median,
            firstQuartile = q1,
            thirdQuartile = q3,
            interquartileRange = q3 - q1,
            sampleVariance = sampleVariance,
            populationVariance = populationVariance,
            sampleStandardDeviation = sampleStandardDeviation,
            populationStandardDeviation = populationStandardDeviation,
            standardErrorOfMean = standardErrorOfMean,
            skewness = skewness,
            kurtosis = kurtosis,
        )
    }
}
