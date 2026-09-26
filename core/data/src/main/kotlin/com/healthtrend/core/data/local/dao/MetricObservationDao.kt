package com.healthtrend.core.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.healthtrend.core.data.local.entity.MetricObservationEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MetricObservationDao {

    /**
     * Range query that rides the composite `(metric_id, timestamp)` index — AGENTS.md 4.4.
     * Half-open interval `[from, to)`.
     */
    @Query(
        """
        SELECT * FROM metric_observations
        WHERE metric_id = :metricId AND timestamp >= :from AND timestamp < :to
        ORDER BY timestamp ASC
        """,
    )
    fun observeRange(metricId: String, from: Long, to: Long): Flow<List<MetricObservationEntity>>

    @Query("SELECT * FROM metric_observations WHERE metric_id = :metricId AND timestamp = :timestamp")
    suspend fun findByMetricAndTimestamp(metricId: String, timestamp: Long): List<MetricObservationEntity>

    @Query("SELECT * FROM metric_observations WHERE source = :source AND timestamp = :dayStart")
    suspend fun findBySourceAndTimestamp(source: String, dayStart: Long): List<MetricObservationEntity>

    /** Idempotency support for the projection: drop the day's previous aggregate before re-inserting. */
    @Query("DELETE FROM metric_observations WHERE source = :source AND timestamp = :dayStart")
    suspend fun deleteBySourceAndTimestamp(source: String, dayStart: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: MetricObservationEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<MetricObservationEntity>)

    @Query("DELETE FROM metric_observations WHERE id = :id")
    suspend fun deleteById(id: String)
}
