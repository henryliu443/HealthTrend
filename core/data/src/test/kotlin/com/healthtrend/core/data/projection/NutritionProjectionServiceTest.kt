package com.healthtrend.core.data.projection

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.healthtrend.core.common.time.TimeKeys
import com.healthtrend.core.data.local.HealthTrendDatabase
import com.healthtrend.core.data.local.ObservationSources
import com.healthtrend.core.data.local.entity.FoodItemEntity
import com.healthtrend.core.data.local.entity.FoodNutrientValueEntity
import com.healthtrend.core.data.local.entity.MealLogEntity
import com.healthtrend.core.data.local.entity.NutrientDefinitionEntity
import com.healthtrend.core.domain.model.NutritionMetricIds
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant
import java.time.ZoneId

/**
 * End-to-end verification of the AGENTS.md 4.3 projection pipeline against a real (in-memory)
 * SQLite database running under Robolectric.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class NutritionProjectionServiceTest {

    private val shanghai = ZoneId.of("Asia/Shanghai")
    private lateinit var db: HealthTrendDatabase
    private lateinit var projection: NutritionProjectionService
    private var idCounter = 0

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            HealthTrendDatabase::class.java,
        ).allowMainThreadQueries().build()

        projection = NutritionProjectionService(db, shanghai) { "obs-${idCounter++}" }
    }

    @After
    fun tearDown() {
        db.close()
    }

    private suspend fun seedRice() {
        db.nutrientDefinitionDao().upsertAll(
            listOf(
                NutrientDefinitionEntity("calories", "能量", "kcal", "MACRO", null, 0),
                NutrientDefinitionEntity("protein", "蛋白质", "g", "MACRO", null, 1),
            ),
        )
        db.foodItemDao().upsert(
            FoodItemEntity(
                id = "rice",
                name = "米饭 (熟)",
                referenceAmount = 100.0,
                referenceUnit = "g",
            ),
        )
        db.foodNutrientValueDao().insertAll(
            listOf(
                FoodNutrientValueEntity("rice", "calories", 130.0),
                FoodNutrientValueEntity("rice", "protein", 2.6),
            ),
        )
    }

    private suspend fun observationValues(metricId: String, dayStart: Long): List<Double> =
        db.metricObservationDao().findByMetricAndTimestamp(metricId, dayStart).map { it.value }

    @Test
    fun `meal is projected onto the day using the food reference ratio`() = runTest {
        seedRice()
        val mealTs = Instant.parse("2026-09-27T04:00:00Z").toEpochMilli() // 12:00 in Shanghai
        db.mealLogDao().upsert(MealLogEntity("m1", "rice", mealTs, 73.0, "g", "LUNCH"))

        projection.recomputeDayContaining(mealTs)

        val dayStart = TimeKeys.startOfDayUtcMillis(mealTs, shanghai)
        val protein = db.metricObservationDao()
            .findByMetricAndTimestamp(NutritionMetricIds.of("protein"), dayStart)

        protein.size shouldBe 1
        protein.first().value shouldBe (1.898 plusOrMinus 1e-12) // 2.6 * 73/100, never a /100 constant
        protein.first().source shouldBe ObservationSources.NUTRITION_AGG

        // Metric definition is materialised lazily with the nutrient's own metadata.
        val definition = db.metricDefinitionDao().findById(NutritionMetricIds.of("protein"))
        definition?.name shouldBe "蛋白质"
        definition?.unit shouldBe "g"
    }

    @Test
    fun `reprojection is idempotent and aggregates all meals of the day`() = runTest {
        seedRice()
        val noon = Instant.parse("2026-09-27T04:00:00Z").toEpochMilli()
        db.mealLogDao().upsert(MealLogEntity("m1", "rice", noon, 100.0, "g", "LUNCH"))
        db.mealLogDao().upsert(MealLogEntity("m2", "rice", noon + 3_600_000, 50.0, "g", "DINNER"))

        projection.recomputeDayContaining(noon)
        projection.recomputeDayContaining(noon) // second pass must not duplicate rows

        val dayStart = TimeKeys.startOfDayUtcMillis(noon, shanghai)
        val proteinValues = observationValues(NutritionMetricIds.of("protein"), dayStart)
        proteinValues.size shouldBe 1
        proteinValues.first() shouldBe (2.6 * 1.5 plusOrMinus 1e-12)
    }

    @Test
    fun `deleting the only meal clears the day's projection`() = runTest {
        seedRice()
        val ts = Instant.parse("2026-09-27T04:00:00Z").toEpochMilli()
        db.mealLogDao().upsert(MealLogEntity("m1", "rice", ts, 73.0, "g", "LUNCH"))

        projection.recomputeDayContaining(ts)
        db.mealLogDao().deleteById("m1")
        projection.recomputeDayContaining(ts)

        val dayStart = TimeKeys.startOfDayUtcMillis(ts, shanghai)
        observationValues(NutritionMetricIds.of("protein"), dayStart) shouldBe emptyList()
    }

    @Test
    fun `bucketing follows local midnight rather than UTC midnight`() = runTest {
        seedRice()
        // 2026-09-27T16:30Z == 2026-09-28 00:30 in Shanghai -> belongs to the Sep 28 local day.
        val ts = Instant.parse("2026-09-27T16:30:00Z").toEpochMilli()
        val utcMidnight = Instant.parse("2026-09-27T00:00:00Z").toEpochMilli()
        db.mealLogDao().upsert(MealLogEntity("m1", "rice", ts, 100.0, "g", "SNACK"))

        projection.recomputeDayContaining(ts)

        val localDayStart = TimeKeys.startOfDayUtcMillis(ts, shanghai)
        localDayStart shouldBe Instant.parse("2026-09-27T16:00:00Z").toEpochMilli()
        db.metricObservationDao()
            .findBySourceAndTimestamp(ObservationSources.NUTRITION_AGG, localDayStart).size shouldBe 2
        db.metricObservationDao()
            .findBySourceAndTimestamp(ObservationSources.NUTRITION_AGG, utcMidnight) shouldBe emptyList()
    }
}
