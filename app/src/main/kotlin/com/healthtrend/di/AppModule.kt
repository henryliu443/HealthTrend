package com.healthtrend.di

import com.healthtrend.core.data.local.HealthTrendDatabase
import com.healthtrend.core.data.projection.NutritionProjectionService
import com.healthtrend.core.data.repository.MetricRepositoryImpl
import com.healthtrend.core.data.repository.NutritionRepositoryImpl
import com.healthtrend.core.domain.repository.MetricRepository
import com.healthtrend.core.domain.repository.NutritionRepository
import com.healthtrend.ui.compare.CompareViewModel
import com.healthtrend.ui.dashboard.DashboardViewModel
import com.healthtrend.ui.detail.MetricDetailViewModel
import com.healthtrend.ui.nutrition.NutritionViewModel
import org.koin.android.ext.koin.androidContext
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.core.module.Module
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

    single<MetricRepository> {
        val database = get<HealthTrendDatabase>()
        MetricRepositoryImpl(database.metricDefinitionDao(), database.metricObservationDao())
    }

    single<NutritionRepository> { NutritionRepositoryImpl(database = get(), projection = get()) }

    viewModel {
        DashboardViewModel(
            metricRepository = get(),
            demoDataInstaller = getOrNull(),
        )
    }
    viewModel { MetricDetailViewModel(metricRepository = get(), zoneId = get()) }
    viewModel { CompareViewModel(metricRepository = get(), zoneId = get()) }
    viewModel {
        NutritionViewModel(nutritionRepository = get(), metricRepository = get(), zoneId = get())
    }
}
