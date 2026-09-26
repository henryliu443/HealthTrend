package com.healthtrend.ui.common

import androidx.annotation.StringRes
import com.healthtrend.R
import com.healthtrend.core.domain.nutrition.lookup.LookupTier

/**
 * How a lookup tier is named in the UI.
 *
 * The label is not decoration: AGENTS.md §5.3 requires the user to confirm externally sourced values
 * before they are stored, and a confirmation is meaningless if the sheet does not say where the
 * numbers came from. The same wording is used on the search row and on the confirmation sheet, so
 * the provenance never changes name between the two.
 */
@get:StringRes
internal val LookupTier.labelRes: Int
    get() = when (this) {
        LookupTier.PERSONAL -> R.string.nutrition_source_personal
        LookupTier.BUNDLED -> R.string.nutrition_source_bundled
        LookupTier.REMOTE -> R.string.nutrition_source_remote
        LookupTier.ESTIMATE -> R.string.nutrition_source_estimate
    }

/** Whether a hit comes from outside the user's own data, and so needs confirming before it is kept. */
internal val LookupTier.needsConfirmation: Boolean
    get() = this != LookupTier.PERSONAL
