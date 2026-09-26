package com.healthtrend.core.data.local

import com.healthtrend.core.domain.model.ObservationSource

/** String values persisted in `metric_observations.source` (AGENTS.md 4.1). */
object ObservationSources {
    const val MANUAL = "manual"
    const val NUTRITION_AGG = "nutrition_agg"
    const val HEALTH_CONNECT = "health_connect"

    fun of(source: ObservationSource): String = when (source) {
        ObservationSource.MANUAL -> MANUAL
        ObservationSource.NUTRITION_AGG -> NUTRITION_AGG
        ObservationSource.HEALTH_CONNECT -> HEALTH_CONNECT
    }

    fun parse(raw: String): ObservationSource = when (raw) {
        MANUAL -> ObservationSource.MANUAL
        NUTRITION_AGG -> ObservationSource.NUTRITION_AGG
        HEALTH_CONNECT -> ObservationSource.HEALTH_CONNECT
        else -> throw IllegalArgumentException("Unknown observation source: $raw")
    }
}
