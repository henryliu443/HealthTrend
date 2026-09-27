package com.healthtrend.di

import com.healthtrend.BuildConfig
import com.healthtrend.core.data.export.DataExporter
import com.healthtrend.core.data.local.HealthTrendDatabase
import com.healthtrend.core.data.nutrition.lexicon.BundledLexiconProvider
import com.healthtrend.core.data.nutrition.lookup.NutritionLookupRepositoryImpl
import com.healthtrend.core.data.nutrition.lookup.SavedFoodLookupProvider
import com.healthtrend.core.data.projection.NutritionProjectionService
import com.healthtrend.core.data.repository.MetricRepositoryImpl
import com.healthtrend.core.data.repository.NutritionRepositoryImpl
import com.healthtrend.core.domain.nutrition.lookup.NutritionLookupProvider
import com.healthtrend.core.domain.nutrition.lookup.NutritionLookupRepository
import com.healthtrend.core.domain.repository.MetricRepository
import com.healthtrend.core.domain.repository.NutritionRepository
import com.healthtrend.ui.compare.CompareViewModel
import com.healthtrend.ui.dashboard.DashboardViewModel
import com.healthtrend.ui.detail.MetricDetailViewModel
import com.healthtrend.ui.nutrition.NutritionViewModel
import org.koin.android.ext.koin.androidContext
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.core.module.Module
import org.koin.core.qualifier.named
import org.koin.dsl.module
import java.time.ZoneId

/**
 * The application's dependency graph (AGENTS.md §3.1: `:app` owns Compose, ViewModels and DI).
 *
 * The graph is intentionally flat and hand-wired: the project has one database, two repositories and
 * a handful of ViewModels, so a reflective/annotation-based DI framework would add indirection
 * without removing any decision.
 */
val appModule: Module = module {

    single { HealthTrendDatabase.build(androidContext()) }

    /** Calendar day boundaries are resolved in the device's zone; see AGENTS.md §6.2. */
    single<ZoneId> { ZoneId.systemDefault() }

    single { NutritionProjectionService(database = get(), zoneId = get()) }

    /** Reads the whole database into a portable snapshot (AGENTS.md §10.4: never writes to it). */
    single {
        DataExporter(
            database = get(),
            appVersion = BuildConfig.VERSION_NAME,
            zoneId = get(),
        )
    }

    single<MetricRepository> {
        val database = get<HealthTrendDatabase>()
        MetricRepositoryImpl(database.metricDefinitionDao(), database.metricObservationDao())
    }

    single<NutritionRepository> { NutritionRepositoryImpl(database = get(), projection = get()) }

    // ---------------------------------------------------------------- nutrition lookup (§5.2)
    //
    // The providers are registered individually and collected by the repository, which is what makes
    // "add a remote or AI tier" a one-line change here rather than an edit to the pipeline.

    /** Tier 1: the user's own foods, already in SQLite. */
    single<NutritionLookupProvider>(named(SavedFoodLookupProvider.NAME)) {
        SavedFoodLookupProvider(nutritionRepository = get())
    }

    /** Tier 2: the lexicon compiled into the app. */
    single<NutritionLookupProvider>(named(BundledLexiconProvider.NAME)) {
        BundledLexiconProvider()
    }

    single<NutritionLookupRepository> {
        NutritionLookupRepositoryImpl(
            providers = getAll<NutritionLookupProvider>(),
            nutritionRepository = get(),
        )
    }

    viewModel {
        DashboardViewModel(
            metricRepository = get(),
            dataExporter = get(),
            demoDataInstaller = getOrNull(),
        )
    }
    viewModel { MetricDetailViewModel(metricRepository = get(), zoneId = get()) }
    viewModel { CompareViewModel(metricRepository = get(), zoneId = get()) }
    viewModel {
        NutritionViewModel(
            nutritionRepository = get(),
            metricRepository = get(),
            lookupRepository = get(),
            zoneId = get(),
        )
    }
}
