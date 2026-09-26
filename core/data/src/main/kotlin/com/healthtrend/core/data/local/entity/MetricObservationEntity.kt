package com.healthtrend.core.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** AGENTS.md 4.1 — `metric_observations`. Integer time is UTC epoch milliseconds. */
@Entity(
    tableName = "metric_observations",
    foreignKeys = [
        ForeignKey(
            entity = MetricDefinitionEntity::class,
            parentColumns = ["id"],
            childColumns = ["metric_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["metric_id", "timestamp"]), // composite covering index (AGENTS.md 4.4)
        Index(value = ["timestamp"]),
    ],
)
data class MetricObservationEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "metric_id") val metricId: String,
    @ColumnInfo(name = "timestamp") val timestamp: Long,
    @ColumnInfo(name = "value") val value: Double,
    @ColumnInfo(name = "unit") val unit: String,
    @ColumnInfo(name = "source") val source: String, // manual, nutrition_agg, health_connect
    @ColumnInfo(name = "metadata_json") val metadataJson: String?,
)
