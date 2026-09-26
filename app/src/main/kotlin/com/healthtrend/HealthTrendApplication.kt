package com.healthtrend

import android.app.Application
import com.healthtrend.core.domain.nutrition.lookup.NutritionLookupRepository
import com.healthtrend.di.appModule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.GlobalContext
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin

/**
 * Process entry point: starts the Koin graph and runs the variant-specific startup hooks.
 *
 * The application owns a process-lifetime [CoroutineScope] for work that must outlive any Activity
 * (nutrient metadata registration, demo seeding, Health Connect import later) — never a
 * `GlobalScope`.
 */
class HealthTrendApplication : Application() {

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidLogger()
            androidContext(this@HealthTrendApplication)
            // `platformExtraModules()` is supplied per build variant: debug adds the demo seeder,
            // release adds nothing.
            modules(appModule + platformExtraModules())
        }
        registerNutrientMetadata()
        installStartupHooks(applicationScope)
    }

    /**
     * Registers the bundled nutrient metadata (AGENTS.md §1.2).
     *
     * It has to happen early and it has to happen in release too: `food_nutrient_values.nutrient_id`
     * is a `RESTRICT` foreign key, so the first food the user adopts from the lexicon would be
     * rejected if its nutrients were unknown. This runs concurrently with the variant's startup
     * hooks; that is safe because the lookup pipeline registers any definition it is about to
     * reference itself, so neither order can lose.
     */
    private fun registerNutrientMetadata() {
        val koin = GlobalContext.getOrNull() ?: return
        applicationScope.launch { koin.get<NutritionLookupRepository>().registerBundledNutrients() }
    }

    override fun onTerminate() {
        stopKoin()
        super.onTerminate()
    }
}
