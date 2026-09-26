package com.healthtrend.ui.common

import androidx.annotation.StringRes
import com.healthtrend.R
import com.healthtrend.core.analytics.normalize.NormalizationMode

/** Display names of the normalisation projections (AGENTS.md §7.3). */
@get:StringRes
internal val NormalizationMode.labelRes: Int
    get() = when (this) {
        NormalizationMode.Z_SCORE -> R.string.compare_mode_zscore
        NormalizationMode.MIN_MAX -> R.string.compare_mode_minmax
        NormalizationMode.BASELINE_100 -> R.string.compare_mode_baseline
    }

/**
 * What the shared y axis means in this mode.
 *
 * A normalised axis is unit-free, so without this line a reader cannot tell whether `2` means "two
 * standard deviations" or "twice the first reading".
 */
@get:StringRes
internal val NormalizationMode.scaleNoteRes: Int
    get() = when (this) {
        NormalizationMode.Z_SCORE -> R.string.compare_scale_zscore
        NormalizationMode.MIN_MAX -> R.string.compare_scale_minmax
        NormalizationMode.BASELINE_100 -> R.string.compare_scale_baseline
    }
