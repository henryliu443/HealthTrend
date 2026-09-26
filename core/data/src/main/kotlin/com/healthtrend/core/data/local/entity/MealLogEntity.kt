package com.healthtrend.core.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * AGENTS.md 4.3 — `meal_logs`.
 * RESTRICT on the food FK: a food that is still referenced by intake history cannot be deleted.
 */
@Entity(
    tableName = "meal_logs",
    foreignKeys = [
        ForeignKey(
            entity = FoodItemEntity::class,
            parentColumns = ["id"],
            childColumns = ["food_id"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [
        Index(value = ["timestamp"]),
        // AGENTS.md 4.3 lists only the timestamp index; Room additionally advises covering the
        // FK child column so parent-table mutations do not trigger full scans.
        Index(value = ["food_id"]),
    ],
)
data class MealLogEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "food_id") val foodId: String,
    @ColumnInfo(name = "timestamp") val timestamp: Long,
    @ColumnInfo(name = "actual_amount") val actualAmount: Double,
    @ColumnInfo(name = "actual_unit") val actualUnit: String,
    @ColumnInfo(name = "meal_type") val mealType: String, // BREAKFAST, LUNCH, DINNER, SNACK
)
