package com.healthtrend.core.data.local

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.healthtrend.core.data.mapper.toDomain
import com.healthtrend.core.data.mapper.toEntity
import com.healthtrend.core.domain.model.MetricCategory
import com.healthtrend.core.domain.model.MetricConcern
import com.healthtrend.core.domain.model.MetricDataType
import com.healthtrend.core.domain.model.MetricDefinition
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * AGENTS.md §4.5 requires a unit test for every migration, and this is the first one.
 *
 * The v1 database is built from the **committed** `1.json` rather than from a DDL string retyped
 * here, so the test cannot drift away from what a shipped v1 install actually has on disk. After the
 * migration the database is opened with Room, which validates the resulting schema against the
 * current entity definitions during `onUpgrade` and throws if they disagree — so a migration that
 * forgets a column, or adds one with the wrong affinity, fails here rather than on a user's phone.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MetricDefinitionMigrationTest {

    private lateinit var context: Context
    private lateinit var databaseFile: File
    private var database: HealthTrendDatabase? = null

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        databaseFile = File(context.cacheDir, "migration-${System.nanoTime()}.db")
    }

    @After
    fun tearDown() {
        database?.close()
        databaseFile.delete()
    }

    @Test
    fun `v1 to v2 adds a nullable concern column and keeps existing definitions`() = runTest {
        writeVersionOneDatabase()

        database = openMigratedDatabase()
        val definitions = database!!.metricDefinitionDao().observeAll().first()

        definitions.size shouldBe 1
        val alt = definitions.single()
        alt.id shouldBe "alt"
        alt.name shouldBe "ALT"
        alt.referenceRangeLow shouldBe 7.0
        alt.referenceRangeHigh shouldBe 40.0
        alt.concernDirection.shouldBeNull()
    }

    @Test
    fun `a concern round-trips through the mapper`() = runTest {
        database = openMigratedDatabase()
        val dao = database!!.metricDefinitionDao()

        dao.upsert(definition(id = "alt", concern = MetricConcern.HIGHER_VALUES).toEntity())
        dao.upsert(definition(id = "daily_steps", concern = MetricConcern.LOWER_VALUES).toEntity())
        dao.upsert(definition(id = "note").toEntity())

        val stored = dao.observeAll().first().associateBy { it.id }
        stored.getValue("alt").concernDirection shouldBe "HIGHER_VALUES"
        stored.getValue("daily_steps").concernDirection shouldBe "LOWER_VALUES"
        stored.getValue("note").concernDirection.shouldBeNull()

        // And back out again: the mapper must not turn an absent value into a default.
        stored.getValue("alt").toDomain().concern shouldBe MetricConcern.HIGHER_VALUES
        stored.getValue("daily_steps").toDomain().concern shouldBe MetricConcern.LOWER_VALUES
        stored.getValue("note").toDomain().concern.shouldBeNull()
    }

    // ------------------------------------------------------------------ helpers

    private fun definition(id: String, concern: MetricConcern? = null) = MetricDefinition(
        id = id,
        name = id,
        category = MetricCategory.HEALTH,
        unit = "U/L",
        dataType = MetricDataType.NUMERIC,
        description = "test",
        expectedFrequency = null,
        minValue = null,
        maxValue = null,
        referenceRangeLow = null,
        referenceRangeHigh = null,
        isBuiltIn = true,
        displayOrder = 0,
        concern = concern,
    )

    private fun writeVersionOneDatabase() {
        val legacy = SQLiteDatabase.openOrCreateDatabase(databaseFile, null)
        try {
            // Every table *and* every index of v1, because Room's migration validation compares the
            // whole schema — a fixture missing a table fails for a reason that has nothing to do
            // with the migration under test.
            schemaStatements(version = 1).forEach { (tableName, statement) ->
                legacy.execSQL(statement.replace("\${TABLE_NAME}", tableName))
            }
            legacy.execSQL(
                """
                INSERT INTO metric_definitions
                    (id, name, category, unit, dataType, description, expectedFrequency,
                     minValue, maxValue, referenceRangeLow, referenceRangeHigh, isBuiltIn, displayOrder)
                VALUES
                    ('alt', 'ALT', 'HEALTH', 'U/L', 'NUMERIC', 'Liver panel', 'MONTHLY',
                     0.0, 2000.0, 7.0, 40.0, 1, 60)
                """.trimIndent(),
            )
            legacy.version = 1
        } finally {
            legacy.close()
        }
    }

    private fun openMigratedDatabase(): HealthTrendDatabase =
        Room.databaseBuilder(context, HealthTrendDatabase::class.java, databaseFile.absolutePath)
            .addMigrations(*ALL_MIGRATIONS)
            .allowMainThreadQueries()
            .build()

    /**
     * Every `(tableName, createStatement)` Room exported for [version].
     *
     * Read from the committed schema rather than retyped, so the fixture is what a real install of
     * that version has on disk — see `exportSchema` / `room.schemaLocation`.
     */
    private fun schemaStatements(version: Int): List<Pair<String, String>> {
        val candidates = SCHEMA_DIRECTORIES.map { directory -> "$directory/$version.json" }
        val file = candidates.firstOrNull { File(it).isFile }
            ?: error("exported schema v$version not found; looked in $candidates")
        val database = JSONObject(File(file).readText()).getJSONObject("database")
        val entities = database.getJSONArray("entities")
        val statements = mutableListOf<Pair<String, String>>()
        for (index in 0 until entities.length()) {
            val entity = entities.getJSONObject(index)
            val tableName = entity.getString("tableName")
            statements += tableName to entity.getString("createSql")
            val indices = entity.optJSONArray("indices") ?: continue
            for (indexEntry in 0 until indices.length()) {
                statements += tableName to indices.getJSONObject(indexEntry).getString("createSql")
            }
        }
        return statements
    }

    private companion object {
        /** Tried in order, so the test works whether Gradle runs it from the module or the root. */
        val SCHEMA_DIRECTORIES = listOf(
            "schemas/com.healthtrend.core.data.local.HealthTrendDatabase",
            "core/data/schemas/com.healthtrend.core.data.local.HealthTrendDatabase",
        )
    }
}
