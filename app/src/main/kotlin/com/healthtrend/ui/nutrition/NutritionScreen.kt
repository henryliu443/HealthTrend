package com.healthtrend.ui.nutrition

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
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
import com.healthtrend.core.common.time.TimeKeys
import com.healthtrend.core.domain.model.FoodItem
import com.healthtrend.core.domain.model.FoodNutrientValue
import com.healthtrend.core.domain.model.FoodWithNutrients
import com.healthtrend.core.domain.model.MealLog
import com.healthtrend.core.domain.model.MealType
import com.healthtrend.core.domain.model.NutrientCategory
import com.healthtrend.core.domain.model.NutrientDefinition
import com.healthtrend.core.domain.nutrition.NutrientMath
import com.healthtrend.ui.common.labelRes
import com.healthtrend.ui.components.ChoiceChips
import com.healthtrend.ui.components.SectionCard
import com.healthtrend.ui.components.StatRow
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
        onSelectFood = viewModel::selectFood,
        onClearSelection = viewModel::clearSelectedFood,
        onSelectMealType = viewModel::selectMealType,
        onLogFood = viewModel::logSelectedFood,
        onDeleteMeal = viewModel::deleteMeal,
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
    onSelectFood: (String) -> Unit,
    onClearSelection: () -> Unit,
    onSelectMealType: (MealType) -> Unit,
    onLogFood: (Double) -> Unit,
    onDeleteMeal: (String) -> Unit,
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
                subtitle = stringResource(R.string.dashboard_observation_count, state.foods.size),
            ) {
                if (state.foods.isEmpty()) {
                    Text(
                        text = stringResource(R.string.nutrition_search_empty),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                } else {
                    state.foods.forEach { food ->
                        FoodRow(
                            food = food,
                            isSelected = state.selectedFood?.food?.id == food.id,
                            onClick = { onSelectFood(food.id) },
                        )
                    }
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
                    subtitle = selected.food.name,
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
                            label = nutrient?.name ?: value.nutrientId,
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
                            label = stringResource(
                                R.string.nutrition_totals_rda,
                                Formatters.value(total.nutrient.dailyRecommended ?: Double.NaN),
                                total.nutrient.unit,
                            ),
                            value = Formatters.valueWithUnit(total.amount, total.nutrient.unit),
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
private fun FoodRow(
    food: FoodItem,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
    ) {
        Text(
            text = food.name,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = stringResource(
                R.string.nutrition_reference_line,
                Formatters.value(food.referenceAmount),
                food.referenceUnit,
            ),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                text = row.food?.name ?: row.meal.foodId,
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
                Text(food.food.name, style = MaterialTheme.typography.titleMedium)
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
                    .heightIn(max = 420.dp)
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
                    OutlinedTextField(
                        value = amounts[nutrient.id].orEmpty(),
                        onValueChange = { input ->
                            amounts = amounts + (nutrient.id to input)
                        },
                        label = {
                            Text(
                                stringResource(
                                    R.string.nutrition_nutrient_per_reference,
                                    nutrient.name,
                                    nutrient.unit,
                                ),
                            )
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                    )
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

private fun previewNutrients(): List<NutrientDefinition> = listOf(
    NutrientDefinition("calories", "能量", "kcal", NutrientCategory.MACRO, 2000.0, 10),
    NutrientDefinition("protein", "蛋白质", "g", NutrientCategory.MACRO, 60.0, 20),
    NutrientDefinition("fat", "脂肪", "g", NutrientCategory.MACRO, 60.0, 30),
    NutrientDefinition("carbohydrate", "碳水化合物", "g", NutrientCategory.MACRO, 300.0, 40),
)

private fun previewFood(): FoodWithNutrients = FoodWithNutrients(
    food = FoodItem(
        id = "demo-rice",
        name = "米饭（熟）",
        referenceAmount = 100.0,
        referenceUnit = "g",
        isCustom = true,
    ),
    nutrients = listOf(
        FoodNutrientValue("demo-rice", "calories", 116.0),
        FoodNutrientValue("demo-rice", "protein", 2.6),
        FoodNutrientValue("demo-rice", "carbohydrate", 25.9),
    ),
)

private fun previewNutritionState(zoneId: ZoneId): NutritionUiState {
    val dayStart = TimeKeys.startOfDayUtcMillis(Instant.parse("2026-06-18T06:00:00Z").toEpochMilli(), zoneId)
    val nutrients = previewNutrients()
    val rice = previewFood().food
    val egg = FoodItem(
        id = "demo-egg",
        name = "鸡蛋",
        referenceAmount = 1.0,
        referenceUnit = "piece",
        isCustom = true,
    )
    return NutritionUiState(
        isLoading = false,
        dayStartEpochMilli = dayStart,
        isToday = true,
        query = "",
        foods = listOf(rice, egg),
        selectedFood = previewFood(),
        mealType = MealType.LUNCH,
        nutrients = nutrients,
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
            DayNutrientTotal(nutrients[0], 348.0),
            DayNutrientTotal(nutrients[1], 18.8),
            DayNutrientTotal(nutrients[2], 46.6),
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
            onSelectFood = {},
            onClearSelection = {},
            onSelectMealType = {},
            onLogFood = {},
            onDeleteMeal = {},
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
                foods = emptyList(),
                nutrients = previewNutrients(),
            ),
            zoneId = ZoneId.of("Asia/Shanghai"),
            onBack = {},
            onPreviousDay = {},
            onNextDay = {},
            onToday = {},
            onQueryChange = {},
            onSelectFood = {},
            onClearSelection = {},
            onSelectMealType = {},
            onLogFood = {},
            onDeleteMeal = {},
            onCreateFood = { _, _, _, _ -> },
            onMessageShown = {},
        )
    }
}
