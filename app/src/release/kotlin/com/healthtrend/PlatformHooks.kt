package com.healthtrend

import kotlinx.coroutines.CoroutineScope
import org.koin.core.module.Module

/**
 * Release twin of `src/debug/kotlin/com/healthtrend/PlatformHooks.kt`.
 *
 * Both declarations must stay in sync with the debug file: no demo data, no demo Koin modules and no
 * bundled sample lexicon reaches a release artifact.
 */

internal fun platformExtraModules(): List<Module> = emptyList()

internal fun installStartupHooks(scope: CoroutineScope) = Unit
