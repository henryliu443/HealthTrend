package com.healthtrend.core.domain.model

/** Mirrors `nutrient_definitions.category` (AGENTS.md 4.2). */
enum class NutrientCategory { MACRO, MINERAL, VITAMIN, LIPID }

/**
 * Metadata describing a nutrient (calories, protein, vitamin C, zinc, ...).
 *
 * Adding a new nutrient is a data-only operation: insert one row here. The schema never changes
 * (AGENTS.md 1.2).
 */
data class NutrientDefinition(
    val id: String,
    val name: String,
    val unit: String,
    val category: NutrientCategory,
    val dailyRecommended: Double?,
    val displayOrder: Int,
)
