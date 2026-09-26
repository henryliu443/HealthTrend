package com.healthtrend.core.domain.nutrition

/**
 * Nutrient amount scaling.
 *
 * HARD RULE (AGENTS.md §1.1 / §10.2): the reference amount is an arbitrary per-food value.
 * Conversion MUST be a ratio against that value, never a division by the constant 100.
 *
 *     ActualNutrient = amountPerReference * actualAmount / referenceAmount
 */
object NutrientMath {

    /**
     * @param amountPerReference nutrient amount quoted for [referenceAmount] of the food
     * @param actualAmount       amount the user actually consumed, in the reference unit
     * @param referenceAmount    the food's own basis (100 g, 1 piece, 237 ml, 30 serving, ...)
     * @return the resulting nutrient amount
     * @throws IllegalArgumentException if [referenceAmount] is not strictly positive
     */
    fun scale(
        amountPerReference: Double,
        actualAmount: Double,
        referenceAmount: Double,
    ): Double {
        require(referenceAmount > 0.0) {
            "referenceAmount must be > 0, was $referenceAmount (AGENTS.md §10.2)"
        }
        require(actualAmount >= 0.0) { "actualAmount must be >= 0, was $actualAmount" }
        return amountPerReference * (actualAmount / referenceAmount)
    }
}
