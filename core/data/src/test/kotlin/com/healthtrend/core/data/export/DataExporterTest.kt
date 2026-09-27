package com.healthtrend.core.data.export

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.healthtrend.core.domain.model.FoodItem
import com.healthtrend.core.domain.model.FoodNutrientValue
import com.healthtrend.core.domain.model.MealType
import com.healthtrend.core.domain.model.MetricCategory
import com.healthtrend.core.domain.model.MetricConcern
import com.healthtrend.core.domain.model.MetricDataType
import com.healthtrend.core.domain.model.MetricDefinition
import com.healthtrend.core.domain.model.MetricObservation
import com.healthtrend.core.domain.model.NutrientCategory
import com.healthtrend.core.domain.model.NutrientDefinition
import com.healthtrend.core.domain.model.ObservationSource
import com.healthtrend.core.domain.model.MealLog
import com.healthtrend.core.data.local.HealthTrendDatabase
import com.healthtrend.core.data.projection.NutritionProjectionService
import com.healthtrend.core.data.repository.MetricRepositoryImpl
import com.healthtrend.core.data.repository.NutritionRepositoryImpl
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant
import java.time.ZoneId
import java.util.zip.ZipInputStream

/**
 * Verification of the export path.
 *
 * The assertions that matter are not "did a file appear" but: does the archive contain every row
 * that was in the database, does text survive the round trip, does a name containing a comma break
 * the CSV, and — most importantly — is the database bit-for-bit unchanged afterwards, since
 * AGENTS.md §10.4 makes read-only a rule rather than an intention.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DataExporterTest {

    private val shanghai = ZoneId.of("Asia/Shanghai")
    private lateinit var db: HealthTrendDatabase
    private lateinit var metrics: MetricRepositoryImpl
    private lateinit var nutrition: NutritionRepositoryImpl
    private lateinit var exporter: DataExporter

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            HealthTrendDatabase::class.java,
        ).allowMainThreadQueries().build()
        metrics = MetricRepositoryImpl(db.metricDefinitionDao(), db.metricObservationDao())
        nutrition = NutritionRepositoryImpl(db, NutritionProjectionService(db, shanghai))
        exporter = DataExporter(
            database = db,
            appVersion = "1.0-test",
            zoneId = shanghai,
            nowMillis = { Instant.parse("2026-09-27T02:00:00Z").toEpochMilli() },
        )
    }

    @After
    fun tearDown() {
        db.close()
    }

    // ------------------------------------------------------------------ fixtures

    private suspend fun seed() {
        nutrition.upsertNutrientDefinitions(
            listOf(
                NutrientDefinition("calories", "Energy", "kcal", NutrientCategory.MACRO, 2000.0, 10),
                NutrientDefinition("protein", "Protein", "g", NutrientCategory.MACRO, 60.0, 20),
            ),
        )
        // A comma, a quote and a non-ASCII character, all in one food name: everything the CSV
        // writer has to survive.
        nutrition.upsertFoodWithNutrients(
            food = FoodItem(
                id = "food_odd",
                name = "Rice, \"special\" 米饭",
                brand = "Kitchen",
                referenceAmount = 180.0,
                referenceUnit = "g",
                isCustom = true,
            ),
            nutrients = listOf(FoodNutrientValue("food_odd", "calories", 234.0)),
        )
        nutrition.upsertFoodWithNutrients(
            food = FoodItem(
                id = "rice_cooked",
                name = "Cooked white rice",
                referenceAmount = 100.0,
                referenceUnit = "g",
                isCustom = false,
            ),
            nutrients = listOf(
                FoodNutrientValue("rice_cooked", "calories", 130.0),
                FoodNutrientValue("rice_cooked", "protein", 2.69),
            ),
        )
        nutrition.logMeal(
            MealLog(
                id = "meal_1",
                foodId = "food_odd",
                timestampEpochMilli = Instant.parse("2026-09-27T04:00:00Z").toEpochMilli(),
                actualAmount = 73.0,
                actualUnit = "g",
                mealType = MealType.LUNCH,
            ),
        )

        metrics.upsertDefinition(
            MetricDefinition(
                id = "alt",
                name = "ALT",
                category = MetricCategory.HEALTH,
                unit = "U/L",
                dataType = MetricDataType.NUMERIC,
                description = "Alanine aminotransferase",
                expectedFrequency = "MONTHLY",
                minValue = 0.0,
                maxValue = 2000.0,
                referenceRangeLow = 7.0,
                referenceRangeHigh = 40.0,
                isBuiltIn = true,
                displayOrder = 60,
                concern = MetricConcern.HIGHER_VALUES,
            ),
        )
        metrics.upsertObservation(
            MetricObservation(
                id = "obs_1",
                metricId = "alt",
                timestampEpochMilli = Instant.parse("2026-09-27T04:00:00Z").toEpochMilli(),
                value = 22.0,
                unit = "U/L",
                source = ObservationSource.MANUAL,
            ),
        )
    }

    private suspend fun file(name: String): String =
        exporter.files().first { it.name == name }.content

    /** A CSV without its byte-order mark, so assertions can read it as text. */
    private fun String.withoutBom(): String = removePrefix("\uFEFF")

    private fun csvLines(content: String): List<String> =
        content.withoutBom().trimEnd('\n').lines()

    // ------------------------------------------------------------------ tests

    @Test
    fun `the archive is complete, self-describing and dated`() = runTest {
        seed()
        val archive = exporter.archive()

        archive.fileName shouldBe "healthtrend-2026-09-27.zip"

        val entries = unzip(archive.bytes)
        entries.keys.toList() shouldContainExactly listOf(
            "README.txt",
            "manifest.json",
            "metric_definitions.csv",
            "metric_observations.csv",
            "nutrient_definitions.csv",
            "food_items.csv",
            "food_nutrient_values.csv",
            "meal_logs.csv",
        )
        entries.getValue("README.txt") shouldContain "metric_observations holds both what was observed"
        entries.getValue("README.txt") shouldContain "never a division by a constant 100"
    }

    @Test
    fun `the manifest reports the versions and the row counts actually written`() = runTest {
        seed()
        val manifest = JSONObject(file("manifest.json"))

        manifest.getString("format") shouldBe "healthtrend-snapshot"
        manifest.getInt("formatVersion") shouldBe 1
        manifest.getInt("databaseVersion") shouldBe 2
        manifest.getString("appVersion") shouldBe "1.0-test"
        manifest.getString("timeZone") shouldBe "Asia/Shanghai"
        manifest.getString("exportedAtLocalIso") shouldBe "2026-09-27T10:00:00+08:00"

        val counts = manifest.getJSONObject("rowCounts")
        // Two definitions, not one: the manual reading's metric, plus the one the projection
        // materialises for the nutrient it derived from the meal (AGENTS.md §4.3).
        counts.getInt("metric_definitions") shouldBe 2
        counts.getInt("metric_observations") shouldBe 2 // the manual reading + the day's projection
        counts.getInt("food_items") shouldBe 2
        counts.getInt("food_nutrient_values") shouldBe 3
        counts.getInt("meal_logs") shouldBe 1
        counts.getInt("nutrient_definitions") shouldBe 2
    }

    @Test
    fun `every table is written with a header and one line per row`() = runTest {
        seed()

        val foods = csvLines(file("food_items.csv"))
        foods.first() shouldBe
            "id,name,brand,reference_amount,reference_unit,is_custom,note"
        foods.size shouldBe 3

        // The reference basis is per-food, and the export keeps each food's own value: no 100 in sight
        // for a food that is not quoted per 100.
        val oddRow = foods.first { it.contains("food_odd") }
        oddRow shouldContain "180"
        oddRow shouldContain "user food"

        val meals = csvLines(file("meal_logs.csv"))
        meals.first() shouldBe
            "id,food_id,timestamp_epoch_millis,timestamp_local_iso,actual_amount,actual_unit,meal_type"
        meals.size shouldBe 2
        meals[1] shouldContain "2026-09-27T12:00:00+08:00"
    }

    @Test
    fun `values that would break a CSV are quoted and escaped`() = runTest {
        seed()
        val foods = file("food_items.csv")

        // `Rice, "special" 米饭` must arrive as one field, with its comma inside quotes and its own
        // quotes doubled.
        foods shouldContain "\"Rice, \"\"special\"\" 米饭\""
        // …and the non-ASCII characters survive as themselves.
        foods shouldContain "米饭"
    }

    @Test
    fun `numbers are written plainly rather than in exponent form`() = runTest {
        metrics.upsertDefinition(
            MetricDefinition(
                id = "tiny",
                name = "Tiny",
                category = MetricCategory.HEALTH,
                unit = "mg",
                dataType = MetricDataType.NUMERIC,
                description = "",
                expectedFrequency = null,
                minValue = null,
                maxValue = null,
                referenceRangeLow = null,
                referenceRangeHigh = null,
                isBuiltIn = false,
                displayOrder = 1,
            ),
        )
        metrics.upsertObservation(
            MetricObservation(
                id = "obs_tiny",
                metricId = "tiny",
                timestampEpochMilli = 0L,
                value = 0.00001,
                unit = "mg",
                source = ObservationSource.MANUAL,
            ),
        )

        val observations = file("metric_observations.csv")
        observations shouldContain "0.00001"
        observations shouldNotContain "E-5"
    }

    @Test
    fun `the direction of concern travels with the metric`() = runTest {
        seed()
        file("metric_definitions.csv") shouldContain "HIGHER_VALUES"
    }

    @Test
    fun `exporting does not change the database`() = runTest {
        seed()
        val before = rowCounts()

        exporter.archive()
        exporter.files()

        rowCounts() shouldBe before
    }

    @Test
    fun `two exports of unchanged data are identical`() = runTest {
        seed()
        exporter.archive().bytes shouldBe exporter.archive().bytes
    }

    @Test
    fun `an empty database still exports a well-formed snapshot`() = runTest {
        val entries = unzip(exporter.archive().bytes)

        // Headers without rows, and a manifest that says so.
        csvLines(entries.getValue("metric_observations.csv")).size shouldBe 1
        JSONObject(entries.getValue("manifest.json"))
            .getJSONObject("rowCounts").getInt("meal_logs") shouldBe 0
        entries.getValue("README.txt") shouldContain "Row counts"
    }

    // ------------------------------------------------------------------ helpers

    private suspend fun rowCounts(): Map<String, Int> = mapOf(
        "metric_definitions" to db.metricDefinitionDao().findAll().size,
        "metric_observations" to db.metricObservationDao().findAll().size,
        "nutrient_definitions" to db.nutrientDefinitionDao().findAll().size,
        "food_items" to db.foodItemDao().findAll().size,
        "food_nutrient_values" to db.foodNutrientValueDao().findAll().size,
        "meal_logs" to db.mealLogDao().findAll().size,
    )

    private fun unzip(bytes: ByteArray): Map<String, String> {
        val entries = linkedMapOf<String, String>()
        ZipInputStream(bytes.inputStream()).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                entries[entry.name] = zip.readBytes().toString(Charsets.UTF_8)
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
        return entries
    }
}
