package com.healthtrend.core.domain.nutrition

import com.healthtrend.core.domain.model.MetricConcern
import com.healthtrend.core.domain.model.NutrientCategory
import com.healthtrend.core.domain.model.NutrientDefinition

/**
 * Metadata for every nutrient the app understands.
 *
 * This is the payload that makes AGENTS.md §1.2 real: the schema has no per-nutrient columns, so
 * supporting a new nutrient is a single row here — no migration, no new DAO, no new UI. The bundled
 * food lexicon (`:core:data`) supplies the *values*; this object supplies what they mean.
 *
 * The keys are the ids written into `food_nutrient_values.nutrient_id` and, after projection, into
 * `metric_observations.metric_id` as `nutrient_<id>`. They are stable identifiers — renaming one
 * would orphan existing rows, so a rename means a migration.
 *
 * [NutrientDefinition.name] is a **canonical English label**, not what the user necessarily reads:
 * a stored row cannot be in two languages at once, and a language switch must not rewrite the user's
 * data. The UI resolves the localised name from the id and only falls back to this label for
 * nutrients the app does not know about (see `ui/common/NutrientUi.kt` in `:app`).
 *
 * `dailyRecommended` is a **reference intake** for orientation, in the nutrient's own unit. Where
 * dietary guidance is an upper limit rather than a target (cholesterol, the fat fractions, total
 * sugar) it is deliberately `null`: the UI renders that as "—", which is honest, whereas printing a
 * number next to a limit invites reading it as a goal to reach. Nothing here is medical advice
 * (AGENTS.md §10.3).
 */
object NutrientCatalog {

    val ALL: List<NutrientDefinition> = listOf(
        // ---------------------------------------------------------------- macro
        nutrient("calories", "Energy", "kcal", NutrientCategory.MACRO, 2000.0, 10),
        nutrient("protein", "Protein", "g", NutrientCategory.MACRO, 60.0, 20),
        nutrient("carbohydrates", "Carbohydrate", "g", NutrientCategory.MACRO, 275.0, 30),
        nutrient("fat", "Fat", "g", NutrientCategory.MACRO, 60.0, 40),
        nutrient("fiber", "Dietary fibre", "g", NutrientCategory.MACRO, 25.0, 50),
        nutrient("sugar", "Sugar", "g", NutrientCategory.MACRO, null, 60),

        // ---------------------------------------------------------------- lipids
        nutrient("saturated_fat", "Saturated fat", "g", NutrientCategory.LIPID, null, 70),
        nutrient("monounsaturated_fat", "Monounsaturated fat", "g", NutrientCategory.LIPID, null, 80),
        nutrient("polyunsaturated_fat", "Polyunsaturated fat", "g", NutrientCategory.LIPID, null, 90),
        nutrient("cholesterol", "Cholesterol", "mg", NutrientCategory.LIPID, null, 100),

        // ---------------------------------------------------------------- minerals
        nutrient("sodium", "Sodium", "mg", NutrientCategory.MINERAL, 2000.0, 110),
        nutrient("potassium", "Potassium", "mg", NutrientCategory.MINERAL, 2000.0, 120),
        nutrient("calcium", "Calcium", "mg", NutrientCategory.MINERAL, 800.0, 130),
        nutrient("iron", "Iron", "mg", NutrientCategory.MINERAL, 15.0, 140),
        nutrient("magnesium", "Magnesium", "mg", NutrientCategory.MINERAL, 330.0, 150),
        nutrient("phosphorus", "Phosphorus", "mg", NutrientCategory.MINERAL, 700.0, 160),
        nutrient("zinc", "Zinc", "mg", NutrientCategory.MINERAL, 12.5, 170),

        // ---------------------------------------------------------------- vitamins
        nutrient("vitamin_a", "Vitamin A", "μg", NutrientCategory.VITAMIN, 800.0, 180),
        nutrient("vitamin_c", "Vitamin C", "mg", NutrientCategory.VITAMIN, 100.0, 190),
        nutrient("vitamin_d", "Vitamin D", "μg", NutrientCategory.VITAMIN, 10.0, 200),
        nutrient("vitamin_b12", "Vitamin B12", "μg", NutrientCategory.VITAMIN, 2.4, 210),
        nutrient("folate", "Folate", "μg", NutrientCategory.VITAMIN, 400.0, 220),
    )

    /** Lookup by id, for turning a stored `nutrient_*` metric id back into metadata. */
    val byId: Map<String, NutrientDefinition> = ALL.associateBy { it.id }

    /**
     * Which end of a nutrient's reference intake is the one usually watched.
     *
     * For most nutrients an intake figure is a *target* — falling short is the watched end. For the
     * four whose guidance is an upper limit rather than a goal (added sugar, saturated fat,
     * cholesterol, sodium) the watched end is the top, and the diary's totals use that to say which
     * numbers are ceilings rather than quotas. It is also why three of those four carry no
     * [NutrientDefinition.dailyRecommended] at all: a limit is not a target, and printing one beside
     * the other invites reading it as one.
     *
     * The remaining fat fractions are absent rather than guessed at — there is no intake direction
     * to state for them. Nothing here is medical advice (AGENTS.md §10.3).
     */
    val concernById: Map<String, MetricConcern> = buildMap {
        // Limits: keep under these.
        listOf("sugar", "saturated_fat", "cholesterol", "sodium").forEach {
            put(it, MetricConcern.HIGHER_VALUES)
        }
        // Targets: reach these.
        listOf(
            "calories", "protein", "carbohydrates", "fat", "fiber",
            "potassium", "calcium", "iron", "magnesium", "phosphorus", "zinc",
            "vitamin_a", "vitamin_c", "vitamin_d", "vitamin_b12", "folate",
        ).forEach { put(it, MetricConcern.LOWER_VALUES) }
    }

    /** Display order of [ids], for ordering a set of nutrients that a food happens to contain. */
    fun sortByDisplayOrder(ids: Collection<String>): List<String> =
        ids.sortedBy { byId[it]?.displayOrder ?: Int.MAX_VALUE }

    private fun nutrient(
        id: String,
        name: String,
        unit: String,
        category: NutrientCategory,
        dailyRecommended: Double?,
        displayOrder: Int,
    ) = NutrientDefinition(
        id = id,
        name = name,
        unit = unit,
        category = category,
        dailyRecommended = dailyRecommended,
        displayOrder = displayOrder,
    )
}
