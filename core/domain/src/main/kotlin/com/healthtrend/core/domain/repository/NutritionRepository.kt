package com.healthtrend.core.domain.repository

import com.healthtrend.core.domain.model.FoodItem
import com.healthtrend.core.domain.model.FoodNutrientValue
import com.healthtrend.core.domain.model.FoodWithNutrients
import com.healthtrend.core.domain.model.MealLog
import com.healthtrend.core.domain.model.NutrientDefinition
import kotlinx.coroutines.flow.Flow

/**
 * Nutrition ingestion domain: the food dictionary, nutrient metadata and intake logs.
 *
 * Writing/deleting a [MealLog] automatically re-projects the affected natural day onto
 * `metric_observations` (AGENTS.md 4.3), so implementors own that side effect.
 */
interface NutritionRepository {

    fun observeNutrientDefinitions(): Flow<List<NutrientDefinition>>

    fun observeFoods(nameQuery: String): Flow<List<FoodItem>>

    suspend fun getFoodWithNutrients(foodId: String): FoodWithNutrients?

    /** Persists a food and replaces its nutrient profile atomically. */
    suspend fun upsertFoodWithNutrients(food: FoodItem, nutrients: List<FoodNutrientValue>)

    /** Persists an intake log and refreshes the natural day it falls in. */
    suspend fun logMeal(meal: MealLog)

    /** Removes an intake log and refreshes the natural day it belonged to. */
    suspend fun deleteMeal(mealId: String)

    fun observeMeals(fromEpochMilli: Long, toEpochMilliExclusive: Long): Flow<List<MealLog>>
}
