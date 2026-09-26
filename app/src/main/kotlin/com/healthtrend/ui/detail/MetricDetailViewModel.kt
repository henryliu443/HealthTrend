package com.healthtrend.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.healthtrend.core.analytics.DescriptiveStats
import com.healthtrend.core.analytics.DescriptiveStatistics
import com.healthtrend.core.analytics.anomaly.AnomalyDetection
import com.healthtrend.core.analytics.anomaly.AnomalyReport
import com.healthtrend.core.analytics.downsample.LttbDownsampler
import com.healthtrend.core.analytics.model.RawDataPoint
import com.healthtrend.core.analytics.model.TimeSeries
import com.healthtrend.core.analytics.quality.DataQuality
import com.healthtrend.core.analytics.quality.DataQualityReport
import com.healthtrend.core.analytics.smoothing.Ewma
import com.healthtrend.core.analytics.trend.LinearTrend
import com.healthtrend.core.analytics.trend.TrendAnalysis
import com.healthtrend.core.domain.model.MetricDefinition
import com.healthtrend.core.domain.model.MetricObservation
import com.healthtrend.core.domain.repository.MetricRepository
import com.healthtrend.ui.chart.ChartPoint
import com.healthtrend.ui.chart.MetricChartData
import com.healthtrend.ui.chart.chartX
import com.healthtrend.ui.common.TimeRange
import com.healthtrend.ui.common.endEpochMilliExclusive
import com.healthtrend.ui.common.startEpochMilli
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withContext
import java.time.ZoneId

/**
 * Screen state for one metric.
 *
 * Every field except [chart] is engine output kept verbatim ([DescriptiveStatistics], [LinearTrend],
 * [AnomalyReport], [DataQualityReport]) so the UI can decide how to phrase it and can never be
 * tempted to recompute statistics on the main thread.
 */
data class MetricDetailUiState(
    val isLoading: Boolean = true,
    val metricId: String = "",
    val definition: MetricDefinition? = null,
    val timeRange: TimeRange = TimeRange.LAST_30_DAYS,
    val chart: MetricChartData = MetricChartData(),
    /** Epoch milliseconds the chart's `x = 0` corresponds to; used to label the time axis. */
    val originEpochMilli: Long = 0L,
    val statistics: DescriptiveStatistics? = null,
    val trend: LinearTrend? = null,
    val anomaly: AnomalyReport? = null,
    val quality: DataQualityReport? = null,
    /** Number of observations in the window, before chart downsampling. */
    val sampleCount: Int = 0,
    /** `true` when the chart is drawing an LTTB-reduced copy rather than every point (§7.4). */
    val downsampled: Boolean = false,
)

/**
 * Drives the single-metric detail page.
 *
 * AGENTS.md §7.1: every engine call happens inside `withContext(Dispatchers.Default)`, so the main
 * thread only ever receives a finished state object.
 */
class MetricDetailViewModel(
    private val metricRepository: MetricRepository,
    private val zoneId: ZoneId,
    private val nowMillis: () -> Long = System::currentTimeMillis,
) : ViewModel() {

    private data class Selection(
        val metricId: String? = null,
        val timeRange: TimeRange = TimeRange.LAST_30_DAYS,
    )

    private val selection = MutableStateFlow(Selection())

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<MetricDetailUiState> = selection
        .flatMapLatest { selected ->
            val metricId = selected.metricId
            if (metricId == null) {
                flowOf(MetricDetailUiState(isLoading = false))
            } else {
                observe(metricId, selected.timeRange)
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = MetricDetailUiState(),
        )

    /** Called by the screen once the navigation argument is known; Koin supplies no per-screen args. */
    fun selectMetric(metricId: String) {
        selection.value = selection.value.copy(metricId = metricId)
    }

    fun selectTimeRange(timeRange: TimeRange) {
        selection.value = selection.value.copy(timeRange = timeRange)
    }

    private fun observe(metricId: String, timeRange: TimeRange): Flow<MetricDetailUiState> {
        val now = nowMillis()
        return metricRepository
            .observeObservation(
                metricId = metricId,
                fromEpochMilli = timeRange.startEpochMilli(now, zoneId),
                toEpochMilliExclusive = timeRange.endEpochMilliExclusive(now, zoneId),
            )
            .map { observations ->
                withContext(Dispatchers.Default) { analyse(metricId, timeRange, observations) }
            }
            .catch {
                emit(
                    MetricDetailUiState(
                        isLoading = false,
                        metricId = metricId,
                        timeRange = timeRange,
                    ),
                )
            }
    }

    private suspend fun analyse(
        metricId: String,
        timeRange: TimeRange,
        observations: List<MetricObservation>,
    ): MetricDetailUiState {
        val definition = metricRepository.getDefinition(metricId)
        val ordered = observations.sortedBy { it.timestampEpochMilli }
        if (ordered.isEmpty()) {
            return MetricDetailUiState(
                isLoading = false,
                metricId = metricId,
                definition = definition,
                timeRange = timeRange,
            )
        }

        val originEpochMilli = ordered.first().timestampEpochMilli
        val series = TimeSeries.of(
            seriesId = metricId,
            unit = definition?.unit.orEmpty(),
            points = ordered.map { RawDataPoint(it.timestampEpochMilli, it.value) },
        )

        val downsampled = series.size > LttbDownsampler.DEFAULT_TARGET_POINTS
        val plotted = if (downsampled) LttbDownsampler.downsample(series) else series
        val smoothed = Ewma.computeDays(series, halfLifeDays = EWMA_HALF_LIFE_DAYS)
        val trend = TrendAnalysis.fit(series)
        val anomaly = AnomalyDetection.detect(series)

        return MetricDetailUiState(
            isLoading = false,
            metricId = metricId,
            definition = definition,
            timeRange = timeRange,
            originEpochMilli = originEpochMilli,
            chart = MetricChartData(
                raw = plotted.points.map { ChartPoint(chartX(it.timestampEpochMilli, originEpochMilli), it.value) },
                smoothed = smoothed.map { ChartPoint(chartX(it.timestampEpochMilli, originEpochMilli), it.value) },
                trend = trend.toChartPoints(originEpochMilli),
                anomalies = anomaly.points
                    .filter { it.isAnomaly }
                    .map { ChartPoint(chartX(it.timestampEpochMilli, originEpochMilli), it.value) },
                referenceRange = definition.referenceRange(),
            ),
            statistics = DescriptiveStats.calculate(series),
            trend = trend,
            anomaly = anomaly,
            quality = DataQuality.analyze(series, zoneId),
            sampleCount = series.size,
            downsampled = downsampled,
        )
    }

    private fun LinearTrend.toChartPoints(originEpochMilli: Long): List<ChartPoint> =
        if (fittedStart.isFinite() && fittedEnd.isFinite()) {
            listOf(
                ChartPoint(chartX(startTimestampEpochMilli, originEpochMilli), fittedStart),
                ChartPoint(chartX(endTimestampEpochMilli, originEpochMilli), fittedEnd),
            )
        } else {
            emptyList()
        }

    private fun MetricDefinition?.referenceRange(): ClosedFloatingPointRange<Double>? {
        val low = this?.referenceRangeLow ?: return null
        val high = this.referenceRangeHigh ?: return null
        return if (low.isFinite() && high.isFinite() && low < high) low..high else null
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L

        /** Half-life of the EWMA overlay, in days (engine §6.5). */
        const val EWMA_HALF_LIFE_DAYS = 7.0
    }
}
