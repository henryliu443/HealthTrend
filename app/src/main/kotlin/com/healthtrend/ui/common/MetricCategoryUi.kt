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

/**
 * The arrow shown alongside [labelRes].
 *
 * It is a shape rather than a word so the verdict is legible at a glance before the label is read —
 * hence the `translatable="false"` strings behind it. [TrendDirection.INSUFFICIENT_DATA] gets a dash
 * rather than a direction: there is no fitted slope to point anywhere.
 */
@get:StringRes
internal val TrendDirection.glyphRes: Int
    get() = when (this) {
        TrendDirection.INCREASING -> R.string.trend_glyph_up
        TrendDirection.DECREASING -> R.string.trend_glyph_down
        TrendDirection.STABLE -> R.string.trend_glyph_flat
        TrendDirection.INSUFFICIENT_DATA -> R.string.trend_glyph_none
    }

/**
 * Whether the regression actually reached a verdict.
 *
 * [TrendDirection.INCREASING] and [TrendDirection.DECREASING] are the only outcomes that survive the
 * _p_ < 0.05 test; the other two mean "no conclusion", which is a different claim and must not be
 * dressed up the same way in the UI.
 */
internal val TrendDirection.isEstablished: Boolean
    get() = this == TrendDirection.INCREASING || this == TrendDirection.DECREASING
