package uk.lunarlab.health2609.core.network

data class NutritionDto(
    val energyKcal: Double? = null,
    val proteinG: Double? = null,
    val fatG: Double? = null,
    val carbohydrateG: Double? = null
)

data class DishDto(
    val id: String,
    val name: String,
    val standardServingGrams: Double?,
    val nutritionPerServing: NutritionDto?
)

data class TodayMenuDto(
    val date: String,
    val mealSlot: String,
    val dishes: List<DishDto>
)

data class MealItemRequest(
    val dishId: String,
    val servingMultiplier: Double
)

data class MealConsumptionRequest(
    val date: String,
    val mealSlot: String,
    val items: List<MealItemRequest>
)

data class ApiOk(
    val ok: Boolean
)
