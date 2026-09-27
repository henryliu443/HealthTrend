package com.healthtrend.core.data.nutrition.lookup

import com.healthtrend.core.domain.nutrition.lookup.FoodNutrientProfile
import com.healthtrend.core.domain.nutrition.lookup.FoodRef
import com.healthtrend.core.domain.nutrition.lookup.FoodSearchResult
import com.healthtrend.core.domain.nutrition.lookup.LookupTier
import com.healthtrend.core.domain.nutrition.lookup.NutritionLookupProvider
import com.healthtrend.core.domain.repository.NutritionRepository
import kotlinx.coroutines.flow.first

/**
 * Tier 1 of AGENTS.md §5.2: what the user already has.
 *
 * This is the "user history and favourites" tier — every food in `food_items`, whether they typed it
 * themselves or adopted it from another tier earlier. It ranks first because the user's own numbers
 * are the ones they have already reviewed, and because re-using a saved food keeps a diary's
 * history comparable instead of restating the same meal against different values.
 *
 * The whole table is read once per search and filtered in memory: `food_items` holds the user's own
 * foods plus whatever they have adopted, not a global database. See the deviations table in
 * `docs/development-setup.md`.
 */
class SavedFoodLookupProvider(
    private val nutritionRepository: NutritionRepository,
) : NutritionLookupProvider {

    override val providerName: String get() = NAME

    override val tier: LookupTier get() = LookupTier.PERSONAL

    /** True: these rows are in the local SQLite database. */
    override val isOfflineCapable: Boolean get() = true

    override suspend fun searchFoods(query: String): Result<List<FoodSearchResult>> {
        val trimmed = query.trim()
        return lookupResult {
            val foods = nutritionRepository.observeFoods("").first()
            val matches = if (trimmed.isEmpty()) {
                foods
            } else {
                foods.filter { it.name.contains(trimmed, ignoreCase = true) }
            }
            matches
                .sortedBy { it.name.length }
                .map { food ->
                    FoodSearchResult(
                        foodRef = FoodRef.of(NAME, food.id),
                        tier = LookupTier.PERSONAL,
                        name = food.name,
                        brand = food.brand,
                        defaultReferenceAmount = food.referenceAmount,
                        defaultReferenceUnit = food.referenceUnit,
                        isCustom = food.isCustom,
                    )
                }
        }
    }

    override suspend fun getNutrientProfile(foodRefId: String): Result<FoodNutrientProfile> {
        val saved = nutritionRepository.getFoodWithNutrients(foodRefId)
            ?: return Result.failure(NoSuchElementException("no saved food '$foodRefId'"))
        return Result.success(
            FoodNutrientProfile(
                foodRef = FoodRef.of(NAME, saved.food.id),
                tier = LookupTier.PERSONAL,
                foodName = saved.food.name,
                brand = saved.food.brand,
                referenceAmount = saved.food.referenceAmount,
                referenceUnit = saved.food.referenceUnit,
                nutrients = saved.nutrients.associate { it.nutrientId to it.amountPerReference },
                // The provenance of a personal food is the user; there is no external record to cite.
                sourceName = null,
                sourceRecordId = null,
            ),
        )
    }

    companion object {
        const val NAME = "saved"
    }
}
