package com.healthtrend.ui.common

import androidx.annotation.StringRes
import com.healthtrend.R

/**
 * Localised names for the metrics the app itself supplies.
 *
 * The demo seed (and any future first-run setup) writes metric rows whose names are single canonical
 * labels, exactly like food names — a stored row cannot be in two languages. Without this table a
 * Chinese reader would get Chinese chrome around cards reading *Body weight* and *Total cholesterol*,
 * which is the mixed-language result this exists to avoid.
 *
 * Only ids the app is responsible for appear here. A metric the user created has an id the app has
 * never seen, so it falls through to the name they typed, which is the only correct answer.
 */
@StringRes
internal fun builtInMetricNameRes(metricId: String): Int? = when (metricId) {
    "body_weight" -> R.string.metric_body_weight
    "body_fat_percent" -> R.string.metric_body_fat_percent
    "resting_heart_rate" -> R.string.metric_resting_heart_rate
    "blood_pressure_systolic" -> R.string.metric_blood_pressure_systolic
    "blood_pressure_diastolic" -> R.string.metric_blood_pressure_diastolic
    "alt" -> R.string.metric_alt
    "ast" -> R.string.metric_ast
    "serum_total_cholesterol" -> R.string.metric_serum_total_cholesterol
    "serum_uric_acid" -> R.string.metric_serum_uric_acid
    "sleep_duration" -> R.string.metric_sleep_duration
    "daily_steps" -> R.string.metric_daily_steps
    else -> null
}
