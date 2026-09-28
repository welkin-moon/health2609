package uk.lunarlab.health2609.core.network

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

interface HealthApi {
    @GET("v1/today/menu")
    suspend fun todayMenu(
        @Query("date") date: String,
        @Query("mealSlot") mealSlot: String = "lunch"
    ): TodayMenuDto

    @POST("v1/meals/consumption")
    suspend fun saveMeal(
        @Body request: MealConsumptionRequest
    ): ApiOk
}
