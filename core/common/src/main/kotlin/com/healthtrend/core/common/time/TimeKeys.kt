package com.healthtrend.core.common.time

import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * Half-open natural-calendar-day bounds: `[startMillis, endMillisExclusive)`.
 */
data class DayRange(val startMillis: Long, val endMillisExclusive: Long)

/**
 * Calendar/time utilities.
 *
 * RULE (AGENTS.md §10.1): the only persisted time representation is UTC epoch milliseconds.
 * RULE (AGENTS.md §6.2): natural-day bucketing must be done with a [ZoneId], never by
 * dividing a timestamp by 86_400_000.
 */
object TimeKeys {

    const val MILLIS_PER_DAY: Long = 86_400_000L

    /**
     * Start of the natural calendar day containing [timestampEpochMilli], expressed as UTC
     * epoch milliseconds. Uses `truncatedTo(DAYS)` as mandated by AGENTS.md §6.2, so that DST
     * transitions are handled by the zone rules rather than by fixed-width arithmetic.
     */
    fun startOfDayUtcMillis(timestampEpochMilli: Long, zoneId: ZoneId): Long =
        Instant.ofEpochMilli(timestampEpochMilli)
            .atZone(zoneId)
            .truncatedTo(ChronoUnit.DAYS)
            .toInstant()
            .toEpochMilli()

    /**
     * Half-open bounds of the natural calendar day containing [timestampEpochMilli].
     * The following day's start is recomputed from the zone, so DST days of 23h/25h are correct.
     */
    fun dayRangeUtcMillis(timestampEpochMilli: Long, zoneId: ZoneId): DayRange {
        val zoned = Instant.ofEpochMilli(timestampEpochMilli).atZone(zoneId)
        val start = zoned.truncatedTo(ChronoUnit.DAYS).toInstant().toEpochMilli()
        val endExclusive = zoned.truncatedTo(ChronoUnit.DAYS)
            .plusDays(1)
            .toInstant()
            .toEpochMilli()
        return DayRange(start, endExclusive)
    }
}
