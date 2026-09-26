package com.healthtrend.core.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

/**
 * AGENTS.md 4.2 — `food_nutrient_values`.
 * Many-to-many between foods and nutrient definitions; no nutrient is ever a hard-coded column.
 */
@Entity(
    tableName = "food_nutrient_values",
    primaryKeys = ["food_id", "nutrient_id"],
    foreignKeys = [
        ForeignKey(
            entity = FoodItemEntity::class,
            parentColumns = ["id"],
            childColumns = ["food_id"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = NutrientDefinitionEntity::class,
            parentColumns = ["id"],
            childColumns = ["nutrient_id"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [Index(value = ["nutrient_id"])],
)
data class FoodNutrientValueEntity(
    @ColumnInfo(name = "food_id") val foodId: String,
    @ColumnInfo(name = "nutrient_id") val nutrientId: String,
    @ColumnInfo(name = "amount_per_reference") val amountPerReference: Double,
)
