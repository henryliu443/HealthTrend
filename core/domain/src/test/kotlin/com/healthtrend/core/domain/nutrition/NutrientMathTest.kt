package com.healthtrend.core.domain.nutrition

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class NutrientMathTest {

    @Test
    fun `rice 73 g against a 100 g basis`() {
        // 2.6 g protein per 100 g, user eats 73 g -> AGENTS.md 1.1 example.
        NutrientMath.scale(amountPerReference = 2.6, actualAmount = 73.0, referenceAmount = 100.0) shouldBe
            (1.898 plusOrMinus 1e-12)
    }

    @Test
    fun `milk 237 ml against a 100 ml basis`() {
        NutrientMath.scale(amountPerReference = 3.4, actualAmount = 237.0, referenceAmount = 100.0) shouldBe
            (8.058 plusOrMinus 1e-12)
    }

    @Test
    fun `two eggs against a 1 piece basis`() {
        // 70 kcal per egg, user eats 2 eggs -> must NOT divide by 100.
        NutrientMath.scale(amountPerReference = 70.0, actualAmount = 2.0, referenceAmount = 1.0) shouldBe
            140.0
    }

    @Test
    fun `non-100 reference basis scales correctly`() {
        // Snack bar: 120 kcal per 30 g serving, user eats 75 g.
        NutrientMath.scale(amountPerReference = 120.0, actualAmount = 75.0, referenceAmount = 30.0) shouldBe
            300.0
    }

    @Test
    fun `zero intake yields zero`() {
        NutrientMath.scale(amountPerReference = 52.0, actualAmount = 0.0, referenceAmount = 100.0) shouldBe 0.0
    }

    @Test
    fun `non-positive reference amount is rejected`() {
        shouldThrow<IllegalArgumentException> {
            NutrientMath.scale(amountPerReference = 1.0, actualAmount = 1.0, referenceAmount = 0.0)
        }
        shouldThrow<IllegalArgumentException> {
            NutrientMath.scale(amountPerReference = 1.0, actualAmount = 1.0, referenceAmount = -5.0)
        }
    }

    @Test
    fun `negative intake amount is rejected`() {
        shouldThrow<IllegalArgumentException> {
            NutrientMath.scale(amountPerReference = 1.0, actualAmount = -1.0, referenceAmount = 100.0)
        }
    }
}
