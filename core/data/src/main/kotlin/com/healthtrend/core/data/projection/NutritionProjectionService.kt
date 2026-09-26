package com.healthtrend.core.data.projection

import androidx.room.withTransaction
import com.healthtrend.core.common.time.DayRange
import com.healthtrend.core.common.time.TimeKeys
import com.healthtrend.core.data.local.HealthTrendDatabase
import com.healthtrend.core.data.local.ObservationSources
import com.healthtrend.core.data.local.entity.MetricDefinitionEntity
import com.healthtrend.core.data.local.entity.MetricObservationEntity
import com.healthtrend.core.domain.model.MetricCategory
import com.healthtrend.core.domain.model.MetricDataType
import com.healthtrend.core.domain.model.NutritionMetricIds
import com.healthtrend.core.domain.nutrition.NutrientMath
import java.time.ZoneId
import java.util.UUID

/**
 * AGENTS.md 4.3 — automatic time-series projection.
 *
 * Whenever a meal log is added, edited or removed, the affected natural calendar day is
 * recomputed and rewritten into `metric_observations` with `source = "nutrition_agg"`, so the
 * analytics layer sees food intake through exactly the same channel as any health metric.
 *
 * The operation is idempotent: the day's previous aggregate rows are deleted before re-inserting,
 * all inside a single transaction.
 */
class NutritionProjectionService(
    private val database: HealthTrendDatabase,
    private val zoneId: ZoneId,
    private val idGenerator: () -> String = { UUID.randomUUID().toString() },
) {

    /** Recomputes the natural day (per [zoneId]) that contains [timestampEpochMilli]. */
    suspend fun recomputeDayContaining(timestampEpochMilli: Long) {
        recomputeDay(TimeKeys.dayRangeUtcMillis(timestampEpochMilli, zoneId))
    }

    /** Recomputes an explicit half-open day range. */
    suspend fun recomputeDay(range: DayRange) {
        database.withTransaction {
            val observationDao = database.metricObservationDao()
            observationDao.deleteBySourceAndTimestamp(ObservationSources.NUTRITION_AGG, range.startMillis)

            val rows = database.mealLogDao()
                .selectNutrientProjection(range.startMillis, range.endMillisExclusive)
            if (rows.isEmpty()) return@withTransaction

            val totals: Map<String, Double> = rows
                .groupBy { it.nutrientId }
                .mapValues { (_, group) ->
                    group.sumOf { row ->
                        // AGENTS.md §1.1: ratio against the food's own reference amount.
                        NutrientMath.scale(
                            amountPerReference = row.amountPerReference,
                            actualAmount = row.actualAmount,
                            referenceAmount = row.referenceAmount,
                        )
                    }
                }

            val definitions = database.nutrientDefinitionDao()
                .findByIds(totals.keys.toList())
                .associateBy { it.id }

            val entities = totals.map { (nutrientId, total) ->
                val definition = definitions[nutrientId]
                // Materialise the metric definition lazily; IGNORE keeps any user-edited row intact.
                database.metricDefinitionDao().insertIfAbsent(
                    nutritionMetricDefinition(
                        nutrientId = nutrientId,
                        name = definition?.name ?: nutrientId,
                        unit = definition?.unit.orEmpty(),
                    ),
                )
                MetricObservationEntity(
                    id = idGenerator(),
                    metricId = NutritionMetricIds.of(nutrientId),
                    timestamp = range.startMillis,
                    value = total,
                    unit = definition?.unit.orEmpty(),
                    source = ObservationSources.NUTRITION_AGG,
                    metadataJson = null,
                )
            }
            observationDao.upsertAll(entities)
        }
    }

    private fun nutritionMetricDefinition(
        nutrientId: String,
        name: String,
        unit: String,
    ): MetricDefinitionEntity = MetricDefinitionEntity(
        id = NutritionMetricIds.of(nutrientId),
        name = name,
        category = MetricCategory.NUTRITION.name,
        unit = unit,
        dataType = MetricDataType.NUMERIC.name,
        description = "Daily aggregated intake, auto-projected from meal logs",
        expectedFrequency = "DAILY",
        minValue = 0.0,
        maxValue = null,
        referenceRangeLow = null,
        referenceRangeHigh = null,
        isBuiltIn = true,
        displayOrder = NUTRITION_DISPLAY_ORDER,
    )

    private companion object {
        const val NUTRITION_DISPLAY_ORDER = 1_000
    }
}
