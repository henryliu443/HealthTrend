package com.healthtrend.core.analytics.smoothing

import com.healthtrend.core.analytics.model.MILLIS_PER_DAY
import com.healthtrend.core.analytics.model.RawDataPoint
import com.healthtrend.core.analytics.model.TimeSeries
import com.healthtrend.core.analytics.model.orderedPoints
import java.time.Duration
import kotlin.math.exp

/**
 * Exponentially weighted moving average for **irregularly spaced** time series — AGENTS.md §6.5.
 *
 * A classic EWMA uses a fixed per-sample weight, which silently assumes a constant sampling rate.
 * Health data is not sampled on a schedule — two weight entries can be one day or three weeks
 * apart — so the decay is modelled in continuous time:
 *
 * ```
 * &#955;       = ln(2) / halfLife
 * &#916;t      = t_k - t_{k-1}
 * &#945;_k     = 1 - e^(-&#955;·&#916;t)
 * S_k     = &#945;_k · x_k + (1 - &#945;_k) · S_{k-1}
 * ```
 *
 * A point one half-life after its predecessor therefore gets the same weight regardless of how
 * many other points sit in between, and a point sampled immediately after another barely moves
 * the average. `&#916;t = 0` yields `&#945; = 0` (the duplicate timestamp cannot move the average).
 */
object Ewma {

    const val LN_2: Double = 0.6931471805599453

    /** Decay constant `&#955; = ln(2) / halfLife` for a half-life given in days. */
    fun decayPerDay(halfLifeDays: Double): Double {
        require(halfLifeDays > 0.0) { "halfLifeDays must be > 0, was $halfLifeDays" }
        return LN_2 / (halfLifeDays * MILLIS_PER_DAY)
    }

    fun compute(series: TimeSeries, halfLife: Duration, seed: Double? = null): List<RawDataPoint> =
        compute(series, halfLife.toMillis().toDouble(), seed)

    /**
     * @param halfLifeMillis half-life of the decay, in milliseconds; must be `> 0`
     * @param seed value used as `S_0` before the first point; defaults to the first observation
     * @return one smoothed point per input point, aligned to the input timestamps
     */
    fun compute(series: TimeSeries, halfLifeMillis: Double, seed: Double? = null): List<RawDataPoint> {
        require(halfLifeMillis > 0.0) { "halfLifeMillis must be > 0, was $halfLifeMillis" }
        val points = series.orderedPoints()
        if (points.isEmpty()) return emptyList()
        val lambda = LN_2 / halfLifeMillis
        val smoothed = ArrayList<RawDataPoint>(points.size)
        var previousSmoothed = seed ?: points.first().value
        var previousTimestamp = points.first().timestampEpochMilli
        for ((index, point) in points.withIndex()) {
            val current = if (index == 0 && seed == null) {
                previousSmoothed
            } else {
                val deltaMillis = (point.timestampEpochMilli - previousTimestamp).toDouble()
                val alpha = 1.0 - exp(-lambda * deltaMillis)
                alpha * point.value + (1.0 - alpha) * previousSmoothed
            }
            smoothed += RawDataPoint(point.timestampEpochMilli, current)
            previousSmoothed = current
            previousTimestamp = point.timestampEpochMilli
        }
        return smoothed
    }

    /** `observed - smoothed` at every input timestamp; the residual feeds §6.7's EWMA detector. */
    fun residuals(series: TimeSeries, halfLifeMillis: Double, seed: Double? = null): List<RawDataPoint> {
        val points = series.orderedPoints()
        val smoothed = compute(series, halfLifeMillis, seed)
        return points.mapIndexed { index, point ->
            RawDataPoint(point.timestampEpochMilli, point.value - smoothed[index].value)
        }
    }

    /** Convenience overload taking a half-life in days. */
    fun computeDays(series: TimeSeries, halfLifeDays: Double, seed: Double? = null): List<RawDataPoint> =
        compute(series, halfLifeDays * MILLIS_PER_DAY, seed)
}
