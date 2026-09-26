package com.healthtrend.core.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.healthtrend.core.data.local.entity.FoodNutrientValueEntity

@Dao
interface FoodNutrientValueDao {

    @Query("SELECT * FROM food_nutrient_values WHERE food_id = :foodId")
    suspend fun findByFoodId(foodId: String): List<FoodNutrientValueEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entities: List<FoodNutrientValueEntity>)

    @Query("DELETE FROM food_nutrient_values WHERE food_id = :foodId")
    suspend fun deleteByFoodId(foodId: String)
}
