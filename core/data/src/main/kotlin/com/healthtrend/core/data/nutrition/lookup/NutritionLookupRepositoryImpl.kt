package com.healthtrend.core.data.nutrition.lookup

import com.healthtrend.core.domain.model.FoodItem
import com.healthtrend.core.domain.model.FoodNutrientValue
import com.healthtrend.core.domain.model.FoodWithNutrients
import com.healthtrend.core.domain.nutrition.NutrientCatalog
import com.healthtrend.core.domain.nutrition.lookup.FoodNutrientProfile
import com.healthtrend.core.domain.nutrition.lookup.FoodSearchResult
import com.healthtrend.core.domain.nutrition.lookup.LookupTier
import com.healthtrend.core.domain.nutrition.lookup.NutritionLookupProvider
import com.healthtrend.core.domain.nutrition.lookup.NutritionLookupRepository
import com.healthtrend.core.domain.repository.NutritionRepository
import java.util.UUID

/**
 * The tier pipeline of AGENTS.md §5.2.
 *
 * Reads walk the providers in tier order and stop at the first tier that knows the food; writes only
 * ever happen in [save], and only after the user has seen the figures (AGENTS.md §5.3).
 *
 * The provider list is injected rather than hard-coded, so adding a remote or AI tier is a Koin
 * module change and nothing else — the ordering, de-duplication, routing and persistence below are
 * tier-agnostic.
 */
class NutritionLookupRepositoryImpl(
    providers: List<NutritionLookupProvider>,
    private val nutritionRepository: NutritionRepository,
    private val idGenerator: () -> String = { "food_${UUID.randomUUID()}" },
) : NutritionLookupRepository {

    /**
     * Sorted once at construction. The order *is* the contract — offline-first, cheapest-and-most-
     * trusted first — so it is not re-derived per call where a collector could reorder it.
     */
    private val orderedProviders: List<NutritionLookupProvider> =
        providers.sortedBy { it.tier.order }

    private val providersByName: Map<String, NutritionLookupProvider> =
        providers.associateBy { it.providerName }

    override suspend fun registerBundledNutrients() {
        nutritionRepository.upsertNutrientDefinitions(NutrientCatalog.ALL)
    }

    override suspend fun search(
        query: String,
        limitPerTier: Int,
    ): List<FoodSearchResult> {
        // A blank query is not "nothing to search": it is "what do I already have", which is how the
        // page shows the user's own food library before they type anything. The tiers decide what to
        // say about a blank query — the bundled lexicon says nothing, the personal tier lists
        // everything.
        val seenNames = HashSet<String>()
        return buildList {
            for (provider in orderedProviders) {
                // A tier that errors is a tier that has nothing to say; the search must survive it.
                val results = provider.searchFoods(query).getOrNull() ?: continue
                for (result in results.take(limitPerTier)) {
                    if (seenNames.add(normalise(result.name))) add(result)
                }
            }
        }
    }

    override suspend fun profile(result: FoodSearchResult): Result<FoodNutrientProfile> {
        val provider = providersByName[result.foodRef.providerName]
            ?: return Result.failure(
                IllegalArgumentException("no provider named '${result.foodRef.providerName}'"),
            )
        return provider.getNutrientProfile(result.foodRef.localId)
    }

    /**
     * Writes a confirmed profile into the local dictionary.
     *
     * Two decisions worth naming:
     *
     * - **Zero values are dropped.** The source tables write `0.0` for nutrients a food does not
     *   contain, and the same `0.0` appears for nutrients that were simply not measured. Storing
     *   them would add a "0 mg" line to the day's totals for every nutrient of every food eaten,
     *   which is noise dressed up as information. A food's profile therefore records what it
     *   *contributes*.
     * - **A locally-known food keeps its id.** Re-confirming 米饭（熟） after editing it updates the
     *   same row instead of creating a second one, so meals logged before the edit still point at a
     *   valid food. See [foodIdFor].
     */
    override suspend fun save(
        profile: FoodNutrientProfile,
        isCustom: Boolean,
    ): Result<FoodWithNutrients> = lookupResult {
        require(profile.referenceAmount.isFinite() && profile.referenceAmount > 0.0) {
            "reference amount must be positive and finite, was ${profile.referenceAmount}"
        }

        val nutrients = profile.nutrients
            .filterKeys { it in NutrientCatalog.byId }
            .filterValues { it.isFinite() && it > 0.0 }
        require(nutrients.isNotEmpty()) { "profile carries no usable nutrient values" }

        // `food_nutrient_values.nutrient_id` is a RESTRICT foreign key, so the metadata rows have to
        // exist before the values can be written (AGENTS.md §1.2).
        nutritionRepository.upsertNutrientDefinitions(
            nutrients.keys.mapNotNull { NutrientCatalog.byId[it] },
        )

        val food = FoodItem(
            id = foodIdFor(profile),
            name = profile.foodName,
            brand = profile.brand,
            referenceAmount = profile.referenceAmount,
            referenceUnit = profile.referenceUnit,
            isCustom = isCustom,
        )
        nutritionRepository.upsertFoodWithNutrients(
            food = food,
            nutrients = nutrients.map { (nutrientId, amount) ->
                FoodNutrientValue(
                    foodId = food.id,
                    nutrientId = nutrientId,
                    amountPerReference = amount,
                )
            },
        )
        nutritionRepository.getFoodWithNutrients(food.id)
            ?: error("food ${food.id} vanished immediately after being saved")
    }

    /**
     * Which local id a confirmed profile is stored under.
     *
     * Both local tiers already *have* an identity, and reusing it is what makes a save an update
     * rather than a duplicate: a bundled food keeps its catalogue slug, and a personal food keeps
     * the id its meals already point at. Only a tier with no local existence — a remote database or
     * an AI estimate — needs a fresh one.
     */
    private fun foodIdFor(profile: FoodNutrientProfile): String = when (profile.tier) {
        LookupTier.PERSONAL, LookupTier.BUNDLED -> profile.foodRef.localId
        LookupTier.REMOTE, LookupTier.ESTIMATE -> idGenerator()
    }

    /**
     * Matches names the way a person does: 米饭（熟） and 米饭 熟 are the same food.
     *
     * De-duplication is by name, not by id, because the same food reaching the app through two tiers
     * has two unrelated ids — that is exactly the case this exists to collapse.
     */
    private fun normalise(name: String): String =
        name.lowercase().filter { it.isLetterOrDigit() }
}
