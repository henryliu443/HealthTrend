package com.healthtrend.ui.chart

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.healthtrend.R
import com.healthtrend.ui.theme.chartColors
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberBottom
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberStart
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLine
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.compose.cartesian.rememberVicoScrollState
import com.patrykandpatrick.vico.compose.cartesian.rememberVicoZoomState
import com.patrykandpatrick.vico.compose.common.ProvideVicoTheme
import com.patrykandpatrick.vico.compose.common.component.rememberShapeComponent
import com.patrykandpatrick.vico.compose.common.fill
import com.patrykandpatrick.vico.compose.m3.common.rememberM3VicoTheme
import com.patrykandpatrick.vico.core.cartesian.Zoom
import com.patrykandpatrick.vico.core.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.core.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.core.cartesian.data.CartesianChartModel
import com.patrykandpatrick.vico.core.cartesian.data.CartesianLayerRangeProvider
import com.patrykandpatrick.vico.core.cartesian.data.LineCartesianLayerModel
import com.patrykandpatrick.vico.core.cartesian.decoration.HorizontalBox
import com.patrykandpatrick.vico.core.cartesian.layer.LineCartesianLayer
import com.patrykandpatrick.vico.core.common.shape.Shape
import java.time.ZoneId
import kotlin.math.max
import kotlin.math.min

/**
 * The composite single-metric chart — AGENTS.md §7.2.
 *
 * Layers are drawn bottom-up in exactly the order the specification lists: the reference band, the
 * raw observation polyline, the time-decayed EWMA fit, the dashed OLS regression line, and finally
 * the anomaly markers. Everything is computed by the caller on `Dispatchers.Default`; this
 * composable only translates the result into Vico primitives.
 *
 * The chart model is built **synchronously** from the finished data instead of being streamed
 * through a `CartesianChartModelProducer`. The data is complete before it arrives here, so a
 * producer would only add coroutines — and, more importantly, a producer renders nothing until a
 * `LaunchedEffect` has run, which would leave the Android Studio previews blank.
 */
@Composable
internal fun MetricDetailChart(
    data: MetricChartData,
    originEpochMilli: Long,
    zoneId: ZoneId,
    modifier: Modifier = Modifier,
) {
    if (data.isEmpty) {
        Box(
            modifier = modifier.fillMaxWidth().height(CHART_HEIGHT),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = stringResource(R.string.chart_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }

    val palette = chartColors

    ProvideVicoTheme(theme = rememberM3VicoTheme(lineCartesianLayerColors = listOf(palette.raw))) {
        val rawLine = LineCartesianLayer.rememberLine(
            fill = LineCartesianLayer.LineFill.single(fill(palette.raw)),
            areaFill = null,
            thickness = 2.dp,
            pointConnector = StraightPointConnector,
        )
        val smoothedLine = LineCartesianLayer.rememberLine(
            fill = LineCartesianLayer.LineFill.single(fill(palette.smoothed)),
            areaFill = null,
            thickness = 2.dp,
            pointConnector = StraightPointConnector,
        )
        val trendLine = LineCartesianLayer.rememberLine(
            fill = LineCartesianLayer.LineFill.single(fill(palette.trend)),
            areaFill = null,
            thickness = 2.dp,
            pointConnector = remember { dashedPointConnector(dashPx = 8f, gapPx = 6f) },
        )
        val anomalyMarker = LineCartesianLayer.Point(
            component = rememberShapeComponent(
                fill = fill(palette.anomaly),
                shape = CircleShape,
            ),
            sizeDp = 10f,
        )
        val anomalyLine = LineCartesianLayer.rememberLine(
            fill = LineCartesianLayer.LineFill.single(fill(Color.Transparent)),
            areaFill = null,
            thickness = 0.dp,
            pointProvider = remember(anomalyMarker) {
                LineCartesianLayer.PointProvider.single(anomalyMarker)
            },
        )

        // Layers that carry no points are dropped rather than submitted empty: an empty series
        // contributes nothing to the chart but does have to survive Vico's range maths, and dropping
        // it keeps the line list and the series list trivially index-aligned.
        val drawn = ArrayList<Pair<LineCartesianLayer.Line, List<ChartPoint>>>(LAYER_COUNT)
        drawn += rawLine to data.raw
        if (data.smoothed.isNotEmpty()) drawn += smoothedLine to data.smoothed
        if (data.trend.isNotEmpty()) drawn += trendLine to data.trend
        if (data.anomalies.isNotEmpty()) drawn += anomalyLine to data.anomalies

        val layer = rememberLineCartesianLayer(
            // Keyed on the colours and the layer count rather than on `drawn`, whose list identity
            // changes every recomposition; the [Line]s themselves are already `remember`ed.
            lineProvider = remember(palette, drawn.size) {
                LineCartesianLayer.LineProvider.series(drawn.map { it.first })
            },
            rangeProvider = remember(data) { data.paddedYRange().toRangeProvider() },
        )

        val model = remember(data) {
            CartesianChartModel(
                LineCartesianLayerModel.build {
                    drawn.forEach { (_, points) ->
                        series(points.map { it.x }, points.map { it.y })
                    }
                },
            )
        }

        val bandComponent = rememberShapeComponent(
            fill = fill(palette.band),
            shape = Shape.Rectangle,
        )
        val decorations = remember(data.referenceRange, palette.band) {
            data.referenceRange?.let { range ->
                listOf(HorizontalBox(y = { range.start..range.endInclusive }, box = bandComponent))
            } ?: emptyList()
        }

        val chart = rememberCartesianChart(
            layer,
            startAxis = VerticalAxis.rememberStart(valueFormatter = ValueAxisFormatter),
            bottomAxis = HorizontalAxis.rememberBottom(
                valueFormatter = remember(originEpochMilli, zoneId) {
                    dayAxisFormatter(originEpochMilli, zoneId)
                },
            ),
            decorations = decorations,
            // Days are the x unit, so one major step is exactly one day. Left to itself Vico would
            // derive the step from the greatest common divisor of the x deltas, which for
            // epoch-derived fractional days falls below its supported precision.
            getXStep = { 1.0 },
        )

        CartesianChartHost(
            chart = chart,
            model = model,
            modifier = modifier.fillMaxWidth().height(CHART_HEIGHT),
            scrollState = rememberVicoScrollState(scrollEnabled = false),
            zoomState = rememberVicoZoomState(zoomEnabled = false, initialZoom = Zoom.Content),
        )
    }
}

private val CHART_HEIGHT = 220.dp
private const val LAYER_COUNT = 4

/**
 * The y range the chart is pinned to: every drawn series plus the reference band, with a little
 * breathing room.
 *
 * A constant series has zero span, which would make the range length zero and put Vico's own
 * positioning maths into a division by zero, so a degenerate range is widened by a nominal unit.
 */
private fun MetricChartData.paddedYRange(): ClosedFloatingPointRange<Double>? {
    var low = Double.POSITIVE_INFINITY
    var high = Double.NEGATIVE_INFINITY
    fun include(value: Double) {
        if (!value.isFinite()) return
        low = min(low, value)
        high = max(high, value)
    }
    raw.forEach { include(it.y) }
    smoothed.forEach { include(it.y) }
    anomalies.forEach { include(it.y) }
    referenceRange?.let { include(it.start); include(it.endInclusive) }
    if (low > high) return null
    val span = high - low
    val padding = if (span <= 0.0) 1.0 else span * 0.05
    return (low - padding)..(high + padding)
}

private fun ClosedFloatingPointRange<Double>?.toRangeProvider(): CartesianLayerRangeProvider =
    if (this == null) {
        CartesianLayerRangeProvider.auto()
    } else {
        CartesianLayerRangeProvider.fixed(minY = start, maxY = endInclusive)
    }
