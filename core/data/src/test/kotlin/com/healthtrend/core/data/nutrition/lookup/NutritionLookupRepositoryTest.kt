package com.healthtrend.core.data.nutrition.lookup

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.healthtrend.core.data.local.HealthTrendDatabase
import com.healthtrend.core.data.nutrition.lexicon.BUNDLED_LEXICON
import com.healthtrend.core.data.nutrition.lexicon.BundledLexiconProvider
import com.healthtrend.core.data.projection.NutritionProjectionService
import com.healthtrend.core.data.repository.NutritionRepositoryImpl
import com.healthtrend.core.domain.model.FoodItem
import com.healthtrend.core.domain.model.FoodNutrientValue
import com.healthtrend.core.domain.nutrition.NutrientCatalog
import com.healthtrend.core.domain.nutrition.lookup.FoodNutrientProfile
import com.healthtrend.core.domain.nutrition.lookup.FoodRef
import com.healthtrend.core.domain.nutrition.lookup.FoodSearchResult
import com.healthtrend.core.domain.nutrition.lookup.LookupTier
import com.healthtrend.core.domain.nutrition.lookup.NutritionLookupProvider
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.ints.shouldBeGreaterThanOrEqual
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.ZoneId

/**
 * Verification of the AGENTS.md §5.2 lookup pipeline.
 *
 * The persistence half runs against a real in-memory SQLite database under Robolectric rather than a
 * fake repository, because the interesting failure here is not "did we call upsert" but "did the
 * `RESTRICT` foreign key on `food_nutrient_values.nutrient_id` reject the write because the metadata
 * row was not registered first". A fake would happily accept it.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class NutritionLookupRepositoryTest {

    private lateinit var db: HealthTrendDatabase
    private lateinit var nutrition: NutritionRepositoryImpl

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            HealthTrendDatabase::class.java,
        ).allowMainThreadQueries().build()
        nutrition = NutritionRepositoryImpl(db, NutritionProjectionService(db, ZoneId.of("UTC")))
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun repository(
        vararg extraProviders: NutritionLookupProvider,
    ) = NutritionLookupRepositoryImpl(
        providers = listOf(SavedFoodLookupProvider(nutrition), BundledLexiconProvider()) +
            extraProviders,
        nutritionRepository = nutrition,
    )

    // ------------------------------------------------------------------ the bundled table itself

    @Test
    fun `every lexicon food is quoted on a usable basis and names only catalog nutrients`() {
        BUNDLED_LEXICON.size shouldBeGreaterThanOrEqual 1
        BUNDLED_LEXICON.map { it.id }.toSet().size shouldBe BUNDLED_LEXICON.size

        for (entry in BUNDLED_LEXICON) {
            if (entry.referenceAmount <= 0.0 || !entry.referenceAmount.isFinite()) {
                throw AssertionError("${entry.id} has a bogus reference amount ${entry.referenceAmount}")
            }
            if (entry.referenceUnit !in setOf("g", "ml", "piece", "serving")) {
                throw AssertionError("${entry.id} has an unexpected unit '${entry.referenceUnit}'")
            }
            if (entry.nutrients.isEmpty()) {
                throw AssertionError("${entry.id} carries no nutrients")
            }
            // The food values and the nutrient catalogue are generated/authored separately, so this
            // is the assertion that catches them drifting apart.
            val unknown = entry.nutrients.keys - NutrientCatalog.byId.keys
            if (unknown.isNotEmpty()) {
                throw AssertionError("${entry.id} references unknown nutrients: $unknown")
            }
            // A food with no energy value at all is a resolution mistake, not a food — the
            // generator refuses to emit one. Zero *is* legitimate though: table salt has no energy.
            val calories = entry.nutrients["calories"]
            if (calories == null || calories < 0.0) {
                throw AssertionError("${entry.id} has no energy value: $calories")
            }
        }

        // A systematic generator failure would surface as many energy-free foods; only salt-like
        // items are permitted to have none.
        val energyFree = BUNDLED_LEXICON.count { it.nutrients.getValue("calories") == 0.0 }
        if (energyFree > 2) throw AssertionError("$energyFree foods carry no energy value")
    }

    @Test
    fun `the catalogue and the lexicon agree in both directions`() {
        val used = BUNDLED_LEXICON.flatMapTo(mutableSetOf()) { it.nutrients.keys }
        // Every nutrient the catalogue describes should actually be reaching the app from somewhere,
        // otherwise it is metadata for a column that will always show "—".
        val unused = NutrientCatalog.ALL.map { it.id } - used
        unused shouldBe emptySet()

        // The specific recall of AGENTS.md 1.1: the reference amount is arbitrary, so the app must
        // ship more than one kind of basis, not just 100 g.
        val units = BUNDLED_LEXICON.map { it.referenceUnit }.toSet()
        units shouldContain "g"
        units shouldContain "piece"
        BUNDLED_LEXICON.count { it.referenceUnit == "piece" } shouldBeGreaterThanOrEqual 1
        BUNDLED_LEXICON.map { it.referenceAmount }.count { it != 100.0 } shouldBeGreaterThanOrEqual 1
    }

    /**
     * Guards the coupling between `DemoDataSeeder.FOOD_SEARCHES` and the lexicon: the seeder asks for
     * foods by name and takes the first *bundled* hit, so a ranking change could quietly swap 鸡蛋 for
     * 鸡蛋面 in every debug build.
     */
    @Test
    fun `demo search terms resolve to the intended foods`() = runTest {
        val repository = repository()
        val expected = mapOf(
            "米饭（熟）" to "rice_cooked",
            "糙米饭（熟）" to "rice_brown_cooked",
            "燕麦片" to "oats_dry",
            "全脂牛奶" to "milk_whole",
            "鸡蛋" to "egg_whole_raw",
            "鸡胸肉" to "chicken_breast_raw",
            "豆腐（北豆腐" to "tofu_firm",
            "红薯（烤" to "sweet_potato_baked",
        )
        for ((query, expectedId) in expected) {
            val hit = repository.search(query).firstOrNull { it.tier == LookupTier.BUNDLED }
            hit.shouldNotBeNull()
            hit.foodRef.localId shouldBe expectedId
        }
    }

    // ------------------------------------------------------------------ search behaviour

    @Test
    fun `search walks the tiers in order and de-duplicates by name`() = runTest {
        // Deliberately supplied worst-first: the contract is the tier *order*, not the list order.
        // Nothing personal is saved and no bundled food matches 同一种食物, so the winner has to be
        // the remaining highest tier — the remote one — even though it was listed second.
        val repository = NutritionLookupRepositoryImpl(
            providers = listOf(
                StubProvider(LookupTier.ESTIMATE, "同一种食物"),
                StubProvider(LookupTier.REMOTE, "同一种食物"),
                SavedFoodLookupProvider(nutrition),
                BundledLexiconProvider(),
            ),
            nutritionRepository = nutrition,
        )

        val hits = repository.search("同一种食物")
        hits.size shouldBe 1
        hits.single().tier shouldBe LookupTier.REMOTE
    }

    @Test
    fun `a personal food shadows the bundled food of the same name`() = runTest {
        val repository = repository()
        val bundled = repository.search("米饭（熟）")
            .first { it.tier == LookupTier.BUNDLED }

        val profile = repository.profile(bundled).getOrThrow()
        repository.save(profile, isCustom = false).getOrThrow()

        val hits = repository.search("米饭（熟）")
        // Personal results are collected tier-first, so the user's own copy leads the list…
        hits.first().tier shouldBe LookupTier.PERSONAL
        hits.first().name shouldBe "米饭（熟）"
        // …and the bundled copy of the same food is de-duplicated away. Foods whose names merely
        // *contain* the query (糯米饭（熟）, 糙米饭（熟）) are different foods and stay.
        hits.filter { it.tier == LookupTier.BUNDLED }.map { it.name } shouldNotContain "米饭（熟）"
    }

    @Test
    fun `a tier that fails does not take the rest of the search down with it`() = runTest {
        val repository = repository(StubProvider(LookupTier.REMOTE, error = true))

        val hits = repository.search("西兰花")
        hits.shouldNotBeEmpty()
        hits.first().tier shouldBe LookupTier.BUNDLED
    }

    @Test
    fun `an unknown provider cannot be routed to`() = runTest {
        val orphan = FoodSearchResult(
            foodRef = FoodRef.of("nonexistent", "x"),
            tier = LookupTier.REMOTE,
            name = "x",
            brand = null,
            defaultReferenceAmount = 100.0,
            defaultReferenceUnit = "g",
        )
        repository().profile(orphan).isFailure.shouldBeTrue()
    }

    @Test
    fun `a blank query offers what the user already has and no more`() = runTest {
        val repository = repository()
        repository.search("   ") shouldBe emptyList()

        val bundled = repository.search("西兰花").first { it.tier == LookupTier.BUNDLED }
        repository.save(repository.profile(bundled).getOrThrow(), isCustom = false).getOrThrow()

        val hits = repository.search("   ")
        hits.shouldNotBeEmpty()
        hits.all { it.tier == LookupTier.PERSONAL }.shouldBeTrue()
    }

    // ------------------------------------------------------------------ saving a confirmed profile

    @Test
    fun `saving registers nutrient metadata before writing values`() = runTest {
        val repository = repository()
        // Nothing is registered up front: if the write order were wrong, SQLite would reject the
        // value row through the RESTRICT foreign key and this test would fail.
        nutrition.observeNutrientDefinitions().first() shouldBe emptyList()

        val profile = repository.profile(
            repository.search("鸡蛋").first { it.tier == LookupTier.BUNDLED },
        ).getOrThrow()
        val saved = repository.save(profile, isCustom = false).getOrThrow()

        saved.food.id shouldBe "egg_whole_raw"
        saved.food.referenceUnit shouldBe "piece"
        saved.nutrients.shouldNotBeEmpty()
        db.nutrientDefinitionDao().findByIds(saved.nutrients.map { it.nutrientId }).size shouldBe
            saved.nutrients.size
    }

    @Test
    fun `re-confirming the same food updates it instead of duplicating`() = runTest {
        val repository = repository()
        val profile = repository.profile(
            repository.search("燕麦片").first { it.tier == LookupTier.BUNDLED },
        ).getOrThrow()

        repository.save(profile, isCustom = false).getOrThrow()
        val edited = profile.copy(
            nutrients = profile.nutrients + ("sodium" to 999.0),
            referenceAmount = 45.0,
        )
        repository.save(edited, isCustom = true).getOrThrow()

        val foods = nutrition.observeFoods("").first()
        foods.count { it.id == "oats_dry" } shouldBe 1
        val stored = nutrition.getFoodWithNutrients("oats_dry").shouldNotBeNull()
        stored.food.isCustom.shouldBeTrue()
        stored.food.referenceAmount shouldBe 45.0
        stored.nutrients.first { it.nutrientId == "sodium" }.amountPerReference shouldBe 999.0
    }

    @Test
    fun `zero values are not persisted`() = runTest {
        val repository = repository()
        val profile = FoodNutrientProfile(
            foodRef = FoodRef.of(BundledLexiconProvider.NAME, "synthetic_zero"),
            tier = LookupTier.BUNDLED,
            foodName = "零值测试食物",
            brand = null,
            referenceAmount = 100.0,
            referenceUnit = "g",
            nutrients = mapOf("calories" to 100.0, "protein" to 0.0, "fat" to 0.0),
        )
        val saved = repository.save(profile, isCustom = false).getOrThrow()

        saved.nutrients.map { it.nutrientId } shouldBe listOf("calories")
        saved.nutrients.map { it.nutrientId } shouldNotContain "protein"
    }

    @Test
    fun `a profile with no usable values or a bogus basis is refused`() = runTest {
        val repository = repository()
        val base = FoodNutrientProfile(
            foodRef = FoodRef.of(BundledLexiconProvider.NAME, "synthetic_bad"),
            tier = LookupTier.BUNDLED,
            foodName = "非法食物",
            brand = null,
            referenceAmount = 100.0,
            referenceUnit = "g",
            nutrients = mapOf("calories" to 100.0),
        )

        repository.save(base.copy(nutrients = mapOf("protein" to 0.0)), isCustom = false)
            .isFailure.shouldBeTrue()
        repository.save(base.copy(referenceAmount = 0.0), isCustom = false)
            .isFailure.shouldBeTrue()
        repository.save(base.copy(referenceAmount = Double.NaN), isCustom = false)
            .isFailure.shouldBeTrue()
    }

    @Test
    fun `a profile from a non-bundled tier gets a generated id`() = runTest {
        var counter = 0
        val repository = NutritionLookupRepositoryImpl(
            providers = listOf(SavedFoodLookupProvider(nutrition), BundledLexiconProvider()),
            nutritionRepository = nutrition,
            idGenerator = { "food_generated_${counter++}" },
        )
        val profile = FoodNutrientProfile(
            foodRef = FoodRef.of(StubProvider.NAME, "remote-123"),
            tier = LookupTier.REMOTE,
            foodName = "远程食物",
            brand = "某品牌",
            referenceAmount = 30.0,
            referenceUnit = "serving",
            nutrients = mapOf("calories" to 120.0),
        )

        val saved = repository.save(profile, isCustom = false).getOrThrow()
        saved.food.id shouldBe "food_generated_0"
        saved.food.brand shouldBe "某品牌"
        saved.food.referenceAmount shouldBe 30.0
    }

    @Test
    fun `saving a personal food keeps its existing id`() = runTest {
        // The metadata row has to exist before the value: RESTRICT foreign key (AGENTS.md §1.2).
        nutrition.upsertNutrientDefinitions(listOf(NutrientCatalog.byId.getValue("calories")))
        nutrition.upsertFoodWithNutrients(
            food = FoodItem(
                id = "food_mine",
                name = "我的炒饭",
                referenceAmount = 250.0,
                referenceUnit = "g",
                isCustom = true,
            ),
            nutrients = listOf(FoodNutrientValue("food_mine", "calories", 420.0)),
        )
        val repository = repository()

        val hit = repository.search("我的炒饭").first { it.tier == LookupTier.PERSONAL }
        hit.foodRef.localId shouldBe "food_mine"
        val profile = repository.profile(hit).getOrThrow()
        profile.sourceName shouldBe null
        repository.save(profile, isCustom = true).getOrThrow().food.id shouldBe "food_mine"
    }

    // ------------------------------------------------------------------ helpers

    /** A provider that returns one fixed name, or fails, for tier-ordering tests. */
    private class StubProvider(
        override val tier: LookupTier,
        private val foodName: String = "stub",
        private val error: Boolean = false,
    ) : NutritionLookupProvider {
        override val providerName: String get() = NAME
        override val isOfflineCapable: Boolean get() = false

        override suspend fun searchFoods(query: String): Result<List<FoodSearchResult>> =
            if (error) {
                Result.failure(IllegalStateException("offline"))
            } else {
                Result.success(
                    listOf(
                        FoodSearchResult(
                            foodRef = FoodRef.of(NAME, foodName),
                            tier = tier,
                            name = foodName,
                            brand = null,
                            defaultReferenceAmount = 100.0,
                            defaultReferenceUnit = "g",
                        ),
                    ),
                )
            }

        override suspend fun getNutrientProfile(foodRefId: String): Result<FoodNutrientProfile> =
            Result.success(
                FoodNutrientProfile(
                    foodRef = FoodRef.of(NAME, foodRefId),
                    tier = tier,
                    foodName = foodRefId,
                    brand = null,
                    referenceAmount = 100.0,
                    referenceUnit = "g",
                    nutrients = mapOf("calories" to 50.0),
                ),
            )

        companion object {
            const val NAME = "stub"
        }
    }
}
