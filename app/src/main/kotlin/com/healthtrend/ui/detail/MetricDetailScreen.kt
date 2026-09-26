package com.healthtrend.ui.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.healthtrend.R
import com.healthtrend.core.analytics.DescriptiveStats
import com.healthtrend.core.analytics.anomaly.AnomalyDetection
import com.healthtrend.core.analytics.downsample.LttbDownsampler
import com.healthtrend.core.analytics.model.RawDataPoint
import com.healthtrend.core.analytics.model.TimeSeries
import com.healthtrend.core.analytics.quality.DataQuality
import com.healthtrend.core.analytics.smoothing.Ewma
import com.healthtrend.core.analytics.trend.TrendAnalysis
import com.healthtrend.core.domain.model.MetricCategory
import com.healthtrend.core.domain.model.MetricDataType
import com.healthtrend.core.domain.model.MetricDefinition
import com.healthtrend.ui.chart.ChartPoint
import com.healthtrend.ui.chart.MetricChartData
import com.healthtrend.ui.chart.MetricDetailChart
import com.healthtrend.ui.chart.chartX
import com.healthtrend.ui.common.TimeRange
import com.healthtrend.ui.common.labelRes
import com.healthtrend.ui.components.ChoiceChips
import com.healthtrend.ui.components.ChartLegend
import com.healthtrend.ui.components.LegendEntry
import com.healthtrend.ui.components.SectionCard
import com.healthtrend.ui.components.StatRow
import com.healthtrend.ui.components.TrendBadge
import com.healthtrend.ui.format.Formatters
import com.healthtrend.ui.theme.HealthTrendTheme
import com.healthtrend.ui.theme.chartColors
import org.koin.androidx.compose.koinViewModel
import java.time.Instant
import java.time.ZoneId
import kotlin.math.PI
import kotlin.math.sin

/** The single-metric detail page: chart, statistics, trend, deviations and data quality. */
@Composable
internal fun MetricDetailScreen(
    metricId: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    zoneId: ZoneId = ZoneId.systemDefault(),
    viewModel: MetricDetailViewModel = koinViewModel(),
) {
    LaunchedEffect(metricId) { viewModel.selectMetric(metricId) }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    MetricDetailContent(
        state = state,
        zoneId = zoneId,
        onBack = onBack,
        onSelectRange = viewModel::selectTimeRange,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MetricDetailContent(
    state: MetricDetailUiState,
    zoneId: ZoneId,
    onBack: () -> Unit,
    onSelectRange: (TimeRange) -> Unit,
    modifier: Modifier = Modifier,
) {
    val unit = state.definition?.unit.orEmpty()
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(state.definition?.name ?: stringResource(R.string.detail_title)) },
                navigationIcon = {
                    TextButton(onClick = onBack) { Text(stringResource(R.string.action_back)) }
                },
            )
        },
    ) { insets ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(insets)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            state.definition?.let { definition ->
                val low = definition.referenceRangeLow
                val high = definition.referenceRangeHigh
                if (low != null && high != null) {
                    Text(
                        text = stringResource(
                            R.string.detail_reference_range,
                            Formatters.value(low),
                            Formatters.value(high),
                            definition.unit,
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            ChoiceChips(
                options = TimeRange.entries,
                selected = state.timeRange,
                label = { stringResource(it.labelRes) },
                onSelect = onSelectRange,
            )

            SectionCard(
                title = stringResource(R.string.detail_title),
                subtitle = stringResource(R.string.label_sample_count) + ": ${state.sampleCount}",
            ) {
                MetricDetailChart(
                    data = state.chart,
                    originEpochMilli = state.originEpochMilli,
                    zoneId = zoneId,
                )
                if (!state.chart.isEmpty) {
                    val palette = chartColors
                    ChartLegend(
                        entries = buildList {
                            add(LegendEntry(palette.raw, stringResource(R.string.chart_legend_raw)))
                            if (state.chart.smoothed.isNotEmpty()) {
                                add(LegendEntry(palette.smoothed, stringResource(R.string.chart_legend_smoothed)))
                            }
                            if (state.chart.trend.isNotEmpty()) {
                                add(LegendEntry(palette.trend, stringResource(R.string.chart_legend_trend)))
                            }
                            if (state.chart.anomalies.isNotEmpty()) {
                                add(LegendEntry(palette.anomaly, stringResource(R.string.chart_legend_anomaly)))
                            }
                            if (state.chart.referenceRange != null) {
                                add(LegendEntry(palette.band, stringResource(R.string.chart_legend_band)))
                            }
                        },
                    )
                }
            }

            state.statistics?.let { statistics ->
                SectionCard(title = stringResource(R.string.detail_stats_title)) {
                    StatRow(stringResource(R.string.detail_stat_mean), Formatters.valueWithUnit(statistics.mean, unit))
                    StatRow(stringResource(R.string.detail_stat_median), Formatters.valueWithUnit(statistics.median, unit))
                    StatRow(
                        stringResource(R.string.detail_stat_sd),
                        Formatters.valueWithUnit(statistics.sampleStandardDeviation, unit),
                    )
                    StatRow(stringResource(R.string.detail_stat_min), Formatters.valueWithUnit(statistics.min, unit))
                    StatRow(stringResource(R.string.detail_stat_max), Formatters.valueWithUnit(statistics.max, unit))
                }
            }

            state.trend?.let { trend ->
                SectionCard(
                    title = stringResource(R.string.detail_trend_title),
                    subtitle = stringResource(R.string.detail_trend_method),
                    trailing = { TrendBadge(trend.direction) },
                ) {
                    StatRow(
                        stringResource(R.string.detail_trend_slope),
                        stringResource(
                            R.string.detail_trend_per_day,
                            Formatters.signedValue(trend.slopePerDay),
                        ),
                    )
                    StatRow(stringResource(R.string.detail_trend_pvalue), Formatters.pValue(trend.pValue))
                    StatRow(stringResource(R.string.detail_trend_r2), Formatters.value(trend.rSquared))
                    Text(
                        text = stringResource(R.string.detail_trend_note),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            SectionCard(title = stringResource(R.string.detail_anomaly_title)) {
                val flagged = state.anomaly?.points?.filter { it.isAnomaly }.orEmpty()
                if (flagged.isEmpty()) {
                    Text(
                        text = stringResource(R.string.detail_anomaly_none),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                } else {
                    flagged.take(MAX_LISTED_ANOMALIES).forEach { point ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = Formatters.day(point.timestampEpochMilli, zoneId),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                text = Formatters.valueWithUnit(point.value, unit),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            Text(
                                text = stringResource(
                                    R.string.detail_anomaly_item,
                                    Formatters.signedValue(point.robustZScore),
                                ),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                }
                Text(
                    text = stringResource(R.string.detail_anomaly_disclaimer),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            state.quality?.let { quality ->
                SectionCard(title = stringResource(R.string.detail_quality_title)) {
                    val intervalDays = quality.intervalStatistics?.meanMillis
                        ?.div(MILLIS_PER_DAY)
                    StatRow(
                        stringResource(R.string.detail_quality_interval),
                        stringResource(
                            R.string.detail_quality_days,
                            intervalDays?.let { Formatters.value(it) } ?: stringResource(R.string.label_undefined),
                        ),
                    )
                    StatRow(
                        stringResource(R.string.detail_quality_longest_gap),
                        stringResource(
                            R.string.detail_quality_days,
                            Formatters.value(quality.longestGapMillis.toDouble() / MILLIS_PER_DAY),
                        ),
                    )
                    StatRow(
                        stringResource(R.string.detail_quality_span),
                        stringResource(R.string.detail_quality_days, Formatters.value(quality.spanDays)),
                    )
                }
            }

            Text(
                text = stringResource(R.string.detail_disclaimer),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private const val MAX_LISTED_ANOMALIES = 5
private const val MILLIS_PER_DAY = 86_400_000.0

// --------------------------------------------------------------------------- previews

/**
 * Builds the preview state by running the real engine over a synthetic series.
 *
 * Constructing the engine result types by hand would let the preview drift from what the ViewModel
 * actually produces; running the engine keeps the preview honest.
 */
private fun previewDetailState(zoneId: ZoneId): MetricDetailUiState {
    val originEpochMilli = Instant.parse("2026-05-02T04:00:00Z").toEpochMilli()
    val points = (0 until 45).map { index ->
        RawDataPoint(
            timestampEpochMilli = originEpochMilli + index * 86_400_000L,
            value = 74.0 - 0.035 * index + 0.35 * sin(2 * PI * index / 7.0),
        )
    }.toMutableList().also { it[20] = it[20].copy(value = it[20].value + 1.6) }

    val series = TimeSeries.of("body_weight", "kg", points)
    val trend = TrendAnalysis.fit(series)
    val anomaly = AnomalyDetection.detect(series)
    val definition = MetricDefinition(
        id = "body_weight",
        name = "体重",
        category = MetricCategory.BODY,
        unit = "kg",
        dataType = MetricDataType.NUMERIC,
        description = "晨起空腹体重",
        expectedFrequency = "DAILY",
        minValue = 0.0,
        maxValue = 300.0,
        referenceRangeLow = 60.0,
        referenceRangeHigh = 72.0,
        isBuiltIn = true,
        displayOrder = 10,
    )
    return MetricDetailUiState(
        isLoading = false,
        metricId = definition.id,
        definition = definition,
        timeRange = TimeRange.LAST_90_DAYS,
        originEpochMilli = originEpochMilli,
        chart = MetricChartData(
            raw = LttbDownsampler.downsample(series).points.map {
                ChartPoint(chartX(it.timestampEpochMilli, originEpochMilli), it.value)
            },
            smoothed = Ewma.computeDays(series, halfLifeDays = 7.0).map {
                ChartPoint(chartX(it.timestampEpochMilli, originEpochMilli), it.value)
            },
            trend = listOf(
                ChartPoint(chartX(trend.startTimestampEpochMilli, originEpochMilli), trend.fittedStart),
                ChartPoint(chartX(trend.endTimestampEpochMilli, originEpochMilli), trend.fittedEnd),
            ),
            anomalies = anomaly.points.filter { it.isAnomaly }.map {
                ChartPoint(chartX(it.timestampEpochMilli, originEpochMilli), it.value)
            },
            referenceRange = 60.0..72.0,
        ),
        statistics = DescriptiveStats.calculate(series),
        trend = trend,
        anomaly = anomaly,
        quality = DataQuality.analyze(series, zoneId),
        sampleCount = series.size,
    )
}

@Preview(name = "Metric detail · light", showBackground = true, heightDp = 1400)
@Composable
private fun MetricDetailPreview() {
    val zoneId = ZoneId.of("Asia/Shanghai")
    val state = remember { previewDetailState(zoneId) }
    HealthTrendTheme(darkTheme = false) {
        MetricDetailContent(
            state = state,
            zoneId = zoneId,
            onBack = {},
            onSelectRange = {},
        )
    }
}

@Preview(name = "Metric detail · empty", showBackground = true)
@Composable
private fun MetricDetailEmptyPreview() {
    HealthTrendTheme(darkTheme = false) {
        MetricDetailContent(
            state = MetricDetailUiState(
                isLoading = false,
                metricId = "body_weight",
                definition = previewDetailState(ZoneId.of("Asia/Shanghai")).definition,
                timeRange = TimeRange.LAST_7_DAYS,
            ),
            zoneId = ZoneId.of("Asia/Shanghai"),
            onBack = {},
            onSelectRange = {},
        )
    }
}
