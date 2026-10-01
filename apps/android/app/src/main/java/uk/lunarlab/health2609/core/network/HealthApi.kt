package uk.lunarlab.health2609.core.network

import okhttp3.MultipartBody
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.PUT
import retrofit2.http.Query

interface HealthApi {
    @GET("v1/student/schools")
    suspend fun studentSchools(): StudentSchoolsDto

    @GET("v1/today/menu")
    suspend fun todayMenu(
        @Query("date") date: String,
        @Query("mealSlot") mealSlot: String = "lunch"
    ): TodayMenuDto

    @GET("v1/today/summary")
    suspend fun todaySummary(
        @Query("date") date: String
    ): DailySummaryDto

    @GET("v1/school/day-windows")
    suspend fun schoolDayWindows(
        @Query("date") date: String
    ): SchoolDayWindowsDto

    @GET("v1/today/school-activity")
    suspend fun todaySchoolActivity(
        @Query("date") date: String
    ): SchoolActivityDto

    @PUT("v1/activity/school-source")
    suspend fun saveSchoolActivitySource(
        @Body request: SchoolActivityOverrideRequest
    ): ApiWriteResult

    @Multipart
    @POST("v1/home-meals/analyze")
    suspend fun analyzeHomeMeals(
        @Part images: List<MultipartBody.Part>
    ): HomeMealAnalysisResultDto

    @Multipart
    @POST("v1/home-meals/analyze")
    suspend fun analyzeHomeMeal(
        @Part image: MultipartBody.Part
    ): HomeMealAnalysisResultDto

    @POST("v1/home-meals")
    suspend fun saveHomeMeal(
        @Body request: ConfirmedHomeMealRequest
    ): ApiWriteResult

    @POST("v1/meals/consumption")
    suspend fun saveMeal(
        @Body request: MealConsumptionRequest
    ): ApiWriteResult

    @POST("v1/activity/outside-school")
    suspend fun saveOutsideSchoolActivity(
        @Body request: OutsideSchoolActivityRequest
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
