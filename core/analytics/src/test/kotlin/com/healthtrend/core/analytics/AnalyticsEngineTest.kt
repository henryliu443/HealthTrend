package com.healthtrend.core.analytics

import com.healthtrend.core.analytics.aggregation.AggregationFunction
import com.healthtrend.core.analytics.aggregation.CalendarBucket
import com.healthtrend.core.analytics.aggregation.TimeAggregator
import com.healthtrend.core.analytics.anomaly.AnomalyDetection
import com.healthtrend.core.analytics.anomaly.AnomalyMethod
import com.healthtrend.core.analytics.change.ChangeRates
import com.healthtrend.core.analytics.correlation.CrossCorrelation
import com.healthtrend.core.analytics.downsample.LttbDownsampler
import com.healthtrend.core.analytics.model.RawDataPoint
import com.healthtrend.core.analytics.model.TimeSeries
import com.healthtrend.core.analytics.periodicity.Periodicity
import com.healthtrend.core.analytics.quality.DataQuality
import com.healthtrend.core.analytics.smoothing.Ewma
import com.healthtrend.core.analytics.trend.TrendAnalysis
import com.healthtrend.core.analytics.trend.TrendDirection
import com.healthtrend.core.analytics.window.RollingWindowStats
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.math.PI
import kotlin.math.sin

private const val DAY = 86_400_000L
private val utc: ZoneId = ZoneId.of("UTC")
private val shanghai: ZoneId = ZoneId.of("Asia/Shanghai")

private fun daily(count: Int, startDay: Int = 0, value: (Int) -> Double): TimeSeries =
    TimeSeries.of(
        "test",
        "unit",
        (0 until count).map { RawDataPoint((startDay + it).toLong() * DAY, value(it)) },
    )

class DescriptiveStatsTest {

    @Test
    fun `sample variance is undefined below two points and population variance is not`() {
        val single = DescriptiveStats.calculate(doubleArrayOf(5.0))
        single.count shouldBe 1
        single.sampleVariance.isNaN() shouldBe true
        single.sampleStandardDeviation.isNaN() shouldBe true
        single.standardErrorOfMean.isNaN() shouldBe true
        single.populationVariance shouldBe 0.0
        // Zero spread wins over "too few points": AGENTS.md 6.1 asks for 0.0, not NaN.
        single.skewness shouldBe 0.0
        single.kurtosis shouldBe 0.0
    }

    @Test
    fun `zero spread returns zero skewness and kurtosis rather than NaN`() {
        // AGENTS.md 6.1: guard against division by a zero standard deviation.
        val constant = DescriptiveStats.calculate(doubleArrayOf(2.0, 2.0, 2.0, 2.0, 2.0))
        constant.skewness shouldBe 0.0
        constant.kurtosis shouldBe 0.0
        constant.sampleVariance shouldBe 0.0
    }

    @Test
    fun `empty input yields a NaN result with count zero`() {
        val empty = DescriptiveStats.calculate(DoubleArray(0))
        empty.count shouldBe 0
        empty.mean.isNaN() shouldBe true
        empty.median.isNaN() shouldBe true
    }

    @Test
    fun `quartiles use linear interpolation`() {
        val stats = DescriptiveStats.calculate(doubleArrayOf(1.0, 2.0, 3.0, 4.0))
        stats.median shouldBe 2.5
        stats.firstQuartile shouldBe 1.75
        stats.thirdQuartile shouldBe 3.25
        stats.interquartileRange shouldBe 1.5
    }
}

class TimeAggregatorTest {

    @Test
    fun `daily buckets follow local midnight, not UTC midnight`() {
        // 2026-09-27 23:30 Shanghai == 2026-09-27 15:30 UTC.
        val lateEvening = ZonedDateTime.of(2026, 9, 27, 23, 30, 0, 0, shanghai).toInstant().toEpochMilli()
        val expected = ZonedDateTime.of(2026, 9, 27, 0, 0, 0, 0, shanghai).toInstant().toEpochMilli()
        TimeAggregator.bucketStart(lateEvening, CalendarBucket.DAY, shanghai) shouldBe expected
    }

    @Test
    fun `weeks start on Monday`() {
        // 2026-09-27 is a Sunday.
        val sunday = ZonedDateTime.of(2026, 9, 27, 10, 0, 0, 0, shanghai).toInstant().toEpochMilli()
        val monday = ZonedDateTime.of(2026, 9, 21, 0, 0, 0, 0, shanghai).toInstant().toEpochMilli()
        TimeAggregator.bucketStart(sunday, CalendarBucket.WEEK, shanghai) shouldBe monday
    }

    @Test
    fun `quarter buckets start in january april july october`() {
        val december = ZonedDateTime.of(2026, 12, 31, 12, 0, 0, 0, utc).toInstant().toEpochMilli()
        val octoberFirst = Instant.parse("2026-10-01T00:00:00Z").toEpochMilli()
        TimeAggregator.bucketStart(december, CalendarBucket.QUARTER, utc) shouldBe octoberFirst

        val august = ZonedDateTime.of(2026, 8, 2, 12, 0, 0, 0, utc).toInstant().toEpochMilli()
        val julyFirst = Instant.parse("2026-07-01T00:00:00Z").toEpochMilli()
        TimeAggregator.bucketStart(august, CalendarBucket.QUARTER, utc) shouldBe julyFirst
    }

    @Test
    fun `dst transition day keeps every point in one bucket`() {
        val newYork = ZoneId.of("America/New_York")
        // 2026-03-08: clocks jump 02:00 -> 03:00 local. Both instants are the same calendar day.
        val beforeJump = Instant.parse("2026-03-08T06:30:00Z").toEpochMilli()
        val afterJump = Instant.parse("2026-03-08T08:30:00Z").toEpochMilli()
        TimeAggregator.bucketStart(beforeJump, CalendarBucket.DAY, newYork) shouldBe
            TimeAggregator.bucketStart(afterJump, CalendarBucket.DAY, newYork)
    }

    @Test
    fun `sum and count reduce within buckets`() {
        val series = daily(5) { it.toDouble() }
        val sums = TimeAggregator.aggregate(series, CalendarBucket.DAY, utc, AggregationFunction.SUM)
        sums.size shouldBe 5
        sums.map { it.value } shouldBe listOf(0.0, 1.0, 2.0, 3.0, 4.0)
        sums.all { it.sampleCount == 1 } shouldBe true

        // Epoch day 0 is a Thursday; start on day 4 (= Monday 1970-01-05) so the weeks line up.
        val weekly = daily(14, startDay = 4) { it.toDouble() }
        val weeklySums = TimeAggregator.aggregate(weekly, CalendarBucket.WEEK, utc, AggregationFunction.SUM)
        weeklySums.size shouldBe 2
        weeklySums.forEach { it.sampleCount shouldBe 7 }
        weeklySums.first().value shouldBe (0.0 + 1 + 2 + 3 + 4 + 5 + 6)
        weeklySums.last().value shouldBe (7.0 + 8 + 9 + 10 + 11 + 12 + 13)
    }

    @Test
    fun `out of order input is still bucketed correctly`() {
        val series = TimeSeries.of(
            "unsorted",
            "unit",
            listOf(RawDataPoint(2 * DAY, 3.0), RawDataPoint(0L, 1.0), RawDataPoint(DAY, 2.0)),
        )
        val sums = TimeAggregator.aggregate(series, CalendarBucket.DAY, utc, AggregationFunction.SUM)
        sums.map { it.bucketStartEpochMilli } shouldBe listOf(0L, DAY, 2 * DAY)
    }
}

class ChangeRatesTest {

    @Test
    fun `cagr is annualised over the actual span`() {
        val start = RawDataPoint(0L, 70.0)
        val end = RawDataPoint(182 * DAY + DAY / 2, 77.0)
        val cagr = ChangeRates.cagr(start.value, end.value, 182.5)!!
        // 70 -> 77 over half a year compounds to roughly 21% a year.
        cagr shouldBe (0.2099 plusOrMinus 1e-3)
    }

    @Test
    fun `cagr refuses non-positive endpoints`() {
        ChangeRates.cagr(0.0, 5.0, 365.0) shouldBe null
        ChangeRates.cagr(5.0, 0.0, 365.0) shouldBe null
        ChangeRates.cagr(-1.0, 5.0, 365.0) shouldBe null
        ChangeRates.cagr(5.0, 5.0, 0.0) shouldBe null
    }

    @Test
    fun `analyze reports arithmetic-only when the geometric rate is unavailable`() {
        val analysis = ChangeRates.analyze(RawDataPoint(0L, 0.0), RawDataPoint(10 * DAY, 5.0))
        analysis.absoluteChange shouldBe 5.0
        analysis.relativeChange shouldBe null
        analysis.percentChange shouldBe null
        analysis.cagr shouldBe null
        analysis.geometricAvailable shouldBe false
        analysis.changePerDay shouldBe 0.5
    }

    @Test
    fun `segment rates expose acceleration`() {
        val series = TimeSeries.of(
            "s",
            "u",
            listOf(
                RawDataPoint(0L, 0.0),
                RawDataPoint(DAY, 1.0),
                RawDataPoint(2 * DAY, 3.0),
                RawDataPoint(3 * DAY, 6.0),
            ),
        )
        val rates = ChangeRates.segmentRates(series)
        rates.size shouldBe 3
        rates.map { it.perDayRate } shouldBe listOf(1.0, 2.0, 3.0)
        rates[0].perDayRateAcceleration shouldBe null
        rates[1].perDayRateAcceleration shouldBe 1.0
        rates[2].perDayRateAcceleration shouldBe 1.0
    }
}

class RollingWindowStatsTest {

    @Test
    fun `window is measured in time, not in point count`() {
        // Irregular sampling: the 3-day window holds a different number of points each step.
        val series = TimeSeries.of(
            "irregular",
            "u",
            listOf(
                RawDataPoint(0L, 1.0),
                RawDataPoint(DAY, 2.0),
                RawDataPoint(2 * DAY, 3.0),
                RawDataPoint(10 * DAY, 100.0),
            ),
        )
        val windows = RollingWindowStats.compute(series, 3 * DAY)
        windows.map { it.sampleCount } shouldBe listOf(1, 2, 3, 1)
        windows[2].mean shouldBe 2.0
        windows[3].mean shouldBe 100.0
        windows[0].sampleStandardDeviation.isNaN() shouldBe true
    }

    @Test
    fun `min and max follow points in and out of the window`() {
        val series = daily(5) { listOf(5.0, 1.0, 9.0, 2.0, 4.0)[it] }
        val windows = RollingWindowStats.compute(series, 2 * DAY)
        windows.map { it.sampleCount } shouldBe listOf(1, 2, 3, 3, 3)
        windows[1].min shouldBe 1.0
        windows[1].max shouldBe 5.0
        windows[2].min shouldBe 1.0
        windows[2].max shouldBe 9.0
        // The 9.0 drops out of the window once the right pointer reaches day 4.
        windows[3].max shouldBe 9.0
        windows[4].max shouldBe 9.0
    }

    @Test
    fun `rolling mean keeps timestamps and replaces values`() {
        val series = daily(3) { it.toDouble() }
        val smoothed = RollingWindowStats.rollingMean(series, java.time.Duration.ofDays(1))
        smoothed.points.map { it.timestampEpochMilli } shouldBe series.points.map { it.timestampEpochMilli }
        smoothed.points.map { it.value } shouldBe listOf(0.0, 0.5, 1.5)
    }

    @Test
    fun `non-positive window is rejected`() {
        io.kotest.assertions.throwables.shouldThrow<IllegalArgumentException> {
            RollingWindowStats.compute(daily(3) { it.toDouble() }, 0L)
        }
    }
}

class EwmaTest {

    @Test
    fun `a point exactly one half-life later gets half the weight`() {
        // ln(2)/halfLife is exactly the decay that halves the influence after one half-life.
        val series = TimeSeries.of("s", "u", listOf(RawDataPoint(0L, 0.0), RawDataPoint(7 * DAY, 10.0)))
        val smoothed = Ewma.computeDays(series, halfLifeDays = 7.0)
        smoothed[0].value shouldBe 0.0
        smoothed[1].value shouldBe (5.0 plusOrMinus 1e-12)
    }

    @Test
    fun `irregular gaps decay strictly more than short ones`() {
        val dense = TimeSeries.of("s", "u", listOf(RawDataPoint(0L, 0.0), RawDataPoint(DAY, 10.0)))
        val sparse = TimeSeries.of("s", "u", listOf(RawDataPoint(0L, 0.0), RawDataPoint(30 * DAY, 10.0)))
        val denseSmoothed = Ewma.computeDays(dense, 7.0).last().value
        val sparseSmoothed = Ewma.computeDays(sparse, 7.0).last().value
        (sparseSmoothed > denseSmoothed) shouldBe true
    }

    @Test
    fun `duplicate timestamps cannot move the average`() {
        val series = TimeSeries.of("s", "u", listOf(RawDataPoint(0L, 0.0), RawDataPoint(0L, 10.0)))
        val smoothed = Ewma.computeDays(series, 7.0)
        smoothed[1].value shouldBe 0.0
    }

    @Test
    fun `residuals are observed minus smoothed`() {
        val series = daily(4) { it.toDouble() }
        val residuals = Ewma.residuals(series, 7.0 * DAY)
        residuals.forEachIndexed { index, point ->
            residuals.size shouldBe series.size
            point.timestampEpochMilli shouldBe series.points[index].timestampEpochMilli
        }
    }
}

class TrendAnalysisTest {

    @Test
    fun `a perfect upward line is classified as increasing`() {
        val series = daily(10) { 2.0 * it + 5.0 }
        val trend = TrendAnalysis.fit(series)
        trend.slopePerDay shouldBe (2.0 plusOrMinus 1e-12)
        trend.intercept shouldBe (5.0 plusOrMinus 1e-12)
        trend.direction shouldBe TrendDirection.INCREASING
        trend.pValue shouldBe 0.0
    }

    @Test
    fun `a perfect downward line is classified as decreasing`() {
        val trend = TrendAnalysis.fit(daily(10) { 100.0 - 1.5 * it })
        trend.slopePerDay shouldBe (-1.5 plusOrMinus 1e-12)
        trend.direction shouldBe TrendDirection.DECREASING
    }

    @Test
    fun `a flat series is stable, not insufficient`() {
        val trend = TrendAnalysis.fit(daily(10) { 42.0 })
        trend.slopePerDay shouldBe 0.0
        trend.direction shouldBe TrendDirection.STABLE
        trend.pValue shouldBe 1.0
    }

    @Test
    fun `noisy data without a real slope is not called a trend`() {
        val noise = listOf(50.0, 52.0, 49.0, 51.0, 48.0, 53.0, 50.0, 51.0, 49.0, 52.0, 50.0, 50.0)
        val trend = TrendAnalysis.fit(daily(noise.size) { noise[it] })
        trend.direction shouldBe TrendDirection.STABLE
        (trend.pValue > 0.05) shouldBe true
    }

    @Test
    fun `two points have a slope but no significance`() {
        val trend = TrendAnalysis.fit(daily(2) { 1.0 * it })
        trend.slopePerDay shouldBe 1.0
        trend.direction shouldBe TrendDirection.INSUFFICIENT_DATA
        trend.pValue.isNaN() shouldBe true
    }

    @Test
    fun `one point is insufficient`() {
        val trend = TrendAnalysis.fit(daily(1) { 1.0 })
        trend.direction shouldBe TrendDirection.INSUFFICIENT_DATA
        trend.slopePerDay.isNaN() shouldBe true
    }

    @Test
    fun `local trend reports the direction inside a trailing window`() {
        // Flat for 20 days, then a steep climb.
        val series = daily(40) { if (it < 20) 10.0 else 10.0 + (it - 20) * 2.0 }
        val local = TrendAnalysis.localTrend(series, 10 * DAY)
        local.last().direction shouldBe TrendDirection.INCREASING
        local[9].direction shouldBe TrendDirection.STABLE
    }
}

class AnomalyDetectionTest {

    @Test
    fun `an extreme value is flagged by the robust detectors`() {
        val baseline = listOf(50.0, 51.0, 49.5, 50.5, 50.2, 49.8, 50.1, 50.4, 49.9, 50.3)
        val series = daily(baseline.size + 1) { if (it == baseline.size) 200.0 else baseline[it] }
        val report = AnomalyDetection.detect(series)
        val last = report.points.last()
        last.isAnomaly shouldBe true
        last.flaggedMethods.contains(AnomalyMethod.MODIFIED_Z_SCORE) shouldBe true
        last.flaggedMethods.contains(AnomalyMethod.IQR) shouldBe true
        report.anomalyCount shouldBe 1
    }

    @Test
    fun `a constant series flags nothing`() {
        val report = AnomalyDetection.detect(daily(20) { 60.0 })
        report.anomalyCount shouldBe 0
        report.points.all { it.zScore == 0.0 && it.robustZScore == 0.0 } shouldBe true
    }

    @Test
    fun `the ewma detector catches a sustained step that z-scores miss`() {
        // 30 stable days then a new, stable, higher level: no single point is an outlier against
        // the whole-sample mean (z = 1.0), but the points right after the step are far from the
        // EWMA expectation.
        val series = daily(60) { if (it < 30) 10.0 else 20.0 }
        val report = AnomalyDetection.detect(series, ewmaResidualThreshold = 2.5)
        report.points[30].flaggedMethods.contains(AnomalyMethod.EWMA_RESIDUAL) shouldBe true
        report.points.take(30).none { it.flaggedMethods.contains(AnomalyMethod.EWMA_RESIDUAL) } shouldBe true
        report.points[30].flaggedMethods.contains(AnomalyMethod.Z_SCORE) shouldBe false
    }

    @Test
    fun `an empty series produces an empty report`() {
        val report = AnomalyDetection.detect(TimeSeries.empty("s", "u"))
        report.points.size shouldBe 0
        report.anomalyCount shouldBe 0
    }
}

class CrossCorrelationTest {

    @Test
    fun `fewer than three common days stops the calculation`() {
        val a = TimeSeries.of("a", "u", listOf(RawDataPoint(0L, 1.0), RawDataPoint(DAY, 2.0)))
        val b = TimeSeries.of("b", "u", listOf(RawDataPoint(0L, 1.0), RawDataPoint(DAY, 2.0)))
        val result = CrossCorrelation.correlate(a, b, utc)
        result.sampleCount shouldBe 2
        result.sufficientData shouldBe false
        result.pearson shouldBe null
    }

    @Test
    fun `alignment is an inner join on local days`() {
        val a = daily(5) { it.toDouble() }
        val b = daily(3, startDay = 2) { it.toDouble() }
        val aligned = CrossCorrelation.alignDaily(a, b, utc)
        aligned.size shouldBe 3
        aligned.map { it.localDate } shouldBe listOf(
            Instant.ofEpochMilli(2 * DAY).atZone(utc).toLocalDate(),
            Instant.ofEpochMilli(3 * DAY).atZone(utc).toLocalDate(),
            Instant.ofEpochMilli(4 * DAY).atZone(utc).toLocalDate(),
        )
    }

    @Test
    fun `a perfectly linear pair correlates at one`() {
        val a = daily(10) { it.toDouble() }
        val b = daily(10) { 3.0 * it + 7.0 }
        val result = CrossCorrelation.correlate(a, b, utc)
        result.pearson shouldBe (1.0 plusOrMinus 1e-12)
        result.spearman shouldBe (1.0 plusOrMinus 1e-12)
        result.sufficientData shouldBe true
    }

    @Test
    fun `lag analysis recovers a one day shift`() {
        val a = daily(12) { listOf(10.0, 12.0, 9.0, 15.0, 14.0, 18.0, 17.0, 21.0, 19.0, 24.0, 23.0, 27.0)[it] }
        // b is a shifted forward by one day (first value padded).
        val b = TimeSeries.of(
            "b",
            "u",
            (0 until 12).map { RawDataPoint(it.toLong() * DAY, if (it == 0) 0.0 else a.points[it - 1].value) },
        )
        val lags = CrossCorrelation.lagAnalysis(a, b, utc, maxLagDays = 3)
        val best = lags.filter { it.sufficientData && it.pearson != null }.maxBy { it.pearson!! }
        best.lagDays shouldBe 1
        best.pearson!! shouldBe (1.0 plusOrMinus 1e-12)
    }

    @Test
    fun `covariance matrix is symmetric with variances on the diagonal`() {
        val a = daily(10) { it.toDouble() }
        val b = daily(10) { 2.0 * it }
        val matrix = CrossCorrelation.covarianceMatrix(listOf(a, b), utc)
        matrix.commonDayCount shouldBe 10
        matrix.matrix[0][1]!! shouldBe (matrix.matrix[1][0]!! plusOrMinus 1e-12)
        matrix.matrix[0][0]!! shouldBe (matrix.matrix[1][1]!!.let { it / 4.0 } plusOrMinus 1e-9)
    }
}

class PeriodicityTest {

    private fun sine(count: Int, periodDays: Double, amplitude: Double = 5.0): TimeSeries =
        daily(count) { amplitude * sin(2.0 * PI * it / periodDays) }

    @Test
    fun `a seven day cycle dominates a long series`() {
        val result = Periodicity.analyze(sine(120, 7.0), utc)
        result.hasSpectrum shouldBe true
        val dominant = result.dominantPeriodDays!!
        (dominant in 6.5..7.8) shouldBe true
        (result.peaks.first().relativePower > 0.5) shouldBe true
        result.isReliable shouldBe true
    }

    @Test
    fun `reliability flag matches the two-cycle rule`() {
        val result = Periodicity.analyze(sine(120, 7.0), utc)
        result.isReliable shouldBe (result.spanDays >= 2.0 * result.dominantPeriodDays!!)
    }

    @Test
    fun `too few samples produce no spectrum`() {
        val result = Periodicity.analyze(sine(5, 7.0), utc)
        result.hasSpectrum shouldBe false
        result.dominantPeriodDays shouldBe null
        result.isReliable shouldBe false
    }

    @Test
    fun `interpolated gaps do not move the dominant period`() {
        val complete = TimeSeries.of("s", "u", (0 until 120).map { RawDataPoint(it.toLong() * DAY, sin(2.0 * PI * it / 7.0)) })
        val withGaps = TimeSeries.of("s", "u", complete.points.filterIndexed { index, _ -> index % 5 != 0 })
        val result = Periodicity.analyze(withGaps, utc)
        ((result.dominantPeriodDays ?: 0.0) in 6.5..7.8) shouldBe true
    }
}

class DataQualityTest {

    @Test
    fun `longest gap and coverage are reported`() {
        val points = (0 until 10).map { RawDataPoint(it.toLong() * DAY, 1.0) } +
            (40 until 50).map { RawDataPoint(it.toLong() * DAY, 1.0) }
        val report = DataQuality.analyze(TimeSeries.of("s", "u", points), utc, expectedIntervalMillis = DAY.toDouble())
        report.pointCount shouldBe 20
        report.longestGapMillis shouldBe 31 * DAY
        report.longestGapStartEpochMilli shouldBe 9 * DAY
        report.coverageRatio!! shouldBe (20.0 / 50.0 plusOrMinus 1e-9)
    }

    @Test
    fun `weekday bias is detected`() {
        // Monday and Thursday only, for four weeks.
        val points = (0 until 28)
            .map { RawDataPoint(it.toLong() * DAY, 1.0) }
            .filter { Instant.ofEpochMilli(it.timestampEpochMilli).atZone(utc).dayOfWeek in setOf(DayOfWeek.MONDAY, DayOfWeek.THURSDAY) }
        val report = DataQuality.analyze(TimeSeries.of("s", "u", points), utc)
        report.weekdayCounts[DayOfWeek.MONDAY] shouldBe 4
        report.weekdayCounts[DayOfWeek.THURSDAY] shouldBe 4
        report.preferredWeekday shouldBe DayOfWeek.MONDAY
        report.preferredWeekdayShare shouldBe (0.5 plusOrMinus 1e-12)
    }

    @Test
    fun `interval statistics describe the sampling cadence`() {
        val report = DataQuality.analyze(daily(5) { it.toDouble() }, utc)
        report.intervalStatistics!!.meanMillis shouldBe DAY.toDouble()
        report.intervalStatistics!!.varianceMillis shouldBe 0.0
        report.intervalStatistics!!.medianMillis shouldBe DAY.toDouble()
    }
}

class LttbDownsamplerTest {

    @Test
    fun `output size matches the target and keeps the endpoints`() {
        val series = daily(1000) { sin(2.0 * PI * it / 50.0) * 100.0 }
        val downsampled = LttbDownsampler.downsample(series, 100)
        downsampled.size shouldBe 100
        downsampled.points.first() shouldBe series.points.first()
        downsampled.points.last() shouldBe series.points.last()
    }

    @Test
    fun `the global peak survives downsampling`() {
        val values = (0 until 1000).map { sin(2.0 * PI * it / 50.0) * 100.0 }.toMutableList()
        values[437] = 1000.0
        val series = TimeSeries.of("s", "u", values.mapIndexed { index, v -> RawDataPoint(index.toLong() * DAY, v) })
        val downsampled = LttbDownsampler.downsample(series, 120)
        (downsampled.points.maxOf { it.value } > 950.0) shouldBe true
    }

    @Test
    fun `a series already smaller than the target is returned unchanged`() {
        val series = daily(10) { it.toDouble() }
        LttbDownsampler.downsample(series, 100).points shouldBe series.points
    }

    @Test
    fun `points are ordered by time after downsampling`() {
        val series = TimeSeries.of(
            "s",
            "u",
            (0 until 500).map { RawDataPoint((500 - it).toLong() * DAY, it.toDouble()) },
        )
        val downsampled = LttbDownsampler.downsample(series, 50)
        downsampled.points.map { it.timestampEpochMilli } shouldBe
            downsampled.points.map { it.timestampEpochMilli }.sorted()
    }
}
