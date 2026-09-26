package com.healthtrend.core.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.healthtrend.core.data.local.entity.NutrientDefinitionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface NutrientDefinitionDao {

    @Query("SELECT * FROM nutrient_definitions ORDER BY displayOrder ASC")
    fun observeAll(): Flow<List<NutrientDefinitionEntity>>

    @Query("SELECT * FROM nutrient_definitions WHERE id = :id LIMIT 1")
    suspend fun findById(id: String): NutrientDefinitionEntity?

    @Query("SELECT * FROM nutrient_definitions WHERE id IN (:ids)")
    suspend fun findByIds(ids: List<String>): List<NutrientDefinitionEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<NutrientDefinitionEntity>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfAbsent(entity: NutrientDefinitionEntity): Long
}
