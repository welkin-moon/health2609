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
import uk.lunarlab.health2609.core.network.HomeMealsDto
import uk.lunarlab.health2609.core.network.IntensityMinutesDto
import uk.lunarlab.health2609.core.network.MacroCompositionDto
import uk.lunarlab.health2609.core.network.ManualActivityRequest
import uk.lunarlab.health2609.core.network.MealConsumptionRequest
import uk.lunarlab.health2609.core.network.MealItemRequest
import uk.lunarlab.health2609.core.network.NutritionDto
import uk.lunarlab.health2609.core.network.NutritionSummaryDto
import uk.lunarlab.health2609.core.network.OutsideSchoolActivityRequest
import uk.lunarlab.health2609.core.network.SavedHomeMealDto
import uk.lunarlab.health2609.core.network.SchoolActivityDto
import uk.lunarlab.health2609.core.network.SchoolActivityOverrideRequest
import uk.lunarlab.health2609.core.network.SchoolDayWindowDto
import uk.lunarlab.health2609.core.network.StudentSchoolDto
import uk.lunarlab.health2609.core.network.TodayMenuDto
import uk.lunarlab.health2609.core.sync.SyncRepository

data class TodayData(
    val menu: TodayMenuDto,
    val summary: DailySummaryDto,
    val schoolActivity: SchoolActivityDto,
    val schools: List<StudentSchoolDto>,
    val homeMeals: HomeMealsDto
)

class TodayRepository(
    private val api: HealthApi,
    private val syncRepository: SyncRepository? = null
) {
    suspend fun load(date: String): TodayData = coroutineScope {
        val menu = async { api.todayMenu(date = date, mealSlot = "lunch") }
        val summary = async { loadSummary(date) }
        val schoolActivity = async { api.todaySchoolActivity(date = date) }
        val homeMeals = async { loadHomeMeals(date) }
        val schools = async { runCatching { api.studentSchools().schools }.getOrDefault(emptyList()) }
        TodayData(
            menu = menu.await(),
            summary = summary.await(),
            schoolActivity = schoolActivity.await(),
            schools = schools.await(),
            homeMeals = homeMeals.await()
        )
    }

    suspend fun loadSummary(date: String): DailySummaryDto {
        val remote = api.todaySummary(date)
        if (syncRepository == null) return remote
        val remoteHomeMeals = runCatching { api.homeMeals(date) }
            .getOrDefault(HomeMealsDto(date = date))
        return overlayPrivateSummary(remote, date, remoteHomeMeals)
    }

    suspend fun loadHomeMeals(date: String): HomeMealsDto {
        val remote = runCatching { api.homeMeals(date) }
            .getOrDefault(HomeMealsDto(date = date))
        val sync = syncRepository ?: return remote
        val local = localHomeMeals(sync, date)
        if (local.isEmpty()) return remote

        val localSlots = local.map { it.mealSlot }.toSet()
        return HomeMealsDto(
            date = date,
            meals = remote.meals.filterNot { it.mealSlot in localSlots } + local
        )
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
        val sync = syncRepository
        if (sync != null) {
            sync.enqueuePrivateRecord(
                entityType = "outside_activity",
                entityId = date,
                payloadJson = JSONObject()
                    .put("date", date)
                    .put("exerciseMinutes", exerciseMinutes)
                    .put("steps", steps ?: JSONObject.NULL)
                    .put("activeEnergyKcal", activeEnergyKcal ?: JSONObject.NULL)
                    .toString()
            )
            return
        }
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

        val parts = images.mapIndexed { index, img ->
            require(img.bytes.isNotEmpty()) { "图片为空" }
            require(img.bytes.size <= 8 * 1024 * 1024) { "单张图片不能超过 8 MB" }
            val body = img.bytes.toRequestBody(img.mimeType.toMediaTypeOrNull())
            MultipartBody.Part.createFormData(
                "images",
                img.fileName.ifBlank { "meal_$index.jpg" },
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
        val sync = syncRepository
        if (sync == null) {
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
        sync.enqueuePrivateRecord(
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

        api.saveMeal(
            MealConsumptionRequest(date = date, mealSlot = "lunch", items = items)
        )
    }

    suspend fun saveManualActivity(
        date: String,
        activityType: String,
        durationMinutes: Int,
        intensity: String
    ) {
        val sync = syncRepository
        if (sync == null) {
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

        sync.enqueuePrivateRecord(
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
        val sync = syncRepository
        if (sync == null) {
            api.saveEnergyReference(EnergyReferenceRequest(dailyEnergyReferenceKcal = kcal))
            return
        }

        sync.enqueuePrivateRecord(
            entityType = "preference",
            entityId = "energy_reference",
            payloadJson = JSONObject()
                .put("dailyEnergyReferenceKcal", kcal ?: JSONObject.NULL)
                .toString()
        )
    }

    private suspend fun localHomeMeals(
        sync: SyncRepository,
        date: String
    ): List<SavedHomeMealDto> =
        sync.listPrivateRecords("home_meal")
            .filter { !it.deleted && it.entityId.startsWith(date + ":") }
            .mapNotNull { record ->
                runCatching {
                    val json = JSONObject(record.payloadJson)
                    val array = json.optJSONArray("items") ?: JSONArray()
                    val items = List(array.length()) { index ->
                        val item = array.getJSONObject(index)
                        ConfirmedHomeMealItemRequest(
                            name = item.getString("name"),
                            grams = item.optNullableDouble("grams"),
                            nutrition = item.optJSONObject("nutrition")?.toNutritionDto()
                        )
                    }
                    SavedHomeMealDto(
                        mealSlot = json.getString("mealSlot"),
                        items = items
                    )
                }.getOrNull()
            }

    private suspend fun overlayPrivateSummary(
        remote: DailySummaryDto,
        date: String,
        remoteHomeMeals: HomeMealsDto
    ): DailySummaryDto {
        val sync = syncRepository ?: return remote
        val localHomeMeals = localHomeMeals(sync, date)
        val overriddenSlots = localHomeMeals.map { it.mealSlot }.toSet()
        val overriddenRemoteItems = remoteHomeMeals.meals
            .filter { it.mealSlot in overriddenSlots }
            .flatMap { it.items }
        val localItems = localHomeMeals.flatMap { it.items }

        fun sum(items: List<ConfirmedHomeMealItemRequest>, selector: (NutritionDto) -> Double?): Double =
            items.sumOf { item -> item.nutrition?.let(selector) ?: 0.0 }

        val remoteNutrition = remote.nutrition
        val energy = (remoteNutrition.energyKcal -
            sum(overriddenRemoteItems) { it.energyKcal } +
            sum(localItems) { it.energyKcal }).coerceAtLeast(0.0)
        val protein = (remoteNutrition.proteinG -
            sum(overriddenRemoteItems) { it.proteinG } +
            sum(localItems) { it.proteinG }).coerceAtLeast(0.0)
        val fat = (remoteNutrition.fatG -
            sum(overriddenRemoteItems) { it.fatG } +
            sum(localItems) { it.fatG }).coerceAtLeast(0.0)
        val carbs = (remoteNutrition.carbohydrateG -
            sum(overriddenRemoteItems) { it.carbohydrateG } +
            sum(localItems) { it.carbohydrateG }).coerceAtLeast(0.0)
        val fiber = (remoteNutrition.fiberG -
            sum(overriddenRemoteItems) { it.fiberG } +
            sum(localItems) { it.fiberG }).coerceAtLeast(0.0)
        val sodium = (remoteNutrition.sodiumMg -
            sum(overriddenRemoteItems) { it.sodiumMg } +
            sum(localItems) { it.sodiumMg }).coerceAtLeast(0.0)
        val sugar = (remoteNutrition.sugarG -
            sum(overriddenRemoteItems) { it.sugarG } +
            sum(localItems) { it.sugarG }).coerceAtLeast(0.0)
        val saturatedFat = (remoteNutrition.saturatedFatG -
            sum(overriddenRemoteItems) { it.saturatedFatG } +
            sum(localItems) { it.saturatedFatG }).coerceAtLeast(0.0)

        val recordedFoodItems = (
            remoteNutrition.recordedFoodItems -
                overriddenRemoteItems.size +
                localItems.size
            ).coerceAtLeast(0)
        val unknownEnergyItems = (
            remoteNutrition.unknownEnergyItems -
                overriddenRemoteItems.count { it.nutrition?.energyKcal == null } +
                localItems.count { it.nutrition?.energyKcal == null }
            ).coerceAtLeast(0)

        val macroEnergy = protein * 4.0 + fat * 9.0 + carbs * 4.0
        val nutrition = NutritionSummaryDto(
            recordedFoodItems = recordedFoodItems,
            unknownEnergyItems = unknownEnergyItems,
            energyKcal = energy,
            proteinG = protein,
            fatG = fat,
            carbohydrateG = carbs,
            fiberG = fiber,
            sodiumMg = sodium,
            sugarG = sugar,
            saturatedFatG = saturatedFat,
            macroCompositionPercent = MacroCompositionDto(
                protein = if (macroEnergy > 0) protein * 4.0 / macroEnergy * 100.0 else 0.0,
                fat = if (macroEnergy > 0) fat * 9.0 / macroEnergy * 100.0 else 0.0,
                carbohydrate = if (macroEnergy > 0) carbs * 4.0 / macroEnergy * 100.0 else 0.0
            )
        )

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

        val wearable = sync.getPrivateRecord("outside_activity", date)
            ?.takeIf { !it.deleted }
            ?.let { record ->
                JSONObject(record.payloadJson).also { json ->
                    require(json.getString("date") == date) { "校外运动记录日期无效" }
                    val minutes = json.getDouble("exerciseMinutes")
                    require(minutes.isFinite() && minutes in 0.0..1440.0 && minutes % 1.0 == 0.0) {
                        "校外运动时长无效"
                    }
                }
            }
        val wearableMinutes = wearable?.getInt("exerciseMinutes") ?: 0
        val wearableSteps = wearable?.optNullableDouble("steps")?.also {
            require(it.isFinite() && it in 0.0..200_000.0 && it % 1.0 == 0.0) { "校外运动步数无效" }
        }?.toLong()
        val wearableEnergy = wearable?.optNullableDouble("activeEnergyKcal")?.also {
            require(it.isFinite() && it in 0.0..20_000.0) { "校外运动能量无效" }
        }

        val activity = remote.activity.let { base ->
            // Campus PE remains a school source. The demo server's personal
            // exercise totals must not enter this encrypted account's totals.
            val outside = maxOf(wearableMinutes, localManualMinutes)
            val total = base.peMinutes + outside
            base.copy(
                healthConnectOutsideMinutes = wearableMinutes,
                manualOutsideMinutes = localManualMinutes,
                outsideMinutes = outside,
                totalMinutes = total,
                targetReached = total >= base.targetMinutes,
                intensityMinutes = IntensityMinutesDto(
                    light = localLight,
                    moderate = localModerate,
                    vigorous = localVigorous
                ),
                activeEnergyKcal = wearableEnergy,
                steps = wearableSteps,
                manuallyEstimatedActiveEnergyKcal = localEstimatedEnergy
            )
        }

        val preferenceRecord = sync.getPrivateRecord("preference", "energy_reference")
        val localTarget = preferenceRecord?.takeIf { !it.deleted }?.let { record ->
            runCatching {
                val json = JSONObject(record.payloadJson)
                if (json.isNull("dailyEnergyReferenceKcal")) null
                else json.getInt("dailyEnergyReferenceKcal")
            }.getOrNull()
        }
        // A saved null is the user's explicit choice to clear the reference.
        // Only an absent/deleted preference should use the server default.
        val target = if (preferenceRecord != null && !preferenceRecord.deleted) {
            localTarget
        } else remote.energy.dailyEnergyReferenceKcal
        val energySummary = EnergySummaryDto(
            dailyEnergyReferenceKcal = target,
            intakeKcal = nutrition.energyKcal,
            referenceGapKcal = target?.minus(nutrition.energyKcal)
        )

        return remote.copy(
            nutrition = nutrition,
            activity = activity,
            energy = energySummary
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

    private fun JSONObject.toNutritionDto(): NutritionDto =
        NutritionDto(
            energyKcal = optNullableDouble("energyKcal"),
            proteinG = optNullableDouble("proteinG"),
            fatG = optNullableDouble("fatG"),
            carbohydrateG = optNullableDouble("carbohydrateG"),
            fiberG = optNullableDouble("fiberG"),
            sodiumMg = optNullableDouble("sodiumMg"),
            sugarG = optNullableDouble("sugarG"),
            saturatedFatG = optNullableDouble("saturatedFatG")
        )

    private fun JSONObject.optNullableDouble(name: String): Double? =
        if (isNull(name) || !has(name)) null else optDouble(name)
}
