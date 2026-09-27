package com.healthtrend.core.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.healthtrend.core.data.local.entity.FoodNutrientValueEntity

@Dao
interface FoodNutrientValueDao {

        /** Whole-table read for export; ordered so two exports of the same data are identical. */
    @Query("SELECT * FROM food_nutrient_values ORDER BY food_id ASC, nutrient_id ASC")
    suspend fun findAll(): List<FoodNutrientValueEntity>

@Query("SELECT * FROM food_nutrient_values WHERE food_id = :foodId")
    suspend fun findByFoodId(foodId: String): List<FoodNutrientValueEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entities: List<FoodNutrientValueEntity>)

    @Query("DELETE FROM food_nutrient_values WHERE food_id = :foodId")
    suspend fun deleteByFoodId(foodId: String)
}
