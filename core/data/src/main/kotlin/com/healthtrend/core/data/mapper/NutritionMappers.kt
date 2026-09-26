package com.healthtrend.core.data.mapper

import com.healthtrend.core.data.local.entity.FoodItemEntity
import com.healthtrend.core.data.local.entity.FoodNutrientValueEntity
import com.healthtrend.core.data.local.entity.MealLogEntity
import com.healthtrend.core.data.local.entity.NutrientDefinitionEntity
import com.healthtrend.core.domain.model.FoodItem
import com.healthtrend.core.domain.model.FoodNutrientValue
import com.healthtrend.core.domain.model.MealLog
import com.healthtrend.core.domain.model.MealType
import com.healthtrend.core.domain.model.NutrientCategory
import com.healthtrend.core.domain.model.NutrientDefinition

fun NutrientDefinitionEntity.toDomain(): NutrientDefinition = NutrientDefinition(
    id = id,
    name = name,
    unit = unit,
    category = enumOrDefault(category, NutrientCategory.MACRO),
    dailyRecommended = dailyRecommended,
    displayOrder = displayOrder,
)

fun NutrientDefinition.toEntity(): NutrientDefinitionEntity = NutrientDefinitionEntity(
    id = id,
    name = name,
    unit = unit,
    category = category.name,
    dailyRecommended = dailyRecommended,
    displayOrder = displayOrder,
)

fun FoodItemEntity.toDomain(): FoodItem = FoodItem(
    id = id,
    name = name,
    brand = brand,
    referenceAmount = referenceAmount,
    referenceUnit = referenceUnit,
    isCustom = isCustom,
)

fun FoodItem.toEntity(): FoodItemEntity = FoodItemEntity(
    id = id,
    name = name,
    brand = brand,
    referenceAmount = referenceAmount,
    referenceUnit = referenceUnit,
    isCustom = isCustom,
)

fun FoodNutrientValueEntity.toDomain(): FoodNutrientValue = FoodNutrientValue(
    foodId = foodId,
    nutrientId = nutrientId,
    amountPerReference = amountPerReference,
)

fun FoodNutrientValue.toEntity(): FoodNutrientValueEntity = FoodNutrientValueEntity(
    foodId = foodId,
    nutrientId = nutrientId,
    amountPerReference = amountPerReference,
)

fun MealLogEntity.toDomain(): MealLog = MealLog(
    id = id,
    foodId = foodId,
    timestampEpochMilli = timestamp,
    actualAmount = actualAmount,
    actualUnit = actualUnit,
    mealType = enumOrDefault(mealType, MealType.SNACK),
)

fun MealLog.toEntity(): MealLogEntity = MealLogEntity(
    id = id,
    foodId = foodId,
    timestamp = timestampEpochMilli,
    actualAmount = actualAmount,
    actualUnit = actualUnit,
    mealType = mealType.name,
)
