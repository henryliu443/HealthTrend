package com.healthtrend.core.common.time

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

class TimeKeysTest {

    private val shanghai = ZoneId.of("Asia/Shanghai")
    private val newYork = ZoneId.of("America/New_York")

    @Test
    fun `start of day is midnight local time`() {
        val noon = ZonedDateTime.of(2026, 9, 27, 12, 34, 56, 0, shanghai).toInstant().toEpochMilli()
        val start = TimeKeys.startOfDayUtcMillis(noon, shanghai)
        val expected = ZonedDateTime.of(2026, 9, 27, 0, 0, 0, 0, shanghai).toInstant().toEpochMilli()
        start shouldBe expected
    }

    @Test
    fun `day range is half open and covers exactly one natural day`() {
        val at = Instant.parse("2026-09-27T16:00:00Z")
        val range = TimeKeys.dayRangeUtcMillis(at.toEpochMilli(), shanghai)
        // 2026-09-28 00:00 in Shanghai == 2026-09-27 16:00 UTC -> belongs to Sep 28 local.
        range.startMillis shouldBe Instant.parse("2026-09-27T16:00:00Z").toEpochMilli()
        range.endMillisExclusive shouldBe Instant.parse("2026-09-28T16:00:00Z").toEpochMilli()
    }

    @Test
    fun `DST spring forward day is 23 hours long, not 86400000 ms`() {
        // 2026-03-08 in New York: clocks jump 02:00 -> 03:00.
        val at = Instant.parse("2026-03-08T12:00:00Z")
        val range = TimeKeys.dayRangeUtcMillis(at.toEpochMilli(), newYork)
        val length = range.endMillisExclusive - range.startMillis
        length shouldBe 23L * 60 * 60 * 1000
        length shouldBe TimeKeys.MILLIS_PER_DAY - 3_600_000L
    }

    @Test
    fun `DST fall back day is 25 hours long`() {
        // 2026-11-01 in New York: clocks fall back 02:00 -> 01:00.
        val at = Instant.parse("2026-11-01T12:00:00Z")
        val range = TimeKeys.dayRangeUtcMillis(at.toEpochMilli(), newYork)
        (range.endMillisExclusive - range.startMillis) shouldBe 25L * 60 * 60 * 1000
    }
}
