package com.healthtrend.core.domain.model

/** Mirrors `metric_observations.source` (AGENTS.md 4.1). */
enum class ObservationSource { MANUAL, NUTRITION_AGG, HEALTH_CONNECT }

/**
 * A single time-series sample. `timestampEpochMilli` is always UTC epoch milliseconds
 * (AGENTS.md §10.1); formatting into local dates happens only in the UI layer.
 */
data class MetricObservation(
    val id: String,
    val metricId: String,
    val timestampEpochMilli: Long,
    val value: Double,
    val unit: String,
    val source: ObservationSource,
    val metadataJson: String? = null,
)

/**
 * Naming convention that unifies the nutrition ingestion domain with the metric time-series
 * domain (AGENTS.md 1.3): every nutrient is projected onto a metric id of this shape.
 */
object NutritionMetricIds {
    const val PREFIX = "nutrient_"

    fun of(nutrientId: String): String = PREFIX + nutrientId

    /** `true` when [metricId] was produced by the nutrition projection rather than entered by hand. */
    fun matches(metricId: String): Boolean = metricId.startsWith(PREFIX)
}

/**
 * The newest observation of a metric plus how many observations it has in total.
 *
 * This is a dedicated read model rather than a derived value because computing it from
 * `observeObservation` would require one range query per metric — precisely the N+1 pattern the
 * composite `(metric_id, timestamp)` index exists to avoid (AGENTS.md §4.4).
 */
data class MetricLatestValue(
    val metricId: String,
    val timestampEpochMilli: Long,
    val value: Double,
    val sampleCount: Int,
)
