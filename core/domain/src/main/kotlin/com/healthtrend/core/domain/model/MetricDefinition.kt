package com.healthtrend.core.domain.model

/** Metric taxonomy, mirrors `metric_definitions.category` (AGENTS.md 4.1). */
enum class MetricCategory { BODY, NUTRITION, ACTIVITY, LIFESTYLE, HEALTH, CUSTOM }

/** Mirrors `metric_definitions.data_type` (AGENTS.md 4.1). */
enum class MetricDataType { NUMERIC, BOOLEAN, DURATION }

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
)
