package uk.lunarlab.health2609

import okhttp3.MultipartBody
import uk.lunarlab.health2609.core.network.ApiWriteResult
import uk.lunarlab.health2609.core.network.ConfirmedHomeMealRequest
import uk.lunarlab.health2609.core.network.DailySummaryDto
import uk.lunarlab.health2609.core.network.DishDto
import uk.lunarlab.health2609.core.network.EnergyReferenceRequest
import uk.lunarlab.health2609.core.network.HealthApi
import uk.lunarlab.health2609.core.network.HomeMealAnalysisItemDto
import uk.lunarlab.health2609.core.network.HomeMealAnalysisResultDto
import uk.lunarlab.health2609.core.network.ManualActivityRequest
import uk.lunarlab.health2609.core.network.MealConsumptionRequest
import uk.lunarlab.health2609.core.network.NutritionDto
import uk.lunarlab.health2609.core.network.OutsideSchoolActivityRequest
import uk.lunarlab.health2609.core.network.SchoolActivityDto
import uk.lunarlab.health2609.core.network.SchoolActivityOverrideRequest
import uk.lunarlab.health2609.core.network.SchoolDayWindowsDto
import uk.lunarlab.health2609.core.network.StudentSchoolDto
import uk.lunarlab.health2609.core.network.StudentSchoolsDto
import uk.lunarlab.health2609.core.network.TodayMenuDto

class FakeHealthApi : HealthApi {
    var savedHomeMeals = uk.lunarlab.health2609.core.network.HomeMealsDto("2026-09-30")
    override suspend fun homeMeals(date: String) = savedHomeMeals
    var schoolsToReturn = StudentSchoolsDto(
        schools = listOf(
            StudentSchoolDto(
                id = "demo-school",
                name = "示例中学",
                timezone = "Asia/Shanghai"
            )
        )
    )

    var schoolActivityToReturn = SchoolActivityDto(
        date = "2026-09-30",
        weekday = 3,
        schoolDayWindows = emptyList(),
        peWindows = emptyList(),
        schoolRecordedMinutes = 40,
        selectedSource = "school"
    )

    var lastSavedSchoolActivityOverride: SchoolActivityOverrideRequest? = null

    override suspend fun studentSchools(): StudentSchoolsDto = schoolsToReturn

    override suspend fun todaySchoolActivity(date: String): SchoolActivityDto = schoolActivityToReturn

    override suspend fun saveSchoolActivitySource(request: SchoolActivityOverrideRequest): ApiWriteResult {
        lastSavedSchoolActivityOverride = request
        return ApiWriteResult(ok = true)
    }
    var menuToReturn = TodayMenuDto(
        date = "2026-09-30",
        mealSlot = "lunch",
        dishes = listOf(
            DishDto(
                id = "dish-1",
                name = "番茄炒蛋",
                standardServingGrams = 150.0,
                nutritionPerServing = NutritionDto(energyKcal = 180.0, proteinG = 12.0)
            ),
            DishDto(
                id = "dish-2",
                name = "清炒西兰花",
                standardServingGrams = 120.0,
                nutritionPerServing = NutritionDto(energyKcal = 60.0, fiberG = 4.0)
            )
        )
    )

    var summaryToReturn = DailySummaryDto(
        date = "2026-09-30"
    )

    var lastSavedMealRequest: MealConsumptionRequest? = null
    var lastSavedManualActivityRequest: ManualActivityRequest? = null
    var manualSaveCount: Int = 0
    var lastSavedOutsideSchoolRequest: OutsideSchoolActivityRequest? = null
    var lastSavedEnergyReferenceRequest: EnergyReferenceRequest? = null
    var lastSavedHomeMealRequest: ConfirmedHomeMealRequest? = null

    override suspend fun todayMenu(date: String, mealSlot: String): TodayMenuDto = menuToReturn

    var summaryGate: kotlinx.coroutines.CompletableDeferred<Unit>? = null
    var manualSaveGate: kotlinx.coroutines.CompletableDeferred<Unit>? = null
    override suspend fun todaySummary(date: String): DailySummaryDto {
        summaryGate?.await()
        return summaryToReturn
    }

    override suspend fun schoolDayWindows(date: String): SchoolDayWindowsDto =
        SchoolDayWindowsDto(date = date, weekday = 3, windows = emptyList())

    var shouldFailAnalyze: Boolean = false
    var analyzeException: Exception = RuntimeException("视觉模型服务异常")

    override suspend fun analyzeHomeMeals(images: List<MultipartBody.Part>): HomeMealAnalysisResultDto {
        if (shouldFailAnalyze) throw analyzeException
        return HomeMealAnalysisResultDto(
            schemaVersion = 1,
            items = listOf(
                HomeMealAnalysisItemDto(
                    name = "红烧牛肉",
                    estimatedGrams = 150.0,
                    confidence = 0.9,
                    nutrition = NutritionDto(energyKcal = 320.0, proteinG = 28.0)
                )
            ),
            notes = listOf("牛肉富含优质蛋白质")
        )
    }

    override suspend fun analyzeHomeMeal(image: MultipartBody.Part): HomeMealAnalysisResultDto =
        analyzeHomeMeals(listOf(image))

    override suspend fun saveHomeMeal(request: ConfirmedHomeMealRequest): ApiWriteResult {
        lastSavedHomeMealRequest = request
        return ApiWriteResult(ok = true, id = "hm-1")
    }

    override suspend fun saveMeal(request: MealConsumptionRequest): ApiWriteResult {
        lastSavedMealRequest = request
        return ApiWriteResult(ok = true, id = "meal-1")
    }

    override suspend fun saveOutsideSchoolActivity(request: OutsideSchoolActivityRequest): ApiWriteResult {
        lastSavedOutsideSchoolRequest = request
        return ApiWriteResult(ok = true, id = "act-1")
    }

    override suspend fun saveManualActivity(request: ManualActivityRequest): ApiWriteResult {
        manualSaveCount += 1
        manualSaveGate?.await()
        lastSavedManualActivityRequest = request
        return ApiWriteResult(ok = true, id = "mact-1")
    }

    override suspend fun saveEnergyReference(request: EnergyReferenceRequest): ApiWriteResult {
        lastSavedEnergyReferenceRequest = request
        return ApiWriteResult(ok = true)
    }
}
