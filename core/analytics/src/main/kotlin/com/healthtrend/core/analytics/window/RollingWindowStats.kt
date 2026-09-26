package com.healthtrend.core.analytics.window

import com.healthtrend.core.analytics.internal.NumericSupport
import com.healthtrend.core.analytics.model.RawDataPoint
import com.healthtrend.core.analytics.model.TimeSeries
import com.healthtrend.core.analytics.model.orderedPoints
import java.time.Duration

/**
 * One trailing window ending at [timestampEpochMilli].
 *
 * The window is the physical time interval `[windowStartEpochMilli, timestampEpochMilli]`; its
 * length in *points* is whatever falls inside, so it varies with sampling density.
 */
data class RollingWindowPoint(
    val timestampEpochMilli: Long,
    val windowStartEpochMilli: Long,
    val sampleCount: Int,
    val sum: Double,
    val mean: Double,
    /** Sample standard deviation; `NaN` for a single-point window. */
    val sampleStandardDeviation: Double,
    val min: Double,
    val max: Double,
)

/**
 * Time-based rolling window statistics — AGENTS.md §6.4.
 *
 * The window is defined in **days of wall-clock time, not in sample counts** (§6.4), so two series
 * sampled at different densities still produce comparable curves. Implemented with a two-pointer
 * sweep: the right pointer advances once per point and the left pointer only ever moves forward,
 * giving O(n) time.
 *
 * Min/max are maintained with monotonic deques so they are also O(1) amortised, and the running
 * sum / sum of squares make the variance O(1) per step. The variance formula is
 * `(Σx² - (Σx)²/n) / (n-1)`, clamped at zero for floating-point cancellation.
 */
object RollingWindowStats {

    fun compute(series: TimeSeries, window: Duration): List<RollingWindowPoint> =
        compute(series, window.toMillis())

    /**
     * @param windowMillis trailing window width; must be `> 0`
     */
    fun compute(series: TimeSeries, windowMillis: Long): List<RollingWindowPoint> {
        require(windowMillis > 0L) { "windowMillis must be > 0, was $windowMillis" }
        val points = series.orderedPoints()
        if (points.isEmpty()) return emptyList()

        val results = ArrayList<RollingWindowPoint>(points.size)
        var left = 0
        var runningSum = 0.0
        var runningSumOfSquares = 0.0
        val minDeque = ArrayDeque<Int>()
        val maxDeque = ArrayDeque<Int>()

        for (right in points.indices) {
            val rightTimestamp = points[right].timestampEpochMilli
            val cutoff = rightTimestamp - windowMillis

            while (left < right && points[left].timestampEpochMilli < cutoff) {
                val evicted = points[left].value
                runningSum -= evicted
                runningSumOfSquares -= evicted * evicted
                if (minDeque.firstOrNull() == left) minDeque.removeFirst()
                if (maxDeque.firstOrNull() == left) maxDeque.removeFirst()
                left++
            }

            val value = points[right].value
            runningSum += value
            runningSumOfSquares += value * value

            while (minDeque.isNotEmpty() && points[minDeque.last()].value >= value) minDeque.removeLast()
            minDeque.addLast(right)
            while (maxDeque.isNotEmpty() && points[maxDeque.last()].value <= value) maxDeque.removeLast()
            maxDeque.addLast(right)

            val count = right - left + 1
            val mean = runningSum / count
            val sampleStandardDeviation = if (count < 2) {
                Double.NaN
            } else {
                val variance = (runningSumOfSquares - runningSum * runningSum / count) / (count - 1)
                NumericSupport.safeSqrt(variance)
            }

            results += RollingWindowPoint(
                timestampEpochMilli = rightTimestamp,
                windowStartEpochMilli = points[left].timestampEpochMilli,
                sampleCount = count,
                sum = runningSum,
                mean = mean,
                sampleStandardDeviation = sampleStandardDeviation,
                min = points[minDeque.first()].value,
                max = points[maxDeque.first()].value,
            )
        }
        return results
    }

    /**
     * Convenience wrapper that keeps the timestamps of [series] but replaces the values with the
     * trailing [window] mean — the shape most chart callers want.
     */
    fun rollingMean(series: TimeSeries, window: Duration): TimeSeries {
        val windows = compute(series, window)
        return series.copy(
            points = windows.map { RawDataPoint(it.timestampEpochMilli, it.mean) },
        )
    }
}
