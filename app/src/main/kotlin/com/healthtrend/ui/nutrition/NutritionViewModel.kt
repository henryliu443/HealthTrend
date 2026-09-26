package com.healthtrend.ui.nutrition

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.healthtrend.R
import com.healthtrend.core.common.time.DayRange
import com.healthtrend.core.common.time.TimeKeys
import com.healthtrend.core.domain.model.FoodItem
import com.healthtrend.core.domain.model.FoodNutrientValue
import com.healthtrend.core.domain.model.FoodWithNutrients
import com.healthtrend.core.domain.model.MealLog
import com.healthtrend.core.domain.model.MealType
import com.healthtrend.core.domain.model.MetricObservation
import com.healthtrend.core.domain.model.NutrientDefinition
import com.healthtrend.core.domain.model.NutritionMetricIds
import com.healthtrend.core.domain.repository.MetricRepository
import com.healthtrend.core.domain.repository.NutritionRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.UUID

/** One row of the day's food diary, with the food resolved for display. */
data class MealRow(
    val meal: MealLog,
    val food: FoodItem?,
)

/** A nutrient's total for the selected day, as projected into `metric_observations`. */
data class DayNutrientTotal(
    val nutrient: NutrientDefinition,
    val amount: Double,
)

/**
 * Screen state for the nutrition ingestion page.
 *
 * [totals] is deliberately read back from `metric_observations` rather than summed from [meals]:
 * that is the observable proof of AGENTS.md §4.3's projection pipeline — the number on screen is
 * the same number the analytics engine sees as a time series.
 */
data class NutritionUiState(
    val isLoading: Boolean = true,
    val dayStartEpochMilli: Long = 0L,
    val isToday: Boolean = true,
    val query: String = "",
    val foods: List<FoodItem> = emptyList(),
    val selectedFood: FoodWithNutrients? = null,
    val mealType: MealType = MealType.LUNCH,
    val nutrients: List<NutrientDefinition> = emptyList(),
    val meals: List<MealRow> = emptyList(),
    val totals: List<DayNutrientTotal> = emptyList(),
    @StringRes val messageRes: Int? = null,
)

/**
 * Drives the food diary: search, log, delete, and create custom foods.
 *
 * Writing or deleting a meal asks the repository to re-project the affected natural day, so this
 * ViewModel never computes nutrient totals itself (AGENTS.md §4.3).
 */
class NutritionViewModel(
    private val nutritionRepository: NutritionRepository,
    private val metricRepository: MetricRepository,
    private val zoneId: ZoneId,
    private val nowMillis: () -> Long = System::currentTimeMillis,
) : ViewModel() {

    private data class Controls(
        val dayStartEpochMilli: Long,
        val query: String = "",
        val selectedFoodId: String? = null,
        val selectedFood: FoodWithNutrients? = null,
        val mealType: MealType = MealType.LUNCH,
        @StringRes val messageRes: Int? = null,
    )

    private val controls = MutableStateFlow(Controls(dayStartEpochMilli = todayStart()))

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<NutritionUiState> = controls
        .map { it.dayStartEpochMilli }
        .distinctUntilChanged()
        .flatMapLatest { dayStart ->
            val range: DayRange = TimeKeys.dayRangeUtcMillis(dayStart, zoneId)
            combine(
                nutritionRepository.observeNutrientDefinitions(),
                // One read of the small food lexicon serves both the search box (filtered in
                // memory — a few hundred rows) and the name lookup for the day's diary rows.
                nutritionRepository.observeFoods(""),
                nutritionRepository.observeMeals(range.startMillis, range.endMillisExclusive),
                metricRepository.observeObservations(range.startMillis, range.endMillisExclusive),
                controls,
            ) { nutrients, foods, meals, observations, current ->
                withContext(Dispatchers.Default) {
                    build(range, nutrients, foods, meals, observations, current)
                }
            }
        }
        .catch { emit(NutritionUiState(isLoading = false)) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = NutritionUiState(),
        )

    // ------------------------------------------------------------------ day navigation

    fun showPreviousDay() = shiftDay(-1)

    fun showNextDay() = shiftDay(1)

    fun showToday() {
        controls.update { it.copy(dayStartEpochMilli = todayStart()) }
    }

    private fun shiftDay(deltaDays: Long) {
        controls.update { current ->
            val shifted = Instant.ofEpochMilli(current.dayStartEpochMilli)
                .atZone(zoneId)
                .plusDays(deltaDays)
                .toInstant()
                .toEpochMilli()
            current.copy(dayStartEpochMilli = shifted)
        }
    }

    // ------------------------------------------------------------------ food selection

    fun setQuery(query: String) {
        controls.update { it.copy(query = query) }
    }

    fun selectFood(foodId: String) {
        controls.update { it.copy(selectedFoodId = foodId, selectedFood = null) }
        viewModelScope.launch {
            val food = nutritionRepository.getFoodWithNutrients(foodId)
            controls.update { current ->
                // Discard a late response for a food the user has since navigated away from.
                if (current.selectedFoodId == foodId) current.copy(selectedFood = food) else current
            }
        }
    }

    fun clearSelectedFood() {
        controls.update { it.copy(selectedFoodId = null, selectedFood = null) }
    }

    fun selectMealType(mealType: MealType) {
        controls.update { it.copy(mealType = mealType) }
    }

    // ------------------------------------------------------------------ logging

    /** Logs [amount] of the selected food in the food's own reference unit. */
    fun logSelectedFood(amount: Double) {
        val current = controls.value
        val food = current.selectedFood
        if (food == null) {
            postMessage(R.string.nutrition_no_food_selected)
            return
        }
        if (!amount.isFinite() || amount <= 0.0) {
            postMessage(R.string.nutrition_invalid_amount)
            return
        }
        viewModelScope.launch {
            nutritionRepository.logMeal(
                MealLog(
                    id = "meal_${UUID.randomUUID()}",
                    foodId = food.food.id,
                    timestampEpochMilli = mealTimestamp(current.dayStartEpochMilli),
                    actualAmount = amount,
                    actualUnit = food.food.referenceUnit,
                    mealType = current.mealType,
                ),
            )
            postMessage(R.string.entry_saved)
        }
    }

    fun deleteMeal(mealId: String) {
        viewModelScope.launch { nutritionRepository.deleteMeal(mealId) }
    }

    /**
     * Creates a user-defined food from nutrient amounts quoted **per [referenceAmount]**.
     *
     * Nothing is written until the UI has shown the figures for confirmation (AGENTS.md §5.3).
     */
    fun createFood(
        name: String,
        referenceAmount: Double,
        referenceUnit: String,
        nutrientPerReference: Map<String, Double>,
    ) {
        val trimmedName = name.trim()
        if (trimmedName.isEmpty()) return
        if (!referenceAmount.isFinite() || referenceAmount <= 0.0) {
            postMessage(R.string.nutrition_invalid_amount)
            return
        }
        val values = nutrientPerReference.filterValues { it.isFinite() && it > 0.0 }
        if (values.isEmpty()) {
            postMessage(R.string.nutrition_at_least_one_nutrient)
            return
        }
        viewModelScope.launch {
            val foodId = "food_${UUID.randomUUID()}"
            nutritionRepository.upsertFoodWithNutrients(
                food = FoodItem(
                    id = foodId,
                    name = trimmedName,
                    referenceAmount = referenceAmount,
                    referenceUnit = referenceUnit.trim().ifEmpty { DEFAULT_REFERENCE_UNIT },
                    isCustom = true,
                ),
                nutrients = values.map { (nutrientId, amount) ->
                    FoodNutrientValue(
                        foodId = foodId,
                        nutrientId = nutrientId,
                        amountPerReference = amount,
                    )
                },
            )
            controls.update { it.copy(query = trimmedName) }
            selectFood(foodId)
            postMessage(R.string.entry_saved)
        }
    }

    fun consumeMessage() {
        controls.update { it.copy(messageRes = null) }
    }

    // ------------------------------------------------------------------ internals

    private fun build(
        range: DayRange,
        nutrients: List<NutrientDefinition>,
        foods: List<FoodItem>,
        meals: List<MealLog>,
        observations: List<MetricObservation>,
        controls: Controls,
    ): NutritionUiState {
        val nutrientsById = nutrients.associateBy { it.id }
        val foodsById = foods.associateBy { it.id }

        // Read the day's totals back out of the projected time series (AGENTS.md §4.3).
        val totals = observations
            .filter { NutritionMetricIds.matches(it.metricId) }
            .groupBy { it.metricId }
            .mapNotNull { (metricId, group) ->
                val nutrient = nutrientsById[metricId.removePrefix(NutritionMetricIds.PREFIX)]
                    ?: return@mapNotNull null
                DayNutrientTotal(nutrient = nutrient, amount = group.sumOf { it.value })
            }
            .sortedBy { it.nutrient.displayOrder }

        val query = controls.query.trim()
        val matches =
            if (query.isEmpty()) foods
            else foods.filter { it.name.contains(query, ignoreCase = true) }

        return NutritionUiState(
            isLoading = false,
            dayStartEpochMilli = range.startMillis,
            isToday = range.startMillis == todayStart(),
            query = controls.query,
            foods = matches,
            selectedFood = controls.selectedFood,
            mealType = controls.mealType,
            nutrients = nutrients,
            meals = meals
                .sortedByDescending { it.timestampEpochMilli }
                .map { MealRow(meal = it, food = foodsById[it.foodId]) },
            totals = totals,
            messageRes = controls.messageRes,
        )
    }

    /**
     * A back-dated meal lands at midday so it cannot be nudged into the neighbouring day by a DST
     * transition; a meal logged for today uses the real clock.
     */
    private fun mealTimestamp(dayStartEpochMilli: Long): Long =
        if (dayStartEpochMilli == todayStart()) nowMillis()
        else ZonedDateTime.ofInstant(Instant.ofEpochMilli(dayStartEpochMilli), zoneId)
            .plusHours(12)
            .toInstant()
            .toEpochMilli()

    private fun todayStart(): Long = TimeKeys.startOfDayUtcMillis(nowMillis(), zoneId)

    private fun postMessage(@StringRes messageRes: Int) {
        controls.update { it.copy(messageRes = messageRes) }
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
        const val DEFAULT_REFERENCE_UNIT = "g"
    }
}
