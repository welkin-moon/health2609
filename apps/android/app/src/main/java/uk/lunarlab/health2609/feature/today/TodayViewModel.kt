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
import uk.lunarlab.health2609.core.network.TodayMenuDto

data class TodayUiState(
    val date: String = LocalDate.now().toString(),
    val loading: Boolean = true,
    val saving: Boolean = false,
    val menu: TodayMenuDto? = null,
    val portions: Map<String, Double> = emptyMap(),
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
            runCatching { repository.loadMenu(date) }
                .onSuccess { menu ->
                    _uiState.update {
                        it.copy(
                            loading = false,
                            menu = menu,
                            portions = menu.dishes.associate { dish ->
                                dish.id to (it.portions[dish.id] ?: 0.0)
                            }
                        )
                    }
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(
                            loading = false,
                            message = error.message ?: "菜单载入失败"
                        )
                    }
                }
        }
    }

    fun setPortion(dishId: String, portion: Double) {
        _uiState.update {
            it.copy(portions = it.portions + (dishId to portion))
        }
    }

    fun save() {
        val state = _uiState.value
        if (state.menu == null || state.saving) return

        viewModelScope.launch {
            _uiState.update { it.copy(saving = true, message = null) }
            runCatching {
                repository.saveMeal(
                    date = state.date,
                    portions = state.portions
                )
            }.onSuccess {
                _uiState.update {
                    it.copy(
                        saving = false,
                        message = "今天的午餐已记录"
                    )
                }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        saving = false,
                        message = error.message ?: "保存失败"
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
