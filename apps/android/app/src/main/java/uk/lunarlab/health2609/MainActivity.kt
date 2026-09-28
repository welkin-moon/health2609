package uk.lunarlab.health2609

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.health.connect.client.PermissionController
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import java.io.ByteArrayOutputStream
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import uk.lunarlab.health2609.core.health.HealthConnectSource
import uk.lunarlab.health2609.core.network.ApiFactory
import uk.lunarlab.health2609.feature.today.TodayRepository
import uk.lunarlab.health2609.feature.today.TodayScreen
import uk.lunarlab.health2609.feature.today.TodayViewModel
import uk.lunarlab.health2609.feature.today.TodayViewModelFactory
import uk.lunarlab.health2609.core.storage.Health2609Preferences
import uk.lunarlab.health2609.ui.StudentAppShell
import uk.lunarlab.health2609.ui.theme.Health2609Theme

class MainActivity : ComponentActivity() {
    private val repository by lazy {
        TodayRepository(ApiFactory.create())
    }

    private val healthConnectSource by lazy {
        HealthConnectSource(this)
    }

    private val preferences by lazy {
        Health2609Preferences(this)
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

                val homeMealPicker =
                    rememberLauncherForActivityResult(
                        ActivityResultContracts.PickVisualMedia()
                    ) { uri ->
                        if (uri != null) {
                            lifecycleScope.launch {
                                runCatching {
                                    withContext(Dispatchers.IO) {
                                        readImageForUpload(uri)
                                    }
                                }.onSuccess { image ->
                                    viewModel.analyzeHomeMeal(
                                        bytes = image.bytes,
                                        mimeType = image.mimeType,
                                        fileName = image.fileName
                                    )
                                }.onFailure { error ->
                                    viewModel.showMessage(
                                        error.message ?: "读取图片失败"
                                    )
                                }
                            }
                        }
                    }

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

                val selectedDestination by preferences.startDestination
                    .collectAsStateWithLifecycle(initialValue = "today")

                StudentAppShell(
                    selectedDestination = selectedDestination,
                    onDestinationChange = { destination ->
                        lifecycleScope.launch {
                            preferences.setStartDestination(destination)
                        }
                    }
                ) {
                    TodayScreen(
                    state = state,
                    onPortionChange = viewModel::setPortion,
                    onGramsChange = viewModel::setConsumedGrams,
                    onRefresh = viewModel::refresh,
                    onSaveMeal = viewModel::saveMeal,
                    onPickHomeMealImage = {
                        homeMealPicker.launch(
                            PickVisualMediaRequest(
                                ActivityResultContracts
                                    .PickVisualMedia
                                    .ImageOnly
                            )
                        )
                    },
                    onHomeMealSlotChange = viewModel::setHomeMealSlot,
                    onHomeMealNameChange = viewModel::setHomeMealName,
                    onHomeMealGramsChange = viewModel::setHomeMealGrams,
                    onRemoveHomeMealItem = viewModel::removeHomeMealItem,
                    onSaveHomeMeal = viewModel::saveHomeMeal,
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
    }

    private data class UploadImage(
        val bytes: ByteArray,
        val mimeType: String,
        val fileName: String
    )

    private fun readImageForUpload(uri: Uri): UploadImage {
        val maxBytes = 8 * 1024 * 1024
        val output = ByteArrayOutputStream()

        contentResolver.openInputStream(uri).use { input ->
            requireNotNull(input) { "无法读取这张图片" }

            val buffer = ByteArray(64 * 1024)
            var total = 0

            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                total += count
                require(total <= maxBytes) {
                    "图片不能超过 8 MB"
                }
                output.write(buffer, 0, count)
            }
        }

        val mimeType = contentResolver.getType(uri)
            ?.takeIf { it.startsWith("image/") }
            ?: "image/jpeg"

        val suffix = when (mimeType) {
            "image/png" -> "png"
            "image/webp" -> "webp"
            "image/heic", "image/heif" -> "heic"
            else -> "jpg"
        }

        return UploadImage(
            bytes = output.toByteArray(),
            mimeType = mimeType,
            fileName = "home-meal.$suffix"
        )
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
