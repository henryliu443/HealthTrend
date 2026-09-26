package com.healthtrend.core.data.local

import androidx.room.TypeConverter
import java.time.Instant

/**
 * AGENTS.md 4.5 — the only persisted time representation is UTC epoch milliseconds (`Long`).
 * The [Instant] form is used by domain/analytics code, never by the entity tables directly.
 */
class CommonConverters {

    @TypeConverter
    fun fromEpoch(value: Long?): Instant? = value?.let(Instant::ofEpochMilli)

    @TypeConverter
    fun toEpoch(instant: Instant?): Long? = instant?.toEpochMilli()
}
