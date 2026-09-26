package com.healthtrend.core.domain.repository

import com.healthtrend.core.domain.model.MetricDefinition
import com.healthtrend.core.domain.model.MetricLatestValue
import com.healthtrend.core.domain.model.MetricObservation
import kotlinx.coroutines.flow.Flow

/**
 * Read/write access to metric definitions and their observations.
 * Implemented in `:core:data`; the analytics engine consumes observations through this contract.
 */
interface MetricRepository {

    fun observeDefinitions(): Flow<List<MetricDefinition>>

    fun observeObservation(
        metricId: String,
        fromEpochMilli: Long,
        toEpochMilliExclusive: Long,
    ): Flow<List<MetricObservation>>

    /** Every observation in `[from, to)`, across all metrics — used by the nutrition day summary. */
    fun observeObservations(
        fromEpochMilli: Long,
        toEpochMilliExclusive: Long,
    ): Flow<List<MetricObservation>>

    /** Newest value and total sample count per metric, for the dashboard's metric cards. */
    fun observeLatestValues(): Flow<List<MetricLatestValue>>

    suspend fun getDefinition(metricId: String): MetricDefinition?

    suspend fun upsertDefinition(definition: MetricDefinition)

    suspend fun upsertObservation(observation: MetricObservation)
}
