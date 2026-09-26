package com.healthtrend.ui.compare

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.healthtrend.R
import com.healthtrend.core.analytics.model.RawDataPoint
import com.healthtrend.core.analytics.model.TimeSeries
import com.healthtrend.core.analytics.normalize.NormalizationMode
import com.healthtrend.core.analytics.normalize.SeriesNormalizer
import com.healthtrend.core.domain.model.MetricCategory
import com.healthtrend.core.domain.model.MetricDataType
import com.healthtrend.core.domain.model.MetricDefinition
import com.healthtrend.ui.chart.ChartPoint
import com.healthtrend.ui.chart.CompareChart
import com.healthtrend.ui.chart.CompareSeries
import com.healthtrend.ui.chart.chartX
import com.healthtrend.ui.common.TimeRange
import com.healthtrend.ui.common.labelRes
import com.healthtrend.ui.common.scaleNoteRes
import com.healthtrend.ui.components.ChoiceChips
import com.healthtrend.ui.components.ChartLegend
import com.healthtrend.ui.components.LegendEntry
import com.healthtrend.ui.components.SectionCard
import com.healthtrend.ui.components.ToggleChips
import com.healthtrend.ui.theme.HealthTrendTheme
import com.healthtrend.ui.theme.chartColors
import org.koin.androidx.compose.koinViewModel
import java.time.Instant
import java.time.ZoneId
import kotlin.math.PI
import kotlin.math.sin

/**
 * The multi-metric normalised comparison (AGENTS.md §7.3).
 *
 * The page states which projection is on screen and that normalisation is a display-only transform,
 * because an axis whose units change with a chip is easy to misread.
 */
@Composable
internal fun CompareScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    zoneId: ZoneId = ZoneId.systemDefault(),
    viewModel: CompareViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    CompareContent(
        state = state,
        zoneId = zoneId,
        onBack = onBack,
        onSelectMode = viewModel::selectMode,
        onSelectRange = viewModel::selectTimeRange,
        onToggleMetric = viewModel::toggleMetric,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CompareContent(
    state: CompareUiState,
    zoneId: ZoneId,
    onBack: () -> Unit,
    onSelectMode: (NormalizationMode) -> Unit,
    onSelectRange: (TimeRange) -> Unit,
    onToggleMetric: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.compare_title)) },
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
            ChoiceChips(
                options = NormalizationMode.entries,
                selected = state.mode,
                label = { stringResource(it.labelRes) },
                onSelect = onSelectMode,
            )
            ChoiceChips(
                options = TimeRange.entries,
                selected = state.timeRange,
                label = { stringResource(it.labelRes) },
                onSelect = onSelectRange,
            )
            Text(
                text = stringResource(state.mode.scaleNoteRes),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            SectionCard(
                title = stringResource(R.string.compare_pick_metrics),
                subtitle = stringResource(R.string.compare_selected, state.selectedMetricIds.size),
            ) {
                ToggleChips(
                    options = state.availableMetrics,
                    isSelected = { it.id in state.selectedMetricIds },
                    label = { it.name },
                    onToggle = { onToggleMetric(it.id) },
                )
            }

            SectionCard(title = stringResource(R.string.compare_title)) {
                if (!state.canRender) {
                    Text(
                        text = stringResource(R.string.compare_need_two),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                } else {
                    CompareChart(
                        normalizedSeries = state.series,
                        originEpochMilli = state.originEpochMilli,
                        zoneId = zoneId,
                    )
                    val palette = chartColors.comparisonPalette
                    ChartLegend(
                        entries = state.series.mapIndexed { index, series ->
                            LegendEntry(palette[index % palette.size], series.name)
                        },
                    )
                }
                if (state.unavailableMetricNames.isNotEmpty()) {
                    state.unavailableMetricNames.forEach { name ->
                        Text(
                            text = stringResource(R.string.compare_unavailable, name),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
                Text(
                    text = stringResource(R.string.compare_normalization_note),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

// --------------------------------------------------------------------------- previews

private fun previewMetric(id: String, name: String, unit: String, order: Int): MetricDefinition =
    MetricDefinition(
        id = id,
        name = name,
        category = MetricCategory.BODY,
        unit = unit,
        dataType = MetricDataType.NUMERIC,
        description = "",
        expectedFrequency = null,
        minValue = null,
        maxValue = null,
        referenceRangeLow = null,
        referenceRangeHigh = null,
        isBuiltIn = true,
        displayOrder = order,
    )

/** Normalises a synthetic series exactly the way the ViewModel does, then converts to chart space. */
private fun previewSeries(
    metric: MetricDefinition,
    originEpochMilli: Long,
    mode: NormalizationMode,
    value: (Int) -> Double,
): CompareSeries {
    val points = (0 until 60).map { index ->
        RawDataPoint(originEpochMilli + index * 86_400_000L, value(index))
    }
    val normalized = SeriesNormalizer.normalize(
        series = TimeSeries.of(metric.id, metric.unit, points),
        mode = mode,
    )
    return CompareSeries(
        metricId = metric.id,
        name = metric.name,
        unit = metric.unit,
        points = normalized.series.points.map {
            ChartPoint(chartX(it.timestampEpochMilli, originEpochMilli), it.value)
        },
    )
}

private fun previewCompareState(): CompareUiState {
    val originEpochMilli = Instant.parse("2026-04-20T04:00:00Z").toEpochMilli()
    val weight = previewMetric("body_weight", "体重", "kg", 10)
    val heartRate = previewMetric("resting_heart_rate", "静息心率", "bpm", 30)
    val sleep = previewMetric("sleep_duration", "睡眠时长", "h", 80)
    val uricAcid = previewMetric("serum_uric_acid", "尿酸", "μmol/L", 70)
    return CompareUiState(
        isLoading = false,
        timeRange = TimeRange.LAST_90_DAYS,
        mode = NormalizationMode.Z_SCORE,
        availableMetrics = listOf(weight, heartRate, sleep, uricAcid),
        selectedMetricIds = setOf(weight.id, heartRate.id, sleep.id, uricAcid.id),
        series = listOf(
            previewSeries(weight, originEpochMilli, NormalizationMode.Z_SCORE) { 74.0 - 0.03 * it + 0.3 * sin(2 * PI * it / 7.0) },
            previewSeries(heartRate, originEpochMilli, NormalizationMode.Z_SCORE) { 62.0 + 2.0 * sin(2 * PI * it / 7.0) },
            previewSeries(sleep, originEpochMilli, NormalizationMode.Z_SCORE) { 7.2 + 0.4 * sin(2 * PI * it / 7.0 + 1.1) },
        ),
        unavailableMetricNames = listOf(uricAcid.name),
        originEpochMilli = originEpochMilli,
    )
}

@Preview(name = "Compare · light", showBackground = true, heightDp = 1100)
@Composable
private fun ComparePreview() {
    val state = remember { previewCompareState() }
    HealthTrendTheme(darkTheme = false) {
        CompareContent(
            state = state,
            zoneId = ZoneId.of("Asia/Shanghai"),
            onBack = {},
            onSelectMode = {},
            onSelectRange = {},
            onToggleMetric = {},
        )
    }
}

@Preview(name = "Compare · min–max", showBackground = true)
@Composable
private fun CompareMinMaxPreview() {
    HealthTrendTheme(darkTheme = true) {
        CompareContent(
            state = CompareUiState(
                isLoading = false,
                mode = NormalizationMode.MIN_MAX,
                availableMetrics = previewCompareState().availableMetrics,
                selectedMetricIds = setOf("body_weight"),
                series = emptyList(),
            ),
            zoneId = ZoneId.of("Asia/Shanghai"),
            onBack = {},
            onSelectMode = {},
            onSelectRange = {},
            onToggleMetric = {},
        )
    }
}
