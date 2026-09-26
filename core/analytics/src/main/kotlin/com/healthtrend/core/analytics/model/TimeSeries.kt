package com.healthtrend.core.analytics.model

/**
 * The analytics engine's only numeric input carrier (AGENTS.md §3.3).
 *
 * [timestampEpochMilli] is a UTC epoch millisecond (AGENTS.md §10.1). The engine never inspects
 * calendar fields itself; a `ZoneId` is passed in explicitly whenever calendar semantics matter.
 */
data class RawDataPoint(
    val timestampEpochMilli: Long,
    val value: Double,
)

/**
 * A time-ordered list of [RawDataPoint]s for one logical series.
 *
 * The engine is strictly domain-agnostic (AGENTS.md §3.3): a series may be a weight in kg, a lab
 * value in U/L or an aggregated nutrient in g — the mathematics does not care.
 */
data class TimeSeries(
    val seriesId: String,
    val points: List<RawDataPoint>,
    val unit: String,
) {
    val size: Int get() = points.size
    val isEmpty: Boolean get() = points.isEmpty()
    val isNotEmpty: Boolean get() = points.isNotEmpty()

    /** Values in the series' current order. */
    fun values(): DoubleArray = DoubleArray(points.size) { points[it].value }

    /** Timestamps in the series' current order. */
    fun timestamps(): LongArray = LongArray(points.size) { points[it].timestampEpochMilli }

    companion object {
        /** Builds a series sorted ascending by time. */
        fun of(seriesId: String, unit: String, points: List<RawDataPoint>): TimeSeries =
            TimeSeries(seriesId, points.sortedBy { it.timestampEpochMilli }, unit)

        /** Convenience builder from `(timestampEpochMilli, value)` pairs. */
        fun fromPairs(seriesId: String, unit: String, pairs: List<Pair<Long, Double>>): TimeSeries =
            of(seriesId, unit, pairs.map { RawDataPoint(it.first, it.second) })

        fun empty(seriesId: String, unit: String): TimeSeries = TimeSeries(seriesId, emptyList(), unit)
    }
}

/**
 * Returns the points in ascending time order.
 *
 * Ascending time is a documented precondition of the engine, but callers are not trusted blindly:
 * an O(n) check avoids the sort in the common already-ordered case (the sort is stable, so points
 * sharing a timestamp keep their input order).
 */
internal fun TimeSeries.orderedPoints(): List<RawDataPoint> {
    if (points.size < 2) return points
    for (i in 1 until points.size) {
        if (points[i].timestampEpochMilli < points[i - 1].timestampEpochMilli) {
            return points.sortedBy { it.timestampEpochMilli }
        }
    }
    return points
}

/** Wall-clock span between the first and the last point; `0` for fewer than two points. */
internal fun TimeSeries.spanMillis(): Long =
    if (points.size < 2) {
        0L
    } else {
        points.maxOf { it.timestampEpochMilli } - points.minOf { it.timestampEpochMilli }
    }

/** Milliseconds in a nominal (non-DST) day — for *scaling* only, never for calendar bucketing. */
internal const val MILLIS_PER_DAY: Double = 86_400_000.0
