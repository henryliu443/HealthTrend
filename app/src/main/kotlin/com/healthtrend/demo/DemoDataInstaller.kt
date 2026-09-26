package com.healthtrend.demo

/**
 * Hook that fills an empty database with a reviewable demo data set.
 *
 * The interface lives in `src/main` so that `DashboardViewModel` can offer a "load demo data" button
 * without depending on the debug source set. The only implementation is registered by the debug-only
 * Koin module; release builds resolve `getOrNull<DemoDataInstaller>()` to `null` and simply hide the
 * button, so no demo code is packaged.
 */
interface DemoDataInstaller {

    /** Seeds the database only when it is still empty, then returns. Safe to call repeatedly. */
    suspend fun install()
}
