package uk.lunarlab.health2609.feature.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import java.time.LocalDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import uk.lunarlab.health2609.core.network.ConfirmedHomeMealItemRequest
import uk.lunarlab.health2609.core.network.DailySummaryDto
import uk.lunarlab.health2609.core.network.NutritionDto
import uk.lunarlab.health2609.core.network.SchoolActivityDto
import uk.lunarlab.health2609.core.network.StudentSchoolDto
import uk.lunarlab.health2609.core.network.TodayMenuDto

data class DishAmount(
    val servingMultiplier: Double = 0.0,
    val consumedGrams: Double? = null
)

data class HomeMealDraftItem(
    val name: String,
    val sourceGrams: Double?,
    val grams: Double?,
    val confidence: Double,
    val nutritionAtSource: NutritionDto?,
    val needsConfirmation: List<String>
)

data class StagedMealImage(
    val id: String = java.util.UUID.randomUUID().toString(),
    val bytes: ByteArray,
    val mimeType: String,
    val fileName: String = "meal.jpg",
    val thumbnail: android.graphics.Bitmap? = null
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is StagedMealImage) return false
        return id == other.id
    }
    override fun hashCode(): Int = id.hashCode()
}

data class TodayUiState(
    val date: String = LocalDate.now().toString(),
    val loading: Boolean = true,
    val savingMeal: Boolean = false,
    val savingActivity: Boolean = false,
    val savingEnergyReference: Boolean = false,
    val syncingPhoneActivity: Boolean = false,
    val syncingSchoolActivity: Boolean = false,
    val analyzingHomeMeal: Boolean = false,
    val savingHomeMeal: Boolean = false,
    val menu: TodayMenuDto? = null,
    val summary: DailySummaryDto? = null,
    val schoolActivity: SchoolActivityDto? = null,
    val schools: List<StudentSchoolDto> = emptyList(),
    val amounts: Map<String, DishAmount> = emptyMap(),
    val manualActivityType: String = "自主运动",
    val manualActivityMinutes: Int = 30,
    val manualActivityIntensity: String = "moderate",
    val energyReferenceInput: String = "",
    val homeMealSlot: String = "dinner",
    val stagedMealImages: List<StagedMealImage> = emptyList(),
    val homeMealDraft: List<HomeMealDraftItem> = emptyList(),
    val homeMealNotes: List<String> = emptyList(),
    val message: String? = null
)

class TodayViewModel(
    private val repository: TodayRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(TodayUiState())
    val uiState: StateFlow<TodayUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        val date = _uiState.value.date
        viewModelScope.launch {
            _uiState.update { it.copy(loading = true, message = null) }
            runCatching { repository.load(date) }
                .onSuccess { data ->
                    _uiState.update { current ->
                        current.copy(
                            loading = false,
                            menu = data.menu,
                            summary = data.summary,
                            schoolActivity = data.schoolActivity,
                            schools = data.schools,
                            energyReferenceInput =
                                data.summary.energy.dailyEnergyReferenceKcal
                                    ?.toString()
                                    ?: current.energyReferenceInput,
                            amounts = data.menu.dishes.associate { dish ->
                                dish.id to (
                                    current.amounts[dish.id]
                                        ?: DishAmount(
                                            servingMultiplier = 0.0,
                                            consumedGrams = 0.0
                                        )
                                    )
                            }
                        )
                    }
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(
                            loading = false,
                            message = error.message ?: "今日数据载入失败"
                        )
                    }
                }
        }
    }

    fun setPortion(dishId: String, portion: Double) {
        val dish = _uiState.value.menu?.dishes?.firstOrNull { it.id == dishId }
        val grams = dish?.standardServingGrams?.times(portion)

        _uiState.update {
            it.copy(
                amounts = it.amounts + (
                    dishId to DishAmount(
                        servingMultiplier = portion,
                        consumedGrams = grams
                    )
                )
            )
        }
    }

    fun setConsumedGrams(dishId: String, grams: Double?) {
        val dish = _uiState.value.menu?.dishes?.firstOrNull { it.id == dishId }
        val maxGrams = dish?.standardServingGrams
            ?.takeIf { it > 0 }
            ?.times(5.0)
            ?: 5000.0
        val safeGrams = grams?.coerceIn(0.0, maxGrams)
        val multiplier = if (
            safeGrams != null &&
            dish?.standardServingGrams != null &&
            dish.standardServingGrams > 0
        ) {
            safeGrams / dish.standardServingGrams
        } else {
            0.0
        }

        _uiState.update {
            it.copy(
                amounts = it.amounts + (
                    dishId to DishAmount(
                        servingMultiplier = multiplier,
                        consumedGrams = safeGrams
                    )
                )
            )
        }
    }

    fun setManualActivityMinutes(minutes: Int) {
        _uiState.update {
            it.copy(manualActivityMinutes = minutes.coerceIn(5, 300))
        }
    }

    fun setManualActivityType(type: String) {
        _uiState.update { it.copy(manualActivityType = type) }
    }

    fun setManualActivityIntensity(intensity: String) {
        if (intensity !in setOf("light", "moderate", "vigorous")) return
        _uiState.update { it.copy(manualActivityIntensity = intensity) }
    }

    fun stageMealImages(newImages: List<StagedMealImage>) {
        _uiState.update { current ->
            val combined = (current.stagedMealImages + newImages).take(5)
            val overLimit = current.stagedMealImages.size + newImages.size > 5
            current.copy(
                stagedMealImages = combined,
                message = if (overLimit) "最多支持添加 5 张餐食图片" else null
            )
        }
    }

    fun removeStagedMealImage(id: String) {
        _uiState.update { current ->
            current.copy(
                stagedMealImages = current.stagedMealImages.filter { it.id != id }
            )
        }
    }

    fun clearStagedMealImages() {
        _uiState.update { it.copy(stagedMealImages = emptyList()) }
    }

    fun analyzeStagedHomeMeals() {
        val images = _uiState.value.stagedMealImages
        if (images.isEmpty()) {
            _uiState.update { it.copy(message = "请先拍照或从相册添加餐食照片") }
            return
        }
        if (_uiState.value.analyzingHomeMeal) return

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    analyzingHomeMeal = true,
                    message = if (images.size > 1) "正在多图综合识别这顿饭（共 ${images.size} 张）…" else "正在识别这顿饭…"
                )
            }

            runCatching {
                repository.analyzeHomeMeals(
                    images.map { img ->
                        TodayRepository.ImagePayload(
                            bytes = img.bytes,
                            mimeType = img.mimeType,
                            fileName = img.fileName
                        )
                    }
                )
            }.onSuccess { result ->
                val draft = result.items.map { item ->
                    HomeMealDraftItem(
                        name = item.name,
                        sourceGrams = item.estimatedGrams,
                        grams = item.estimatedGrams,
                        confidence = item.confidence,
                        nutritionAtSource = item.nutrition,
                        needsConfirmation = item.needsConfirmation
                    )
                }

                _uiState.update {
                    it.copy(
                        analyzingHomeMeal = false,
                        homeMealDraft = draft,
                        homeMealNotes = result.notes,
                        message = if (draft.isEmpty()) {
                            "未能识别出明确食物，可尝试补充更清晰的角度照片或手动添加"
                        } else {
                            if (images.size > 1) {
                                "识别完成（已汇总 ${images.size} 张照片），请确认菜品名称与分量"
                            } else {
                                "识别完成，请确认菜品名称与分量"
                            }
                        }
                    )
                }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        analyzingHomeMeal = false,
                        homeMealNotes = listOf("识别失败: ${error.message ?: "视觉大模型未返回有效结果"}"),
                        message = error.message ?: "图片识别失败，请检查网络或重试"
                    )
                }
            }
        }
    }

    fun analyzeHomeMeal(
        bytes: ByteArray,
        mimeType: String,
        fileName: String = "meal.jpg"
    ) {
        stageMealImages(listOf(StagedMealImage(bytes = bytes, mimeType = mimeType, fileName = fileName)))
        analyzeStagedHomeMeals()
    }

    fun setHomeMealSlot(slot: String) {
        if (slot !in setOf("breakfast", "lunch", "dinner")) return
        _uiState.update { it.copy(homeMealSlot = slot) }
    }

    fun setHomeMealName(index: Int, name: String) {
        _uiState.update { state ->
            state.copy(
                homeMealDraft = state.homeMealDraft.mapIndexed { i, item ->
                    if (i == index) item.copy(name = name) else item
                }
            )
        }
    }

    fun setHomeMealGrams(index: Int, grams: Double?) {
        _uiState.update { state ->
            state.copy(
                homeMealDraft = state.homeMealDraft.mapIndexed { i, item ->
                    if (i == index) {
                        item.copy(grams = grams?.coerceIn(0.0, 5000.0))
                    } else {
                        item
                    }
                }
            )
        }
    }

    fun removeHomeMealItem(index: Int) {
        _uiState.update { state ->
            state.copy(
                homeMealDraft = state.homeMealDraft.filterIndexed { i, _ ->
                    i != index
                }
            )
        }
    }

    fun saveHomeMeal() {
        val state = _uiState.value
        if (state.savingHomeMeal || state.homeMealDraft.isEmpty()) return

        val confirmed = state.homeMealDraft
            .filter { it.name.trim().isNotEmpty() }
            .map { item ->
                ConfirmedHomeMealItemRequest(
                    name = item.name.trim(),
                    grams = item.grams,
                    nutrition = repository.scaledNutrition(
                        nutrition = item.nutritionAtSource,
                        sourceGrams = item.sourceGrams,
                        confirmedGrams = item.grams
                    )
                )
            }

        if (confirmed.isEmpty()) {
            _uiState.update {
                it.copy(message = "至少保留一项食物")
            }
            return
        }

        viewModelScope.launch {
            _uiState.update {
                it.copy(savingHomeMeal = true, message = null)
            }

            runCatching {
                repository.saveHomeMeal(
                    date = state.date,
                    mealSlot = state.homeMealSlot,
                    items = confirmed
                )
            }.onSuccess {
                _uiState.update {
                    it.copy(
                        savingHomeMeal = false,
                        stagedMealImages = emptyList(),
                        homeMealDraft = emptyList(),
                        homeMealNotes = emptyList()
                    )
                }
                refreshSummary("这顿饭已加入今天的记录")
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        savingHomeMeal = false,
                        message = error.message ?: "餐食保存失败"
                    )
                }
            }
        }
    }

    fun showMessage(message: String) {
        _uiState.update { it.copy(message = message) }
    }

    fun phoneActivitySyncStarted() {
        _uiState.update {
            it.copy(syncingPhoneActivity = true, message = null)
        }
    }

    fun phoneActivitySyncFinished(message: String = "手机运动数据已更新") {
        val date = _uiState.value.date
        viewModelScope.launch {
            runCatching { repository.loadSummary(date) }
                .onSuccess { summary ->
                    _uiState.update {
                        it.copy(
                            summary = summary,
                            syncingPhoneActivity = false,
                            message = message
                        )
                    }
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(
                            syncingPhoneActivity = false,
                            message = error.message
                                ?: "手机数据已提交，但汇总刷新失败"
                        )
                    }
                }
        }
    }

    fun phoneActivitySyncFailed(message: String) {
        _uiState.update {
            it.copy(
                syncingPhoneActivity = false,
                message = message
            )
        }
    }

    fun schoolActivitySyncStarted() {
        _uiState.update {
            it.copy(syncingSchoolActivity = true, message = null)
        }
    }

    fun schoolActivitySyncFinished(message: String) {
        val date = _uiState.value.date
        viewModelScope.launch {
            runCatching {
                repository.loadSummary(date) to repository.loadSchoolActivity(date)
            }.onSuccess { (summary, schoolActivity) ->
                _uiState.update {
                    it.copy(
                        summary = summary,
                        schoolActivity = schoolActivity,
                        syncingSchoolActivity = false,
                        message = message
                    )
                }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        syncingSchoolActivity = false,
                        message = error.message ?: "学校体育数据已更新，但刷新失败"
                    )
                }
            }
        }
    }

    fun schoolActivitySyncFailed(message: String) {
        _uiState.update {
            it.copy(syncingSchoolActivity = false, message = message)
        }
    }

    fun setEnergyReferenceInput(value: String) {
        _uiState.update {
            it.copy(
                energyReferenceInput = value.filter(Char::isDigit).take(4)
            )
        }
    }

    fun saveMeal() {
        val state = _uiState.value
        if (state.menu == null || state.savingMeal) return

        viewModelScope.launch {
            _uiState.update { it.copy(savingMeal = true, message = null) }
            runCatching {
                repository.saveMeal(
                    date = state.date,
                    amounts = state.amounts
                )
            }.onSuccess {
                refreshSummary("今天的午餐已记录")
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        savingMeal = false,
                        message = error.message ?: "午餐保存失败"
                    )
                }
            }
        }
    }

    fun saveManualActivity(type: String = "自主运动") {
        val state = _uiState.value
        if (state.savingActivity) return

        val activityType = type.ifBlank { state.manualActivityType.ifBlank { "自主运动" } }

        viewModelScope.launch {
            _uiState.update { it.copy(savingActivity = true, message = null) }
            runCatching {
                repository.saveManualActivity(
                    date = state.date,
                    activityType = activityType,
                    durationMinutes = state.manualActivityMinutes,
                    intensity = state.manualActivityIntensity
                )
            }.onSuccess {
                refreshSummary("运动记录已加入今天")
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        savingActivity = false,
                        message = error.message ?: "运动保存失败"
                    )
                }
            }
        }
    }

    fun saveEnergyReference() {
        val state = _uiState.value
        if (state.savingEnergyReference) return

        val kcal = state.energyReferenceInput.toIntOrNull()
        if (kcal != null && kcal !in 500..6000) {
            _uiState.update {
                it.copy(message = "参考能量请输入 500–6000 kcal")
            }
            return
        }

        viewModelScope.launch {
            _uiState.update {
                it.copy(savingEnergyReference = true, message = null)
            }
            runCatching {
                repository.saveEnergyReference(kcal)
            }.onSuccess {
                refreshSummary("参考能量已更新")
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        savingEnergyReference = false,
                        message = error.message ?: "参考能量保存失败"
                    )
                }
            }
        }
    }

    private fun refreshSummary(successMessage: String) {
        val date = _uiState.value.date
        viewModelScope.launch {
            runCatching { repository.loadSummary(date) }
                .onSuccess { summary ->
                    _uiState.update {
                        it.copy(
                            summary = summary,
                            savingMeal = false,
                            savingActivity = false,
                            savingEnergyReference = false,
                            message = successMessage
                        )
                    }
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(
                            savingMeal = false,
                            savingActivity = false,
                            savingEnergyReference = false,
                            message = error.message
                                ?: "$successMessage，但刷新汇总失败"
                        )
                    }
                }
        }
    }
}

class TodayViewModelFactory(
    private val repository: TodayRepository
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(TodayViewModel::class.java))
        return TodayViewModel(repository) as T
    }
}
