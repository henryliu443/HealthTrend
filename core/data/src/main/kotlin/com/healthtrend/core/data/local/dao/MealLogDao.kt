package com.healthtrend.core.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.healthtrend.core.data.local.entity.MealLogEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MealLogDao {

    @Query("SELECT * FROM meal_logs WHERE timestamp >= :from AND timestamp < :to ORDER BY timestamp ASC")
    fun observeRange(from: Long, to: Long): Flow<List<MealLogEntity>>

    @Query("SELECT * FROM meal_logs WHERE id = :id LIMIT 1")
    suspend fun findById(id: String): MealLogEntity?

    /**
     * Projection source for AGENTS.md 4.3. `food.referenceAmount > 0` guards the ratio while
     * `NutrientMath.scale` remains the single place performing the division.
     */
    @Query(
        """
        SELECT value.nutrient_id      AS nutrient_id,
               value.amount_per_reference AS amount_per_reference,
               food.referenceAmount   AS reference_amount,
               log.actual_amount      AS actual_amount
        FROM meal_logs AS log
        INNER JOIN food_items AS food ON food.id = log.food_id
        INNER JOIN food_nutrient_values AS value ON value.food_id = food.id
        WHERE log.timestamp >= :from AND log.timestamp < :to
          AND food.referenceAmount > 0
        """,
    )
    suspend fun selectNutrientProjection(from: Long, to: Long): List<MealNutrientRow>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: MealLogEntity)

    @Query("DELETE FROM meal_logs WHERE id = :id")
    suspend fun deleteById(id: String)
}
