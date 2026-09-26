package com.healthtrend.core.data.local.dao

import androidx.room.ColumnInfo

/**
 * Flattened join row used by the automatic projection (AGENTS.md 4.3):
 * one row per (meal log x nutrient) with everything needed to scale the amount.
 */
data class MealNutrientRow(
    @ColumnInfo(name = "nutrient_id") val nutrientId: String,
    @ColumnInfo(name = "amount_per_reference") val amountPerReference: Double,
    @ColumnInfo(name = "reference_amount") val referenceAmount: Double,
    @ColumnInfo(name = "actual_amount") val actualAmount: Double,
)
