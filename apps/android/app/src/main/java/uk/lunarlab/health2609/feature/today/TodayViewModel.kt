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
import uk.lunarlab.health2609.core.network.DailySummaryDto
import uk.lunarlab.health2609.core.network.TodayMenuDto

data class DishAmount(
    val servingMultiplier: Double = 0.0,
    val consumedGrams: Double? = null
)

data class TodayUiState(
    val date: String = LocalDate.now().toString(),
    val loading: Boolean = true,
    val savingMeal: Boolean = false,
    val savingActivity: Boolean = false,
    val savingEnergyReference: Boolean = false,
    val menu: TodayMenuDto? = null,
    val summary: DailySummaryDto? = null,
    val amounts: Map<String, DishAmount> = emptyMap(),
    val manualActivityType: String = "自主运动",
    val manualActivityMinutes: Int = 30,
    val manualActivityIntensity: String = "moderate",
    val energyReferenceInput: String = "",
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
        val safeGrams = grams?.coerceIn(0.0, 5000.0)
        val multiplier = if (
            safeGrams != null &&
            dish?.standardServingGrams != null &&
            dish.standardServingGrams > 0
        ) {
            (safeGrams / dish.standardServingGrams).coerceIn(0.0, 5.0)
        } else {
            _uiState.value.amounts[dishId]?.servingMultiplier ?: 0.0
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

    fun setManualActivityIntensity(intensity: String) {
        if (intensity !in setOf("light", "moderate", "vigorous")) return
        _uiState.update { it.copy(manualActivityIntensity = intensity) }
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

    fun saveManualActivity() {
        val state = _uiState.value
        if (state.savingActivity) return

        viewModelScope.launch {
            _uiState.update { it.copy(savingActivity = true, message = null) }
            runCatching {
                repository.saveManualActivity(
                    date = state.date,
                    activityType = state.manualActivityType,
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
