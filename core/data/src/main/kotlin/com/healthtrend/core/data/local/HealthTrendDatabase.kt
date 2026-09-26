package com.healthtrend.core.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.healthtrend.core.data.local.dao.FoodItemDao
import com.healthtrend.core.data.local.dao.FoodNutrientValueDao
import com.healthtrend.core.data.local.dao.MealLogDao
import com.healthtrend.core.data.local.dao.MetricDefinitionDao
import com.healthtrend.core.data.local.dao.MetricObservationDao
import com.healthtrend.core.data.local.dao.NutrientDefinitionDao
import com.healthtrend.core.data.local.entity.FoodItemEntity
import com.healthtrend.core.data.local.entity.FoodNutrientValueEntity
import com.healthtrend.core.data.local.entity.MealLogEntity
import com.healthtrend.core.data.local.entity.MetricDefinitionEntity
import com.healthtrend.core.data.local.entity.MetricObservationEntity
import com.healthtrend.core.data.local.entity.NutrientDefinitionEntity

/**
 * AGENTS.md chapter 4.
 *
 * `exportSchema = true` (AGENTS.md 4.5) — every schema version is written to
 * `core/data/schemas/` and committed, so that future `Migration(M, N)` steps have a baseline.
 */
@Database(
    entities = [
        MetricDefinitionEntity::class,
        MetricObservationEntity::class,
        NutrientDefinitionEntity::class,
        FoodItemEntity::class,
        FoodNutrientValueEntity::class,
        MealLogEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
@TypeConverters(CommonConverters::class)
abstract class HealthTrendDatabase : RoomDatabase() {

    abstract fun metricDefinitionDao(): MetricDefinitionDao
    abstract fun metricObservationDao(): MetricObservationDao
    abstract fun nutrientDefinitionDao(): NutrientDefinitionDao
    abstract fun foodItemDao(): FoodItemDao
    abstract fun foodNutrientValueDao(): FoodNutrientValueDao
    abstract fun mealLogDao(): MealLogDao

    companion object {
        const val NAME = "healthtrend.db"

        /**
         * NOTE: intentionally no `fallbackToDestructiveMigration()` — AGENTS.md 4.5 forbids it
         * in release. Until the first migration exists this only ever creates schema v1.
         */
        fun build(context: Context): HealthTrendDatabase =
            Room.databaseBuilder(context.applicationContext, HealthTrendDatabase::class.java, NAME)
                .build()
    }
}
