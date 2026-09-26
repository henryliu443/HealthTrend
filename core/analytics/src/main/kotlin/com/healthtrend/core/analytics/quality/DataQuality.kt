package com.healthtrend.core.analytics.quality

import com.healthtrend.core.analytics.internal.NumericSupport
import com.healthtrend.core.analytics.model.MILLIS_PER_DAY
import com.healthtrend.core.analytics.model.TimeSeries
import com.healthtrend.core.analytics.model.orderedPoints
import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId

/** Distribution of the gaps between consecutive samples. */
data class IntervalStatistics(
    val count: Int,
    val meanMillis: Double,
    val varianceMillis: Double,
    val standardDeviationMillis: Double,
    val medianMillis: Double,
    val minMillis: Long,
    val maxMillis: Long,
)

/**
 * Sampling-density report — AGENTS.md §6.10.
 *
 * Answering "can I trust an analysis of this series?" is a prerequisite for trusting any of the
 * other chapters: a 90-day window with four points cannot support a 7-day cycle no matter how
 * elegant the FFT is.
 */
data class DataQualityReport(
    val pointCount: Int,
    val spanMillis: Long,
    val spanDays: Double,
    /** `null` for fewer than two points. */
    val intervalStatistics: IntervalStatistics?,
    val longestGapMillis: Long,
    val longestGapStartEpochMilli: Long?,
    /** The interval the coverage was measured against; defaults to the median interval. */
    val expectedIntervalMillis: Double?,
    /** Observed points / points expected at [expectedIntervalMillis]; `null` when not computable. */
    val coverageRatio: Double?,
    /** How many points fall on each weekday, in the series' [ZoneId]. */
    val weekdayCounts: Map<DayOfWeek, Int>,
    val preferredWeekday: DayOfWeek?,
    /** Share of points landing on [preferredWeekday]; `1/7` means perfectly uniform. */
    val preferredWeekdayShare: Double?,
)

/**
 * Data-quality and sampling-density analysis — AGENTS.md §6.10.
 *
 * Computes gap statistics (mean/variance/median/min/max of the sampling interval), the longest
 * missing stretch, coverage against an expected cadence, and the "check-in weekday" bias — the
 * common pattern of only remembering to log on certain days of the week.
 */
object DataQuality {

    fun analyze(
        series: TimeSeries,
        zoneId: ZoneId,
        expectedIntervalMillis: Double? = null,
    ): DataQualityReport {
        require(expectedIntervalMillis == null || expectedIntervalMillis > 0.0) {
            "expectedIntervalMillis must be > 0, was $expectedIntervalMillis"
        }
        val points = series.orderedPoints()
        if (points.isEmpty()) {
            return DataQualityReport(
                pointCount = 0,
                spanMillis = 0L,
                spanDays = 0.0,
                intervalStatistics = null,
                longestGapMillis = 0L,
                longestGapStartEpochMilli = null,
                expectedIntervalMillis = expectedIntervalMillis,
                coverageRatio = null,
                weekdayCounts = emptyMap(),
                preferredWeekday = null,
                preferredWeekdayShare = null,
            )
        }

        val spanMillis = points.last().timestampEpochMilli - points.first().timestampEpochMilli
        val weekdayCounts = points
            .groupingBy { Instant.ofEpochMilli(it.timestampEpochMilli).atZone(zoneId).dayOfWeek }
            .eachCount()
            .toSortedMap()
        val preferredWeekday = weekdayCounts.maxByOrNull { it.value }?.key
        val preferredWeekdayShare = preferredWeekday?.let { weekdayCounts.getValue(it).toDouble() / points.size }

        if (points.size < 2) {
            return DataQualityReport(
                pointCount = points.size,
                spanMillis = spanMillis,
                spanDays = spanMillis / MILLIS_PER_DAY,
                intervalStatistics = null,
                longestGapMillis = 0L,
                longestGapStartEpochMilli = null,
                expectedIntervalMillis = expectedIntervalMillis,
                coverageRatio = null,
                weekdayCounts = weekdayCounts,
                preferredWeekday = preferredWeekday,
                preferredWeekdayShare = preferredWeekdayShare,
            )
        }

        val intervals = DoubleArray(points.size - 1) {
            (points[it + 1].timestampEpochMilli - points[it].timestampEpochMilli).toDouble()
        }
        val statistics = intervalStatistics(intervals)

        var longestGapMillis = intervals[0].toLong()
        var longestGapIndex = 0
        for (index in intervals.indices) {
            if (intervals[index] > longestGapMillis) {
                longestGapMillis = intervals[index].toLong()
                longestGapIndex = index
            }
        }

        val reference = expectedIntervalMillis ?: statistics.medianMillis
        val coverageRatio = if (reference > 0.0) {
            val expectedPoints = spanMillis / reference + 1.0
            points.size / expectedPoints
        } else {
            null
        }

        return DataQualityReport(
            pointCount = points.size,
            spanMillis = spanMillis,
            spanDays = spanMillis / MILLIS_PER_DAY,
            intervalStatistics = statistics,
            longestGapMillis = longestGapMillis,
            longestGapStartEpochMilli = points[longestGapIndex].timestampEpochMilli,
            expectedIntervalMillis = reference,
            coverageRatio = coverageRatio,
            weekdayCounts = weekdayCounts,
            preferredWeekday = preferredWeekday,
            preferredWeekdayShare = preferredWeekdayShare,
        )
    }

    private fun intervalStatistics(intervals: DoubleArray): IntervalStatistics {
        val mean = NumericSupport.mean(intervals)
        val variance = NumericSupport.sumSquaredDeviations(intervals, mean) / (intervals.size - 1).coerceAtLeast(1)
        val sorted = NumericSupport.sorted(intervals)
        return IntervalStatistics(
            count = intervals.size,
            meanMillis = mean,
            varianceMillis = variance,
            standardDeviationMillis = NumericSupport.safeSqrt(variance),
            medianMillis = NumericSupport.quantileSorted(sorted, 0.5),
            minMillis = sorted.first().toLong(),
            maxMillis = sorted.last().toLong(),
        )
    }
}
