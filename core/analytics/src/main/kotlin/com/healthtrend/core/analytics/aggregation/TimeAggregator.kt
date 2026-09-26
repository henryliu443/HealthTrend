package com.healthtrend.core.analytics.aggregation

import com.healthtrend.core.analytics.model.TimeSeries
import com.healthtrend.core.analytics.model.orderedPoints
import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

/** Calendar bucket sizes for [TimeAggregator]. */
enum class CalendarBucket {
    DAY,
    /** ISO week: Monday-based. */
    WEEK,
    MONTH,
    QUARTER,
    YEAR,
}

/** Reduction applied to the points that fall into one bucket. */
enum class AggregationFunction {
    SUM,

    /** Arithmetic mean — the sensible default when projecting raw observations onto a day grid. */
    MEAN,
    MIN,
    MAX,

    /** First point of the bucket, in time order. */
    FIRST,

    /** Last point of the bucket, in time order. */
    LAST,

    /** Number of points in the bucket, as a `Double`. */
    COUNT,
}

/** One aggregated bucket. [bucketStartEpochMilli] is the *local* start of the bucket in UTC millis. */
data class AggregatedPoint(
    val bucketStartEpochMilli: Long,
    val value: Double,
    val sampleCount: Int,
)

/**
 * Calendar aggregation — AGENTS.md §6.2.
 *
 * HARD RULE (§10.1): bucketing is done with `ZonedDateTime` against a real [ZoneId], never by
 * dividing an epoch timestamp by 86_400_000. A DST-transition day is 23h or 25h long and still
 * lands in exactly one bucket; a fixed-width division would misplace points near the boundary.
 */
object TimeAggregator {

    /**
     * Start of the calendar bucket containing [timestampEpochMilli], as UTC epoch millis.
     *
     * Weeks start on Monday (ISO-8601). Quarters start in January, April, July and October.
     */
    fun bucketStart(timestampEpochMilli: Long, bucket: CalendarBucket, zoneId: ZoneId): Long {
        val zoned = Instant.ofEpochMilli(timestampEpochMilli)
            .atZone(zoneId)
            .truncatedTo(ChronoUnit.DAYS)
        val start = when (bucket) {
            CalendarBucket.DAY -> zoned
            CalendarBucket.WEEK -> zoned.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            CalendarBucket.MONTH -> zoned.withDayOfMonth(1)
            CalendarBucket.QUARTER -> zoned
                .withDayOfMonth(1)
                .withMonth(((zoned.monthValue - 1) / 3) * 3 + 1)
            CalendarBucket.YEAR -> zoned.withDayOfYear(1)
        }
        return start.toInstant().toEpochMilli()
    }

    /**
     * Groups [series] into calendar buckets and reduces each with [function].
     *
     * Buckets are emitted in ascending time order and empty buckets are skipped (the result is
     * sparse — gap-filling belongs to the periodicity pipeline, not here).
     */
    fun aggregate(
        series: TimeSeries,
        bucket: CalendarBucket,
        zoneId: ZoneId,
        function: AggregationFunction,
    ): List<AggregatedPoint> {
        val ordered = series.orderedPoints()
        if (ordered.isEmpty()) return emptyList()
        val grouped = LinkedHashMap<Long, MutableList<Double>>()
        for (point in ordered) {
            val start = bucketStart(point.timestampEpochMilli, bucket, zoneId)
            grouped.getOrPut(start) { ArrayList() }.add(point.value)
        }
        return grouped.map { (start, values) ->
            AggregatedPoint(start, reduce(values, function), values.size)
        }
    }

    private fun reduce(values: List<Double>, function: AggregationFunction): Double = when (function) {
        AggregationFunction.SUM -> values.sum()
        AggregationFunction.MEAN -> values.average()
        AggregationFunction.MIN -> values.min()
        AggregationFunction.MAX -> values.max()
        AggregationFunction.FIRST -> values.first()
        AggregationFunction.LAST -> values.last()
        AggregationFunction.COUNT -> values.size.toDouble()
    }
}
