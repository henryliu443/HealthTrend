package com.healthtrend

import kotlinx.coroutines.CoroutineScope
import org.koin.core.module.Module

/**
 * Build-variant startup hooks.
 *
 * This file has a sibling in `src/release` with exactly the same two declarations. Only the variant
 * that is being compiled is seen by the compiler, which is how AGENTS.md-friendly "debug features
 * never ship" is enforced without a `BuildConfig.DEBUG` check that could be stripped incorrectly.
 */

/** Extra Koin modules for this variant. */
internal fun platformExtraModules(): List<Module> = listOf(com.healthtrend.demo.demoModule)

/** Work to run once at process start, outside any Activity. */
internal fun installStartupHooks(scope: CoroutineScope) {
    com.healthtrend.demo.seedIfEmptyOnStart(scope)
}
