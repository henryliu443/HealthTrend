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

    /** Every metric's observations in `[from, to)`, ordered so a caller can group in a single pass. */
    @Query(
        """
        SELECT * FROM metric_observations
        WHERE timestamp >= :from AND timestamp < :to
        ORDER BY metric_id ASC, timestamp ASC
        """,
    )
    fun observeRangeAll(from: Long, to: Long): Flow<List<MetricObservationEntity>>

    /**
     * Newest observation per metric plus that metric's total sample count, in one index scan.
     *
     * Window functions need SQLite 3.25+, guaranteed from API 30 (this project's minSdk is 35). The
     * alternative — one range query per metric — is the N+1 pattern the composite
     * `(metric_id, timestamp)` index exists to avoid (AGENTS.md 4.4).
     *
     * The rank alias is `rn`, not `row_number`: Room's SQL parser treats `ROW_NUMBER` as a reserved
     * word and rejects it as a column alias.
     */
    @Query(
        """
        SELECT metric_id, timestamp, value, sample_count FROM (
            SELECT metric_id,
                   timestamp,
                   value,
                   COUNT(*) OVER (PARTITION BY metric_id) AS sample_count,
                   ROW_NUMBER() OVER (PARTITION BY metric_id ORDER BY timestamp DESC) AS rn
            FROM metric_observations
        )
        WHERE rn = 1
        """,
    )
    fun observeLatestPerMetric(): Flow<List<MetricLatestRow>>

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
