package com.healthtrend.ui.common

import androidx.annotation.StringRes
import com.healthtrend.R
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit

/**
 * A user-selectable look-back window.
 *
 * The window is expressed in **natural calendar days** and converted through [ZoneId] exactly as
 * AGENTS.md §6.2 requires — never by subtracting a fixed number of milliseconds, because a DST
 * transition makes one of those days 23 or 25 hours long.
 */
enum class TimeRange(@StringRes val labelRes: Int, val days: Int?) {
    LAST_7_DAYS(R.string.range_7d, 7),
    LAST_30_DAYS(R.string.range_30d, 30),
    LAST_90_DAYS(R.string.range_90d, 90),
    LAST_YEAR(R.string.range_1y, 365),
    ALL(R.string.range_all, null),
}

/**
 * Inclusive start of [range]'s window: the **start of the day** `days - 1` days before [now], so
 * "7 days" contains today plus the six preceding calendar days.
 *
 * [TimeRange.ALL] returns [Long.MIN_VALUE] so that the caller's half-open query can never clip an
 * observation, whatever epoch the data happens to use.
 */
fun TimeRange.startEpochMilli(nowEpochMilli: Long, zoneId: ZoneId): Long {
    val days = days ?: return Long.MIN_VALUE
    return ZonedDateTime.ofInstant(Instant.ofEpochMilli(nowEpochMilli), zoneId)
        .truncatedTo(ChronoUnit.DAYS)
        .minusDays((days - 1).toLong())
        .toInstant()
        .toEpochMilli()
}

/** Exclusive end of [range]'s window: the start of tomorrow, i.e. everything up to *now* is included. */
fun TimeRange.endEpochMilliExclusive(nowEpochMilli: Long, zoneId: ZoneId): Long =
    ZonedDateTime.ofInstant(Instant.ofEpochMilli(nowEpochMilli), zoneId)
        .truncatedTo(ChronoUnit.DAYS)
        .plusDays(1)
        .toInstant()
        .toEpochMilli()
