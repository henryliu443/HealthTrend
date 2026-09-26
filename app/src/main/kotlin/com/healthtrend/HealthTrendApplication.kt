package com.healthtrend

import android.app.Application
import com.healthtrend.di.appModule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin

/**
 * Process entry point: starts the Koin graph and runs the variant-specific startup hooks.
 *
 * The application owns a process-lifetime [CoroutineScope] for work that must outlive any Activity
 * (demo seeding today, Health Connect import later) — never a `GlobalScope`.
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
        installStartupHooks(applicationScope)
    }

    override fun onTerminate() {
        stopKoin()
        super.onTerminate()
    }
}
