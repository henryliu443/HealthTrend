package com.healthtrend.core.data.nutrition.lookup

import kotlinx.coroutines.CancellationException

/**
 * Turns a failure into `Result.failure` **without** swallowing coroutine cancellation.
 *
 * `runCatching` looks like the obvious tool here and is the wrong one: it catches everything,
 * including the `CancellationException` a coroutine uses to unwind, so a cancelled search would be
 * reported to the next tier as "this provider had nothing" and the coroutine would carry on running
 * after its scope was torn down.
 */
internal suspend fun <T> lookupResult(block: suspend () -> T): Result<T> =
    try {
        Result.success(block())
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (exception: Exception) {
        Result.failure(exception)
    }
