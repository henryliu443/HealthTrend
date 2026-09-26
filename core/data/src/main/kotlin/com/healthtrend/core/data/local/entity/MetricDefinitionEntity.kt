package com.healthtrend.core.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** AGENTS.md 4.1 — `metric_definitions`. */
@Entity(
    tableName = "metric_definitions",
    indices = [
        Index(value = ["category"]),
        Index(value = ["name"], unique = true),
    ],
)
data class MetricDefinitionEntity(
    @PrimaryKey val id: String,
    val name: String,
    val category: String, // BODY, NUTRITION, ACTIVITY, LIFESTYLE, HEALTH, CUSTOM
    val unit: String,
    val dataType: String, // NUMERIC, BOOLEAN, DURATION
    val description: String,
    val expectedFrequency: String?,
    val minValue: Double?,
    val maxValue: Double?,
    val referenceRangeLow: Double?,
    val referenceRangeHigh: Double?,
    val isBuiltIn: Boolean,
    val displayOrder: Int,
)
