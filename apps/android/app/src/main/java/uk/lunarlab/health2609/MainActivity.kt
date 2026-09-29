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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.content.FileProvider
import androidx.health.connect.client.PermissionController
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import java.io.ByteArrayOutputStream
import java.io.File
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
                var pendingCameraUri by remember { mutableStateOf<Uri?>(null) }
                var pendingCameraFile by remember { mutableStateOf<File?>(null) }

                val homeMealPicker =
                    rememberLauncherForActivityResult(
                        ActivityResultContracts.PickVisualMedia()
                    ) { uri ->
                        if (uri != null) {
                            analyzeHomeMealUri(
                                uri = uri,
                                viewModel = viewModel
                            )
                        }
                    }

                val homeMealCamera =
                    rememberLauncherForActivityResult(
                        ActivityResultContracts.TakePicture()
                    ) { captured ->
                        val uri = pendingCameraUri
                        val file = pendingCameraFile
                        pendingCameraUri = null
                        pendingCameraFile = null

                        if (captured && uri != null) {
                            analyzeHomeMealUri(
                                uri = uri,
                                viewModel = viewModel,
                                cleanupFile = file
                            )
                        } else {
                            file?.delete()
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
                                "没有获得读取运动数据的权限"
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
                ) { destination ->
                    TodayScreen(
                    destination = destination,
                    state = state,
                    onPortionChange = viewModel::setPortion,
                    onGramsChange = viewModel::setConsumedGrams,
                    onRefresh = viewModel::refresh,
                    onSaveMeal = viewModel::saveMeal,
                    onTakeHomeMealPhoto = {
                        runCatching {
                            val captureDir = File(
                                cacheDir,
                                "home-meal-camera"
                            ).apply { mkdirs() }
                            val captureFile = File.createTempFile(
                                "home-meal-",
                                ".jpg",
                                captureDir
                            )
                            val captureUri = FileProvider.getUriForFile(
                                this@MainActivity,
                                "${BuildConfig.APPLICATION_ID}.fileprovider",
                                captureFile
                            )
                            pendingCameraFile = captureFile
                            pendingCameraUri = captureUri
                            homeMealCamera.launch(captureUri)
                        }.onFailure { error ->
                            pendingCameraFile?.delete()
                            pendingCameraFile = null
                            pendingCameraUri = null
                            viewModel.showMessage(
                                error.message ?: "无法打开相机"
                            )
                        }
                    },
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
                                "这台手机暂时无法读取运动数据"
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

    private fun analyzeHomeMealUri(
        uri: Uri,
        viewModel: TodayViewModel,
        cleanupFile: File? = null
    ) {
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
            cleanupFile?.delete()
        }
    }

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
                "已更新校外运动：${aggregate.exerciseMinutes} 分钟 · " +
                    "${aggregate.steps} 步"
            )
        }.onFailure { error ->
            viewModel.phoneActivitySyncFailed(
                error.message ?: "手机运动数据同步失败"
            )
        }
    }
}
