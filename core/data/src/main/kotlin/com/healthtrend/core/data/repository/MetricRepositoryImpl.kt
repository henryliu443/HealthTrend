package com.healthtrend.core.data.repository

import com.healthtrend.core.data.local.dao.MetricDefinitionDao
import com.healthtrend.core.data.local.dao.MetricObservationDao
import com.healthtrend.core.data.mapper.toDomain
import com.healthtrend.core.data.mapper.toEntity
import com.healthtrend.core.domain.model.MetricDefinition
import com.healthtrend.core.domain.model.MetricObservation
import com.healthtrend.core.domain.repository.MetricRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class MetricRepositoryImpl(
    private val definitionDao: MetricDefinitionDao,
    private val observationDao: MetricObservationDao,
) : MetricRepository {

    override fun observeDefinitions(): Flow<List<MetricDefinition>> =
        definitionDao.observeAll().map { entities -> entities.map { it.toDomain() } }

    override fun observeObservation(
        metricId: String,
        fromEpochMilli: Long,
        toEpochMilliExclusive: Long,
    ): Flow<List<MetricObservation>> =
        observationDao.observeRange(metricId, fromEpochMilli, toEpochMilliExclusive)
            .map { entities -> entities.map { it.toDomain() } }

    override suspend fun getDefinition(metricId: String): MetricDefinition? =
        definitionDao.findById(metricId)?.toDomain()

    override suspend fun upsertDefinition(definition: MetricDefinition) =
        definitionDao.upsert(definition.toEntity())

    override suspend fun upsertObservation(observation: MetricObservation) =
        observationDao.upsert(observation.toEntity())
}
