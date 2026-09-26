package com.healthtrend.core.domain.nutrition

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
 * `dailyRecommended` is a **reference intake** for orientation, in the nutrient's own unit. Where
 * dietary guidance is an upper limit rather than a target (cholesterol, the fat fractions, total
 * sugar) it is deliberately `null`: the UI renders that as "—", which is honest, whereas printing a
 * number next to a limit invites reading it as a goal to reach. Nothing here is medical advice
 * (AGENTS.md §10.3).
 */
object NutrientCatalog {

    val ALL: List<NutrientDefinition> = listOf(
        // ---------------------------------------------------------------- macro
        nutrient("calories", "能量", "kcal", NutrientCategory.MACRO, 2000.0, 10),
        nutrient("protein", "蛋白质", "g", NutrientCategory.MACRO, 60.0, 20),
        nutrient("carbohydrates", "碳水化合物", "g", NutrientCategory.MACRO, 275.0, 30),
        nutrient("fat", "脂肪", "g", NutrientCategory.MACRO, 60.0, 40),
        nutrient("fiber", "膳食纤维", "g", NutrientCategory.MACRO, 25.0, 50),
        nutrient("sugar", "糖", "g", NutrientCategory.MACRO, null, 60),

        // ---------------------------------------------------------------- lipids
        nutrient("saturated_fat", "饱和脂肪", "g", NutrientCategory.LIPID, null, 70),
        nutrient("monounsaturated_fat", "单不饱和脂肪", "g", NutrientCategory.LIPID, null, 80),
        nutrient("polyunsaturated_fat", "多不饱和脂肪", "g", NutrientCategory.LIPID, null, 90),
        nutrient("cholesterol", "胆固醇", "mg", NutrientCategory.LIPID, null, 100),

        // ---------------------------------------------------------------- minerals
        nutrient("sodium", "钠", "mg", NutrientCategory.MINERAL, 2000.0, 110),
        nutrient("potassium", "钾", "mg", NutrientCategory.MINERAL, 2000.0, 120),
        nutrient("calcium", "钙", "mg", NutrientCategory.MINERAL, 800.0, 130),
        nutrient("iron", "铁", "mg", NutrientCategory.MINERAL, 15.0, 140),
        nutrient("magnesium", "镁", "mg", NutrientCategory.MINERAL, 330.0, 150),
        nutrient("phosphorus", "磷", "mg", NutrientCategory.MINERAL, 700.0, 160),
        nutrient("zinc", "锌", "mg", NutrientCategory.MINERAL, 12.5, 170),

        // ---------------------------------------------------------------- vitamins
        nutrient("vitamin_a", "维生素 A", "μg", NutrientCategory.VITAMIN, 800.0, 180),
        nutrient("vitamin_c", "维生素 C", "mg", NutrientCategory.VITAMIN, 100.0, 190),
        nutrient("vitamin_d", "维生素 D", "μg", NutrientCategory.VITAMIN, 10.0, 200),
        nutrient("vitamin_b12", "维生素 B12", "μg", NutrientCategory.VITAMIN, 2.4, 210),
        nutrient("folate", "叶酸", "μg", NutrientCategory.VITAMIN, 400.0, 220),
    )

    /** Lookup by id, for turning a stored `nutrient_*` metric id back into metadata. */
    val byId: Map<String, NutrientDefinition> = ALL.associateBy { it.id }

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
