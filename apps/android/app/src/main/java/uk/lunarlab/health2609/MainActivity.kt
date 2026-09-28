package uk.lunarlab.health2609

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.health.connect.client.PermissionController
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import java.time.LocalDate
import kotlinx.coroutines.launch
import uk.lunarlab.health2609.core.health.HealthConnectSource
import uk.lunarlab.health2609.core.network.ApiFactory
import uk.lunarlab.health2609.feature.today.TodayRepository
import uk.lunarlab.health2609.feature.today.TodayScreen
import uk.lunarlab.health2609.feature.today.TodayViewModel
import uk.lunarlab.health2609.feature.today.TodayViewModelFactory
import uk.lunarlab.health2609.ui.theme.Health2609Theme

class MainActivity : ComponentActivity() {
    private val repository by lazy {
        TodayRepository(ApiFactory.create())
    }

    private val healthConnectSource by lazy {
        HealthConnectSource(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            Health2609Theme {
                val viewModel: TodayViewModel = viewModel(
                    factory = TodayViewModelFactory(repository)
                )
                val state by viewModel.uiState.collectAsStateWithLifecycle()

                val healthPermissionLauncher =
                    rememberLauncherForActivityResult(
                        PermissionController
                            .createRequestPermissionResultContract()
                    ) { granted ->
                        if (
                            granted.containsAll(
                                HealthConnectSource.REQUIRED_PERMISSIONS
                            )
                        ) {
                            lifecycleScope.launch {
                                syncPhoneActivity(
                                    date = state.date,
                                    viewModel = viewModel
                                )
                            }
                        } else {
                            viewModel.phoneActivitySyncFailed(
                                "没有获得所需的 Health Connect 读取权限"
                            )
                        }
                    }

                TodayScreen(
                    state = state,
                    onPortionChange = viewModel::setPortion,
                    onGramsChange = viewModel::setConsumedGrams,
                    onRefresh = viewModel::refresh,
                    onSaveMeal = viewModel::saveMeal,
                    onActivityMinutesChange =
                        viewModel::setManualActivityMinutes,
                    onActivityIntensityChange =
                        viewModel::setManualActivityIntensity,
                    onSaveActivity = viewModel::saveManualActivity,
                    onSyncPhoneActivity = {
                        if (!healthConnectSource.isAvailable()) {
                            viewModel.phoneActivitySyncFailed(
                                "这台设备暂时不可用 Health Connect"
                            )
                        } else {
                            lifecycleScope.launch {
                                if (
                                    healthConnectSource
                                        .hasRequiredPermissions()
                                ) {
                                    syncPhoneActivity(
                                        date = state.date,
                                        viewModel = viewModel
                                    )
                                } else {
                                    viewModel.phoneActivitySyncStarted()
                                    healthPermissionLauncher.launch(
                                        HealthConnectSource
                                            .REQUIRED_PERMISSIONS
                                    )
                                }
                            }
                        }
                    },
                    onEnergyReferenceChange =
                        viewModel::setEnergyReferenceInput,
                    onSaveEnergyReference =
                        viewModel::saveEnergyReference
                )
            }
        }
    }

    private suspend fun syncPhoneActivity(
        date: String,
        viewModel: TodayViewModel
    ) {
        viewModel.phoneActivitySyncStarted()

        runCatching {
            val windows = repository.loadSchoolDayWindows(date)
            require(windows.isNotEmpty()) {
                "学校管理员还没有配置当天的在校时段"
            }

            val aggregate = healthConnectSource.readOutsideSchoolDay(
                date = LocalDate.parse(date),
                schoolWindows = windows
            )

            repository.saveOutsideSchoolActivity(
                date = date,
                exerciseMinutes = aggregate.exerciseMinutes,
                steps = aggregate.steps,
                activeEnergyKcal = aggregate.activeEnergyKcal
            )

            aggregate
        }.onSuccess { aggregate ->
            viewModel.phoneActivitySyncFinished(
                "已同步校外运动：${aggregate.exerciseMinutes} 分钟 · " +
                    "${aggregate.steps} 步"
            )
        }.onFailure { error ->
            viewModel.phoneActivitySyncFailed(
                error.message ?: "手机运动数据同步失败"
            )
        }
    }
}
