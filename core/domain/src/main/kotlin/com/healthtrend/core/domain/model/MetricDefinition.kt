package com.healthtrend.core.domain.model

/** Metric taxonomy, mirrors `metric_definitions.category` (AGENTS.md 4.1). */
enum class MetricCategory { BODY, NUTRITION, ACTIVITY, LIFESTYLE, HEALTH, CUSTOM }

/** Mirrors `metric_definitions.data_type` (AGENTS.md 4.1). */
enum class MetricDataType { NUMERIC, BOOLEAN, DURATION }

/**
 * Which end of a metric's range is usually the one worth watching.
 *
 * This is a property of the *measurement*, not a verdict about a person. ALT and daily steps both
 * have a reference range; only one of them is a range you want to stay under. Recording that here
 * lets the UI point at the right end without inventing a judgement — "higher values are usually the
 * ones watched" is a statement about a reference range, whereas "high is bad" would be a diagnosis,
 * which this app must not make (AGENTS.md §10.3).
 *
 * `null` in [MetricDefinition.concern] means *not stated*, which is deliberately distinct from
 * [BOTH_ENDS]: for a habit counter there is no direction to state.
 */
enum class MetricConcern {
    /** The upper end is the watched one — liver enzymes, cholesterol, blood pressure. */
    HIGHER_VALUES,

    /** The lower end is the watched one — step counts, sleep duration. */
    LOWER_VALUES,

    /** Either end can be the watched one — body weight against a target band. */
    BOTH_ENDS,
}

/**
 * A metric that can be observed over time (weight, ALT, protein intake, ...).
 * Deliberately generic: nutrition metrics are just metrics whose id follows
 * [NutritionMetricIds] (AGENTS.md 1.3).
 */
data class MetricDefinition(
    val id: String,
    val name: String,
    val category: MetricCategory,
    val unit: String,
    val dataType: MetricDataType,
    val description: String,
    val expectedFrequency: String?,
    val minValue: Double?,
    val maxValue: Double?,
    val referenceRangeLow: Double?,
    val referenceRangeHigh: Double?,
    val isBuiltIn: Boolean,
    val displayOrder: Int,
    /**
     * Which end of [referenceRangeLow]..[referenceRangeHigh] is the one usually watched; `null` when
     * the metric does not say. See [MetricConcern].
     */
    val concern: MetricConcern? = null,
)
