package com.healthtrend.ui.dashboard

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.healthtrend.R
import com.healthtrend.core.domain.model.MetricCategory
import com.healthtrend.core.domain.model.MetricConcern
import com.healthtrend.core.domain.model.MetricDataType
import com.healthtrend.core.domain.model.MetricDefinition
import com.healthtrend.core.domain.model.MetricLatestValue
import com.healthtrend.ui.common.labelRes
import com.healthtrend.ui.common.metricDisplayName
import com.healthtrend.ui.components.ChoiceChips
import com.healthtrend.ui.components.SectionCard
import com.healthtrend.ui.format.Formatters
import com.healthtrend.ui.theme.HealthTrendTheme
import org.koin.androidx.compose.koinViewModel
import java.time.Instant
import java.time.ZoneId

/** The app's landing screen: every metric, grouped by category, with its latest reading. */
@Composable
internal fun DashboardScreen(
    onOpenMetric: (String) -> Unit,
    onOpenCompare: () -> Unit,
    onOpenNutrition: () -> Unit,
    modifier: Modifier = Modifier,
    zoneId: ZoneId = ZoneId.systemDefault(),
    viewModel: DashboardViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    DashboardContent(
        state = state,
        zoneId = zoneId,
        onOpenMetric = onOpenMetric,
        onOpenCompare = onOpenCompare,
        onOpenNutrition = onOpenNutrition,
        onLoadDemoData = viewModel::loadDemoData,
        onAddMetric = viewModel::addMetric,
        onAddObservation = viewModel::addObservation,
        onMessageShown = viewModel::consumeMessage,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DashboardContent(
    state: DashboardUiState,
    zoneId: ZoneId,
    onOpenMetric: (String) -> Unit,
    onOpenCompare: () -> Unit,
    onOpenNutrition: () -> Unit,
    onLoadDemoData: () -> Unit,
    onAddMetric: (name: String, unit: String, concern: MetricConcern?) -> Unit,
    onAddObservation: (metricId: String, value: Double) -> Unit,
    onMessageShown: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val messageText = state.messageRes?.let { stringResource(it) }
    LaunchedEffect(messageText) {
        if (messageText != null) {
            snackbarHostState.showSnackbar(messageText)
            onMessageShown()
        }
    }

    var showAddMetricDialog by remember { mutableStateOf(false) }
    var entryTarget by remember { mutableStateOf<MetricDefinition?>(null) }

    Scaffold(
        modifier = modifier,
        topBar = { TopAppBar(title = { Text(stringResource(R.string.dashboard_title)) }) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { insets ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(insets)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.dashboard_subtitle),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onOpenCompare) {
                    Text(stringResource(R.string.dashboard_action_compare))
                }
                OutlinedButton(onClick = onOpenNutrition) {
                    Text(stringResource(R.string.dashboard_action_nutrition))
                }
            }

            if (state.isEmpty) {
                SectionCard(title = stringResource(R.string.dashboard_empty_title)) {
                    Text(
                        text = stringResource(R.string.dashboard_empty_body),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    if (state.canLoadDemoData) {
                        Button(onClick = onLoadDemoData) {
                            Text(stringResource(R.string.dashboard_action_seed_demo))
                        }
                    }
                }
            }

            state.sections.forEach { section ->
                SectionCard(title = stringResource(section.category.labelRes)) {
                    section.cards.forEach { card ->
                        MetricCardRow(
                            card = card,
                            zoneId = zoneId,
                            onOpen = { onOpenMetric(card.definition.id) },
                            onAddObservation = { entryTarget = card.definition },
                        )
                    }
                }
            }

            OutlinedButton(
                onClick = { showAddMetricDialog = true },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.dashboard_add_metric))
            }
        }
    }

    if (showAddMetricDialog) {
        NewMetricDialog(
            onDismiss = { showAddMetricDialog = false },
            onConfirm = { name, unit, concern ->
                onAddMetric(name, unit, concern)
                showAddMetricDialog = false
            },
        )
    }

    entryTarget?.let { definition ->
        AddObservationDialog(
            definition = definition,
            onDismiss = { entryTarget = null },
            onConfirm = { value ->
                onAddObservation(definition.id, value)
                entryTarget = null
            },
        )
    }
}

@Composable
private fun MetricCardRow(
    card: MetricCardUi,
    zoneId: ZoneId,
    onOpen: () -> Unit,
    onAddObservation: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = metricDisplayName(card.definition),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
            )
            val detail = card.latest?.let { latest ->
                val day = Formatters.day(latest.timestampEpochMilli, zoneId)
                stringResource(R.string.dashboard_latest_on, day) + " · " +
                    stringResource(R.string.dashboard_observation_count, latest.sampleCount)
            }
            if (detail != null) {
                Text(
                    text = detail,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Text(
            text = card.latest?.let {
                Formatters.valueWithUnit(it.value, card.definition.unit)
            } ?: stringResource(R.string.label_undefined),
            style = MaterialTheme.typography.titleMedium,
        )
        TextButton(onClick = onAddObservation) {
            Text(stringResource(R.string.action_add))
        }
    }
}

@Composable
private fun NewMetricDialog(
    onDismiss: () -> Unit,
    onConfirm: (name: String, unit: String, concern: MetricConcern?) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var unit by remember { mutableStateOf("") }
    var concern by remember { mutableStateOf<MetricConcern?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.dashboard_new_metric_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.dashboard_metric_name)) },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = unit,
                    onValueChange = { unit = it },
                    label = { Text(stringResource(R.string.dashboard_metric_unit)) },
                    singleLine = true,
                )
                Text(
                    text = stringResource(R.string.dashboard_metric_concern),
                    style = MaterialTheme.typography.labelMedium,
                )
                ChoiceChips(
                    options = CONCERN_OPTIONS,
                    selected = concern,
                    label = { option ->
                        option?.let { stringResource(it.labelRes) }
                            ?: stringResource(R.string.concern_not_stated)
                    },
                    onSelect = { concern = it },
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank(),
                onClick = { onConfirm(name, unit, concern) },
            ) {
                Text(stringResource(R.string.action_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

/** `null` first, so "not stated" is the default and reads as the neutral option it is. */
private val CONCERN_OPTIONS: List<MetricConcern?> = listOf(
    null,
    MetricConcern.HIGHER_VALUES,
    MetricConcern.LOWER_VALUES,
    MetricConcern.BOTH_ENDS,
)

@Composable
private fun AddObservationDialog(
    definition: MetricDefinition,
    onDismiss: () -> Unit,
    onConfirm: (value: Double) -> Unit,
) {
    var text by remember { mutableStateOf("") }
    val parsed = text.trim().replace(',', '.').toDoubleOrNull()
    val name = metricDisplayName(definition)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.entry_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = if (definition.unit.isBlank()) name else "$name (${definition.unit})",
                    style = MaterialTheme.typography.bodyMedium,
                )
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text(stringResource(R.string.entry_value)) },
                    isError = text.isNotBlank() && parsed == null,
                    supportingText = {
                        if (text.isNotBlank() && parsed == null) {
                            Text(stringResource(R.string.entry_invalid_value))
                        }
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                )
                Text(
                    text = stringResource(R.string.entry_use_now),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = parsed != null,
                onClick = { parsed?.let(onConfirm) },
            ) {
                Text(stringResource(R.string.action_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

// --------------------------------------------------------------------------- previews

private fun previewMetric(
    id: String,
    name: String,
    category: MetricCategory,
    unit: String,
    order: Int,
): MetricDefinition = MetricDefinition(
    id = id,
    name = name,
    category = category,
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

private fun previewLatest(metricId: String, value: Double, count: Int): MetricLatestValue =
    MetricLatestValue(
        metricId = metricId,
        timestampEpochMilli = Instant.parse("2026-06-18T04:00:00Z").toEpochMilli(),
        value = value,
        sampleCount = count,
    )

private fun previewDashboardState(): DashboardUiState {
    val weight = previewMetric("body_weight", "Body weight", MetricCategory.BODY, "kg", 10)
    val heartRate = previewMetric("resting_heart_rate", "Resting heart rate", MetricCategory.BODY, "bpm", 30)
    val sleep = previewMetric("sleep_duration", "Sleep duration", MetricCategory.LIFESTYLE, "h", 80)
    val steps = previewMetric("daily_steps", "Daily steps", MetricCategory.ACTIVITY, "steps", 90)
    val protein = previewMetric("nutrient_protein", "Protein", MetricCategory.NUTRITION, "g", 200)
    return DashboardUiState(
        isLoading = false,
        canLoadDemoData = true,
        sections = listOf(
            MetricSectionUi(
                category = MetricCategory.BODY,
                cards = listOf(
                    MetricCardUi(weight, previewLatest(weight.id, 68.4, 90)),
                    MetricCardUi(heartRate, previewLatest(heartRate.id, 61.0, 90)),
                ),
            ),
            MetricSectionUi(
                category = MetricCategory.NUTRITION,
                cards = listOf(MetricCardUi(protein, previewLatest(protein.id, 96.5, 4))),
            ),
            MetricSectionUi(
                category = MetricCategory.ACTIVITY,
                cards = listOf(MetricCardUi(steps, previewLatest(steps.id, 9210.0, 90))),
            ),
            MetricSectionUi(
                category = MetricCategory.LIFESTYLE,
                cards = listOf(MetricCardUi(sleep, previewLatest(sleep.id, 7.4, 90))),
            ),
        ),
    )
}

@Preview(name = "Dashboard · light", showBackground = true)
@Composable
private fun DashboardPreview() {
    HealthTrendTheme(darkTheme = false) {
        DashboardContent(
            state = previewDashboardState(),
            zoneId = ZoneId.of("Asia/Shanghai"),
            onOpenMetric = {},
            onOpenCompare = {},
            onOpenNutrition = {},
            onLoadDemoData = {},
            onAddMetric = { _, _, _ -> },
            onAddObservation = { _, _ -> },
            onMessageShown = {},
        )
    }
}

@Preview(name = "Dashboard · empty", showBackground = true)
@Composable
private fun DashboardEmptyPreview() {
    HealthTrendTheme(darkTheme = false) {
        DashboardContent(
            state = DashboardUiState(isLoading = false, canLoadDemoData = true),
            zoneId = ZoneId.of("Asia/Shanghai"),
            onOpenMetric = {},
            onOpenCompare = {},
            onOpenNutrition = {},
            onLoadDemoData = {},
            onAddMetric = { _, _, _ -> },
            onAddObservation = { _, _ -> },
            onMessageShown = {},
        )
    }
}
