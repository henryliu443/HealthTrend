package com.healthtrend.ui.dashboard

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.healthtrend.R
import com.healthtrend.core.domain.model.MetricCategory
import com.healthtrend.core.domain.model.MetricConcern
import com.healthtrend.core.domain.model.MetricDataType
import com.healthtrend.core.domain.model.MetricDefinition
import com.healthtrend.core.domain.model.MetricLatestValue
import com.healthtrend.core.domain.model.MetricObservation
import com.healthtrend.core.domain.model.ObservationSource
import com.healthtrend.core.domain.repository.MetricRepository
import com.healthtrend.demo.DemoDataInstaller
import com.healthtrend.ui.common.uiOrder
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID

/** One metric tile: its definition plus the newest value known for it, if any. */
data class MetricCardUi(
    val definition: MetricDefinition,
    val latest: MetricLatestValue?,
)

/** A category section of the dashboard, in [uiOrder]. */
data class MetricSectionUi(
    val category: MetricCategory,
    val cards: List<MetricCardUi>,
)

/**
 * Screen state for the dashboard.
 *
 * There is deliberately no separate "loading finished" flag beyond [isLoading]: the two flows are
 * combined into a single emission, so the first emission already carries the full picture.
 */
data class DashboardUiState(
    val isLoading: Boolean = true,
    val sections: List<MetricSectionUi> = emptyList(),
    /** `true` when this build can seed review data (debug only — see `DemoDataInstaller`). */
    val canLoadDemoData: Boolean = false,
    @StringRes val messageRes: Int? = null,
) {
    val isEmpty: Boolean get() = !isLoading && sections.isEmpty()
}

/**
 * The dashboard: every known metric grouped by category, each with its latest reading.
 *
 * Both streams are served by the same `(metric_id, timestamp)` index — `observeLatestValues` is a
 * single window-function scan rather than one query per metric (AGENTS.md §4.4).
 */
class DashboardViewModel(
    private val metricRepository: MetricRepository,
    private val demoDataInstaller: DemoDataInstaller?,
    private val nowMillis: () -> Long = System::currentTimeMillis,
) : ViewModel() {

    @StringRes
    private val message = MutableStateFlow<Int?>(null)

    val uiState: StateFlow<DashboardUiState> =
        combine(
            metricRepository.observeDefinitions(),
            metricRepository.observeLatestValues(),
            message,
        ) { definitions, latestValues, messageRes ->
            val latestByMetric = latestValues.associateBy { it.metricId }
            val sections = definitions
                .groupBy { it.category }
                .toList()
                .sortedBy { (category, _) -> category.uiOrder }
                .map { (category, metrics) ->
                    MetricSectionUi(
                        category = category,
                        cards = metrics
                            .sortedBy { it.displayOrder }
                            .map { definition ->
                                MetricCardUi(definition, latestByMetric[definition.id])
                            },
                    )
                }
            DashboardUiState(
                isLoading = false,
                sections = sections,
                canLoadDemoData = demoDataInstaller != null,
                messageRes = messageRes,
            )
        }
            .catch { emit(DashboardUiState(isLoading = false, canLoadDemoData = demoDataInstaller != null)) }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                initialValue = DashboardUiState(canLoadDemoData = demoDataInstaller != null),
            )

    /** Seeds the review data set. Debug-only: [demoDataInstaller] is `null` in release builds. */
    fun loadDemoData() {
        val installer = demoDataInstaller ?: return
        viewModelScope.launch { installer.install() }
    }

    /** Creates a user-defined metric (AGENTS.md calls this out as the `CUSTOM` category). */
    fun addMetric(name: String, unit: String, concern: MetricConcern?) {
        val trimmedName = name.trim()
        if (trimmedName.isEmpty()) return
        viewModelScope.launch {
            metricRepository.upsertDefinition(
                MetricDefinition(
                    id = "custom_${UUID.randomUUID()}",
                    name = trimmedName,
                    category = MetricCategory.CUSTOM,
                    unit = unit.trim(),
                    dataType = MetricDataType.NUMERIC,
                    description = "",
                    expectedFrequency = null,
                    minValue = null,
                    maxValue = null,
                    referenceRangeLow = null,
                    referenceRangeHigh = null,
                    isBuiltIn = false,
                    displayOrder = CUSTOM_DISPLAY_ORDER,
                    concern = concern,
                ),
            )
        }
    }

    /** Records one manual reading for [metricId], stamped with the current instant. */
    fun addObservation(metricId: String, value: Double) {
        if (!value.isFinite()) return
        viewModelScope.launch {
            val definition = metricRepository.getDefinition(metricId) ?: return@launch
            metricRepository.upsertObservation(
                MetricObservation(
                    id = "manual_${UUID.randomUUID()}",
                    metricId = metricId,
                    timestampEpochMilli = nowMillis(),
                    value = value,
                    unit = definition.unit,
                    source = ObservationSource.MANUAL,
                ),
            )
            message.value = R.string.entry_saved
        }
    }

    /** Acknowledges the current one-shot message so it is not shown twice. */
    fun consumeMessage() {
        message.value = null
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
        const val CUSTOM_DISPLAY_ORDER = 1_000
    }
}
