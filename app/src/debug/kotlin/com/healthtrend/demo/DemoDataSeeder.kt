package com.healthtrend.demo

import com.healthtrend.core.common.time.TimeKeys
import com.healthtrend.core.domain.model.FoodItem
import com.healthtrend.core.domain.model.FoodNutrientValue
import com.healthtrend.core.domain.model.MealLog
import com.healthtrend.core.domain.model.MealType
import com.healthtrend.core.domain.model.MetricCategory
import com.healthtrend.core.domain.model.MetricDataType
import com.healthtrend.core.domain.model.MetricDefinition
import com.healthtrend.core.domain.model.MetricObservation
import com.healthtrend.core.domain.model.NutrientCategory
import com.healthtrend.core.domain.model.NutrientDefinition
import com.healthtrend.core.domain.model.ObservationSource
import com.healthtrend.core.domain.repository.MetricRepository
import com.healthtrend.core.domain.repository.NutritionRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.koin.core.context.GlobalContext
import org.koin.core.module.Module
import org.koin.dsl.module
import java.time.ZoneId
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

/**
 * Demo-data Koin module — **debug builds only**.
 *
 * Registered through `platformExtraModules()` in `src/debug`, which has no counterpart in
 * `src/release`, so none of this code is reachable from a release artifact.
 */
val demoModule: Module = module {
    single<DemoDataInstaller> {
        DemoDataSeeder(metricRepository = get(), nutritionRepository = get(), zoneId = get())
    }
}

/** Seeds at process start so the very first launch already has something to look at. */
fun seedIfEmptyOnStart(scope: CoroutineScope) {
    val koin = GlobalContext.getOrNull() ?: return
    scope.launch { koin.getOrNull<DemoDataInstaller>()?.install() }
}

/**
 * Fills an empty database with roughly three months of reviewable data.
 *
 * WHY THIS EXISTS: every screen in Phase 3 is a data visualisation, and an empty database renders as
 * an empty screen — which makes the UI impossible to review. The data is **deterministic** (fixed
 * [Random] seed, offsets computed from "today"), so a screenshot taken today and one taken tomorrow
 * differ only in the day they end on.
 *
 * HONESTY NOTE (AGENTS.md §5.3): the nutrient values below are illustrative reference figures, which
 * is why every seeded food is flagged `isCustom = true`. The verified, curated offline lexicon is
 * Phase 4 work; until then the UI's "review before saving" step is what keeps these numbers explicit
 * rather than silently authoritative.
 */
class DemoDataSeeder(
    private val metricRepository: MetricRepository,
    private val nutritionRepository: NutritionRepository,
    private val zoneId: ZoneId,
    private val nowMillis: () -> Long = System::currentTimeMillis,
    private val random: Random = Random(20260101),
) : DemoDataInstaller {

    override suspend fun install() {
        // Nothing to do if the user (or a previous run) already has data.
        if (metricRepository.observeDefinitions().first().isNotEmpty()) return

        val today = TimeKeys.startOfDayUtcMillis(nowMillis(), zoneId)
        seedNutrients()
        val foods = seedFoods()
        seedMeals(today, foods)
        seedMetricSeries(today)
    }

    // ------------------------------------------------------------------ nutrients

    private suspend fun seedNutrients() {
        nutritionRepository.upsertNutrientDefinitions(
            NUTRIENT_SEEDS.map { seed ->
                NutrientDefinition(
                    id = seed.id,
                    name = seed.name,
                    unit = seed.unit,
                    category = seed.category,
                    dailyRecommended = seed.dailyRecommended,
                    displayOrder = seed.displayOrder,
                )
            },
        )
    }

    // ------------------------------------------------------------------ foods

    private suspend fun seedFoods(): Map<String, FoodSeed> {
        FOOD_SEEDS.forEach { seed ->
            nutritionRepository.upsertFoodWithNutrients(
                food = FoodItem(
                    id = seed.id,
                    name = seed.name,
                    referenceAmount = seed.referenceAmount,
                    referenceUnit = seed.referenceUnit,
                    // Illustrative reference values, so they are marked as user-created entries
                    // rather than as the curated bundled lexicon (see the class KDoc).
                    isCustom = true,
                ),
                nutrients = seed.nutrients.map { (nutrientId, amount) ->
                    FoodNutrientValue(
                        foodId = seed.id,
                        nutrientId = nutrientId,
                        amountPerReference = amount,
                    )
                },
            )
        }
        return FOOD_SEEDS.associateBy { it.id }
    }

    // ------------------------------------------------------------------ meals

    /**
     * A plausible four-day food diary. Logging these through [NutritionRepository.logMeal] exercises
     * the automatic projection (AGENTS.md §4.3), so the `nutrient_*` metric definitions and their
     * daily aggregates appear on the dashboard as a side effect of eating — which is exactly the
     * pipeline the spec describes.
     */
    private suspend fun seedMeals(todayStartEpochMilli: Long, foods: Map<String, FoodSeed>) {
        MEAL_SEEDS.forEach { seed ->
            nutritionRepository.logMeal(
                MealLog(
                    id = "demo-meal-${seed.daysAgo}-${seed.hour}-${seed.minute}-${seed.foodId}",
                    foodId = foods.getValue(seed.foodId).id,
                    timestampEpochMilli = todayStartEpochMilli -
                        seed.daysAgo * TimeKeys.MILLIS_PER_DAY +
                        seed.hour * 3_600_000L +
                        seed.minute * 60_000L,
                    actualAmount = seed.amount,
                    actualUnit = seed.unit,
                    mealType = seed.mealType,
                ),
            )
        }
    }

    // ------------------------------------------------------------------ metric series

    private suspend fun seedMetricSeries(todayStartEpochMilli: Long) {
        metricSeeds().forEach { seed ->
            metricRepository.upsertDefinition(
                MetricDefinition(
                    id = seed.id,
                    name = seed.name,
                    category = seed.category,
                    unit = seed.unit,
                    dataType = MetricDataType.NUMERIC,
                    description = seed.description,
                    expectedFrequency = seed.expectedFrequency,
                    minValue = seed.minValue,
                    maxValue = seed.maxValue,
                    referenceRangeLow = seed.referenceRangeLow,
                    referenceRangeHigh = seed.referenceRangeHigh,
                    isBuiltIn = true,
                    displayOrder = seed.displayOrder,
                ),
            )
            seed.observations.forEach { (daysAgo, value) ->
                metricRepository.upsertObservation(
                    MetricObservation(
                        // Stable ids keep repeated seeding idempotent.
                        id = "demo-obs-${seed.id}-$daysAgo",
                        metricId = seed.id,
                        timestampEpochMilli = todayStartEpochMilli -
                            daysAgo * TimeKeys.MILLIS_PER_DAY +
                            NOON_OFFSET_MILLIS,
                        value = value,
                        unit = seed.unit,
                        source = ObservationSource.MANUAL,
                    ),
                )
            }
        }
    }

    private fun metricSeeds(): List<MetricSeed> {
        // Weekly rhythm on top of a slow drift, plus noise — the shape every smoothing / trend /
        // anomaly algorithm in :core:analytics is designed for.
        val weight = daily(90, decimals = 1) { i ->
            74.0 - 0.035 * i + 0.35 * sin(2 * PI * i / 7.0) + random.nextDouble(-0.15, 0.15)
        }.toMutableList().also {
            // Two genuine deviations so the anomaly overlay has something to mark.
            nudge(it, daysAgo = 42, delta = 1.8)
            nudge(it, daysAgo = 61, delta = -2.1)
        }

        val restingHeartRate = daily(90, decimals = 0) { i ->
            62.0 + 2.0 * sin(2 * PI * i / 7.0) + random.nextDouble(-2.0, 2.0)
        }

        val sleep = daily(90, decimals = 1) { i ->
            7.2 + 0.45 * sin(2 * PI * i / 7.0 + 1.1) + random.nextDouble(-0.35, 0.35)
        }

        val steps = daily(90, decimals = 0) { i ->
            8400.0 + 1300.0 * sin(2 * PI * i / 7.0 + 2.4) + random.nextDouble(-900.0, 900.0)
        }.toMutableList().also {
            nudge(it, daysAgo = 30, delta = -6800.0)
        }

        val bodyFat = every(stepDays = 7, spanDays = 90, decimals = 1) { i ->
            24.6 - 0.055 * i + random.nextDouble(-0.25, 0.25)
        }

        val systolic = every(stepDays = 3, spanDays = 90, decimals = 0) { i ->
            118.0 + 3.0 * sin(2 * PI * i / 8.0) + random.nextDouble(-3.0, 3.0)
        }

        val diastolic = every(stepDays = 3, spanDays = 90, decimals = 0) { i ->
            76.0 + 2.4 * sin(2 * PI * i / 8.0 + 0.8) + random.nextDouble(-2.5, 2.5)
        }

        return listOf(
            MetricSeed(
                id = "body_weight", name = "体重", category = MetricCategory.BODY, unit = "kg",
                description = "晨起空腹体重", expectedFrequency = "DAILY",
                minValue = 0.0, maxValue = 300.0, referenceRangeLow = null, referenceRangeHigh = null,
                displayOrder = 10, observations = weight,
            ),
            MetricSeed(
                id = "body_fat_percent", name = "体脂率", category = MetricCategory.BODY, unit = "%",
                description = "生物电阻抗法，仅用于观察趋势", expectedFrequency = "WEEKLY",
                minValue = 0.0, maxValue = 80.0, referenceRangeLow = 10.0, referenceRangeHigh = 25.0,
                displayOrder = 20, observations = bodyFat,
            ),
            MetricSeed(
                id = "resting_heart_rate", name = "静息心率", category = MetricCategory.BODY, unit = "bpm",
                description = "晨起静息心率", expectedFrequency = "DAILY",
                minValue = 0.0, maxValue = 250.0, referenceRangeLow = 50.0, referenceRangeHigh = 70.0,
                displayOrder = 30, observations = restingHeartRate,
            ),
            MetricSeed(
                id = "blood_pressure_systolic", name = "收缩压", category = MetricCategory.HEALTH,
                unit = "mmHg", description = "上臂式电子血压计", expectedFrequency = "EVERY_3_DAYS",
                minValue = 0.0, maxValue = 300.0, referenceRangeLow = 90.0, referenceRangeHigh = 120.0,
                displayOrder = 40, observations = systolic,
            ),
            MetricSeed(
                id = "blood_pressure_diastolic", name = "舒张压", category = MetricCategory.HEALTH,
                unit = "mmHg", description = "上臂式电子血压计", expectedFrequency = "EVERY_3_DAYS",
                minValue = 0.0, maxValue = 200.0, referenceRangeLow = 60.0, referenceRangeHigh = 80.0,
                displayOrder = 50, observations = diastolic,
            ),
            MetricSeed(
                id = "alt", name = "丙氨酸氨基转移酶", category = MetricCategory.HEALTH, unit = "U/L",
                description = "ALT，肝功能", expectedFrequency = "MONTHLY",
                minValue = 0.0, maxValue = 2000.0, referenceRangeLow = 7.0, referenceRangeHigh = 40.0,
                displayOrder = 60,
                observations = listOf(84 to 32.0, 63 to 29.0, 42 to 38.0, 21 to 26.0, 0 to 22.0),
            ),
            MetricSeed(
                id = "serum_uric_acid", name = "尿酸", category = MetricCategory.HEALTH, unit = "μmol/L",
                description = "血清尿酸", expectedFrequency = "MONTHLY",
                minValue = 0.0, maxValue = 1500.0, referenceRangeLow = 208.0, referenceRangeHigh = 428.0,
                displayOrder = 70,
                observations = listOf(75 to 430.0, 50 to 415.0, 25 to 398.0, 0 to 380.0),
            ),
            MetricSeed(
                id = "sleep_duration", name = "睡眠时长", category = MetricCategory.LIFESTYLE, unit = "h",
                description = "前一晚总睡眠时长", expectedFrequency = "DAILY",
                minValue = 0.0, maxValue = 24.0, referenceRangeLow = 7.0, referenceRangeHigh = 9.0,
                displayOrder = 80, observations = sleep,
            ),
            MetricSeed(
                id = "daily_steps", name = "日步数", category = MetricCategory.ACTIVITY, unit = "步",
                description = "当日累计步数", expectedFrequency = "DAILY",
                minValue = 0.0, maxValue = 100000.0, referenceRangeLow = 6000.0, referenceRangeHigh = null,
                displayOrder = 90, observations = steps,
            ),
        )
    }

    /** Daily observations covering [count] days, newest last, with [decimals] display precision. */
    private fun daily(count: Int, decimals: Int, value: (Int) -> Double): List<Pair<Int, Double>> =
        (0 until count).map { offset ->
            val daysAgo = count - 1 - offset
            daysAgo to rounded(value(offset), decimals)
        }

    /** One observation every [stepDays] days across a [spanDays] window. */
    private fun every(
        stepDays: Int,
        spanDays: Int,
        decimals: Int,
        value: (Int) -> Double,
    ): List<Pair<Int, Double>> {
        val count = spanDays / stepDays + 1
        return (0 until count).map { offset ->
            val daysAgo = spanDays - offset * stepDays
            daysAgo to rounded(value(offset), decimals)
        }
    }

    private fun rounded(value: Double, decimals: Int): Double =
        Math.round(value * ROUNDING_FACTORS[decimals]) / ROUNDING_FACTORS[decimals]

    /** Nudges the observation [daysAgo] days back — used to plant a real deviation. */
    private fun nudge(observations: MutableList<Pair<Int, Double>>, daysAgo: Int, delta: Double) {
        val index = observations.indexOfFirst { it.first == daysAgo }
        if (index < 0) return
        val (day, value) = observations[index]
        observations[index] = day to Math.round((value + delta) * 10.0) / 10.0
    }

    private data class NutrientSeed(
        val id: String,
        val name: String,
        val unit: String,
        val category: NutrientCategory,
        val dailyRecommended: Double,
        val displayOrder: Int,
    )

    private data class FoodSeed(
        val id: String,
        val name: String,
        val referenceAmount: Double,
        val referenceUnit: String,
        val nutrients: Map<String, Double>,
    )

    private data class MealSeed(
        val daysAgo: Int,
        val hour: Int,
        val minute: Int,
        val mealType: MealType,
        val foodId: String,
        val amount: Double,
        val unit: String,
    )

    private data class MetricSeed(
        val id: String,
        val name: String,
        val category: MetricCategory,
        val unit: String,
        val description: String,
        val expectedFrequency: String,
        val minValue: Double?,
        val maxValue: Double?,
        val referenceRangeLow: Double?,
        val referenceRangeHigh: Double?,
        val displayOrder: Int,
        /** `(daysAgo, value)` pairs; `daysAgo` is relative to today. */
        val observations: List<Pair<Int, Double>>,
    )

    private companion object {
        /** Midday, so a record never sits on a boundary where a DST shift could move it. */
        const val NOON_OFFSET_MILLIS = 12L * 3_600_000L

        /** `[decimals]` -> multiplier. */
        val ROUNDING_FACTORS = doubleArrayOf(1.0, 10.0, 100.0)

        val NUTRIENT_SEEDS = listOf(
            NutrientSeed("calories", "能量", "kcal", NutrientCategory.MACRO, 2000.0, 10),
            NutrientSeed("protein", "蛋白质", "g", NutrientCategory.MACRO, 60.0, 20),
            NutrientSeed("carbohydrates", "碳水化合物", "g", NutrientCategory.MACRO, 275.0, 30),
            NutrientSeed("fat", "脂肪", "g", NutrientCategory.MACRO, 60.0, 40),
            NutrientSeed("fiber", "膳食纤维", "g", NutrientCategory.MACRO, 25.0, 50),
            NutrientSeed("sodium", "钠", "mg", NutrientCategory.MINERAL, 2000.0, 60),
            NutrientSeed("potassium", "钾", "mg", NutrientCategory.MINERAL, 2000.0, 70),
            NutrientSeed("calcium", "钙", "mg", NutrientCategory.MINERAL, 800.0, 80),
            NutrientSeed("vitamin_c", "维生素 C", "mg", NutrientCategory.VITAMIN, 100.0, 90),
        )

        /**
         * `referenceAmount`/`referenceUnit` are deliberately heterogeneous (100 g, 100 ml, 1 piece)
         * so that AGENTS.md §1.1's rule — never divide by a hard-coded 100 — is exercised by the demo
         * data and not only by the unit tests.
         */
        val FOOD_SEEDS = listOf(
            FoodSeed("rice_cooked", "米饭（熟）", 100.0, "g", mapOf(
                "calories" to 116.0, "protein" to 2.6, "carbohydrates" to 25.9, "fat" to 0.3,
                "sodium" to 2.0)),
            FoodSeed("milk_whole", "全脂牛奶", 100.0, "ml", mapOf(
                "calories" to 61.0, "protein" to 3.2, "carbohydrates" to 4.8, "fat" to 3.3,
                "calcium" to 113.0)),
            FoodSeed("egg", "鸡蛋", 1.0, "piece", mapOf(
                "calories" to 72.0, "protein" to 6.3, "carbohydrates" to 0.4, "fat" to 5.0,
                "calcium" to 28.0)),
            FoodSeed("chicken_breast", "鸡胸肉（生）", 100.0, "g", mapOf(
                "calories" to 133.0, "protein" to 24.6, "carbohydrates" to 0.6, "fat" to 3.2,
                "sodium" to 74.0)),
            FoodSeed("oats_dry", "燕麦片（干）", 100.0, "g", mapOf(
                "calories" to 367.0, "protein" to 12.4, "carbohydrates" to 61.0, "fat" to 6.7,
                "fiber" to 7.0, "calcium" to 54.0, "potassium" to 429.0)),
            FoodSeed("broccoli", "西兰花", 100.0, "g", mapOf(
                "calories" to 34.0, "protein" to 2.8, "carbohydrates" to 6.6, "fat" to 0.4,
                "fiber" to 2.6, "vitamin_c" to 51.0, "potassium" to 316.0, "calcium" to 47.0)),
            FoodSeed("apple", "苹果", 1.0, "piece", mapOf(
                "calories" to 104.0, "protein" to 0.5, "carbohydrates" to 27.6, "fat" to 0.3,
                "fiber" to 4.8, "vitamin_c" to 9.2, "potassium" to 214.0)),
            FoodSeed("banana", "香蕉", 1.0, "piece", mapOf(
                "calories" to 107.0, "protein" to 1.3, "carbohydrates" to 27.4, "fat" to 0.4,
                "fiber" to 3.1, "potassium" to 430.0, "vitamin_c" to 10.4)),
            FoodSeed("olive_oil", "橄榄油", 100.0, "ml", mapOf("calories" to 884.0, "fat" to 100.0)),
            FoodSeed("tofu_firm", "豆腐（北豆腐）", 100.0, "g", mapOf(
                "calories" to 98.0, "protein" to 12.2, "carbohydrates" to 2.0, "fat" to 4.8,
                "calcium" to 138.0, "sodium" to 7.0)),
            FoodSeed("salmon", "三文鱼", 100.0, "g", mapOf(
                "calories" to 208.0, "protein" to 20.4, "carbohydrates" to 0.0, "fat" to 13.4,
                "sodium" to 59.0, "potassium" to 363.0)),
            FoodSeed("sweet_potato", "红薯", 100.0, "g", mapOf(
                "calories" to 86.0, "protein" to 1.6, "carbohydrates" to 20.1, "fat" to 0.1,
                "fiber" to 3.0, "vitamin_c" to 2.4, "potassium" to 337.0)),
            FoodSeed("brown_rice_cooked", "糙米饭（熟）", 100.0, "g", mapOf(
                "calories" to 112.0, "protein" to 2.6, "carbohydrates" to 23.5, "fat" to 0.9,
                "fiber" to 1.8)),
            FoodSeed("tomato", "番茄", 100.0, "g", mapOf(
                "calories" to 18.0, "protein" to 0.9, "carbohydrates" to 3.9, "fat" to 0.2,
                "fiber" to 1.2, "vitamin_c" to 14.0, "potassium" to 237.0)),
        )

        val MEAL_SEEDS = listOf(
            MealSeed(0, 7, 30, MealType.BREAKFAST, "oats_dry", 60.0, "g"),
            MealSeed(0, 7, 30, MealType.BREAKFAST, "milk_whole", 250.0, "ml"),
            MealSeed(0, 7, 35, MealType.BREAKFAST, "egg", 1.0, "piece"),
            MealSeed(0, 12, 30, MealType.LUNCH, "rice_cooked", 200.0, "g"),
            MealSeed(0, 12, 30, MealType.LUNCH, "chicken_breast", 150.0, "g"),
            MealSeed(0, 12, 35, MealType.LUNCH, "broccoli", 150.0, "g"),
            MealSeed(0, 19, 0, MealType.DINNER, "rice_cooked", 150.0, "g"),
            MealSeed(0, 19, 0, MealType.DINNER, "salmon", 120.0, "g"),
            MealSeed(0, 19, 5, MealType.DINNER, "tomato", 100.0, "g"),
            MealSeed(0, 16, 0, MealType.SNACK, "apple", 1.0, "piece"),

            MealSeed(1, 8, 0, MealType.BREAKFAST, "oats_dry", 50.0, "g"),
            MealSeed(1, 8, 0, MealType.BREAKFAST, "milk_whole", 200.0, "ml"),
            MealSeed(1, 12, 45, MealType.LUNCH, "brown_rice_cooked", 220.0, "g"),
            MealSeed(1, 12, 45, MealType.LUNCH, "tofu_firm", 180.0, "g"),
            MealSeed(1, 19, 15, MealType.DINNER, "sweet_potato", 200.0, "g"),
            MealSeed(1, 19, 15, MealType.DINNER, "chicken_breast", 130.0, "g"),
            MealSeed(1, 16, 30, MealType.SNACK, "banana", 1.0, "piece"),

            MealSeed(2, 7, 45, MealType.BREAKFAST, "egg", 2.0, "piece"),
            MealSeed(2, 7, 45, MealType.BREAKFAST, "milk_whole", 200.0, "ml"),
            MealSeed(2, 13, 0, MealType.LUNCH, "rice_cooked", 250.0, "g"),
            MealSeed(2, 13, 0, MealType.LUNCH, "chicken_breast", 120.0, "g"),
            MealSeed(2, 13, 5, MealType.LUNCH, "olive_oil", 10.0, "ml"),
            MealSeed(2, 18, 45, MealType.DINNER, "salmon", 150.0, "g"),
            MealSeed(2, 18, 45, MealType.DINNER, "broccoli", 200.0, "g"),

            MealSeed(3, 8, 15, MealType.BREAKFAST, "oats_dry", 70.0, "g"),
            MealSeed(3, 8, 15, MealType.BREAKFAST, "banana", 1.0, "piece"),
            MealSeed(3, 12, 30, MealType.LUNCH, "brown_rice_cooked", 200.0, "g"),
            MealSeed(3, 12, 30, MealType.LUNCH, "tofu_firm", 200.0, "g"),
            MealSeed(3, 12, 35, MealType.LUNCH, "tomato", 150.0, "g"),
            MealSeed(3, 19, 30, MealType.DINNER, "sweet_potato", 180.0, "g"),
            MealSeed(3, 19, 30, MealType.DINNER, "egg", 1.0, "piece"),
        )
    }
}
