package com.healthtrend

import kotlinx.coroutines.CoroutineScope
import org.koin.core.module.Module

/**
 * Build-variant startup hooks.
 *
 * This file has a sibling in `src/release` with exactly the same two declarations. Only the variant
 * that is being compiled is seen by the compiler, which is how "debug features never ship" is
 * enforced without a `BuildConfig.DEBUG` check that could be stripped incorrectly.
 */

/** Extra Koin modules for this variant. */
internal fun platformExtraModules(): List<Module> = listOf(com.healthtrend.demo.demoModule)

/**
 * Work to run once at process start, outside any Activity.
 *
 * Deliberately empty. A debug build used to seed review data here, which meant the app wrote months
 * of made-up readings into the user's database the first time it was opened — the app's data should
 * start empty and stay the user's. Seeding is now opt-in: `DemoDataInstaller` is still registered as
 * a Koin module, and the dashboard offers it as a button when there is nothing to show.
 *
 * The hook itself stays, and stays paired with the release twin, because it is the seam for
 * variant-specific startup work (a future Health Connect import, for instance) and the pair is what
 * makes that work without a runtime flag.
 */
internal fun installStartupHooks(scope: CoroutineScope) = Unit
