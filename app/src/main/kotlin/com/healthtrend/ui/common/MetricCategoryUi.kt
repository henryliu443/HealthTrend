package com.healthtrend.ui.common

import androidx.annotation.StringRes
import com.healthtrend.R
import com.healthtrend.core.domain.model.MetricCategory
import com.healthtrend.core.analytics.trend.TrendDirection

/** Display order of the dashboard's category sections. Mirrors the enum's declaration order. */
internal val MetricCategory.uiOrder: Int
    get() = when (this) {
        MetricCategory.BODY -> 0
        MetricCategory.NUTRITION -> 1
        MetricCategory.HEALTH -> 2
        MetricCategory.ACTIVITY -> 3
        MetricCategory.LIFESTYLE -> 4
        MetricCategory.CUSTOM -> 5
    }

@get:StringRes
internal val MetricCategory.labelRes: Int
    get() = when (this) {
        MetricCategory.BODY -> R.string.category_body
        MetricCategory.NUTRITION -> R.string.category_nutrition
        MetricCategory.ACTIVITY -> R.string.category_activity
        MetricCategory.LIFESTYLE -> R.string.category_lifestyle
        MetricCategory.HEALTH -> R.string.category_health
        MetricCategory.CUSTOM -> R.string.category_custom
    }

/**
 * Wording for a fitted trend.
 *
 * AGENTS.md §6.6: [TrendDirection.STABLE] means "not distinguishable from no change at the chosen
 * significance level" — it must never be rendered as "no change", and
 * [TrendDirection.INSUFFICIENT_DATA] must not be rendered as "stable".
 */
@get:StringRes
internal val TrendDirection.labelRes: Int
    get() = when (this) {
        TrendDirection.INCREASING -> R.string.detail_trend_increasing
        TrendDirection.DECREASING -> R.string.detail_trend_decreasing
        TrendDirection.STABLE -> R.string.detail_trend_stable
        TrendDirection.INSUFFICIENT_DATA -> R.string.detail_trend_insufficient
    }
