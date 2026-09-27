package com.healthtrend.ui.nutrition

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.healthtrend.R
import com.healthtrend.core.common.time.TimeKeys
import com.healthtrend.core.domain.model.FoodItem
import com.healthtrend.core.domain.model.FoodNutrientValue
import com.healthtrend.core.domain.model.FoodWithNutrients
import com.healthtrend.core.domain.model.MealLog
import com.healthtrend.core.domain.model.MealType
import com.healthtrend.core.domain.model.MetricConcern
import com.healthtrend.core.domain.model.NutrientDefinition
import com.healthtrend.core.domain.nutrition.NutrientCatalog
import com.healthtrend.core.domain.nutrition.NutrientMath
import com.healthtrend.core.domain.nutrition.lookup.FoodNutrientProfile
import com.healthtrend.core.domain.nutrition.lookup.FoodRef
import com.healthtrend.core.domain.nutrition.lookup.FoodSearchResult
import com.healthtrend.core.domain.nutrition.lookup.LookupTier
import com.healthtrend.ui.common.foodDisplayName
import com.healthtrend.ui.common.labelRes
import com.healthtrend.ui.common.needsConfirmation
import com.healthtrend.ui.common.nutrientName
import com.healthtrend.ui.components.ChoiceChips
import com.healthtrend.ui.components.CompactNumberField
import com.healthtrend.ui.components.SectionCard
import com.healthtrend.ui.components.StatRow
import com.healthtrend.ui.components.StatusPill
import com.healthtrend.ui.format.Formatters
import com.healthtrend.ui.theme.HealthTrendTheme
import org.koin.androidx.compose.koinViewModel
import java.time.Instant
import java.time.ZoneId

/**
 * The nutrition ingestion page (AGENTS.md §4.2 / §4.3).
 *
 * Every screen here writes through the repository, which re-projects the affected natural day into
 * `metric_observations`; the totals card reads that projection back, so what the user sees is the
 * same series the analytics engine will later analyse.
 */
@Composable
internal fun NutritionScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    zoneId: ZoneId = ZoneId.systemDefault(),
    viewModel: NutritionViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    NutritionContent(
        state = state,
        zoneId = zoneId,
        onBack = onBack,
        onPreviousDay = viewModel::showPreviousDay,
        onNextDay = viewModel::showNextDay,
        onToday = viewModel::showToday,
        onQueryChange = viewModel::setQuery,
        onSelectResult = viewModel::selectSearchResult,
        onClearSelection = viewModel::clearSelectedFood,
        onSelectMealType = viewModel::selectMealType,
        onLogFood = viewModel::logSelectedFood,
        onDeleteMeal = viewModel::deleteMeal,
        onConfirmProfile = viewModel::confirmPendingProfile,
        onDismissProfile = viewModel::dismissPendingProfile,
        onCreateFood = viewModel::createFood,
        onMessageShown = viewModel::consumeMessage,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NutritionContent(
    state: NutritionUiState,
    zoneId: ZoneId,
    onBack: () -> Unit,
    onPreviousDay: () -> Unit,
    onNextDay: () -> Unit,
    onToday: () -> Unit,
    onQueryChange: (String) -> Unit,
    onSelectResult: (FoodSearchResult) -> Unit,
    onClearSelection: () -> Unit,
    onSelectMealType: (MealType) -> Unit,
    onLogFood: (Double) -> Unit,
    onDeleteMeal: (String) -> Unit,
    onConfirmProfile: (referenceAmount: Double, nutrients: Map<String, Double>) -> Unit,
    onDismissProfile: () -> Unit,
    onCreateFood: (name: String, referenceAmount: Double, referenceUnit: String, nutrients: Map<String, Double>) -> Unit,
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

    var showNewFoodDialog by remember { mutableStateOf(false) }
    var showLogDialog by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.nutrition_title)) },
                navigationIcon = {
                    TextButton(onClick = onBack) { Text(stringResource(R.string.action_back)) }
                },
            )
        },
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
            DayNavigator(
                dayStartEpochMilli = state.dayStartEpochMilli,
                isToday = state.isToday,
                zoneId = zoneId,
                onPreviousDay = onPreviousDay,
                onNextDay = onNextDay,
                onToday = onToday,
            )

            OutlinedTextField(
                value = state.query,
                onValueChange = onQueryChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.nutrition_search_hint)) },
                singleLine = true,
            )

            SectionCard(
                title = stringResource(R.string.nutrition_search_title),
                subtitle = pluralStringResource(
                    R.plurals.dashboard_observation_count,
                    state.searchResults.size,
                    state.searchResults.size,
                ),
            ) {
                if (state.searchResults.isEmpty()) {
                    Text(
                        text = stringResource(R.string.nutrition_search_empty),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                } else {
                    state.searchResults.forEach { result ->
                        SearchResultRow(
                            result = result,
                            isSelected = state.selectedFood?.food?.id == result.foodRef.localId,
                            onClick = { onSelectResult(result) },
                        )
                    }
                }
                if (state.isResolving) {
                    Text(
                        text = stringResource(R.string.nutrition_resolving),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                OutlinedButton(
                    onClick = { showNewFoodDialog = true },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.nutrition_new_food))
                }
            }

            state.selectedFood?.let { selected ->
                SectionCard(
                    title = stringResource(R.string.nutrition_estimate_title),
                    subtitle = foodDisplayName(selected.food),
                ) {
                    Text(
                        text = stringResource(
                            R.string.nutrition_reference_line,
                            Formatters.value(selected.food.referenceAmount),
                            selected.food.referenceUnit,
                        ),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    selected.nutrients.forEach { value ->
                        val nutrient = state.nutrients.firstOrNull { it.id == value.nutrientId }
                        StatRow(
                            label = nutrient?.let { nutrientName(it.id, it.name) } ?: value.nutrientId,
                            value = Formatters.valueWithUnit(
                                value.amountPerReference,
                                nutrient?.unit.orEmpty(),
                            ),
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { showLogDialog = true }) {
                            Text(stringResource(R.string.nutrition_add_meal_title))
                        }
                        OutlinedButton(onClick = onClearSelection) {
                            Text(stringResource(R.string.action_close))
                        }
                    }
                }
            }

            SectionCard(title = stringResource(R.string.nutrition_totals_title)) {
                if (state.totals.isEmpty()) {
                    Text(
                        text = stringResource(R.string.nutrition_totals_empty),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                } else {
                    state.totals.forEach { total ->
                        StatRow(
                            label = nutrientName(total.nutrient.id, total.nutrient.name),
                            value = total.nutrient.dailyRecommended?.let { reference ->
                                stringResource(
                                    R.string.nutrition_totals_progress,
                                    Formatters.value(total.amount),
                                    Formatters.value(reference),
                                    total.nutrient.unit,
                                )
                            } ?: Formatters.valueWithUnit(total.amount, total.nutrient.unit),
                        )
                    }
                    // Some of those figures are ceilings, not quotas, and a list of numbers cannot
                    // say so on its own.
                    val ceilings = state.totals
                        .filter { NutrientCatalog.concernById[it.nutrient.id] == MetricConcern.HIGHER_VALUES }
                        .map { nutrientName(it.nutrient.id, it.nutrient.name) }
                    if (ceilings.isNotEmpty()) {
                        Text(
                            text = stringResource(
                                R.string.nutrition_totals_upper_limits,
                                ceilings.joinToString(", "),
                            ),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            SectionCard(title = stringResource(R.string.nutrition_meals_title)) {
                if (state.meals.isEmpty()) {
                    Text(
                        text = stringResource(R.string.nutrition_meals_empty),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                } else {
                    state.meals.forEach { row ->
                        MealRowItem(
                            row = row,
                            onDelete = { onDeleteMeal(row.meal.id) },
                        )
                    }
                }
            }
        }
    }

    if (showLogDialog && state.selectedFood != null) {
        LogMealDialog(
            food = state.selectedFood,
            nutrients = state.nutrients,
            mealType = state.mealType,
            onSelectMealType = onSelectMealType,
            onDismiss = { showLogDialog = false },
            onConfirm = { amount ->
                onLogFood(amount)
                showLogDialog = false
            },
        )
    }

    if (showNewFoodDialog) {
        NewFoodDialog(
            nutrients = state.nutrients,
            onDismiss = { showNewFoodDialog = false },
            onConfirm = { name, amount, unit, values ->
                onCreateFood(name, amount, unit, values)
                showNewFoodDialog = false
            },
        )
    }

    state.pendingProfile?.let { profile ->
        ConfirmProfileDialog(
            profile = profile,
            nutrients = state.profileNutrients,
            onDismiss = onDismissProfile,
            onConfirm = onConfirmProfile,
        )
    }
}

/**
 * The confirmation step of AGENTS.md §5.3: a profile fetched from outside is shown — with its source
 * named and every figure editable — before anything is written.
 *
 * The edits live here rather than in the ViewModel so that twenty-two text fields do not become
 * twenty-two pieces of screen state. Only the parsed numbers leave the dialog.
 */
@Composable
private fun ConfirmProfileDialog(
    profile: FoodNutrientProfile,
    nutrients: List<NutrientDefinition>,
    onDismiss: () -> Unit,
    onConfirm: (referenceAmount: Double, nutrients: Map<String, Double>) -> Unit,
) {
    val key = profile.foodRef.raw
    var referenceText by remember(key) { mutableStateOf(Formatters.value(profile.referenceAmount)) }
    var amounts by remember(key) {
        mutableStateOf(profile.nutrients.mapValues { (_, value) -> Formatters.value(value) })
    }
    val byId = remember(nutrients) { nutrients.associateBy { it.id } }
    // Listed in the catalogue's own order, and only for nutrients the source actually reports: an
    // empty field would be indistinguishable from a measured zero.
    val listed = remember(profile, nutrients) {
        profile.nutrients.keys.sortedBy { byId[it]?.displayOrder ?: Int.MAX_VALUE }
    }

    val parsedReference = referenceText.trim().replace(',', '.').toDoubleOrNull()
    val parsedAmounts = amounts.mapValues { (_, raw) ->
        raw.trim().replace(',', '.').toDoubleOrNull() ?: 0.0
    }
    val isValid = parsedReference != null && parsedReference > 0.0 &&
        parsedAmounts.values.any { it > 0.0 }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.nutrition_confirm_title)) },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 320.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(foodDisplayName(profile), style = MaterialTheme.typography.titleMedium)
                profile.sourceName?.let { source ->
                    Text(
                        text = stringResource(
                            R.string.nutrition_confirm_source,
                            source,
                            profile.sourceRecordId.orEmpty(),
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                // The basis is quoted inline with its field: it is the one figure that changes what
                // every other row means, so it stays visible at the top instead of scrolling away.
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.nutrition_reference_amount),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    CompactNumberField(
                        value = referenceText,
                        onValueChange = { referenceText = it },
                        modifier = Modifier.width(72.dp),
                        isError = referenceText.isNotBlank() &&
                            (parsedReference == null || parsedReference <= 0.0),
                    )
                    Text(
                        text = profile.referenceUnit,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                if (referenceText.isNotBlank() &&
                    (parsedReference == null || parsedReference <= 0.0)
                ) {
                    Text(
                        text = stringResource(R.string.nutrition_invalid_amount),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                listed.forEach { nutrientId ->
                    val nutrient = byId[nutrientId]
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = nutrient?.let { nutrientName(it.id, it.name) } ?: nutrientId,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f),
                        )
                        CompactNumberField(
                            value = amounts[nutrientId].orEmpty(),
                            onValueChange = { input -> amounts = amounts + (nutrientId to input) },
                            modifier = Modifier.width(72.dp),
                        )
                        // A fixed gutter keeps every field on the same axis regardless of how long
                        // the unit string is (g, mg, μg).
                        Text(
                            text = nutrient?.unit.orEmpty(),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.width(24.dp),
                        )
                    }
                }
                Text(
                    text = stringResource(R.string.nutrition_confirm_note),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = isValid,
                onClick = { onConfirm(parsedReference ?: 0.0, parsedAmounts) },
            ) {
                Text(stringResource(R.string.nutrition_confirm_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

@Composable
private fun DayNavigator(
    dayStartEpochMilli: Long,
    isToday: Boolean,
    zoneId: ZoneId,
    onPreviousDay: () -> Unit,
    onNextDay: () -> Unit,
    onToday: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextButton(onClick = onPreviousDay) { Text(stringResource(R.string.nutrition_day_prev)) }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = Formatters.day(dayStartEpochMilli, zoneId),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
            )
            if (isToday) {
                Text(
                    text = stringResource(R.string.nutrition_day_today),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            } else {
                TextButton(onClick = onToday) { Text(stringResource(R.string.nutrition_day_today)) }
            }
        }
        // Looking forward past today would only ever produce empty days.
        TextButton(onClick = onNextDay, enabled = !isToday) {
            Text(stringResource(R.string.nutrition_day_next))
        }
    }
}

@Composable
private fun SearchResultRow(
    result: FoodSearchResult,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = foodDisplayName(result),
                style = MaterialTheme.typography.bodyLarge,
                // Selection is emphasis, not hue: in a colourless palette there is no accent left to
                // spend on it, and lightening the selected row would read backwards. Full-contrast
                // ink against a muted label, plus the weight change, is the whole signal.
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                color = if (isSelected) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.weight(1f),
            )
            SourceBadge(result.tier)
        }
        Text(
            text = stringResource(
                R.string.nutrition_reference_line,
                Formatters.value(result.defaultReferenceAmount),
                result.defaultReferenceUnit,
            ),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * Where a search hit came from (AGENTS.md §5.2).
 *
 * A hit that needs confirming is the filled variant, because that is the one the user has not seen
 * before and is about to be asked about; their own foods are the quiet default.
 */
@Composable
private fun SourceBadge(tier: LookupTier) {
    StatusPill(filled = tier.needsConfirmation) {
        Text(
            text = stringResource(tier.labelRes),
            style = MaterialTheme.typography.labelSmall,
        )
    }
}

@Composable
private fun MealRowItem(
    row: MealRow,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = row.food?.let { foodDisplayName(it) } ?: row.meal.foodId,
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                text = stringResource(row.meal.mealType.labelRes) + " · " +
                    Formatters.value(row.meal.actualAmount) + " " + row.meal.actualUnit,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        TextButton(onClick = onDelete) {
            Text(stringResource(R.string.nutrition_delete_meal))
        }
    }
}

@Composable
private fun LogMealDialog(
    food: FoodWithNutrients,
    nutrients: List<NutrientDefinition>,
    mealType: MealType,
    onSelectMealType: (MealType) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: (Double) -> Unit,
) {
    // Default to the food's own reference amount, so the common case is one tap.
    var text by remember(food.food.id) { mutableStateOf(Formatters.value(food.food.referenceAmount)) }
    val amount = text.trim().replace(',', '.').toDoubleOrNull()
    val isValid = amount != null && amount > 0.0

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.nutrition_add_meal_title)) },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(foodDisplayName(food.food), style = MaterialTheme.typography.titleMedium)
                Text(
                    text = stringResource(
                        R.string.nutrition_reference_line,
                        Formatters.value(food.food.referenceAmount),
                        food.food.referenceUnit,
                    ),
                    style = MaterialTheme.typography.labelMedium,
                )
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = {
                        Text(
                            stringResource(R.string.nutrition_actual_amount) +
                                " (" + food.food.referenceUnit + ")",
                        )
                    },
                    isError = text.isNotBlank() && !isValid,
                    supportingText = {
                        if (text.isNotBlank() && !isValid) {
                            Text(stringResource(R.string.nutrition_invalid_amount))
                        }
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                )
                Text(
                    text = stringResource(R.string.nutrition_meal_type),
                    style = MaterialTheme.typography.labelMedium,
                )
                ChoiceChips(
                    options = MealType.entries,
                    selected = mealType,
                    label = { stringResource(it.labelRes) },
                    onSelect = onSelectMealType,
                )
                Text(
                    text = stringResource(R.string.nutrition_estimate_title),
                    style = MaterialTheme.typography.labelMedium,
                )
                val scaled = amount?.takeIf { it > 0.0 }
                food.nutrients.forEach { value ->
                    val nutrient = nutrients.firstOrNull { it.id == value.nutrientId }
                    StatRow(
                        label = nutrient?.name ?: value.nutrientId,
                        value = Formatters.valueWithUnit(
                            scaled?.let {
                                // AGENTS.md §1.1: ratio against this food's own reference amount.
                                NutrientMath.scale(
                                    amountPerReference = value.amountPerReference,
                                    actualAmount = it,
                                    referenceAmount = food.food.referenceAmount,
                                )
                            } ?: Double.NaN,
                            nutrient?.unit.orEmpty(),
                        ),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(enabled = isValid, onClick = { amount?.let(onConfirm) }) {
                Text(stringResource(R.string.action_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

@Composable
private fun NewFoodDialog(
    nutrients: List<NutrientDefinition>,
    onDismiss: () -> Unit,
    onConfirm: (name: String, referenceAmount: Double, referenceUnit: String, nutrients: Map<String, Double>) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var referenceAmount by remember { mutableStateOf("100") }
    var referenceUnit by remember { mutableStateOf("g") }
    var amounts by remember { mutableStateOf(emptyMap<String, String>()) }

    val parsedReferenceAmount = referenceAmount.trim().replace(',', '.').toDoubleOrNull()
    val isValid = name.isNotBlank() && parsedReferenceAmount != null && parsedReferenceAmount > 0.0

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.nutrition_new_food)) },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 320.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.nutrition_food_name)) },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = referenceAmount,
                    onValueChange = { referenceAmount = it },
                    label = { Text(stringResource(R.string.nutrition_reference_amount)) },
                    isError = referenceAmount.isNotBlank() && parsedReferenceAmount == null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                )
                OutlinedTextField(
                    value = referenceUnit,
                    onValueChange = { referenceUnit = it },
                    label = { Text(stringResource(R.string.nutrition_reference_unit)) },
                    supportingText = { Text(stringResource(R.string.nutrition_reference_unit_hint)) },
                    singleLine = true,
                )
                nutrients.forEach { nutrient ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = nutrientName(nutrient.id, nutrient.name),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f),
                        )
                        CompactNumberField(
                            value = amounts[nutrient.id].orEmpty(),
                            onValueChange = { input -> amounts = amounts + (nutrient.id to input) },
                            modifier = Modifier.width(72.dp),
                        )
                        Text(
                            text = nutrient.unit,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.width(24.dp),
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = isValid,
                onClick = {
                    onConfirm(
                        name,
                        parsedReferenceAmount ?: 0.0,
                        referenceUnit,
                        amounts.mapValues { (_, raw) -> raw.trim().replace(',', '.').toDoubleOrNull() ?: 0.0 },
                    )
                },
            ) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

// --------------------------------------------------------------------------- previews

private fun previewSearchResults(): List<FoodSearchResult> = listOf(
    // A food the demo diary already adopted: still "mine", but an untouched copy, so it shows under
    // its curated name — in Chinese, when the preview is rendered in Chinese.
    FoodSearchResult(
        foodRef = FoodRef.of("saved", "egg_whole_raw"),
        tier = LookupTier.PERSONAL,
        name = "Egg (1 large, about 50 g)",
        brand = null,
        defaultReferenceAmount = 50.0,
        defaultReferenceUnit = "piece",
        isCustom = false,
    ),
    FoodSearchResult(
        foodRef = FoodRef.of("bundled", "rice_cooked"),
        tier = LookupTier.BUNDLED,
        name = "Cooked white rice",
        brand = null,
        defaultReferenceAmount = 100.0,
        defaultReferenceUnit = "g",
    ),
    FoodSearchResult(
        foodRef = FoodRef.of("bundled", "broccoli_raw"),
        tier = LookupTier.BUNDLED,
        name = "Broccoli (raw)",
        brand = null,
        defaultReferenceAmount = 100.0,
        defaultReferenceUnit = "g",
    ),
)

private fun previewFood(): FoodWithNutrients = FoodWithNutrients(
    food = FoodItem(
        // A real lexicon id, so the preview exercises the localised-name path rather than falling
        // back to the stored English label.
        id = "rice_cooked",
        name = "Cooked white rice",
        referenceAmount = 100.0,
        referenceUnit = "g",
        isCustom = false,
    ),
    nutrients = listOf(
        FoodNutrientValue("rice_cooked", "calories", 130.0),
        FoodNutrientValue("rice_cooked", "protein", 2.69),
        FoodNutrientValue("rice_cooked", "carbohydrates", 28.17),
        FoodNutrientValue("rice_cooked", "fat", 0.28),
    ),
)

/** A profile in exactly the shape the bundled lexicon produces, for the confirmation sheet. */
private fun previewProfile(): FoodNutrientProfile = FoodNutrientProfile(
    foodRef = FoodRef.of("bundled", "egg_whole_raw"),
    tier = LookupTier.BUNDLED,
    foodName = "Egg (1 large, about 50 g)",
    brand = null,
    referenceAmount = 50.0,
    referenceUnit = "piece",
    nutrients = mapOf(
        "calories" to 71.5,
        "protein" to 6.28,
        "fat" to 4.76,
        "carbohydrates" to 0.36,
    ),
    sourceName = "USDA FoodData Central · SR Legacy",
    sourceRecordId = "FDC 171287",
)

private fun previewNutritionState(zoneId: ZoneId): NutritionUiState {
    val dayStart = TimeKeys.startOfDayUtcMillis(Instant.parse("2026-06-18T06:00:00Z").toEpochMilli(), zoneId)
    val rice = previewFood().food
    val egg = FoodItem(
        id = "egg_whole_raw",
        name = "Egg",
        referenceAmount = 1.0,
        referenceUnit = "piece",
        isCustom = false,
    )
    return NutritionUiState(
        isLoading = false,
        dayStartEpochMilli = dayStart,
        isToday = true,
        query = "rice",
        searchResults = previewSearchResults(),
        selectedFood = previewFood(),
        mealType = MealType.LUNCH,
        nutrients = NutrientCatalog.ALL,
        profileNutrients = NutrientCatalog.ALL,
        meals = listOf(
            MealRow(
                meal = MealLog(
                    id = "m1",
                    foodId = rice.id,
                    timestampEpochMilli = dayStart + 12 * 3_600_000L,
                    actualAmount = 180.0,
                    actualUnit = "g",
                    mealType = MealType.LUNCH,
                ),
                food = rice,
            ),
            MealRow(
                meal = MealLog(
                    id = "m2",
                    foodId = egg.id,
                    timestampEpochMilli = dayStart + 8 * 3_600_000L,
                    actualAmount = 2.0,
                    actualUnit = "piece",
                    mealType = MealType.BREAKFAST,
                ),
                food = egg,
            ),
        ),
        totals = listOf(
            DayNutrientTotal(NutrientCatalog.byId.getValue("calories"), 512.0),
            DayNutrientTotal(NutrientCatalog.byId.getValue("protein"), 18.8),
            DayNutrientTotal(NutrientCatalog.byId.getValue("fat"), 12.4),
            DayNutrientTotal(NutrientCatalog.byId.getValue("carbohydrates"), 46.6),
            DayNutrientTotal(NutrientCatalog.byId.getValue("sodium"), 1480.0),
        ),
        messageRes = null,
    )
}

@Preview(name = "Nutrition · light", showBackground = true, heightDp = 1500)
@Composable
private fun NutritionPreview() {
    val zoneId = ZoneId.of("Asia/Shanghai")
    val state = remember { previewNutritionState(zoneId) }
    HealthTrendTheme(darkTheme = false) {
        NutritionContent(
            state = state,
            zoneId = zoneId,
            onBack = {},
            onPreviousDay = {},
            onNextDay = {},
            onToday = {},
            onQueryChange = {},
            onSelectResult = {},
            onClearSelection = {},
            onSelectMealType = {},
            onLogFood = {},
            onDeleteMeal = {},
            onConfirmProfile = { _, _ -> },
            onDismissProfile = {},
            onCreateFood = { _, _, _, _ -> },
            onMessageShown = {},
        )
    }
}

/** The AGENTS.md §5.3 confirmation sheet — the new step in this phase. */
@Preview(name = "Nutrition · confirm lookup", showBackground = true, heightDp = 900)
@Composable
private fun NutritionConfirmPreview() {
    val zoneId = ZoneId.of("Asia/Shanghai")
    val base = remember { previewNutritionState(zoneId) }
    HealthTrendTheme(darkTheme = false) {
        NutritionContent(
            state = base.copy(pendingProfile = previewProfile()),
            zoneId = zoneId,
            onBack = {},
            onPreviousDay = {},
            onNextDay = {},
            onToday = {},
            onQueryChange = {},
            onSelectResult = {},
            onClearSelection = {},
            onSelectMealType = {},
            onLogFood = {},
            onDeleteMeal = {},
            onConfirmProfile = { _, _ -> },
            onDismissProfile = {},
            onCreateFood = { _, _, _, _ -> },
            onMessageShown = {},
        )
    }
}

/**
 * The same diary in Chinese. Food names come from the bundled lexicon's string resources, so a
 * Chinese reader sees 米饭（熟） and 鸡蛋（1 个，约 50 g） rather than the English labels the rows are
 * stored under.
 */
@Preview(name = "Nutrition · 中文", showBackground = true, heightDp = 1500, locale = "zh-rCN")
@Composable
private fun NutritionChinesePreview() {
    val zoneId = ZoneId.of("Asia/Shanghai")
    val state = remember { previewNutritionState(zoneId) }
    HealthTrendTheme(darkTheme = false) {
        NutritionContent(
            state = state,
            zoneId = zoneId,
            onBack = {},
            onPreviousDay = {},
            onNextDay = {},
            onToday = {},
            onQueryChange = {},
            onSelectResult = {},
            onClearSelection = {},
            onSelectMealType = {},
            onLogFood = {},
            onDeleteMeal = {},
            onConfirmProfile = { _, _ -> },
            onDismissProfile = {},
            onCreateFood = { _, _, _, _ -> },
            onMessageShown = {},
        )
    }
}

@Preview(name = "Nutrition · empty day", showBackground = true)
@Composable
private fun NutritionEmptyPreview() {
    HealthTrendTheme(darkTheme = true) {
        NutritionContent(
            state = NutritionUiState(
                isLoading = false,
                dayStartEpochMilli = Instant.parse("2026-06-18T00:00:00Z").toEpochMilli(),
                isToday = false,
                nutrients = NutrientCatalog.ALL,
                profileNutrients = NutrientCatalog.ALL,
            ),
            zoneId = ZoneId.of("Asia/Shanghai"),
            onBack = {},
            onPreviousDay = {},
            onNextDay = {},
            onToday = {},
            onQueryChange = {},
            onSelectResult = {},
            onClearSelection = {},
            onSelectMealType = {},
            onLogFood = {},
            onDeleteMeal = {},
            onConfirmProfile = { _, _ -> },
            onDismissProfile = {},
            onCreateFood = { _, _, _, _ -> },
            onMessageShown = {},
        )
    }
}
