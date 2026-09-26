package com.healthtrend.core.data.local.dao

import androidx.room.ColumnInfo

/**
 * Projection returned by [MetricObservationDao.observeLatestPerMetric].
 *
 * Room maps this from a query alias rather than from a table, so it is a plain data class and not an
 * `@Entity`.
 */
data class MetricLatestRow(
    @ColumnInfo(name = "metric_id") val metricId: String,
    @ColumnInfo(name = "timestamp") val timestamp: Long,
    @ColumnInfo(name = "value") val value: Double,
    @ColumnInfo(name = "sample_count") val sampleCount: Int,
)
