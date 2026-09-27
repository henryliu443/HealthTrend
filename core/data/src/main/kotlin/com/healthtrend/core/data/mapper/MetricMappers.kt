package com.healthtrend.core.data.mapper

import com.healthtrend.core.data.local.ObservationSources
import com.healthtrend.core.data.local.entity.MetricDefinitionEntity
import com.healthtrend.core.data.local.entity.MetricObservationEntity
import com.healthtrend.core.domain.model.MetricCategory
import com.healthtrend.core.domain.model.MetricConcern
import com.healthtrend.core.domain.model.MetricDataType
import com.healthtrend.core.domain.model.MetricDefinition
import com.healthtrend.core.domain.model.MetricObservation

internal inline fun <reified T : Enum<T>> enumOrDefault(name: String, default: T): T =
    enumValues<T>().firstOrNull { it.name.equals(name, ignoreCase = true) } ?: default

/** Like [enumOrDefault], but "absent" stays absent instead of collapsing into a default. */
internal inline fun <reified T : Enum<T>> enumOrNull(name: String?): T? =
    name?.let { stored -> enumValues<T>().firstOrNull { it.name.equals(stored, ignoreCase = true) } }

fun MetricDefinitionEntity.toDomain(): MetricDefinition = MetricDefinition(
    id = id,
    name = name,
    category = enumOrDefault(category, MetricCategory.CUSTOM),
    unit = unit,
    dataType = enumOrDefault(dataType, MetricDataType.NUMERIC),
    description = description,
    expectedFrequency = expectedFrequency,
    minValue = minValue,
    maxValue = maxValue,
    referenceRangeLow = referenceRangeLow,
    referenceRangeHigh = referenceRangeHigh,
    isBuiltIn = isBuiltIn,
    displayOrder = displayOrder,
    concern = enumOrNull<MetricConcern>(concernDirection),
)

fun MetricDefinition.toEntity(): MetricDefinitionEntity = MetricDefinitionEntity(
    id = id,
    name = name,
    category = category.name,
    unit = unit,
    dataType = dataType.name,
    description = description,
    expectedFrequency = expectedFrequency,
    minValue = minValue,
    maxValue = maxValue,
    referenceRangeLow = referenceRangeLow,
    referenceRangeHigh = referenceRangeHigh,
    isBuiltIn = isBuiltIn,
    displayOrder = displayOrder,
    concernDirection = concern?.name,
)

fun MetricObservationEntity.toDomain(): MetricObservation = MetricObservation(
    id = id,
    metricId = metricId,
    timestampEpochMilli = timestamp,
    value = value,
    unit = unit,
    source = ObservationSources.parse(source),
    metadataJson = metadataJson,
)

fun MetricObservation.toEntity(): MetricObservationEntity = MetricObservationEntity(
    id = id,
    metricId = metricId,
    timestamp = timestampEpochMilli,
    value = value,
    unit = unit,
    source = ObservationSources.of(source),
    metadataJson = metadataJson,
)
