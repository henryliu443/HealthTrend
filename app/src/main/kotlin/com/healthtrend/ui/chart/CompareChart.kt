package com.healthtrend.ui.chart

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
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
import com.patrykandpatrick.vico.compose.common.fill
import com.patrykandpatrick.vico.compose.m3.common.rememberM3VicoTheme
import com.patrykandpatrick.vico.core.cartesian.Zoom
import com.patrykandpatrick.vico.core.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.core.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.core.cartesian.data.CartesianChartModel
import com.patrykandpatrick.vico.core.cartesian.data.LineCartesianLayerModel
import com.patrykandpatrick.vico.core.cartesian.layer.LineCartesianLayer
import java.time.ZoneId

/**
 * The multi-metric overlay — AGENTS.md §7.3.
 *
 * Every series arrives already normalised, so the shared y axis is unit-free and the caller is
 * responsible for saying which projection is on screen. Nothing here writes back to storage: the
 * normalisation is a pure in-memory presentation copy (AGENTS.md §10.4).
 *
 * Like [MetricDetailChart], the model is assembled synchronously so the Studio previews render
 * without needing a `LaunchedEffect` to fire.
 */
@Composable
internal fun CompareChart(
    normalizedSeries: List<CompareSeries>,
    originEpochMilli: Long,
    zoneId: ZoneId,
    modifier: Modifier = Modifier,
) {
    // A series with no points cannot form a chart line, and submitting an empty one only adds a
    // range-maths edge case.
    val drawable = normalizedSeries.filter { it.points.isNotEmpty() }
    if (drawable.isEmpty()) return

    val palette = chartColors.comparisonPalette

    ProvideVicoTheme(theme = rememberM3VicoTheme(lineCartesianLayerColors = palette)) {
        // A plain loop rather than `map`: `rememberLine` is a composable and must be called from the
        // composition itself, with a stable call count.
        val lines = ArrayList<LineCartesianLayer.Line>(drawable.size)
        for (index in drawable.indices) {
            lines += LineCartesianLayer.rememberLine(
                fill = LineCartesianLayer.LineFill.single(fill(palette[index % palette.size])),
                areaFill = null,
                thickness = 2.dp,
                pointConnector = StraightPointConnector,
            )
        }

        val layer = rememberLineCartesianLayer(
            lineProvider = remember(palette, drawable.size) {
                LineCartesianLayer.LineProvider.series(lines)
            },
        )

        val model = remember(normalizedSeries) {
            CartesianChartModel(
                LineCartesianLayerModel.build {
                    drawable.forEach { entry ->
                        series(entry.points.map { it.x }, entry.points.map { it.y })
                    }
                },
            )
        }

        val chart = rememberCartesianChart(
            layer,
            startAxis = VerticalAxis.rememberStart(valueFormatter = ValueAxisFormatter),
            bottomAxis = HorizontalAxis.rememberBottom(
                valueFormatter = remember(originEpochMilli, zoneId) {
                    dayAxisFormatter(originEpochMilli, zoneId)
                },
            ),
            getXStep = { 1.0 },
        )

        CartesianChartHost(
            chart = chart,
            model = model,
            modifier = modifier.fillMaxWidth().height(COMPARE_CHART_HEIGHT),
            scrollState = rememberVicoScrollState(scrollEnabled = false),
            zoomState = rememberVicoZoomState(zoomEnabled = false, initialZoom = Zoom.Content),
        )
    }
}

private val COMPARE_CHART_HEIGHT = 260.dp
