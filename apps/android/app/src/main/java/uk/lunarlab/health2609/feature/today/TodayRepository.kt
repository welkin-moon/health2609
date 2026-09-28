package uk.lunarlab.health2609.feature.today

import uk.lunarlab.health2609.core.network.HealthApi
import uk.lunarlab.health2609.core.network.MealConsumptionRequest
import uk.lunarlab.health2609.core.network.MealItemRequest
import uk.lunarlab.health2609.core.network.TodayMenuDto

class TodayRepository(
    private val api: HealthApi
) {
    suspend fun loadMenu(date: String): TodayMenuDto =
        api.todayMenu(date = date, mealSlot = "lunch")

    suspend fun saveMeal(
        date: String,
        portions: Map<String, Double>
    ) {
        api.saveMeal(
            MealConsumptionRequest(
                date = date,
                mealSlot = "lunch",
                items = portions.map { (dishId, portion) ->
                    MealItemRequest(
                        dishId = dishId,
                        servingMultiplier = portion
                    )
                }
            )
        )
    }
}
