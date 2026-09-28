package uk.lunarlab.health2609.core.network

data class NutritionDto(
    val energyKcal: Double? = null,
    val proteinG: Double? = null,
    val fatG: Double? = null,
    val carbohydrateG: Double? = null,
    val fiberG: Double? = null,
    val sodiumMg: Double? = null,
    val sugarG: Double? = null,
    val saturatedFatG: Double? = null
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
    val servingMultiplier: Double? = null,
    val consumedGrams: Double? = null
)

data class MealConsumptionRequest(
    val date: String,
    val mealSlot: String,
    val items: List<MealItemRequest>
)

data class SchoolDayWindowDto(
    val id: String,
    val startTime: String,
    val endTime: String
)

data class SchoolDayWindowsDto(
    val date: String,
    val weekday: Int,
    val windows: List<SchoolDayWindowDto>
)

data class OutsideSchoolActivityRequest(
    val date: String,
    val exerciseMinutes: Int,
    val steps: Long? = null,
    val activeEnergyKcal: Double? = null
)

data class ManualActivityRequest(
    val date: String,
    val activityType: String,
    val startTime: String? = null,
    val durationMinutes: Int,
    val intensity: String,
    val estimatedActiveEnergyKcal: Double? = null
)

data class EnergyReferenceRequest(
    val dailyEnergyReferenceKcal: Int?
)

data class MacroCompositionDto(
    val protein: Double = 0.0,
    val fat: Double = 0.0,
    val carbohydrate: Double = 0.0
)

data class NutritionSummaryDto(
    val energyKcal: Double = 0.0,
    val proteinG: Double = 0.0,
    val fatG: Double = 0.0,
    val carbohydrateG: Double = 0.0,
    val fiberG: Double = 0.0,
    val sodiumMg: Double = 0.0,
    val sugarG: Double = 0.0,
    val saturatedFatG: Double = 0.0,
    val macroCompositionPercent: MacroCompositionDto = MacroCompositionDto()
)

data class IntensityMinutesDto(
    val light: Int = 0,
    val moderate: Int = 0,
    val vigorous: Int = 0
)

data class ActivitySummaryDto(
    val peMinutes: Int = 0,
    val healthConnectOutsideMinutes: Int = 0,
    val manualOutsideMinutes: Int = 0,
    val outsideMinutes: Int = 0,
    val totalMinutes: Int = 0,
    val targetMinutes: Int = 120,
    val targetReached: Boolean = false,
    val intensityMinutes: IntensityMinutesDto = IntensityMinutesDto(),
    val activeEnergyKcal: Double? = null,
    val manuallyEstimatedActiveEnergyKcal: Double = 0.0
)

data class EnergySummaryDto(
    val dailyEnergyReferenceKcal: Int? = null,
    val intakeKcal: Double = 0.0,
    val referenceGapKcal: Double? = null
)

data class DailySummaryDto(
    val date: String,
    val nutrition: NutritionSummaryDto = NutritionSummaryDto(),
    val activity: ActivitySummaryDto = ActivitySummaryDto(),
    val energy: EnergySummaryDto = EnergySummaryDto()
)

data class HomeMealAnalysisItemDto(
    val name: String,
    val estimatedGrams: Double? = null,
    val servingMultiplier: Double? = null,
    val confidence: Double = 0.0,
    val nutrition: NutritionDto? = null,
    val needsConfirmation: List<String> = emptyList()
)

data class HomeMealAnalysisResultDto(
    val schemaVersion: Int,
    val items: List<HomeMealAnalysisItemDto> = emptyList(),
    val notes: List<String> = emptyList()
)

data class ConfirmedHomeMealItemRequest(
    val name: String,
    val grams: Double?,
    val nutrition: NutritionDto?
)

data class ConfirmedHomeMealRequest(
    val date: String,
    val mealSlot: String,
    val items: List<ConfirmedHomeMealItemRequest>
)

data class ApiWriteResult(
    val ok: Boolean,
    val id: String? = null
)
