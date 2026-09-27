package com.healthtrend.core.domain.nutrition.lookup

import com.healthtrend.core.domain.model.FoodWithNutrients

/**
 * Where a food fact came from, in the order AGENTS.md §5.2 consults them.
 *
 * The order is the contract, not decoration: the orchestrator walks these in [order] and stops at
 * the first tier that can answer, which is what makes the whole feature offline-first.
 */
enum class LookupTier(val order: Int) {
    /** First: what the user already has — their own foods and anything they previously adopted. */
    PERSONAL(0),

    /** Second: the lexicon compiled into the app. Reads no network, answers in microseconds. */
    BUNDLED(1),

    /** Third: an optional public database plugin (OpenFoodFacts, USDA, ...). */
    REMOTE(2),

    /** Fourth: an optional natural-language estimate. Never authoritative, always confirmed. */
    ESTIMATE(3),
}

/**
 * A provider-scoped handle to a food.
 *
 * Providers key their own foods with their own ids, so a bare id is ambiguous the moment two tiers
 * exist (`rice_cooked` means something to the bundled lexicon and something else to a brand
 * database). The pair travels together and is only ever serialised as `provider:localId`.
 */
data class FoodRef(val providerName: String, val localId: String) {

    val raw: String get() = "$providerName$SEPARATOR$localId"

    companion object {
        private const val SEPARATOR = ":"

        fun of(providerName: String, localId: String): FoodRef {
            require(!providerName.contains(SEPARATOR)) {
                "provider names must not contain '$SEPARATOR': $providerName"
            }
            return FoodRef(providerName, localId)
        }

        /** Splits on the first separator, so a local id may itself contain one. */
        fun parse(raw: String): FoodRef {
            val index = raw.indexOf(SEPARATOR)
            require(index > 0 && index < raw.length - 1) { "malformed food ref: $raw" }
            return FoodRef(raw.substring(0, index), raw.substring(index + 1))
        }
    }
}

/**
 * One hit from a provider (AGENTS.md §5.1).
 *
 * Deliberately carries no nutrient values: a search returns many foods and only the one the user
 * picks needs its full profile fetched.
 *
 * [isCustom] answers "are these the user's own words?" — set when a food was created or corrected by
 * hand. It matters for display: an untouched copy of a curated food may be shown under the curated
 * name in the reader's language, while a food they edited must be quoted verbatim.
 */
data class FoodSearchResult(
    val foodRef: FoodRef,
    val tier: LookupTier,
    val name: String,
    val brand: String?,
    val defaultReferenceAmount: Double,
    val defaultReferenceUnit: String,
    val isCustom: Boolean = false,
)

/**
 * A food's full nutrient profile (AGENTS.md §5.1).
 *
 * [nutrients] is keyed by `NutrientCatalog` ids and quoted **per [referenceAmount]** — the same
 * arbitrary-amount basis the rest of the app uses (AGENTS.md §1.1). [sourceName]/[sourceRecordId]
 * exist so the confirmation sheet can tell the user exactly which record a number came from.
 */
data class FoodNutrientProfile(
    val foodRef: FoodRef,
    val tier: LookupTier,
    val foodName: String,
    val brand: String?,
    val referenceAmount: Double,
    val referenceUnit: String,
    val nutrients: Map<String, Double>,
    val sourceName: String? = null,
    val sourceRecordId: String? = null,
)

/**
 * A pluggable source of food facts (AGENTS.md §5.1).
 *
 * Implementations must not throw for ordinary failure — a provider that is offline, rate-limited or
 * simply has no opinion returns `Result.failure`, and the orchestrator moves on to the next tier. An
 * exception escaping here would break a search for every other provider too.
 */
interface NutritionLookupProvider {

    val providerName: String

    val tier: LookupTier

    /** Whether the provider keeps working with no network. Surfaced so the UI can explain itself. */
    val isOfflineCapable: Boolean

    suspend fun searchFoods(query: String): Result<List<FoodSearchResult>>

    suspend fun getNutrientProfile(foodRefId: String): Result<FoodNutrientProfile>
}

/**
 * Orchestrates the tiers (AGENTS.md §5.2) and owns the one write path into the local dictionary.
 *
 * Reads never touch storage; only [save] does, and only after the user has confirmed the figures on
 * screen (AGENTS.md §5.3).
 */
interface NutritionLookupRepository {

    /**
     * Registers the bundled nutrient metadata.
     *
     * Must run before any food value is written: `food_nutrient_values.nutrient_id` carries a
     * `RESTRICT` foreign key, so a value referring to an unregistered nutrient is rejected by
     * SQLite. Safe to call repeatedly — existing rows are left untouched.
     */
    suspend fun registerBundledNutrients()

    /**
     * Every tier's hits for [query], in priority order.
     *
     * Results are de-duplicated so the same food offered by two sources appears once, at the
     * highest-priority tier that knew about it.
     */
    suspend fun search(query: String, limitPerTier: Int = DEFAULT_LIMIT_PER_TIER): List<FoodSearchResult>

    /** Fetches the profile for a search result. Fails if its provider is unavailable. */
    suspend fun profile(result: FoodSearchResult): Result<FoodNutrientProfile>

    /**
     * Persists a confirmed profile into `food_items` / `food_nutrient_values` (AGENTS.md §5.3).
     *
     * Idempotent per source food: re-confirming the same lexicon entry updates it instead of
     * creating a second copy, which keeps a day's existing meal logs pointing at valid rows.
     *
     * @param isCustom marks the copy as the user's own. True when they created it or edited any
     *   figure, false when it is an untouched copy of a curated record.
     */
    suspend fun save(profile: FoodNutrientProfile, isCustom: Boolean): Result<FoodWithNutrients>

    companion object {
        const val DEFAULT_LIMIT_PER_TIER = 25
    }
}
