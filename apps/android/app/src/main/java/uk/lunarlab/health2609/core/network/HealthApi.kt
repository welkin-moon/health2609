package uk.lunarlab.health2609.core.network

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Query

interface HealthApi {
    @GET("v1/today/menu")
    suspend fun todayMenu(
        @Query("date") date: String,
        @Query("mealSlot") mealSlot: String = "lunch"
    ): TodayMenuDto

    @GET("v1/today/summary")
    suspend fun todaySummary(
        @Query("date") date: String
    ): DailySummaryDto

    @POST("v1/meals/consumption")
    suspend fun saveMeal(
        @Body request: MealConsumptionRequest
    ): ApiWriteResult

    @POST("v1/activity/manual")
    suspend fun saveManualActivity(
        @Body request: ManualActivityRequest
    ): ApiWriteResult

    @PUT("v1/preferences/energy-reference")
    suspend fun saveEnergyReference(
        @Body request: EnergyReferenceRequest
    ): ApiWriteResult
}
