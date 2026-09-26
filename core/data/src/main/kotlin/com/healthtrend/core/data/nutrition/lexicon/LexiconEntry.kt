package com.healthtrend.core.data.nutrition.lexicon

/**
 * One record of the bundled offline lexicon (AGENTS.md §5.2, tier 2).
 *
 * The instances live in `BundledFoodLexiconData.kt`, which is **generated** from USDA FoodData
 * Central by `tools/generate_food_lexicon.py`. This class is hand-written so the generated file
 * contains nothing but data.
 *
 * [fdcId] and [fdcDescription] are not used at runtime — they are the audit trail. Given any number
 * shown in the app, they say exactly which published record it came from.
 *
 * [referenceAmount]/[referenceUnit] are the food's own basis and are *not* always 100 g: the foods
 * that are naturally counted (鸡蛋, 苹果, 香蕉, 橙子) are quoted per piece, precisely so the
 * "never divide by a constant 100" rule of AGENTS.md §1.1 is exercised by shipped data and not only
 * by tests.
 */
internal data class LexiconEntry(
    val id: String,
    val name: String,
    val fdcId: Long,
    val fdcDescription: String,
    val referenceAmount: Double,
    val referenceUnit: String,
    /** `NutrientCatalog` id -> amount per [referenceAmount]. Sparse: absent means "not measured". */
    val nutrients: Map<String, Double>,
    /** Extra search terms (synonyms, English names). The display [name] is always searchable too. */
    val searchTerms: List<String> = emptyList(),
) {

    /** Whether [query] matches this food's name or one of its synonyms. */
    fun matches(query: String): Boolean =
        name.contains(query, ignoreCase = true) ||
            searchTerms.any { it.contains(query, ignoreCase = true) }
}
