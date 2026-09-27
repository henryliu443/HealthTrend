package com.healthtrend.core.data.nutrition.lexicon

import androidx.annotation.StringRes

/**
 * Localised display names for the bundled lexicon.
 *
 * A food's *stored* name is one canonical label — the language of the record it came from — because a
 * database row cannot be in two languages at once and switching the app language must not rewrite
 * what the user has. That leaves the display name to be resolved at render time, which is what this
 * exposes: [`nameRes`] returns the string resource holding the food's name in the language the
 * device is using.
 *
 * The table itself is generated (see `BundledFoodLexiconData.kt` and
 * `tools/generate_food_lexicon.py`) from the same two names the catalogue already carries, so the
 * English and Chinese lists cannot drift apart from the data they describe.
 */
object BundledLexiconNames {

    /**
     * The string resource holding [foodId]'s localised name, or `null` for a food the bundled
     * lexicon does not know — a user's own creation, or something adopted from a future tier. The
     * caller falls back to the stored name, which is right for both.
     */
    @StringRes
    fun nameRes(foodId: String): Int? = LEXICON_NAME_RES[foodId]

    /** How many foods carry a localised name. Test-facing, so the generated map can be checked. */
    val size: Int get() = LEXICON_NAME_RES.size
}
