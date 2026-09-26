package com.healthtrend.core.domain.model

/** Mirrors `meal_logs.meal_type` (AGENTS.md 4.3). */
enum class MealType { BREAKFAST, LUNCH, DINNER, SNACK }

/**
 * One intake event. [actualAmount]/[actualUnit] are what the user actually ate, in whatever
 * unit the food was presented in; conversion to nutrient amounts happens via
 * [com.healthtrend.core.domain.nutrition.NutrientMath] (AGENTS.md 1.1).
 */
data class MealLog(
    val id: String,
    val foodId: String,
    val timestampEpochMilli: Long,
    val actualAmount: Double,
    val actualUnit: String,
    val mealType: MealType,
)
