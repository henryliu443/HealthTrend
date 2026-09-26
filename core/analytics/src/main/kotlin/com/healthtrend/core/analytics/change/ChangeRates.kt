package com.healthtrend.core.analytics.change

import com.healthtrend.core.analytics.model.MILLIS_PER_DAY
import com.healthtrend.core.analytics.model.RawDataPoint
import com.healthtrend.core.analytics.model.TimeSeries
import com.healthtrend.core.analytics.model.orderedPoints
import kotlin.math.pow

/**
 * Change between two observations (AGENTS.md §6.3).
 *
 * [cagr] is deliberately nullable: a geometric (compound) rate is undefined whenever either
 * endpoint is `<= 0` (a negative or zero value has no meaningful ratio growth). In that case the
 * analysis is arithmetic-only, and [geometricAvailable] is `false` so the UI can say so.
 */
data class ChangeAnalysis(
    val startTimestampEpochMilli: Long,
    val endTimestampEpochMilli: Long,
    val startValue: Double,
    val endValue: Double,
    /** `end - start`. */
    val absoluteChange: Double,
    /** `(end - start) / start`; `null` when `start == 0.0`. */
    val relativeChange: Double?,
    /** [relativeChange] scaled by 100; `null` when `start == 0.0`. */
    val percentChange: Double?,
    val spanDays: Double,
    /** Average absolute change per day; `null` when both points share a timestamp. */
    val changePerDay: Double?,
    /** Annualised compound growth rate; `null` when either endpoint is `<= 0`. */
    val cagr: Double?,
    /** `false` when a geometric rate is mathematically unavailable (AGENTS.md: ARITHMETIC_ONLY). */
    val geometricAvailable: Boolean,
)

/**
 * Per-segment rate and its change from the previous segment ("acceleration").
 *
 * [perDayRateAcceleration] is in value/day² and is `null` for the first segment.
 */
data class SegmentRate(
    val fromTimestampEpochMilli: Long,
    val toTimestampEpochMilli: Long,
    val perDayRate: Double,
    val perDayRateAcceleration: Double?,
)

/** Change, rate and compound-growth math — AGENTS.md §6.3. */
object ChangeRates {

    /** Days per year used for CAGR annualisation, as fixed by AGENTS.md §6.3. */
    const val DAYS_PER_YEAR: Double = 365.25

    /**
     * CAGR: `(end / start) ^ (365.25 / spanDays) - 1`.
     *
     * @return `null` when either endpoint is `<= 0`, or when the span is not strictly positive.
     */
    fun cagr(startValue: Double, endValue: Double, spanDays: Double): Double? {
        if (startValue <= 0.0 || endValue <= 0.0 || spanDays <= 0.0) return null
        return (endValue / startValue).pow(DAYS_PER_YEAR / spanDays) - 1.0
    }

    /** Compares two explicit observations. */
    fun analyze(start: RawDataPoint, end: RawDataPoint): ChangeAnalysis {
        val spanDays = (end.timestampEpochMilli - start.timestampEpochMilli) / MILLIS_PER_DAY
        val absoluteChange = end.value - start.value
        val relativeChange = if (start.value == 0.0) null else absoluteChange / start.value
        val changePerDay = if (spanDays > 0.0) absoluteChange / spanDays else null
        val geometricAvailable = start.value > 0.0 && end.value > 0.0
        return ChangeAnalysis(
            startTimestampEpochMilli = start.timestampEpochMilli,
            endTimestampEpochMilli = end.timestampEpochMilli,
            startValue = start.value,
            endValue = end.value,
            absoluteChange = absoluteChange,
            relativeChange = relativeChange,
            percentChange = relativeChange?.times(100.0),
            spanDays = spanDays,
            changePerDay = changePerDay,
            cagr = if (geometricAvailable) cagr(start.value, end.value, spanDays) else null,
            geometricAvailable = geometricAvailable,
        )
    }

    /** Compares the first and the last point of [series]; `null` for fewer than two points. */
    fun analyze(series: TimeSeries): ChangeAnalysis? {
        val ordered = series.orderedPoints()
        if (ordered.size < 2) return null
        return analyze(ordered.first(), ordered.last())
    }

    /**
     * Rate of every consecutive pair, plus the change of rate from the previous segment.
     *
     * Segments with a zero-width time span get a `0.0` rate rather than an infinity.
     */
    fun segmentRates(series: TimeSeries): List<SegmentRate> {
        val ordered = series.orderedPoints()
        if (ordered.size < 2) return emptyList()
        val rates = ArrayList<SegmentRate>(ordered.size - 1)
        var previousRate: Double? = null
        for (i in 0 until ordered.size - 1) {
            val from = ordered[i]
            val to = ordered[i + 1]
            val spanDays = (to.timestampEpochMilli - from.timestampEpochMilli) / MILLIS_PER_DAY
            val rate = if (spanDays > 0.0) (to.value - from.value) / spanDays else 0.0
            rates += SegmentRate(
                fromTimestampEpochMilli = from.timestampEpochMilli,
                toTimestampEpochMilli = to.timestampEpochMilli,
                perDayRate = rate,
                perDayRateAcceleration = previousRate?.let { rate - it },
            )
            previousRate = rate
        }
        return rates
    }
}
