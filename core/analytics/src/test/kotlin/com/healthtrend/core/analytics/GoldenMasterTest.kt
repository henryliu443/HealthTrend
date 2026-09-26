package com.healthtrend.core.analytics

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.healthtrend.core.analytics.aggregation.AggregationFunction
import com.healthtrend.core.analytics.aggregation.CalendarBucket
import com.healthtrend.core.analytics.aggregation.TimeAggregator
import com.healthtrend.core.analytics.anomaly.AnomalyDetection
import com.healthtrend.core.analytics.change.ChangeRates
import com.healthtrend.core.analytics.correlation.CrossCorrelation
import com.healthtrend.core.analytics.model.RawDataPoint
import com.healthtrend.core.analytics.model.TimeSeries
import com.healthtrend.core.analytics.normalize.NormalizationMode
import com.healthtrend.core.analytics.normalize.SeriesNormalizer
import com.healthtrend.core.analytics.smoothing.Ewma
import com.healthtrend.core.analytics.trend.TrendAnalysis
import com.healthtrend.core.analytics.window.RollingWindowStats
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.time.ZoneId
import kotlin.math.abs
import kotlin.math.max

/**
 * Golden-master verification — AGENTS.md §8.2.
 *
 * Loads `golden_test_vectors.json`, produced independently by NumPy/SciPy through
 * `tools/generate_golden_vectors.py` (§8.1), and asserts the Kotlin engine against it. Inputs live
 * in the fixture too, so neither side duplicates the other's data.
 */
class GoldenMasterTest {

    private val fixture: JsonObject = javaClass
        .getResourceAsStream("/golden/golden_test_vectors.json")
        ?.bufferedReader()?.use { JsonParser.parseReader(it).asJsonObject }
        ?: error("golden_test_vectors.json missing from the test classpath; run tools/generate_golden_vectors.py")

    private val tolerance: Double = fixture.get("tolerance").asDouble

    private val utc: ZoneId = ZoneId.of("UTC")

    private fun vectors(kind: String): List<JsonObject> =
        fixture.getAsJsonArray("vectors").map { it.asJsonObject }.filter { it.get("kind").asString == kind }

    @Test
    fun `descriptive statistics match numpy and scipy`() {
        val all = vectors("descriptive")
        all.size shouldBe 6
        for (vector in all) {
            val id = vector.get("id").asString
            val expected = vector.getAsJsonObject("expected")
            val actual = DescriptiveStats.calculate(vector.doubles("values"))
            actual.count shouldBe expected.get("count").asInt
            assertField(actual.sum, expected, "sum", id)
            assertField(actual.mean, expected, "mean", id)
            assertField(actual.min, expected, "min", id)
            assertField(actual.max, expected, "max", id)
            assertField(actual.range, expected, "range", id)
            assertField(actual.median, expected, "median", id)
            assertField(actual.firstQuartile, expected, "firstQuartile", id)
            assertField(actual.thirdQuartile, expected, "thirdQuartile", id)
            assertField(actual.interquartileRange, expected, "interquartileRange", id)
            assertField(actual.populationVariance, expected, "populationVariance", id)
            assertField(actual.populationStandardDeviation, expected, "populationStandardDeviation", id)
            assertField(actual.sampleVariance, expected, "sampleVariance", id)
            assertField(actual.sampleStandardDeviation, expected, "sampleStandardDeviation", id)
            assertField(actual.standardErrorOfMean, expected, "standardErrorOfMean", id)
            assertField(actual.skewness, expected, "skewness", id)
            assertField(actual.kurtosis, expected, "kurtosis", id)
        }
    }

    @Test
    fun `time-decayed ewma matches numpy`() {
        for (vector in vectors("ewma")) {
            val id = vector.get("id").asString
            val smoothed = Ewma.computeDays(vector.series(), vector.get("halfLifeDays").asDouble)
            val expected = vector.getAsJsonObject("expected").getAsJsonArray("smoothed")
            expected.size() shouldBe smoothed.size
            expected.forEachIndexed { index, element ->
                assertClose(smoothed[index].value, element.asDouble, tolerance, "$id smoothed[$index]")
            }
        }
    }

    @Test
    fun `ols trend matches scipy linregress`() {
        for (vector in vectors("linear_trend")) {
            val id = vector.get("id").asString
            val expected = vector.getAsJsonObject("expected")
            val actual = TrendAnalysis.fit(vector.series())
            assertField(actual.slopePerDay, expected, "slopePerDay", id)
            assertField(actual.intercept, expected, "intercept", id)
            assertField(actual.rSquared, expected, "rSquared", id)
            assertField(actual.residualStandardError, expected, "residualStandardError", id)
            assertField(actual.slopeStandardError, expected, "slopeStandardError", id)
            assertField(actual.tStatistic, expected, "tStatistic", id)
            // Tail probabilities come from two different t-distribution implementations.
            assertClose(actual.pValue, expected.get("pValue").asDouble, 1e-7, "$id.pValue")
            assertField(actual.fittedEnd, expected, "fittedEnd", id)
        }
    }

    @Test
    fun `rolling window statistics match numpy`() {
        for (vector in vectors("rolling_window")) {
            val id = vector.get("id").asString
            val windowMillis = (vector.get("windowDays").asDouble * 86_400_000.0).toLong()
            val actual = RollingWindowStats.compute(vector.series(), windowMillis)
            val expected = vector.getAsJsonObject("expected").getAsJsonArray("points")
            actual.size shouldBe expected.size()
            expected.forEachIndexed { index, element ->
                val point = element.asJsonObject
                actual[index].sampleCount shouldBe point.get("sampleCount").asInt
                assertField(actual[index].mean, point, "mean", "$id[$index]")
                assertField(actual[index].sampleStandardDeviation, point, "sampleStandardDeviation", "$id[$index]")
                assertField(actual[index].min, point, "min", "$id[$index]")
                assertField(actual[index].max, point, "max", "$id[$index]")
            }
        }
    }

    @Test
    fun `anomaly scores match numpy`() {
        for (vector in vectors("anomaly")) {
            val id = vector.get("id").asString
            val expected = vector.getAsJsonObject("expected")
            val values = vector.doubles("values")
            val series = TimeSeries.of(
                "golden",
                "unit",
                values.indices.map { RawDataPoint(it.toLong(), values[it]) },
            )
            val report = AnomalyDetection.detect(series)
            assertField(report.mean, expected, "mean", id)
            assertField(report.standardDeviation, expected, "standardDeviation", id)
            assertField(report.median, expected, "median", id)
            assertField(report.medianAbsoluteDeviation, expected, "medianAbsoluteDeviation", id)
            assertField(report.firstQuartile, expected, "firstQuartile", id)
            assertField(report.thirdQuartile, expected, "thirdQuartile", id)
            assertField(report.interquartileRange, expected, "interquartileRange", id)
            assertField(report.iqrLowerFence, expected, "iqrLowerFence", id)
            assertField(report.iqrUpperFence, expected, "iqrUpperFence", id)
            val zScores = expected.getAsJsonArray("zScores")
            val robustZScores = expected.getAsJsonArray("robustZScores")
            report.points.forEachIndexed { index, point ->
                assertClose(point.zScore, zScores[index].asDouble, tolerance, "$id z[$index]")
                assertClose(point.robustZScore, robustZScores[index].asDouble, tolerance, "$id robustZ[$index]")
            }
        }
    }

    @Test
    fun `pearson spearman and covariance match scipy`() {
        for (vector in vectors("correlation")) {
            val id = vector.get("id").asString
            val timestamps = vector.longs("timestamps")
            val valuesA = vector.doubles("valuesA")
            val valuesB = vector.doubles("valuesB")
            val seriesA = TimeSeries.of("a", "u", timestamps.indices.map { RawDataPoint(timestamps[it], valuesA[it]) })
            val seriesB = TimeSeries.of("b", "u", timestamps.indices.map { RawDataPoint(timestamps[it], valuesB[it]) })
            val expected = vector.getAsJsonObject("expected")
            val actual = CrossCorrelation.correlate(seriesA, seriesB, utc)
            actual.sampleCount shouldBe expected.get("sampleCount").asInt
            assertClose(actual.pearson ?: Double.NaN, expected.get("pearson").asDouble, tolerance, "$id.pearson")
            assertClose(actual.spearman ?: Double.NaN, expected.get("spearman").asDouble, 1e-7, "$id.spearman")
            assertClose(actual.covariance ?: Double.NaN, expected.get("covariance").asDouble, tolerance, "$id.covariance")
        }
    }

    @Test
    fun `cagr matches the spec formula`() {
        val all = vectors("cagr")
        all.size shouldBe 2
        for (vector in all) {
            val id = vector.get("id").asString
            val expected = vector.getAsJsonObject("expected")
            val start = RawDataPoint(0L, vector.get("startValue").asDouble)
            val spanDays = vector.get("spanDays").asDouble
            val end = RawDataPoint((spanDays * 86_400_000.0).toLong(), vector.get("endValue").asDouble)
            val actual = ChangeRates.analyze(start, end)
            assertClose(actual.cagr ?: Double.NaN, expected.get("cagr"), tolerance, "$id.cagr")
            assertClose(actual.relativeChange ?: Double.NaN, expected.get("relativeChange"), tolerance, "$id.relativeChange")
            actual.geometricAvailable shouldBe !expected.get("cagr").isJsonNull
        }
    }

    @Test
    fun `calendar aggregation matches numpy grouping`() {
        for (vector in vectors("aggregation")) {
            val id = vector.get("id").asString
            val series = vector.series()
            val expected = vector.getAsJsonObject("expected").getAsJsonArray("points")
            val sums = TimeAggregator.aggregate(series, CalendarBucket.DAY, utc, AggregationFunction.SUM)
            val means = TimeAggregator.aggregate(series, CalendarBucket.DAY, utc, AggregationFunction.MEAN)
            sums.size shouldBe expected.size()
            expected.forEachIndexed { index, element ->
                val bucket = element.asJsonObject
                sums[index].bucketStartEpochMilli shouldBe bucket.get("bucketStartEpochMilli").asLong
                sums[index].sampleCount shouldBe bucket.get("sampleCount").asInt
                assertField(sums[index].value, bucket, "sum", "$id[$index]")
                assertField(means[index].value, bucket, "mean", "$id[$index]")
            }
        }
    }

    @Test
    fun `normalisation matches numpy`() {
        for (vector in vectors("normalize")) {
            val id = vector.get("id").asString
            val mode = when (vector.get("mode").asString) {
                "z_score" -> NormalizationMode.Z_SCORE
                "min_max" -> NormalizationMode.MIN_MAX
                "baseline_100" -> NormalizationMode.BASELINE_100
                else -> error("$id: unknown normalisation mode")
            }
            val expected = vector.getAsJsonObject("expected")
            val actual = SeriesNormalizer.normalize(vector.series(), mode)
            actual.available shouldBe expected.get("available").asBoolean
            if (!actual.available) {
                expected.getAsJsonArray("values").size() shouldBe 0
                actual.series.points.size shouldBe 0
                continue
            }
            val values = expected.getAsJsonArray("values")
            actual.series.points.size shouldBe values.size()
            values.forEachIndexed { index, element ->
                assertClose(actual.series.points[index].value, element.asDouble, tolerance, "$id[$index]")
            }
            assertField(actual.centre ?: Double.NaN, expected, "centre", id)
            assertField(actual.scale ?: Double.NaN, expected, "scale", id)
        }
    }

    // ---------------------------------------------------------------- helpers

    private fun JsonObject.series(): TimeSeries {
        val timestamps = longs("timestamps")
        val values = doubles("values")
        require(timestamps.size == values.size) { "golden vector has mismatched timestamps/values" }
        return TimeSeries.of("golden", "unit", timestamps.indices.map { RawDataPoint(timestamps[it], values[it]) })
    }

    private fun JsonObject.doubles(key: String): DoubleArray {
        val array: JsonArray = getAsJsonArray(key)
        return DoubleArray(array.size()) { array[it].asDouble }
    }

    private fun JsonObject.longs(key: String): LongArray {
        val array: JsonArray = getAsJsonArray(key)
        return LongArray(array.size()) { array[it].asLong }
    }

    /** Compares against a JSON scalar; a JSON `null` asserts the Kotlin side is `NaN`. */
    private fun assertField(actual: Double, expected: JsonObject, key: String, id: String) {
        val element: JsonElement = expected.get(key) ?: error("$id: golden vector is missing '$key'")
        if (element.isJsonNull) {
            actual.isNaN() shouldBe true
            return
        }
        assertClose(actual, element.asDouble, tolerance, "$id.$key")
    }

    /** Relative + absolute comparison; a JSON `null` asserts `NaN`. */
    private fun assertClose(actual: Double, expected: JsonElement, tolerance: Double, label: String) {
        if (expected.isJsonNull) {
            actual.isNaN() shouldBe true
            return
        }
        assertClose(actual, expected.asDouble, tolerance, label)
    }
    private fun assertClose(actual: Double, expected: Double, tolerance: Double, label: String) {
        if (expected.isNaN()) {
            actual.isNaN() shouldBe true
            return
        }
        val delta = abs(actual - expected)
        val allowed = tolerance * max(1.0, abs(expected))
        if (delta > allowed) {
            error("$label: expected $expected but was $actual (delta $delta > $allowed)")
        }
    }
}
