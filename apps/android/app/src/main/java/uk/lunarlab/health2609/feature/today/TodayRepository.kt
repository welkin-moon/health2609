package uk.lunarlab.health2609.feature.today

import kotlinx.coroutines.async
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import kotlinx.coroutines.coroutineScope
import uk.lunarlab.health2609.core.network.ConfirmedHomeMealItemRequest
import uk.lunarlab.health2609.core.network.ConfirmedHomeMealRequest
import uk.lunarlab.health2609.core.network.DailySummaryDto
import uk.lunarlab.health2609.core.network.HomeMealAnalysisResultDto
import uk.lunarlab.health2609.core.network.NutritionDto
import uk.lunarlab.health2609.core.network.EnergyReferenceRequest
import uk.lunarlab.health2609.core.network.HealthApi
import uk.lunarlab.health2609.core.network.ManualActivityRequest
import uk.lunarlab.health2609.core.network.MealConsumptionRequest
import uk.lunarlab.health2609.core.network.MealItemRequest
import uk.lunarlab.health2609.core.network.OutsideSchoolActivityRequest
import uk.lunarlab.health2609.core.network.SchoolActivityDto
import uk.lunarlab.health2609.core.network.StudentSchoolDto
import uk.lunarlab.health2609.core.network.SchoolActivityOverrideRequest
import uk.lunarlab.health2609.core.network.SchoolDayWindowDto
import uk.lunarlab.health2609.core.network.TodayMenuDto

data class TodayData(
    val menu: TodayMenuDto,
    val summary: DailySummaryDto,
    val schoolActivity: SchoolActivityDto,
    val schools: List<StudentSchoolDto>
)

class TodayRepository(
    private val api: HealthApi
) {
    suspend fun load(date: String): TodayData = coroutineScope {
        val menu = async { api.todayMenu(date = date, mealSlot = "lunch") }
        val summary = async { api.todaySummary(date = date) }
        val schoolActivity = async { api.todaySchoolActivity(date = date) }
        val schools = async { api.studentSchools().schools }
        TodayData(
            menu = menu.await(),
            summary = summary.await(),
            schoolActivity = schoolActivity.await(),
            schools = schools.await()
        )
    }

    suspend fun loadSummary(date: String): DailySummaryDto =
        api.todaySummary(date)

    suspend fun loadSchoolDayWindows(date: String): List<SchoolDayWindowDto> =
        api.schoolDayWindows(date).windows

    suspend fun loadSchoolActivity(date: String): SchoolActivityDto =
        api.todaySchoolActivity(date)

    suspend fun saveSchoolActivitySource(
        date: String,
        source: String,
        exerciseMinutes: Int? = null,
        steps: Long? = null,
        activeEnergyKcal: Double? = null
    ) {
        api.saveSchoolActivitySource(
            SchoolActivityOverrideRequest(
                date = date,
                source = source,
                exerciseMinutes = exerciseMinutes,
                steps = steps,
                activeEnergyKcal = activeEnergyKcal
            )
        )
    }

    suspend fun saveOutsideSchoolActivity(
        date: String,
        exerciseMinutes: Int,
        steps: Long?,
        activeEnergyKcal: Double?
    ) {
        api.saveOutsideSchoolActivity(
            OutsideSchoolActivityRequest(
                date = date,
                exerciseMinutes = exerciseMinutes,
                steps = steps,
                activeEnergyKcal = activeEnergyKcal
            )
        )
    }

    suspend fun analyzeHomeMeal(
        bytes: ByteArray,
        mimeType: String,
        fileName: String = "meal.jpg"
    ): HomeMealAnalysisResultDto {
        require(bytes.isNotEmpty()) { "图片为空" }
        require(bytes.size <= 8 * 1024 * 1024) { "图片不能超过 8 MB" }

        val body = bytes.toRequestBody(
            mimeType.toMediaTypeOrNull()
        )
        val part = MultipartBody.Part.createFormData(
            "image",
            fileName,
            body
        )
        return api.analyzeHomeMeal(part)
    }

    suspend fun saveHomeMeal(
        date: String,
        mealSlot: String,
        items: List<ConfirmedHomeMealItemRequest>
    ) {
        api.saveHomeMeal(
            ConfirmedHomeMealRequest(
                date = date,
                mealSlot = mealSlot,
                items = items
            )
        )
    }

    fun scaledNutrition(
        nutrition: NutritionDto?,
        sourceGrams: Double?,
        confirmedGrams: Double?
    ): NutritionDto? {
        if (nutrition == null) return null
        if (
            sourceGrams == null ||
            sourceGrams <= 0.0 ||
            confirmedGrams == null
        ) {
            return nutrition
        }

        val factor = (confirmedGrams / sourceGrams).coerceIn(0.0, 10.0)
        return NutritionDto(
            energyKcal = nutrition.energyKcal?.times(factor),
            proteinG = nutrition.proteinG?.times(factor),
            fatG = nutrition.fatG?.times(factor),
            carbohydrateG = nutrition.carbohydrateG?.times(factor),
            fiberG = nutrition.fiberG?.times(factor),
            sodiumMg = nutrition.sodiumMg?.times(factor),
            sugarG = nutrition.sugarG?.times(factor),
            saturatedFatG = nutrition.saturatedFatG?.times(factor)
        )
    }

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
