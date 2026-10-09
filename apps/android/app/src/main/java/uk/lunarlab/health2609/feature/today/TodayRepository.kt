package uk.lunarlab.health2609.feature.today

import java.util.UUID
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import uk.lunarlab.health2609.core.network.ConfirmedHomeMealItemRequest
import uk.lunarlab.health2609.core.network.ConfirmedHomeMealRequest
import uk.lunarlab.health2609.core.network.DailySummaryDto
import uk.lunarlab.health2609.core.network.EnergyReferenceRequest
import uk.lunarlab.health2609.core.network.EnergySummaryDto
import uk.lunarlab.health2609.core.network.HealthApi
import uk.lunarlab.health2609.core.network.HomeMealAnalysisResultDto
import uk.lunarlab.health2609.core.network.IntensityMinutesDto
import uk.lunarlab.health2609.core.network.MacroCompositionDto
import uk.lunarlab.health2609.core.network.ManualActivityRequest
import uk.lunarlab.health2609.core.network.MealConsumptionRequest
import uk.lunarlab.health2609.core.network.MealItemRequest
import uk.lunarlab.health2609.core.network.NutritionDto
import uk.lunarlab.health2609.core.network.NutritionSummaryDto
import uk.lunarlab.health2609.core.network.OutsideSchoolActivityRequest
import uk.lunarlab.health2609.core.network.SchoolActivityDto
import uk.lunarlab.health2609.core.network.SchoolActivityOverrideRequest
import uk.lunarlab.health2609.core.network.SchoolDayWindowDto
import uk.lunarlab.health2609.core.network.StudentSchoolDto
import uk.lunarlab.health2609.core.network.TodayMenuDto
import uk.lunarlab.health2609.core.sync.SyncRepository
import uk.lunarlab.health2609.core.utils.ImageCompressor

data class TodayData(
    val menu: TodayMenuDto,
    val summary: DailySummaryDto,
    val schoolActivity: SchoolActivityDto,
    val schools: List<StudentSchoolDto>
)

class TodayRepository(
    private val api: HealthApi,
    private val syncRepository: SyncRepository? = null
) {
    suspend fun load(date: String): TodayData = coroutineScope {
        val menu = async { api.todayMenu(date = date, mealSlot = "lunch") }
        val summary = async { loadSummary(date) }
        val schoolActivity = async { api.todaySchoolActivity(date = date) }
        val schools = async { runCatching { api.studentSchools().schools }.getOrDefault(emptyList()) }
        TodayData(
            menu = menu.await(),
            summary = summary.await(),
            schoolActivity = schoolActivity.await(),
            schools = schools.await()
        )
    }

    suspend fun loadSummary(date: String): DailySummaryDto {
        val remote = api.todaySummary(date)
        return if (syncRepository == null) remote else overlayPrivateSummary(remote, date)
    }

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
        syncRepository?.enqueuePrivateRecord(
            entityType = "outside_activity",
            entityId = date,
            payloadJson = JSONObject()
                .put("date", date)
                .put("exerciseMinutes", exerciseMinutes)
                .put("steps", steps ?: JSONObject.NULL)
                .put("activeEnergyKcal", activeEnergyKcal ?: JSONObject.NULL)
                .toString()
        )

        // This aggregate feeds the school dashboard. Raw sensor samples and
        // trajectories never leave the device; the encrypted journal above is
        // the personal cross-device source of truth.
        api.saveOutsideSchoolActivity(
            OutsideSchoolActivityRequest(
                date = date,
                exerciseMinutes = exerciseMinutes,
                steps = steps,
                activeEnergyKcal = activeEnergyKcal
            )
        )
    }

    data class ImagePayload(
        val bytes: ByteArray,
        val mimeType: String,
        val fileName: String = "meal.jpg"
    )

    suspend fun analyzeHomeMeals(
        images: List<ImagePayload>
    ): HomeMealAnalysisResultDto {
        require(images.isNotEmpty()) { "请至少提供一张图片" }
        require(images.size <= 5) { "最多支持上传 5 张图片" }

        val compressedList = images.map { img ->
            val result = runCatching {
                ImageCompressor.compressFromBytes(img.bytes)
            }.getOrNull()
            if (result != null) {
                img.copy(
                    bytes = result.bytes,
                    mimeType = result.mimeType,
                    fileName = result.fileName
                )
            } else {
                img
            }
        }

        val parts = compressedList.mapIndexed { index, img ->
            require(img.bytes.isNotEmpty()) { "图片为空" }
            require(img.bytes.size <= 8 * 1024 * 1024) { "单张图片不能超过 8 MB" }
            val body = img.bytes.toRequestBody(img.mimeType.toMediaTypeOrNull())
            MultipartBody.Part.createFormData(
                "images",
                img.fileName.ifBlank { "meal_" + index + ".jpg" },
                body
            )
        }
        return api.analyzeHomeMeals(parts)
    }

    suspend fun analyzeHomeMeal(
        bytes: ByteArray,
        mimeType: String,
        fileName: String = "meal.jpg"
    ): HomeMealAnalysisResultDto =
        analyzeHomeMeals(listOf(ImagePayload(bytes, mimeType, fileName)))

    suspend fun saveHomeMeal(
        date: String,
        mealSlot: String,
        items: List<ConfirmedHomeMealItemRequest>
    ) {
        if (syncRepository == null) {
            api.saveHomeMeal(
                ConfirmedHomeMealRequest(date = date, mealSlot = mealSlot, items = items)
            )
            return
        }

        val jsonItems = JSONArray()
        items.forEach { item ->
            jsonItems.put(
                JSONObject()
                    .put("name", item.name)
                    .put("grams", item.grams ?: JSONObject.NULL)
                    .put("nutrition", item.nutrition?.toJson() ?: JSONObject.NULL)
            )
        }
        syncRepository.enqueuePrivateRecord(
            entityType = "home_meal",
            entityId = date + ":" + mealSlot,
            payloadJson = JSONObject()
                .put("date", date)
                .put("mealSlot", mealSlot)
                .put("items", jsonItems)
                .toString()
        )
    }

    fun scaledNutrition(
        nutrition: NutritionDto?,
        sourceGrams: Double?,
        confirmedGrams: Double?
    ): NutritionDto? {
        if (nutrition == null) return null
        if (sourceGrams == null || sourceGrams <= 0.0 || confirmedGrams == null) {
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
        val items = amounts.map { (dishId, amount) ->
            MealItemRequest(
                dishId = dishId,
                servingMultiplier = amount.servingMultiplier,
                consumedGrams = amount.consumedGrams
            )
        }

        syncRepository?.enqueuePrivateRecord(
            entityType = "school_meal",
            entityId = date + ":lunch",
            payloadJson = JSONObject()
                .put("date", date)
                .put("mealSlot", "lunch")
                .put(
                    "items",
                    JSONArray().apply {
                        items.forEach { item ->
                            put(
                                JSONObject()
                                    .put("dishId", item.dishId)
                                    .put("servingMultiplier", item.servingMultiplier ?: JSONObject.NULL)
                                    .put("consumedGrams", item.consumedGrams ?: JSONObject.NULL)
                            )
                        }
                    }
                )
                .toString()
        )

        // Campus meal contribution remains in the school-readable operational
        // boundary because it drives k-anonymous class statistics.
        api.saveMeal(MealConsumptionRequest(date = date, mealSlot = "lunch", items = items))
    }

    suspend fun saveManualActivity(
        date: String,
        activityType: String,
        durationMinutes: Int,
        intensity: String
    ) {
        if (syncRepository == null) {
            api.saveManualActivity(
                ManualActivityRequest(
                    date = date,
                    activityType = activityType,
                    durationMinutes = durationMinutes,
                    intensity = intensity
                )
            )
            return
        }

        syncRepository.enqueuePrivateRecord(
            entityType = "manual_activity",
            entityId = date + ":" + UUID.randomUUID().toString(),
            payloadJson = JSONObject()
                .put("date", date)
                .put("activityType", activityType)
                .put("durationMinutes", durationMinutes)
                .put("intensity", intensity)
                .toString()
        )
    }

    suspend fun saveEnergyReference(kcal: Int?) {
        if (syncRepository == null) {
            api.saveEnergyReference(EnergyReferenceRequest(dailyEnergyReferenceKcal = kcal))
            return
        }

        syncRepository.enqueuePrivateRecord(
            entityType = "preference",
            entityId = "energy_reference",
            payloadJson = JSONObject()
                .put("dailyEnergyReferenceKcal", kcal ?: JSONObject.NULL)
                .toString()
        )
    }

    private suspend fun overlayPrivateSummary(
        remote: DailySummaryDto,
        date: String
    ): DailySummaryDto {
        val sync = syncRepository ?: return remote

        var homeEnergy = 0.0
        var homeProtein = 0.0
        var homeFat = 0.0
        var homeCarbs = 0.0
        var homeFiber = 0.0
        var homeSodium = 0.0
        var homeSugar = 0.0
        var homeSaturatedFat = 0.0

        sync.listPrivateRecords("home_meal")
            .filter { !it.deleted && it.entityId.startsWith(date + ":") }
            .forEach { record ->
                val items = runCatching {
                    JSONObject(record.payloadJson).optJSONArray("items")
                }.getOrNull() ?: return@forEach
                for (i in 0 until items.length()) {
                    val nutrition = items.optJSONObject(i)?.optJSONObject("nutrition") ?: continue
                    homeEnergy += nutrition.optDouble("energyKcal", 0.0)
                    homeProtein += nutrition.optDouble("proteinG", 0.0)
                    homeFat += nutrition.optDouble("fatG", 0.0)
                    homeCarbs += nutrition.optDouble("carbohydrateG", 0.0)
                    homeFiber += nutrition.optDouble("fiberG", 0.0)
                    homeSodium += nutrition.optDouble("sodiumMg", 0.0)
                    homeSugar += nutrition.optDouble("sugarG", 0.0)
                    homeSaturatedFat += nutrition.optDouble("saturatedFatG", 0.0)
                }
            }

        var localManualMinutes = 0
        var localLight = 0
        var localModerate = 0
        var localVigorous = 0
        var localEstimatedEnergy = 0.0
        sync.listPrivateRecords("manual_activity")
            .filter { !it.deleted }
            .forEach { record ->
                val json = runCatching { JSONObject(record.payloadJson) }.getOrNull()
                    ?: return@forEach
                if (json.optString("date") != date) return@forEach
                val minutes = json.optInt("durationMinutes", 0).coerceAtLeast(0)
                localManualMinutes += minutes
                when (json.optString("intensity")) {
                    "light" -> localLight += minutes
                    "vigorous" -> localVigorous += minutes
                    else -> localModerate += minutes
                }
                localEstimatedEnergy += json.optDouble("estimatedActiveEnergyKcal", 0.0)
            }

        val preferenceRecord = sync.getPrivateRecord("preference", "energy_reference")
        val localEnergyReference = preferenceRecord?.takeIf { !it.deleted }?.let { record ->
            runCatching {
                val json = JSONObject(record.payloadJson)
                if (json.isNull("dailyEnergyReferenceKcal")) null
                else json.getInt("dailyEnergyReferenceKcal")
            }.getOrNull()
        }

        val nutrition = NutritionSummaryDto(
            energyKcal = remote.nutrition.energyKcal + homeEnergy,
            proteinG = remote.nutrition.proteinG + homeProtein,
            fatG = remote.nutrition.fatG + homeFat,
            carbohydrateG = remote.nutrition.carbohydrateG + homeCarbs,
            fiberG = remote.nutrition.fiberG + homeFiber,
            sodiumMg = remote.nutrition.sodiumMg + homeSodium,
            sugarG = remote.nutrition.sugarG + homeSugar,
            saturatedFatG = remote.nutrition.saturatedFatG + homeSaturatedFat
        )
        val macroEnergy =
            nutrition.proteinG * 4.0 +
            nutrition.fatG * 9.0 +
            nutrition.carbohydrateG * 4.0
        val nutritionWithMacro = nutrition.copy(
            macroCompositionPercent = MacroCompositionDto(
                protein = if (macroEnergy > 0) nutrition.proteinG * 4.0 / macroEnergy * 100.0 else 0.0,
                fat = if (macroEnergy > 0) nutrition.fatG * 9.0 / macroEnergy * 100.0 else 0.0,
                carbohydrate = if (macroEnergy > 0) nutrition.carbohydrateG * 4.0 / macroEnergy * 100.0 else 0.0
            )
        )

        val manualTotal = remote.activity.manualOutsideMinutes + localManualMinutes
        val outsideMinutes = maxOf(remote.activity.healthConnectOutsideMinutes, manualTotal)
        val totalMinutes = remote.activity.peMinutes + outsideMinutes
        val activity = remote.activity.copy(
            manualOutsideMinutes = manualTotal,
            outsideMinutes = outsideMinutes,
            totalMinutes = totalMinutes,
            targetReached = totalMinutes >= remote.activity.targetMinutes,
            intensityMinutes = IntensityMinutesDto(
                light = remote.activity.intensityMinutes.light + localLight,
                moderate = remote.activity.intensityMinutes.moderate + localModerate,
                vigorous = remote.activity.intensityMinutes.vigorous + localVigorous
            ),
            manuallyEstimatedActiveEnergyKcal =
                remote.activity.manuallyEstimatedActiveEnergyKcal + localEstimatedEnergy
        )

        val target = localEnergyReference ?: remote.energy.dailyEnergyReferenceKcal
        val energy = EnergySummaryDto(
            dailyEnergyReferenceKcal = target,
            intakeKcal = nutritionWithMacro.energyKcal,
            referenceGapKcal = target?.minus(nutritionWithMacro.energyKcal)
        )

        return remote.copy(
            nutrition = nutritionWithMacro,
            activity = activity,
            energy = energy
        )
    }

    private fun NutritionDto.toJson(): JSONObject =
        JSONObject()
            .put("energyKcal", energyKcal ?: JSONObject.NULL)
            .put("proteinG", proteinG ?: JSONObject.NULL)
            .put("fatG", fatG ?: JSONObject.NULL)
            .put("carbohydrateG", carbohydrateG ?: JSONObject.NULL)
            .put("fiberG", fiberG ?: JSONObject.NULL)
            .put("sodiumMg", sodiumMg ?: JSONObject.NULL)
            .put("sugarG", sugarG ?: JSONObject.NULL)
            .put("saturatedFatG", saturatedFatG ?: JSONObject.NULL)
}
