package com.healthtrend.ui.compare

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.healthtrend.core.analytics.model.RawDataPoint
import com.healthtrend.core.analytics.model.TimeSeries
import com.healthtrend.core.analytics.normalize.NormalizationMode
import com.healthtrend.core.analytics.normalize.SeriesNormalizer
import com.healthtrend.core.domain.model.MetricDefinition
import com.healthtrend.core.domain.model.MetricObservation
import com.healthtrend.core.domain.repository.MetricRepository
import com.healthtrend.ui.chart.ChartPoint
import com.healthtrend.ui.chart.CompareSeries
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
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.ZoneId

/**
 * Screen state for the multi-metric comparison (AGENTS.md §7.3).
 *
 * [series] holds **normalised** copies only; the stored observations are never modified
 * (AGENTS.md §10.4). A metric whose values cannot be normalised in the current [mode] is reported
 * in [unavailableMetricIds] rather than being silently dropped, because a missing line otherwise
 * reads as "no data". Ids, not names: the label has to be resolved in the current language, which
 * only the UI layer knows.
 */
data class CompareUiState(
    val isLoading: Boolean = true,
    val timeRange: TimeRange = TimeRange.LAST_90_DAYS,
    val mode: NormalizationMode = NormalizationMode.Z_SCORE,
    val availableMetrics: List<MetricDefinition> = emptyList(),
    val selectedMetricIds: Set<String> = emptySet(),
    val series: List<CompareSeries> = emptyList(),
    val unavailableMetricIds: List<String> = emptyList(),
    val originEpochMilli: Long = 0L,
) {
    /** The chart needs at least two lines to be a comparison rather than a duplicate detail view. */
    val canRender: Boolean get() = series.size >= 2
}

/**
 * Overlays several metrics on one shared axis by normalising each into its own unit-free space.
 *
 * All series are read in a single range query and grouped in memory, so selecting a tenth metric
 * costs no additional database round trip (AGENTS.md §4.4).
 */
class CompareViewModel(
    private val metricRepository: MetricRepository,
    private val zoneId: ZoneId,
    private val nowMillis: () -> Long = System::currentTimeMillis,
) : ViewModel() {

    private data class Controls(
        val timeRange: TimeRange = TimeRange.LAST_90_DAYS,
        val mode: NormalizationMode = NormalizationMode.Z_SCORE,
        val selected: Set<String> = emptySet(),
    )

    private val controls = MutableStateFlow(Controls())

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<CompareUiState> = controls
        .flatMapLatest { observe(it) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = CompareUiState(),
        )

    init {
        // Open on a meaningful comparison instead of an empty chart.
        viewModelScope.launch {
            val firstTwo = metricRepository.observeDefinitions().first().take(2).map { it.id }
            if (firstTwo.isNotEmpty()) {
                controls.update { if (it.selected.isEmpty()) it.copy(selected = firstTwo.toSet()) else it }
            }
        }
    }

    fun toggleMetric(metricId: String) {
        controls.update { current ->
            val selected =
                if (metricId in current.selected) current.selected - metricId
                else current.selected + metricId
            current.copy(selected = selected)
        }
    }

    fun selectMode(mode: NormalizationMode) {
        controls.update { it.copy(mode = mode) }
    }

    fun selectTimeRange(timeRange: TimeRange) {
        controls.update { it.copy(timeRange = timeRange) }
    }

    private fun observe(controls: Controls): Flow<CompareUiState> {
        val now = nowMillis()
        return combine(
            metricRepository.observeDefinitions(),
            metricRepository.observeObservations(
                fromEpochMilli = controls.timeRange.startEpochMilli(now, zoneId),
                toEpochMilliExclusive = controls.timeRange.endEpochMilliExclusive(now, zoneId),
            ),
        ) { definitions, observations -> definitions to observations }
            .map { (definitions, observations) ->
                withContext(Dispatchers.Default) { normalise(controls, definitions, observations) }
            }
            .catch { emit(CompareUiState(isLoading = false, timeRange = controls.timeRange, mode = controls.mode)) }
    }

    private fun normalise(
        controls: Controls,
        definitions: List<MetricDefinition>,
        observations: List<MetricObservation>,
    ): CompareUiState {
        val byMetric = observations.groupBy { it.metricId }
        val origin = observations.minOfOrNull { it.timestampEpochMilli } ?: nowMillis()
        val series = ArrayList<CompareSeries>()
        val unavailable = ArrayList<String>()

        definitions.filter { it.id in controls.selected }.forEach { definition ->
            val points = byMetric[definition.id].orEmpty().sortedBy { it.timestampEpochMilli }
            val normalized = points.takeIf { it.isNotEmpty() }?.let { nonEmpty ->
                SeriesNormalizer.normalize(
                    series = TimeSeries.of(
                        seriesId = definition.id,
                        unit = definition.unit,
                        points = nonEmpty.map { RawDataPoint(it.timestampEpochMilli, it.value) },
                    ),
                    mode = controls.mode,
                )
            }
            if (normalized == null || !normalized.available) {
                // Only the id travels: the label is resolved where the locale is known.
                unavailable += definition.id
                return@forEach
            }
            series += CompareSeries(
                metricId = definition.id,
                name = definition.name,
                unit = definition.unit,
                points = normalized.series.points.map {
                    ChartPoint(chartX(it.timestampEpochMilli, origin), it.value)
                },
            )
        }

        return CompareUiState(
            isLoading = false,
            timeRange = controls.timeRange,
            mode = controls.mode,
            availableMetrics = definitions,
            selectedMetricIds = controls.selected,
            series = series,
            unavailableMetricIds = unavailable,
            originEpochMilli = origin,
        )
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
