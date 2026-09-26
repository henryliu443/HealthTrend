package com.healthtrend.ui.chart

/**
 * Chart-coordinate point.
 *
 * [x] is **days since the chart's origin**, not an epoch timestamp. Vico needs a monotonically
 * increasing numeric *x* and validates the precision of the step between points, so the raw
 * `Long` epoch range (`1.7e12`) would have to be handled with 4-decimal steps to render at all.
 * Mapping to days keeps the step a plain `1.0`, and the axis labels are reconstructed by adding
 * [x] whole calendar days to the origin in the display time zone.
 */
data class ChartPoint(val x: Double, val y: Double)

/**
 * Every layer the single-metric detail chart draws, precomputed off the main thread before it
 * reaches Compose (AGENTS.md §7.2 / §10.5).
 *
 * Layer order is bottom-up: reference band, raw, smoothed, trend, anomalies. Each list may be
 * empty — an empty layer is simply not drawn.
 */
data class MetricChartData(
    val raw: List<ChartPoint> = emptyList(),
    val smoothed: List<ChartPoint> = emptyList(),
    /** The OLS fit, reduced to its two endpoints (engine §6.6 reports the fitted start/end). */
    val trend: List<ChartPoint> = emptyList(),
    /** The subset of [raw] the anomaly detectors flagged (§6.7). */
    val anomalies: List<ChartPoint> = emptyList(),
    /** The metric's own reference range, drawn as a background band when both bounds exist. */
    val referenceRange: ClosedFloatingPointRange<Double>? = null,
) {
    val isEmpty: Boolean get() = raw.isEmpty()
}

/**
 * One normalised series on the comparison chart (AGENTS.md §7.3).
 *
 * [points] are already normalised; this model deliberately carries no colour so that the palette
 * stays a presentation concern of the chart composable.
 */
data class CompareSeries(
    val metricId: String,
    val name: String,
    val unit: String,
    val points: List<ChartPoint>,
)

/** Milliseconds in a nominal day; the chart's *x* unit. */
internal const val CHART_MILLIS_PER_DAY: Double = 86_400_000.0

/** Converts an epoch-millisecond timestamp into the chart's days-since-[origin] coordinate. */
internal fun chartX(timestampEpochMilli: Long, originEpochMilli: Long): Double =
    (timestampEpochMilli - originEpochMilli) / CHART_MILLIS_PER_DAY
