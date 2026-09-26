package com.healthtrend.core.analytics.anomaly

import com.healthtrend.core.analytics.internal.NumericSupport
import com.healthtrend.core.analytics.model.MILLIS_PER_DAY
import com.healthtrend.core.analytics.model.TimeSeries
import com.healthtrend.core.analytics.model.orderedPoints
import com.healthtrend.core.analytics.smoothing.Ewma
import kotlin.math.abs

/** Detector that flagged a point. A point may be flagged by more than one. */
enum class AnomalyMethod {
    /** Classical `(x - mean) / sd`; sensitive to the very outliers it detects. */
    Z_SCORE,

    /** Robust `0.6745 · (x - median) / MAD`; AGENTS.md §6.7's preferred detector. */
    MODIFIED_Z_SCORE,

    /** Outside the Tukey fences `[Q1 - k·IQR, Q3 + k·IQR]`. */
    IQR,

    /** Standardised residual against the time-decayed EWMA (§6.5). */
    EWMA_RESIDUAL,
}

/**
 * Per-point anomaly evidence. Every statistic is reported for every point, flagged or not, so a
 * chart can plot the score curve instead of only the marked points.
 */
data class AnomalyPoint(
    val timestampEpochMilli: Long,
    val value: Double,
    /** Classical z-score; `0.0` when the sample has no spread. */
    val zScore: Double,
    /** Robust z-score using the MAD; `0.0` when the MAD is zero. */
    val robustZScore: Double,
    val ewmaResidual: Double,
    val ewmaResidualZScore: Double,
    val flaggedMethods: Set<AnomalyMethod>,
) {
    val isAnomaly: Boolean get() = flaggedMethods.isNotEmpty()
}

/**
 * Anomaly report with the thresholds and robust location/scale actually used.
 *
 * UI RULE (AGENTS.md §6.7 / §10.3): the caller must render this as "deviates from your historical
 * baseline by X" — never as a diagnosis, and never with disease or danger vocabulary.
 */
data class AnomalyReport(
    val points: List<AnomalyPoint>,
    val mean: Double,
    val median: Double,
    val standardDeviation: Double,
    /** Raw median absolute deviation (unscaled by 0.6745). */
    val medianAbsoluteDeviation: Double,
    val firstQuartile: Double,
    val thirdQuartile: Double,
    val interquartileRange: Double,
    val iqrLowerFence: Double,
    val iqrUpperFence: Double,
    val zThreshold: Double,
    val modifiedZThreshold: Double,
    val ewmaResidualThreshold: Double,
    val iqrMultiplier: Double,
    /** Convenience for the UI: only the flagged points, in time order. */
    val anomalies: List<AnomalyPoint>,
) {
    val anomalyCount: Int get() = anomalies.size
}

/**
 * Composite anomaly detection — AGENTS.md §6.7.
 *
 * Four detectors are combined because each fails differently:
 *
 * * **Z-score** — cheap and familiar, but a single extreme value inflates the standard deviation
 *   and can hide itself.
 * * **Modified z-score (MAD)** — median-based, so up to 50% of the sample can be outliers before
 *   the scale estimate breaks down.
 * * **IQR fences** — non-parametric, no distributional assumption.
 * * **EWMA residual** — the only detector that accounts for *trend*, by comparing each point to a
 *   time-decayed expectation rather than to the global mean.
 *
 * Degenerate samples never produce spurious flags: zero spread gives a score of `0.0`.
 */
object AnomalyDetection {

    const val ROBUST_SCALE: Double = 0.6745

    fun detect(
        series: TimeSeries,
        zThreshold: Double = 3.0,
        modifiedZThreshold: Double = 3.5,
        iqrMultiplier: Double = 1.5,
        ewmaHalfLifeMillis: Double = 7.0 * MILLIS_PER_DAY,
        ewmaResidualThreshold: Double = 3.0,
    ): AnomalyReport {
        require(zThreshold > 0.0) { "zThreshold must be > 0, was $zThreshold" }
        require(modifiedZThreshold > 0.0) { "modifiedZThreshold must be > 0, was $modifiedZThreshold" }
        require(iqrMultiplier > 0.0) { "iqrMultiplier must be > 0, was $iqrMultiplier" }
        require(ewmaHalfLifeMillis > 0.0) { "ewmaHalfLifeMillis must be > 0, was $ewmaHalfLifeMillis" }
        require(ewmaResidualThreshold > 0.0) { "ewmaResidualThreshold must be > 0, was $ewmaResidualThreshold" }

        val ordered = series.orderedPoints()
        val values = DoubleArray(ordered.size) { ordered[it].value }
        val (mean, standardDeviation) = stats(values)
        val sorted = NumericSupport.sorted(values)
        val median = if (values.isEmpty()) Double.NaN else NumericSupport.quantileSorted(sorted, 0.5)
        val q1 = if (values.isEmpty()) Double.NaN else NumericSupport.quantileSorted(sorted, 0.25)
        val q3 = if (values.isEmpty()) Double.NaN else NumericSupport.quantileSorted(sorted, 0.75)
        val iqr = q3 - q1
        val mad = NumericSupport.medianAbsoluteDeviation(values, median)
        val lowerFence = q1 - iqrMultiplier * iqr
        val upperFence = q3 + iqrMultiplier * iqr

        val residuals = Ewma.residuals(series, ewmaHalfLifeMillis)
        val (residualMean, residualStandardDeviation) = stats(residuals.map { it.value }.toDoubleArray())

        val points = ordered.mapIndexed { index, point ->
            val zScore = if (standardDeviation.isFinite() && standardDeviation > 0.0) {
                (point.value - mean) / standardDeviation
            } else {
                0.0
            }
            val robustZScore = if (mad.isFinite() && mad > 0.0) {
                ROBUST_SCALE * (point.value - median) / mad
            } else {
                0.0
            }
            val ewmaResidual = residuals[index].value
            val ewmaResidualZScore = if (residualStandardDeviation.isFinite() && residualStandardDeviation > 0.0) {
                (ewmaResidual - residualMean) / residualStandardDeviation
            } else {
                0.0
            }

            val flagged = buildSet {
                if (abs(zScore) > zThreshold) add(AnomalyMethod.Z_SCORE)
                if (abs(robustZScore) > modifiedZThreshold) add(AnomalyMethod.MODIFIED_Z_SCORE)
                if (point.value < lowerFence || point.value > upperFence) add(AnomalyMethod.IQR)
                if (abs(ewmaResidualZScore) > ewmaResidualThreshold) add(AnomalyMethod.EWMA_RESIDUAL)
            }

            AnomalyPoint(
                timestampEpochMilli = point.timestampEpochMilli,
                value = point.value,
                zScore = zScore,
                robustZScore = robustZScore,
                ewmaResidual = ewmaResidual,
                ewmaResidualZScore = ewmaResidualZScore,
                flaggedMethods = flagged,
            )
        }

        return AnomalyReport(
            points = points,
            mean = mean,
            median = median,
            standardDeviation = standardDeviation,
            medianAbsoluteDeviation = mad,
            firstQuartile = q1,
            thirdQuartile = q3,
            interquartileRange = iqr,
            iqrLowerFence = lowerFence,
            iqrUpperFence = upperFence,
            zThreshold = zThreshold,
            modifiedZThreshold = modifiedZThreshold,
            ewmaResidualThreshold = ewmaResidualThreshold,
            iqrMultiplier = iqrMultiplier,
            anomalies = points.filter { it.isAnomaly },
        )
    }

    private fun stats(values: DoubleArray): Pair<Double, Double> {
        if (values.isEmpty()) return Double.NaN to Double.NaN
        val mean = NumericSupport.mean(values)
        val sd = NumericSupport.safeSqrt(NumericSupport.sumSquaredDeviations(values, mean) / (values.size - 1).coerceAtLeast(1))
        return mean to sd
    }
}
