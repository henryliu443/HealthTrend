package com.healthtrend.core.analytics.normalize

import com.healthtrend.core.analytics.internal.NumericSupport
import com.healthtrend.core.analytics.model.RawDataPoint
import com.healthtrend.core.analytics.model.TimeSeries
import com.healthtrend.core.analytics.model.orderedPoints

/**
 * Rescaling applied to a series so that several series with different units can share one axis
 * (AGENTS.md §7.3).
 *
 * Normalisation is a **presentation transform only**: it produces a pure in-memory copy and never
 * touches the stored observations (AGENTS.md §10.4).
 */
enum class NormalizationMode {
    /** `(x - mean) / sampleSd` — spread in standard deviations around the series' own mean. */
    Z_SCORE,

    /** `(x - min) / (max - min)` — spread across `[0, 1]` between the series' own extremes. */
    MIN_MAX,

    /** `100 · x / x₀` — the first observation is the index base. */
    BASELINE_100,
}

/**
 * Result of a [NormalizationMode] applied to one series.
 *
 * [available] is the counterpart of AGENTS.md §6.3's `ARITHMETIC_ONLY` marker: it is `false` when
 * the mode is mathematically undefined for the given series, in which case [series] is empty and
 * the UI must say "cannot normalise" instead of drawing a fabricated line.
 *
 * | mode | `available = false` when |
 * |---|---|
 * | [NormalizationMode.Z_SCORE] | fewer than 2 points (no sample deviation) |
 * | [NormalizationMode.MIN_MAX] | fewer than 2 points |
 * | [NormalizationMode.BASELINE_100] | empty series, or the first value is exactly `0.0` |
 *
 * A **zero spread** is *not* undefined: it keeps the same "collapse to `0.0`" rule that
 * `DescriptiveStats` uses for skewness/kurtosis, so a constant series normalises to a flat line at
 * `0.0` rather than to `NaN`.
 */
data class NormalizedSeries(
    val mode: NormalizationMode,
    val series: TimeSeries,
    val available: Boolean,
    /** The location the values were centred on: the mean (Z-score), or the min (min–max). */
    val centre: Double?,
    /** The divisor the centred values were divided by, or the base of the index. */
    val scale: Double?,
)

/**
 * Unit-free rescaling of a single series — AGENTS.md §7.3.
 *
 * The transforms deliberately reuse the same location/scale conventions as the rest of the engine:
 * the mean is the two-pass arithmetic mean ([NumericSupport.mean]) and the standard deviation is the
 * **sample** deviation with the `n - 1` denominator, matching `DescriptiveStats.sampleStandardDeviation`
 * (AGENTS.md §6.1). A series normalised at `Z_SCORE` therefore has, by construction, mean `0` and
 * sample deviation `1`.
 *
 * Input order is never relied upon: points are ordered by time first, so `BASELINE_100`'s base is the
 * **earliest** observation regardless of how the caller built the list.
 */
object SeriesNormalizer {

    fun normalize(series: TimeSeries, mode: NormalizationMode): NormalizedSeries =
        when (mode) {
            NormalizationMode.Z_SCORE -> zScore(series)
            NormalizationMode.MIN_MAX -> minMax(series)
            NormalizationMode.BASELINE_100 -> baseline100(series)
        }

    private fun zScore(series: TimeSeries): NormalizedSeries {
        val points = series.orderedPoints()
        if (points.size < 2) return unavailable(series, NormalizationMode.Z_SCORE)
        val values = DoubleArray(points.size) { points[it].value }
        val mean = NumericSupport.mean(values)
        val sumSquaredDeviations = NumericSupport.sumSquaredDeviations(values, mean)
        val sampleStandardDeviation = NumericSupport.safeSqrt(sumSquaredDeviations / (points.size - 1))
        val scaled = points.map { point ->
            RawDataPoint(
                timestampEpochMilli = point.timestampEpochMilli,
                value = if (sampleStandardDeviation > 0.0) {
                    (point.value - mean) / sampleStandardDeviation
                } else {
                    0.0
                },
            )
        }
        return NormalizedSeries(
            mode = NormalizationMode.Z_SCORE,
            series = TimeSeries(series.seriesId, scaled, series.unit),
            available = true,
            centre = mean,
            scale = sampleStandardDeviation,
        )
    }

    private fun minMax(series: TimeSeries): NormalizedSeries {
        val points = series.orderedPoints()
        if (points.size < 2) return unavailable(series, NormalizationMode.MIN_MAX)
        val values = points.map { it.value }
        val min = values.min()
        val max = values.max()
        val range = max - min
        val scaled = points.map { point ->
            RawDataPoint(
                timestampEpochMilli = point.timestampEpochMilli,
                value = if (range > 0.0) (point.value - min) / range else 0.0,
            )
        }
        return NormalizedSeries(
            mode = NormalizationMode.MIN_MAX,
            series = TimeSeries(series.seriesId, scaled, UNIT_INTERVAL),
            available = true,
            centre = min,
            scale = range,
        )
    }

    private fun baseline100(series: TimeSeries): NormalizedSeries {
        val points = series.orderedPoints()
        if (points.isEmpty()) return unavailable(series, NormalizationMode.BASELINE_100)
        val baseline = points.first().value
        if (baseline == 0.0) return unavailable(series, NormalizationMode.BASELINE_100)
        val scaled = points.map { point ->
            RawDataPoint(
                timestampEpochMilli = point.timestampEpochMilli,
                value = BASELINE_INDEX * point.value / baseline,
            )
        }
        return NormalizedSeries(
            mode = NormalizationMode.BASELINE_100,
            series = TimeSeries(series.seriesId, scaled, BASELINE_INDEX_UNIT),
            available = true,
            centre = baseline,
            scale = baseline,
        )
    }

    private fun unavailable(series: TimeSeries, mode: NormalizationMode): NormalizedSeries =
        NormalizedSeries(
            mode = mode,
            series = TimeSeries(series.seriesId, emptyList(), series.unit),
            available = false,
            centre = null,
            scale = null,
        )

    /** The index value of the first observation under [NormalizationMode.BASELINE_100]. */
    const val BASELINE_INDEX: Double = 100.0

    private const val UNIT_INTERVAL = "unitless"
    private const val BASELINE_INDEX_UNIT = "index(100)"
}
