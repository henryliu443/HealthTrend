package com.healthtrend.core.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Schema history (AGENTS.md §4.5).
 *
 * `exportSchema = true` means every version's shape is committed under `core/data/schemas/`, which is
 * what makes these steps reviewable and testable: `MetricDefinitionMigrationTest` builds a v1
 * database from the committed v1 JSON, runs [MIGRATION_1_2], and compares the result against the
 * committed v2 JSON.
 *
 * There is deliberately no `fallbackToDestructiveMigration()`. Losing a user's health history to
 * avoid writing a migration is not a trade this app makes.
 */

/**
 * v1 → v2: metric definitions learn which end of their reference range is the watched one.
 *
 * Nullable with no default, so existing rows read back as "not stated" rather than as a guess.
 */
internal val MIGRATION_1_2: Migration = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE metric_definitions ADD COLUMN concernDirection TEXT")
    }
}

/** Every migration, in order. Passed to the database builder. */
internal val ALL_MIGRATIONS: Array<Migration> = arrayOf(MIGRATION_1_2)
