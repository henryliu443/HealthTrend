package com.healthtrend.core.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.healthtrend.core.data.local.entity.MetricDefinitionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MetricDefinitionDao {

        /** Whole-table read for export; ordered so two exports of the same data are identical. */
    @Query("SELECT * FROM metric_definitions ORDER BY displayOrder ASC, id ASC")
    suspend fun findAll(): List<MetricDefinitionEntity>

@Query("SELECT * FROM metric_definitions ORDER BY displayOrder ASC")
    fun observeAll(): Flow<List<MetricDefinitionEntity>>

    @Query("SELECT * FROM metric_definitions WHERE id = :id LIMIT 1")
    suspend fun findById(id: String): MetricDefinitionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: MetricDefinitionEntity)

    /**
     * Insert only if absent. Used by the nutrition projection so that a nutrient metric
     * definition can be materialised lazily without clobbering user edits.
     * @return the new rowId, or -1 when the id already existed.
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfAbsent(entity: MetricDefinitionEntity): Long
}
