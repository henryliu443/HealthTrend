package com.healthtrend.core.domain.model

/**
 * A food entry with a flexible reference amount (AGENTS.md 1.1).
 *
 * `referenceAmount` + `referenceUnit` describe the basis the nutrient values are quoted on,
 * e.g. `100.0 g`, `237.0 ml`, `1.0 piece`, `30.0 serving`. It is NEVER assumed to be 100.
 */
data class FoodItem(
    val id: String,
    val name: String,
    val brand: String? = null,
    val referenceAmount: Double,
    val referenceUnit: String,
    val isCustom: Boolean = false,
)

/** Amount of a single nutrient per [FoodItem.referenceAmount]. */
data class FoodNutrientValue(
    val foodId: String,
    val nutrientId: String,
    val amountPerReference: Double,
)

/** A food together with its full nutrient profile. */
data class FoodWithNutrients(
    val food: FoodItem,
    val nutrients: List<FoodNutrientValue>,
)
