package com.healthtrend.demo

import com.healthtrend.core.common.time.TimeKeys
import com.healthtrend.core.domain.model.FoodItem
import com.healthtrend.core.domain.model.MealLog
import com.healthtrend.core.domain.model.MealType
import com.healthtrend.core.domain.model.MetricCategory
import com.healthtrend.core.domain.model.MetricDataType
import com.healthtrend.core.domain.model.MetricDefinition
import com.healthtrend.core.domain.model.MetricObservation
import com.healthtrend.core.domain.model.ObservationSource
import com.healthtrend.core.domain.nutrition.lookup.LookupTier
import com.healthtrend.core.domain.nutrition.lookup.NutritionLookupRepository
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
        DemoDataSeeder(
            metricRepository = get(),
            nutritionRepository = get(),
            lookupRepository = get(),
            zoneId = get(),
        )
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
 * WHY THIS EXISTS: every screen is a data visualisation, and an empty database renders as an empty
 * screen — which makes the UI impossible to review. The data is **deterministic** (fixed [Random]
 * seed, offsets computed from "today"), so a screenshot taken today and one taken tomorrow differ
 * only in the day they end on.
 *
 * The foods are *not* invented: they are adopted through the same lookup pipeline the UI uses
 * (AGENTS.md §5.2 search → §5.3 save), so every nutrient figure in a debug build traces back to the
 * USDA record recorded in the bundled lexicon, and the pipeline itself runs on every fresh install.
 */
class DemoDataSeeder(
    private val metricRepository: MetricRepository,
    private val nutritionRepository: NutritionRepository,
    private val lookupRepository: NutritionLookupRepository,
    private val zoneId: ZoneId,
    private val nowMillis: () -> Long = System::currentTimeMillis,
    private val random: Random = Random(20260101),
) : DemoDataInstaller {

    override suspend fun install() {
        // Nothing to do if the user (or a previous run) already has data.
        if (metricRepository.observeDefinitions().first().isNotEmpty()) return

        val today = TimeKeys.startOfDayUtcMillis(nowMillis(), zoneId)
        lookupRepository.registerBundledNutrients()
        val foods = adoptFoods()
        seedMeals(today, foods)
        seedMetricSeries(today)
    }

    // ------------------------------------------------------------------ foods

    /**
     * Adopts the demo's foods from the bundled lexicon.
     *
     * Each name is put through the real search, so this also exercises tier ordering and ranking: the
     * first bundled hit for 鸡蛋 must be the whole egg and not 鸡蛋面. The map is keyed by the saved
     * food's id, which for a bundled food is its stable catalogue slug (see
     * `NutritionLookupRepositoryImpl.foodIdFor`), so [MEAL_SEEDS] can name foods directly.
     */
    private suspend fun adoptFoods(): Map<String, FoodItem> {
        val adopted = mutableMapOf<String, FoodItem>()
        for (search in FOOD_SEARCHES) {
            val hit = lookupRepository.search(search).firstOrNull { it.tier == LookupTier.BUNDLED }
                ?: continue
            val profile = lookupRepository.profile(hit).getOrNull() ?: continue
            val saved = lookupRepository.save(profile, isCustom = false).getOrNull() ?: continue
            adopted[saved.food.id] = saved.food
        }
        return adopted
    }

    // ------------------------------------------------------------------ meals

    /**
     * A plausible four-day food diary. Logging these through the repository exercises the automatic
     * projection (AGENTS.md §4.3), so the `nutrient_*` metric definitions and their daily aggregates
     * appear on the dashboard as a side effect of eating — which is exactly the pipeline the spec
     * describes.
     */
    private suspend fun seedMeals(todayStartEpochMilli: Long, foods: Map<String, FoodItem>) {
        MEAL_SEEDS.forEach { seed ->
            val food = foods[seed.foodId] ?: return@forEach
            nutritionRepository.logMeal(
                MealLog(
                    id = "demo-meal-${seed.daysAgo}-${seed.hour}-${seed.minute}-${seed.foodId}",
                    foodId = food.id,
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
                id = "body_weight", name = "Body weight", category = MetricCategory.BODY, unit = "kg",
                description = "Fasted, first thing in the morning", expectedFrequency = "DAILY",
                minValue = 0.0, maxValue = 300.0, referenceRangeLow = null, referenceRangeHigh = null,
                displayOrder = 10, observations = weight,
            ),
            MetricSeed(
                id = "body_fat_percent", name = "Body fat", category = MetricCategory.BODY, unit = "%",
                description = "Bioimpedance; for watching the trend only", expectedFrequency = "WEEKLY",
                minValue = 0.0, maxValue = 80.0, referenceRangeLow = 10.0, referenceRangeHigh = 25.0,
                displayOrder = 20, observations = bodyFat,
            ),
            MetricSeed(
                id = "resting_heart_rate", name = "Resting heart rate", category = MetricCategory.BODY,
                unit = "bpm", description = "Resting heart rate on waking",
                expectedFrequency = "DAILY",
                minValue = 0.0, maxValue = 250.0, referenceRangeLow = 50.0, referenceRangeHigh = 70.0,
                displayOrder = 30, observations = restingHeartRate,
            ),
            MetricSeed(
                id = "blood_pressure_systolic", name = "Systolic blood pressure",
                category = MetricCategory.HEALTH,
                unit = "mmHg", description = "Upper-arm cuff", expectedFrequency = "EVERY_3_DAYS",
                minValue = 0.0, maxValue = 300.0, referenceRangeLow = 90.0, referenceRangeHigh = 120.0,
                displayOrder = 40, observations = systolic,
            ),
            MetricSeed(
                id = "blood_pressure_diastolic", name = "Diastolic blood pressure",
                category = MetricCategory.HEALTH,
                unit = "mmHg", description = "Upper-arm cuff", expectedFrequency = "EVERY_3_DAYS",
                minValue = 0.0, maxValue = 200.0, referenceRangeLow = 60.0, referenceRangeHigh = 80.0,
                displayOrder = 50, observations = diastolic,
            ),
            MetricSeed(
                id = "alt", name = "ALT", category = MetricCategory.HEALTH, unit = "U/L",
                description = "Alanine aminotransferase, liver panel",
                expectedFrequency = "MONTHLY",
                minValue = 0.0, maxValue = 2000.0, referenceRangeLow = 7.0, referenceRangeHigh = 40.0,
                displayOrder = 60,
                observations = listOf(84 to 32.0, 63 to 29.0, 42 to 38.0, 21 to 26.0, 0 to 22.0),
            ),
            MetricSeed(
                id = "serum_uric_acid", name = "Uric acid", category = MetricCategory.HEALTH,
                unit = "μmol/L", description = "Serum uric acid", expectedFrequency = "MONTHLY",
                minValue = 0.0, maxValue = 1500.0, referenceRangeLow = 208.0, referenceRangeHigh = 428.0,
                displayOrder = 70,
                observations = listOf(75 to 430.0, 50 to 415.0, 25 to 398.0, 0 to 380.0),
            ),
            MetricSeed(
                id = "sleep_duration", name = "Sleep duration", category = MetricCategory.LIFESTYLE,
                unit = "h", description = "Total sleep the night before", expectedFrequency = "DAILY",
                minValue = 0.0, maxValue = 24.0, referenceRangeLow = 7.0, referenceRangeHigh = 9.0,
                displayOrder = 80, observations = sleep,
            ),
            MetricSeed(
                id = "daily_steps", name = "Daily steps", category = MetricCategory.ACTIVITY,
                unit = "steps",
                description = "Steps accumulated during the day", expectedFrequency = "DAILY",
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

    private data class MealSeed(
        val daysAgo: Int,
        val hour: Int,
        val minute: Int,
        val mealType: MealType,
        /** A bundled-lexicon catalogue slug, which is also the saved food's id. */
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

        /**
         * Search terms for the foods the diary uses. Each must resolve to the intended lexicon
         * entry as its first bundled hit — see [adoptFoods].
         */
        val FOOD_SEARCHES = listOf(
            "米饭（熟）", "糙米饭（熟）", "燕麦片", "全麦面包", "全脂牛奶", "鸡蛋", "鸡胸肉",
            "西兰花", "菠菜", "大白菜", "番茄", "三文鱼", "虾（生）", "牛西冷", "豆腐（北豆腐",
            "红薯（烤", "毛豆", "苹果", "香蕉", "橄榄油",
        )

        val MEAL_SEEDS = listOf(
            MealSeed(0, 7, 30, MealType.BREAKFAST, "oats_dry", 60.0, "g"),
            MealSeed(0, 7, 30, MealType.BREAKFAST, "milk_whole", 250.0, "g"),
            MealSeed(0, 7, 35, MealType.BREAKFAST, "egg_whole_raw", 1.0, "piece"),
            MealSeed(0, 12, 30, MealType.LUNCH, "rice_cooked", 200.0, "g"),
            MealSeed(0, 12, 30, MealType.LUNCH, "chicken_breast_raw", 150.0, "g"),
            MealSeed(0, 12, 35, MealType.LUNCH, "broccoli_raw", 150.0, "g"),
            MealSeed(0, 19, 0, MealType.DINNER, "rice_cooked", 150.0, "g"),
            MealSeed(0, 19, 0, MealType.DINNER, "salmon_atlantic_farmed_raw", 120.0, "g"),
            MealSeed(0, 19, 5, MealType.DINNER, "tomato_raw", 100.0, "g"),
            MealSeed(0, 16, 0, MealType.SNACK, "apple_raw", 1.0, "piece"),

            MealSeed(1, 8, 0, MealType.BREAKFAST, "oats_dry", 50.0, "g"),
            MealSeed(1, 8, 0, MealType.BREAKFAST, "milk_whole", 200.0, "g"),
            MealSeed(1, 12, 45, MealType.LUNCH, "rice_brown_cooked", 220.0, "g"),
            MealSeed(1, 12, 45, MealType.LUNCH, "tofu_firm", 180.0, "g"),
            MealSeed(1, 12, 50, MealType.LUNCH, "cabbage_napa_raw", 150.0, "g"),
            MealSeed(1, 19, 15, MealType.DINNER, "sweet_potato_baked", 200.0, "g"),
            MealSeed(1, 19, 15, MealType.DINNER, "chicken_breast_raw", 130.0, "g"),
            MealSeed(1, 16, 30, MealType.SNACK, "banana_raw", 1.0, "piece"),

            MealSeed(2, 7, 45, MealType.BREAKFAST, "egg_whole_raw", 2.0, "piece"),
            MealSeed(2, 7, 45, MealType.BREAKFAST, "milk_whole", 200.0, "g"),
            MealSeed(2, 13, 0, MealType.LUNCH, "rice_cooked", 250.0, "g"),
            MealSeed(2, 13, 0, MealType.LUNCH, "chicken_breast_raw", 120.0, "g"),
            MealSeed(2, 13, 5, MealType.LUNCH, "olive_oil", 10.0, "g"),
            MealSeed(2, 18, 45, MealType.DINNER, "salmon_atlantic_farmed_raw", 150.0, "g"),
            MealSeed(2, 18, 45, MealType.DINNER, "broccoli_raw", 200.0, "g"),
            MealSeed(2, 18, 50, MealType.DINNER, "spinach_raw", 100.0, "g"),

            MealSeed(3, 8, 15, MealType.BREAKFAST, "oats_dry", 70.0, "g"),
            MealSeed(3, 8, 15, MealType.BREAKFAST, "banana_raw", 1.0, "piece"),
            MealSeed(3, 12, 30, MealType.LUNCH, "rice_brown_cooked", 200.0, "g"),
            MealSeed(3, 12, 30, MealType.LUNCH, "edamame_prepared", 120.0, "g"),
            MealSeed(3, 12, 35, MealType.LUNCH, "tomato_raw", 150.0, "g"),
            MealSeed(3, 19, 30, MealType.DINNER, "sweet_potato_baked", 180.0, "g"),
            MealSeed(3, 19, 30, MealType.DINNER, "shrimp_raw", 150.0, "g"),
            MealSeed(3, 19, 35, MealType.DINNER, "beef_sirloin_raw", 100.0, "g"),
        )
    }
}
