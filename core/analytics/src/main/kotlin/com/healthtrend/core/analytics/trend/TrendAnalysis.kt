package com.healthtrend.core.analytics.trend

import com.healthtrend.core.analytics.internal.NumericSupport
import com.healthtrend.core.analytics.model.MILLIS_PER_DAY
import com.healthtrend.core.analytics.model.RawDataPoint
import com.healthtrend.core.analytics.model.TimeSeries
import com.healthtrend.core.analytics.model.orderedPoints
import org.apache.commons.math3.distribution.TDistribution
import kotlin.math.abs
import kotlin.math.sqrt

/** Trend classification. [STABLE] means "not statistically distinguishable from flat". */
enum class TrendDirection {
    INCREASING,
    DECREASING,

    /** Slope is not significant at the requested level — explicitly *not* "no change". */
    STABLE,

    /** Too few points, or no spread in time, to fit anything. */
    INSUFFICIENT_DATA,
}

/**
 * Ordinary-least-squares trend of a series against **time**, AGENTS.md §6.6.
 *
 * The regressor is elapsed days since the first point, so [slopePerDay] is always "units per day"
 * regardless of when the window starts. Significance uses the two-sided t-test on the slope:
 * only `p < significanceLevel` yields [TrendDirection.INCREASING] / [TrendDirection.DECREASING];
 * anything else is reported as [TrendDirection.STABLE] rather than being over-interpreted.
 */
data class LinearTrend(
    val sampleCount: Int,
    val spanDays: Double,
    /** Units per day. `NaN` when the fit is undefined. */
    val slopePerDay: Double,
    /** Value at the first timestamp (i.e. the fit evaluated at `x = 0`). */
    val intercept: Double,
    val rSquared: Double,
    val residualStandardError: Double,
    val slopeStandardError: Double,
    val tStatistic: Double,
    val degreesOfFreedom: Int,
    val pValue: Double,
    val significanceLevel: Double,
    val direction: TrendDirection,
    val startTimestampEpochMilli: Long,
    val endTimestampEpochMilli: Long,
    /** Fitted value at the first / last timestamp, for overlay rendering. */
    val fittedStart: Double,
    val fittedEnd: Double,
)

/** A trend fitted inside one trailing time window; see [TrendAnalysis.localTrend]. */
data class LocalTrendPoint(
    val timestampEpochMilli: Long,
    val windowStartEpochMilli: Long,
    val sampleCount: Int,
    val slopePerDay: Double,
    val pValue: Double,
    val direction: TrendDirection,
)

/**
 * Linear regression and local (sliding-window) trend analysis — AGENTS.md §6.6.
 *
 * `commons-math3`'s `TDistribution` supplies the Student-t tail probability, as sanctioned by
 * AGENTS.md §0.3: pure Kotlin sources are free to call a mature Java scientific library.
 */
object TrendAnalysis {

    fun fit(series: TimeSeries, significanceLevel: Double = 0.05): LinearTrend =
        fit(series.orderedPoints(), significanceLevel)

    fun fit(points: List<RawDataPoint>, significanceLevel: Double = 0.05): LinearTrend {
        require(significanceLevel > 0.0 && significanceLevel < 1.0) {
            "significanceLevel must be in (0, 1), was $significanceLevel"
        }
        val ordered = points.sortedBy { it.timestampEpochMilli }
        val n = ordered.size
        if (n < 2) return insufficient(ordered, significanceLevel)

        val origin = ordered.first().timestampEpochMilli
        val x = DoubleArray(n) { (ordered[it].timestampEpochMilli - origin) / MILLIS_PER_DAY }
        val y = DoubleArray(n) { ordered[it].value }

        val fit = NumericSupport.linearFit(x, y) ?: return insufficient(ordered, significanceLevel)

        val df = n - 2
        if (df < 1) {
            // Two points define a line exactly, but there is no residual information: slope is
            // reported, significance is not.
            val spanDays = x.last()
            return LinearTrend(
                sampleCount = n,
                spanDays = spanDays,
                slopePerDay = fit.slope,
                intercept = fit.intercept,
                rSquared = fit.rSquared,
                residualStandardError = Double.NaN,
                slopeStandardError = Double.NaN,
                tStatistic = Double.NaN,
                degreesOfFreedom = 0,
                pValue = Double.NaN,
                significanceLevel = significanceLevel,
                direction = TrendDirection.INSUFFICIENT_DATA,
                startTimestampEpochMilli = ordered.first().timestampEpochMilli,
                endTimestampEpochMilli = ordered.last().timestampEpochMilli,
                fittedStart = fit.intercept,
                fittedEnd = fit.intercept + fit.slope * x.last(),
            )
        }

        val residualStandardError = sqrt(fit.residualSumOfSquares / df)
        val slopeStandardError = residualStandardError / sqrt(fit.sumSquaredXDeviations)

        // A perfect fit has zero residual variance, so the t-statistic is 0/0. That is not
        // "insufficient data": the evidence is absolute. Handle it explicitly instead of
        // propagating NaN and having callers read a deterministic trend as "unknown".
        val tStatistic: Double
        val pValue: Double
        when {
            slopeStandardError > 0.0 -> {
                tStatistic = fit.slope / slopeStandardError
                pValue = twoSidedPValue(tStatistic, df)
            }
            fit.slope == 0.0 -> {
                tStatistic = Double.NaN
                pValue = 1.0
            }
            else -> {
                tStatistic = if (fit.slope > 0.0) Double.POSITIVE_INFINITY else Double.NEGATIVE_INFINITY
                pValue = 0.0
            }
        }

        val direction = when {
            pValue < significanceLevel && fit.slope > 0.0 -> TrendDirection.INCREASING
            pValue < significanceLevel && fit.slope < 0.0 -> TrendDirection.DECREASING
            else -> TrendDirection.STABLE
        }

        return LinearTrend(
            sampleCount = n,
            spanDays = x.last(),
            slopePerDay = fit.slope,
            intercept = fit.intercept,
            rSquared = fit.rSquared,
            residualStandardError = residualStandardError,
            slopeStandardError = slopeStandardError,
            tStatistic = tStatistic,
            degreesOfFreedom = df,
            pValue = pValue,
            significanceLevel = significanceLevel,
            direction = direction,
            startTimestampEpochMilli = ordered.first().timestampEpochMilli,
            endTimestampEpochMilli = ordered.last().timestampEpochMilli,
            fittedStart = fit.intercept,
            fittedEnd = fit.intercept + fit.slope * x.last(),
        )
    }

    /**
     * Fits a line inside every trailing window of [windowMillis] and reports the direction of the
     * local slope — "is the trend right now up or down", as opposed to the single global slope.
     *
     * A window with fewer than three points yields [TrendDirection.INSUFFICIENT_DATA].
     */
    fun localTrend(
        series: TimeSeries,
        windowMillis: Long,
        significanceLevel: Double = 0.05,
    ): List<LocalTrendPoint> {
        require(windowMillis > 0L) { "windowMillis must be > 0, was $windowMillis" }
        val points = series.orderedPoints()
        if (points.size < 3) return emptyList()
        val results = ArrayList<LocalTrendPoint>(points.size)
        var left = 0
        for (right in points.indices) {
            val cutoff = points[right].timestampEpochMilli - windowMillis
            while (left < right && points[left].timestampEpochMilli < cutoff) left++
            val window = points.subList(left, right + 1)
            val trend = fit(window, significanceLevel)
            results += LocalTrendPoint(
                timestampEpochMilli = points[right].timestampEpochMilli,
                windowStartEpochMilli = points[left].timestampEpochMilli,
                sampleCount = window.size,
                slopePerDay = trend.slopePerDay,
                pValue = trend.pValue,
                direction = trend.direction,
            )
        }
        return results
    }

    /** Two-sided t-test p-value: `2 · (1 - CDF(|t|))`, clamped into `[0, 1]`. */
    private fun twoSidedPValue(tStatistic: Double, degreesOfFreedom: Int): Double {
        val distribution = TDistribution(degreesOfFreedom.toDouble())
        val tail = 1.0 - distribution.cumulativeProbability(abs(tStatistic))
        return (2.0 * tail).coerceIn(0.0, 1.0)
    }

    private fun insufficient(points: List<RawDataPoint>, significanceLevel: Double): LinearTrend {
        val start = points.firstOrNull()?.timestampEpochMilli ?: 0L
        val end = points.lastOrNull()?.timestampEpochMilli ?: 0L
        return LinearTrend(
            sampleCount = points.size,
            spanDays = if (points.size < 2) 0.0 else (end - start) / MILLIS_PER_DAY,
            slopePerDay = Double.NaN,
            intercept = Double.NaN,
            rSquared = Double.NaN,
            residualStandardError = Double.NaN,
            slopeStandardError = Double.NaN,
            tStatistic = Double.NaN,
            degreesOfFreedom = 0,
            pValue = Double.NaN,
            significanceLevel = significanceLevel,
            direction = TrendDirection.INSUFFICIENT_DATA,
            startTimestampEpochMilli = start,
            endTimestampEpochMilli = end,
            fittedStart = Double.NaN,
            fittedEnd = Double.NaN,
        )
    }
}
