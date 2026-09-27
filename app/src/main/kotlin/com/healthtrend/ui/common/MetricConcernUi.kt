package com.healthtrend.ui.common

import androidx.annotation.StringRes
import com.healthtrend.R
import com.healthtrend.core.domain.model.MetricConcern

/**
 * Wording for [MetricConcern].
 *
 * Deliberately phrased as a property of the *range* ("higher values are the ones watched") rather
 * than as a verdict on a reading. The app is allowed to say which end of a reference range a marker
 * is usually judged on — that is what a reference range is for — but not to tell anyone that a
 * number is bad (AGENTS.md §10.3). `null` has no entry: a metric that does not state a direction
 * shows nothing at all rather than a vague dash.
 */
@get:StringRes
internal val MetricConcern.labelRes: Int
    get() = when (this) {
        MetricConcern.HIGHER_VALUES -> R.string.concern_higher
        MetricConcern.LOWER_VALUES -> R.string.concern_lower
        MetricConcern.BOTH_ENDS -> R.string.concern_both
    }
