package com.healthtrend.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.res.stringResource
import com.healthtrend.core.data.nutrition.lexicon.BundledLexiconNames
import com.healthtrend.core.domain.model.FoodItem
import com.healthtrend.core.domain.nutrition.lookup.FoodNutrientProfile
import com.healthtrend.core.domain.nutrition.lookup.FoodSearchResult

/**
 * The name of a food, in the language the device is using.
 *
 * A food's stored name is a single canonical label, and it is deliberately **not** retranslated when
 * the app language changes — the user's rows are theirs. For the foods the app itself supplies,
 * however, a name in the wrong language is just a bug: a Chinese reader looking at their own diary
 * should not see *Cooked white rice*. The bundled lexicon therefore also ships its names as string
 * resources, and this resolves them at render time.
 *
 * The distinction that decides which one wins is authorship, not where the food came from:
 *
 * - A food the user created or corrected (`isCustom`) is quoted **verbatim**. Their words are the
 *   most accurate description of what they ate and nothing should overwrite them.
 * - An untouched copy of a curated food is shown under the curated name, localised. That includes
 *   the user's *own* library: adopting 米饭（熟） copies it in, and it should keep reading as 米饭
 *   afterwards rather than reverting to the English label it was stored under.
 *
 * A name with no resource — a user's own food, or one adopted from a future remote tier — falls back
 * to the stored string, which is the only name it has.
 */
@Composable
@ReadOnlyComposable
internal fun foodDisplayName(food: FoodItem): String =
    if (food.isCustom) food.name else curatedFoodName(food.id, food.name)

/** A search hit. See [foodDisplayName] for the rule. */
@Composable
@ReadOnlyComposable
internal fun foodDisplayName(result: FoodSearchResult): String =
    if (result.isCustom) result.name else curatedFoodName(result.foodRef.localId, result.name)

/** A profile awaiting confirmation, shown under the same rule. */
@Composable
@ReadOnlyComposable
internal fun foodDisplayName(profile: FoodNutrientProfile): String =
    curatedFoodName(profile.foodRef.localId, profile.foodName)

/** The shared rule, so the three entry points above cannot disagree about it. */
@Composable
@ReadOnlyComposable
private fun curatedFoodName(foodId: String, fallback: String): String =
    BundledLexiconNames.nameRes(foodId)?.let { stringResource(it) } ?: fallback
