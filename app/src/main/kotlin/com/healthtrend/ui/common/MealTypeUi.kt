package com.healthtrend.ui.common

import androidx.annotation.StringRes
import com.healthtrend.R
import com.healthtrend.core.domain.model.MealType

/** Display names of the four meal slots. */
@get:StringRes
internal val MealType.labelRes: Int
    get() = when (this) {
        MealType.BREAKFAST -> R.string.nutrition_meal_breakfast
        MealType.LUNCH -> R.string.nutrition_meal_lunch
        MealType.DINNER -> R.string.nutrition_meal_dinner
        MealType.SNACK -> R.string.nutrition_meal_snack
    }
