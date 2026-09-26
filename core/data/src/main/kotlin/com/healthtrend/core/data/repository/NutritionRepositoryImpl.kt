package com.healthtrend.core.data.repository

import androidx.room.withTransaction
import com.healthtrend.core.data.local.HealthTrendDatabase
import com.healthtrend.core.data.mapper.toDomain
import com.healthtrend.core.data.mapper.toEntity
import com.healthtrend.core.data.projection.NutritionProjectionService
import com.healthtrend.core.domain.model.FoodItem
import com.healthtrend.core.domain.model.FoodNutrientValue
import com.healthtrend.core.domain.model.FoodWithNutrients
import com.healthtrend.core.domain.model.MealLog
import com.healthtrend.core.domain.model.NutrientDefinition
import com.healthtrend.core.domain.repository.NutritionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class NutritionRepositoryImpl(
    private val database: HealthTrendDatabase,
    private val projection: NutritionProjectionService,
) : NutritionRepository {

    private val nutrientDao = database.nutrientDefinitionDao()
    private val foodDao = database.foodItemDao()
    private val valueDao = database.foodNutrientValueDao()
    private val mealDao = database.mealLogDao()

    override fun observeNutrientDefinitions(): Flow<List<NutrientDefinition>> =
        nutrientDao.observeAll().map { entities -> entities.map { it.toDomain() } }

    override suspend fun upsertNutrientDefinitions(definitions: List<NutrientDefinition>) {
        if (definitions.isEmpty()) return
        // IGNORE, not REPLACE: an existing definition may have been edited by the user (a renamed
        // display name, a corrected RDA) and re-registering metadata must not clobber that.
        nutrientDao.insertAllIfAbsent(definitions.map { it.toEntity() })
    }

    override fun observeFoods(nameQuery: String): Flow<List<FoodItem>> =
        foodDao.observeByName(nameQuery).map { entities -> entities.map { it.toDomain() } }

    override suspend fun getFoodWithNutrients(foodId: String): FoodWithNutrients? {
        val food = foodDao.findById(foodId) ?: return null
        val nutrients = valueDao.findByFoodId(foodId)
        return FoodWithNutrients(
            food = food.toDomain(),
            nutrients = nutrients.map { it.toDomain() },
        )
    }

    override suspend fun upsertFoodWithNutrients(
        food: FoodItem,
        nutrients: List<FoodNutrientValue>,
    ) {
        database.withTransaction {
            foodDao.upsert(food.toEntity())
            valueDao.deleteByFoodId(food.id)
            if (nutrients.isNotEmpty()) {
                valueDao.insertAll(nutrients.map { it.toEntity() })
            }
        }
    }

    override suspend fun logMeal(meal: MealLog) {
        mealDao.upsert(meal.toEntity())
        projection.recomputeDayContaining(meal.timestampEpochMilli)
    }

    override suspend fun deleteMeal(mealId: String) {
        val existing = mealDao.findById(mealId) ?: return
        mealDao.deleteById(mealId)
        projection.recomputeDayContaining(existing.timestamp)
    }

    override fun observeMeals(
        fromEpochMilli: Long,
        toEpochMilliExclusive: Long,
    ): Flow<List<MealLog>> =
        mealDao.observeRange(fromEpochMilli, toEpochMilliExclusive)
            .map { entities -> entities.map { it.toDomain() } }
}
