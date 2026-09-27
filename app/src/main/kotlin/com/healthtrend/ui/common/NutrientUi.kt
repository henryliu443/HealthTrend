package com.healthtrend.ui.common

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.res.stringResource
import com.healthtrend.R
import com.healthtrend.core.domain.model.MetricDefinition
import com.healthtrend.core.domain.model.NutritionMetricIds

/**
 * Localised names for the nutrients the app knows about.
 *
 * Nutrient *values* are stored per food and nutrient *definitions* live in the database, but a
 * database row cannot be in two languages at once: the stored `nutrient_definitions.name` is a
 * canonical English label (the same language as the USDA records the values come from), and the
 * user-facing name is resolved here at display time. That keeps the stored data stable — switching
 * the system language does not rewrite the user's rows — while still showing 能量 to a Chinese
 * reader.
 *
 * An id with no entry here is not an error: it is a nutrient the user invented, and the caller falls
 * back to the stored name, which is exactly right for something only they know about.
 */
@StringRes
internal fun nutrientNameRes(nutrientId: String): Int? = when (nutrientId) {
    "calories" -> R.string.nutrient_calories
    "protein" -> R.string.nutrient_protein
    "carbohydrates" -> R.string.nutrient_carbohydrates
    "fat" -> R.string.nutrient_fat
    "fiber" -> R.string.nutrient_fiber
    "sugar" -> R.string.nutrient_sugar
    "saturated_fat" -> R.string.nutrient_saturated_fat
    "monounsaturated_fat" -> R.string.nutrient_monounsaturated_fat
    "polyunsaturated_fat" -> R.string.nutrient_polyunsaturated_fat
    "cholesterol" -> R.string.nutrient_cholesterol
    "sodium" -> R.string.nutrient_sodium
    "potassium" -> R.string.nutrient_potassium
    "calcium" -> R.string.nutrient_calcium
    "iron" -> R.string.nutrient_iron
    "magnesium" -> R.string.nutrient_magnesium
    "phosphorus" -> R.string.nutrient_phosphorus
    "zinc" -> R.string.nutrient_zinc
    "vitamin_a" -> R.string.nutrient_vitamin_a
    "vitamin_c" -> R.string.nutrient_vitamin_c
    "vitamin_d" -> R.string.nutrient_vitamin_d
    "vitamin_b12" -> R.string.nutrient_vitamin_b12
    "folate" -> R.string.nutrient_folate
    else -> null
}

/** The display name of a nutrient, preferring the localised one and falling back to [storedName]. */
@Composable
@ReadOnlyComposable
internal fun nutrientName(nutrientId: String, storedName: String): String =
    nutrientNameRes(nutrientId)?.let { stringResource(it) } ?: storedName

/** The display name of a metric, preferring the localised nutrient name where there is one. */
@Composable
@ReadOnlyComposable
internal fun metricDisplayName(metricId: String, storedName: String): String = when {
    // Nutrients are projected into `metric_observations` as `nutrient_<id>` metrics (AGENTS.md §4.3),
    // so a projected metric is named from the nutrient catalogue rather than from the name the
    // projection happened to write.
    NutritionMetricIds.matches(metricId) ->
        nutrientName(metricId.removePrefix(NutritionMetricIds.PREFIX), storedName)

    // A metric the app itself seeds is named from the app's own table, so it reads correctly in
    // either language. A user-created metric has an id this knows nothing about and keeps its name.
    else -> builtInMetricNameRes(metricId)?.let { stringResource(it) } ?: storedName
}

/**
 * The display name of a metric.
 *
 * Nutrients are projected into `metric_observations` as `nutrient_<id>` metrics (AGENTS.md §4.3), so
 * a projected metric is named from the nutrient catalogue rather than from the name the projection
 * happened to write — that is what lets the diary's totals and the dashboard read correctly in
 * either language.
 */
@Composable
@ReadOnlyComposable
internal fun metricDisplayName(definition: MetricDefinition): String =
    metricDisplayName(definition.id, definition.name)
