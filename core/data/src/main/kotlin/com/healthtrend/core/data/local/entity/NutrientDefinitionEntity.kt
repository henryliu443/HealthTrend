package com.healthtrend.core.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * AGENTS.md 4.2 — `nutrient_definitions`.
 * Adding a nutrient is a pure data insert; the schema never changes (AGENTS.md 1.2).
 */
@Entity(tableName = "nutrient_definitions")
data class NutrientDefinitionEntity(
    @PrimaryKey val id: String, // "calories", "protein", "vitamin_c", ...
    val name: String,
    val unit: String, // "kcal", "g", "mg", "ug"
    val category: String, // MACRO, MINERAL, VITAMIN, LIPID
    val dailyRecommended: Double?,
    val displayOrder: Int,
)
