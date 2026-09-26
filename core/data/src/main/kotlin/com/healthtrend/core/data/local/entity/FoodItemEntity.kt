package com.healthtrend.core.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * AGENTS.md 4.2 — `food_items`.
 * `referenceAmount` is arbitrary (100 g, 1 piece, 237 ml, 30 serving, ...) — never a constant.
 */
@Entity(
    tableName = "food_items",
    indices = [Index(value = ["name"])],
)
data class FoodItemEntity(
    @PrimaryKey val id: String,
    val name: String,
    val brand: String? = null,
    val referenceAmount: Double = 100.0,
    val referenceUnit: String = "g",
    val isCustom: Boolean = false,
)
