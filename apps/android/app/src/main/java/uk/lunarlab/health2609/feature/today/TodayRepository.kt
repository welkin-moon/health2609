package uk.lunarlab.health2609.feature.today

import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import uk.lunarlab.health2609.core.network.DailySummaryDto
import uk.lunarlab.health2609.core.network.EnergyReferenceRequest
import uk.lunarlab.health2609.core.network.HealthApi
import uk.lunarlab.health2609.core.network.ManualActivityRequest
import uk.lunarlab.health2609.core.network.MealConsumptionRequest
import uk.lunarlab.health2609.core.network.MealItemRequest
import uk.lunarlab.health2609.core.network.TodayMenuDto

data class TodayData(
    val menu: TodayMenuDto,
    val summary: DailySummaryDto
)

class TodayRepository(
    private val api: HealthApi
) {
    suspend fun load(date: String): TodayData = coroutineScope {
        val menu = async { api.todayMenu(date = date, mealSlot = "lunch") }
        val summary = async { api.todaySummary(date = date) }
        TodayData(menu = menu.await(), summary = summary.await())
    }

    suspend fun loadSummary(date: String): DailySummaryDto =
        api.todaySummary(date)

    suspend fun saveMeal(
        date: String,
        amounts: Map<String, DishAmount>
    ) {
        api.saveMeal(
            MealConsumptionRequest(
                date = date,
                mealSlot = "lunch",
                items = amounts.map { (dishId, amount) ->
                    MealItemRequest(
                        dishId = dishId,
                        servingMultiplier = amount.servingMultiplier,
                        consumedGrams = amount.consumedGrams
                    )
                }
            )
        )
    }

    suspend fun saveManualActivity(
        date: String,
        activityType: String,
        durationMinutes: Int,
        intensity: String
    ) {
        api.saveManualActivity(
            ManualActivityRequest(
                date = date,
                activityType = activityType,
                durationMinutes = durationMinutes,
                intensity = intensity
            )
        )
    }

    suspend fun saveEnergyReference(kcal: Int?) {
        api.saveEnergyReference(
            EnergyReferenceRequest(
                dailyEnergyReferenceKcal = kcal
            )
        )
    }
}
