package uk.lunarlab.health2609

import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import uk.lunarlab.health2609.core.health.HealthPermissionState
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.LaunchedEffect
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
import uk.lunarlab.health2609.core.network.DemoIdentity
import uk.lunarlab.health2609.feature.today.StagedMealImage
import uk.lunarlab.health2609.feature.today.TodayRepository
import uk.lunarlab.health2609.feature.today.TodayScreen
import uk.lunarlab.health2609.feature.today.TodayViewModel
import uk.lunarlab.health2609.feature.today.TodayViewModelFactory
import uk.lunarlab.health2609.core.storage.AppearanceMode
import uk.lunarlab.health2609.core.storage.AppearancePreferences
import uk.lunarlab.health2609.core.storage.Health2609Preferences
import uk.lunarlab.health2609.core.storage.UserProfile
import uk.lunarlab.health2609.core.sync.SyncRepository
import uk.lunarlab.health2609.core.sync.SyncState
import uk.lunarlab.health2609.core.utils.ImageCompressor
import uk.lunarlab.health2609.feature.sync.LoginSyncDialog
import uk.lunarlab.health2609.feature.today.SettingsDialog
import uk.lunarlab.health2609.ui.StudentAppShell
import uk.lunarlab.health2609.ui.theme.Health2609Theme
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import java.time.format.DateTimeFormatter
import java.util.Locale

class MainActivity : ComponentActivity() {
    private val syncRepository by lazy {
        SyncRepository(this)
    }

    private val repository by lazy {
        TodayRepository(ApiFactory.create(), syncRepository)
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
            val appearance by preferences.appearance.collectAsStateWithLifecycle(
                initialValue = AppearancePreferences()
            )
            val selectedSchoolId by preferences.selectedSchoolId
                .collectAsStateWithLifecycle(initialValue = "demo-school")
            DemoIdentity.schoolId = selectedSchoolId
            val systemDark = isSystemInDarkTheme()
            val darkTheme = when (appearance.mode) {
                AppearanceMode.SYSTEM -> systemDark
                AppearanceMode.LIGHT -> false
                AppearanceMode.DARK -> true
            }

            Health2609Theme(
                darkTheme = darkTheme,
                dynamicColor = appearance.dynamicColor
            ) {
                val viewModel: TodayViewModel = viewModel(
                    factory = TodayViewModelFactory(repository)
                )
                val state by viewModel.uiState.collectAsStateWithLifecycle()
                var pendingCameraUri by remember { mutableStateOf<Uri?>(null) }
                var pendingCameraFile by remember { mutableStateOf<File?>(null) }
                var pendingSchoolWearableSync by remember { mutableStateOf(false) }

                val homeMealPicker =
                    rememberLauncherForActivityResult(
                        ActivityResultContracts.PickMultipleVisualMedia(maxItems = 5)
                    ) { uris ->
                        if (uris.isNotEmpty()) {
                            stageHomeMealUris(
                                uris = uris,
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
                            stageHomeMealUris(
                                uris = listOf(uri),
                                viewModel = viewModel,
                                cleanupFiles = file?.let { listOf(it) } ?: emptyList()
                            )
                        } else {
                            file?.delete()
                        }
                    }

                val healthPermissionLauncher =
                    rememberLauncherForActivityResult(
                        PermissionController
                            .createRequestPermissionResultContract()
                    ) { _ ->
                        lifecycleScope.launch {
                            val permState = healthConnectSource.checkPermissionState()
                            Log.i("HealthConnectQA", "Permission callback checked: ${healthConnectSource.getDiagnostics()}")
                            when (permState) {
                                is HealthPermissionState.AvailableAndGranted -> {
                                    if (pendingSchoolWearableSync) {
                                        pendingSchoolWearableSync = false
                                        syncSchoolActivity(
                                            date = state.date,
                                            viewModel = viewModel
                                        )
                                    } else {
                                        syncPhoneActivity(
                                            date = state.date,
                                            viewModel = viewModel
                                        )
                                    }
                                }
                                is HealthPermissionState.MissingPermissions -> {
                                    val wasSchoolSync = pendingSchoolWearableSync
                                    pendingSchoolWearableSync = false
                                    val missingText = permState.missingLabels.joinToString("、")
                                    val msg = if (permState.granted.isEmpty()) {
                                        "未获得运动健康授权；若系统未自动弹出授权窗口，请在系统设置中开启"
                                    } else {
                                        "仍缺少【$missingText】读取权限，请在系统设置中允许所有运动权限"
                                    }
                                    if (permState.granted.isEmpty()) {
                                        runCatching {
                                            startActivity(healthConnectSource.createSettingsIntent())
                                        }
                                    }
                                    if (wasSchoolSync) {
                                        viewModel.schoolActivitySyncFailed(msg)
                                    } else {
                                        viewModel.phoneActivitySyncFailed(msg)
                                    }
                                }
                                is HealthPermissionState.SdkUnavailable -> {
                                    pendingSchoolWearableSync = false
                                    val msg = "这台设备当前不支持或未安装运动健康服务"
                                    viewModel.phoneActivitySyncFailed(msg)
                                }
                                is HealthPermissionState.SdkUpdateRequired -> {
                                    pendingSchoolWearableSync = false
                                    val msg = "系统运动健康服务需要更新后才能使用"
                                    viewModel.phoneActivitySyncFailed(msg)
                                }
                            }
                        }
                    }

                val savedStartDestination by preferences.startDestination
                    .collectAsStateWithLifecycle(initialValue = "today")
                var currentDestination by rememberSaveable { mutableStateOf<String?>(null) }
                val selectedDestination = currentDestination ?: savedStartDestination
                val customApiBaseUrl by preferences.customApiBaseUrl
                    .collectAsStateWithLifecycle(initialValue = null)
                val userProfile by preferences.userProfile
                    .collectAsStateWithLifecycle(initialValue = UserProfile())

                LaunchedEffect(customApiBaseUrl) {
                    ApiFactory.customBaseUrl = customApiBaseUrl
                }

                val prettyDate = remember(state.date) {
                    runCatching {
                        LocalDate.parse(state.date).format(
                            DateTimeFormatter.ofPattern("M月d日 EEEE", Locale.SIMPLIFIED_CHINESE)
                        )
                    }.getOrDefault(state.date)
                }

                var showSettingsDialog by rememberSaveable { mutableStateOf(false) }
                val syncState by syncRepository.syncState.collectAsStateWithLifecycle(
                    initialValue = SyncState()
                )
                var showLoginSyncDialog by rememberSaveable { mutableStateOf(false) }
                var isSyncingNow by remember { mutableStateOf(false) }

                StudentAppShell(
                    selectedDestination = selectedDestination,
                    onDestinationChange = { destination ->
                        currentDestination = destination
                    },
                    dateText = prettyDate,
                    onOpenSettings = { showSettingsDialog = true },
                    onRefresh = viewModel::refresh,
                    isRefreshing = state.loading
                ) { destination, wideLayout, hasBottomDock ->
                    TodayScreen(
                    destination = destination,
                    state = state,
                    selectedSchoolId = selectedSchoolId,
                    appearanceMode = appearance.mode,
                    dynamicColor = appearance.dynamicColor,
                    wideLayout = wideLayout,
                    hasBottomDock = hasBottomDock,
                    onAppearanceModeChange = { mode ->
                        lifecycleScope.launch {
                            preferences.setAppearanceMode(mode)
                        }
                    },
                    onDynamicColorChange = { enabled ->
                        lifecycleScope.launch {
                            preferences.setDynamicColor(enabled)
                        }
                    },
                    onSchoolChange = { schoolId ->
                        DemoIdentity.schoolId = schoolId
                        lifecycleScope.launch {
                            preferences.setSelectedSchoolId(schoolId)
                            viewModel.refresh()
                        }
                    },
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
                    onRemoveStagedMealImage = viewModel::removeStagedMealImage,
                    onClearStagedMealImages = viewModel::clearStagedMealImages,
                    onAnalyzeHomeMeal = viewModel::analyzeStagedHomeMeals,
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
                    onUseSchoolActivity = {
                        lifecycleScope.launch {
                            selectSchoolActivitySource(
                                date = state.date,
                                viewModel = viewModel
                            )
                        }
                    },
                    onUseWearableSchoolActivity = {
                        if (!healthConnectSource.isAvailable()) {
                            viewModel.schoolActivitySyncFailed(
                                "这台手机暂时无法读取手环运动数据"
                            )
                        } else {
                            lifecycleScope.launch {
                                if (healthConnectSource.hasRequiredPermissions()) {
                                    syncSchoolActivity(
                                        date = state.date,
                                        viewModel = viewModel
                                    )
                                } else {
                                    pendingSchoolWearableSync = true
                                    viewModel.schoolActivitySyncStarted()
                                    val launched = runCatching {
                                        healthPermissionLauncher.launch(
                                            HealthConnectSource.REQUIRED_PERMISSIONS
                                        )
                                    }.isSuccess
                                    if (!launched) {
                                        runCatching {
                                            startActivity(healthConnectSource.createSettingsIntent())
                                            viewModel.showMessage("正在打开系统设置，请开启运动健康权限后返回")
                                        }.onFailure {
                                            viewModel.schoolActivitySyncFailed("无法启动运动健康授权界面")
                                        }
                                    }
                                }
                            }
                        }
                    },
                    onRequestHealthPermissions = {
                        lifecycleScope.launch {
                            when (val permState = healthConnectSource.checkPermissionState()) {
                                is HealthPermissionState.AvailableAndGranted -> {
                                    viewModel.showMessage("运动健康权限已全部授予")
                                }
                                is HealthPermissionState.SdkUnavailable -> {
                                    viewModel.showMessage("这台设备当前不支持运动健康服务")
                                }
                                is HealthPermissionState.SdkUpdateRequired -> {
                                    viewModel.showMessage("系统运动健康服务需要更新")
                                }
                                is HealthPermissionState.MissingPermissions -> {
                                    val launched = runCatching {
                                        healthPermissionLauncher.launch(
                                            HealthConnectSource.REQUIRED_PERMISSIONS
                                        )
                                    }.isSuccess
                                    if (!launched) {
                                        runCatching {
                                            startActivity(healthConnectSource.createSettingsIntent())
                                            viewModel.showMessage("正在打开系统设置，请开启运动权限后返回")
                                        }.onFailure {
                                            viewModel.showMessage("无法打开系统设置")
                                        }
                                    }
                                }
                            }
                        }
                    },
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
                                    val launched = runCatching {
                                        healthPermissionLauncher.launch(
                                            HealthConnectSource.REQUIRED_PERMISSIONS
                                        )
                                    }.isSuccess
                                    if (!launched) {
                                        runCatching {
                                            startActivity(healthConnectSource.createSettingsIntent())
                                            viewModel.showMessage("正在打开系统设置，请开启运动权限后返回")
                                        }.onFailure {
                                            viewModel.phoneActivitySyncFailed("无法启动系统设置")
                                        }
                                    }
                                }
                            }
                        }
                    },
                    onEnergyReferenceChange =
                        viewModel::setEnergyReferenceInput,
                    onSaveEnergyReference =
                        viewModel::saveEnergyReference,
                    customApiBaseUrl = customApiBaseUrl,
                    onCustomApiBaseUrlChange = { url ->
                        lifecycleScope.launch {
                            preferences.setCustomApiBaseUrl(url)
                            ApiFactory.customBaseUrl = url
                            viewModel.refresh()
                        }
                    },
                    onClearMessage = viewModel::clearMessage,
                    onAddManualHomeMealItem = viewModel::addManualHomeMealItem,
                    userProfile = userProfile,
                    onUserProfileChange = { profile ->
                        lifecycleScope.launch {
                            preferences.setUserProfile(profile)
                        }
                    },
                    onOpenSettings = { showSettingsDialog = true }
                    )
                }

                if (showSettingsDialog) {
                    Dialog(
                        onDismissRequest = { showSettingsDialog = false },
                        properties = DialogProperties(usePlatformDefaultWidth = false)
                    ) {
                        SettingsDialog(
                            schools = state.schools,
                            selectedSchoolId = selectedSchoolId,
                            appearanceMode = appearance.mode,
                            dynamicColor = appearance.dynamicColor,
                            customApiBaseUrl = customApiBaseUrl,
                            userProfile = userProfile,
                            onSchoolChange = { schoolId ->
                                DemoIdentity.schoolId = schoolId
                                lifecycleScope.launch {
                                    preferences.setSelectedSchoolId(schoolId)
                                    viewModel.refresh()
                                }
                            },
                            onAppearanceModeChange = { mode ->
                                lifecycleScope.launch {
                                    preferences.setAppearanceMode(mode)
                                }
                            },
                            onDynamicColorChange = { enabled ->
                                lifecycleScope.launch {
                                    preferences.setDynamicColor(enabled)
                                }
                            },
                            onCustomApiBaseUrlChange = { url ->
                                lifecycleScope.launch {
                                    preferences.setCustomApiBaseUrl(url)
                                    ApiFactory.customBaseUrl = url
                                    viewModel.refresh()
                                }
                            },
                            onUserProfileChange = { profile ->
                                lifecycleScope.launch {
                                    preferences.setUserProfile(profile)
                                    viewModel.setEnergyReferenceInput(profile.recommendedEnergyKcal.toString())
                                    viewModel.saveEnergyReference()
                                }
                            },
                            onApplyRecommendedEnergy = { kcal ->
                                viewModel.setEnergyReferenceInput(kcal.toString())
                                viewModel.saveEnergyReference()
                            },
                            syncState = syncState,
                            onOpenSyncDialog = { showLoginSyncDialog = true },
                            onTriggerSync = {
                                if (!isSyncingNow) {
                                    isSyncingNow = true
                                    lifecycleScope.launch {
                                        syncRepository.triggerSync()
                                            .onSuccess { msg ->
                                                isSyncingNow = false
                                                viewModel.showMessage(msg)
                                            }
                                            .onFailure { err ->
                                                isSyncingNow = false
                                                viewModel.showMessage(err.message ?: "同步失败")
                                            }
                                    }
                                }
                            },
                            isSyncing = isSyncingNow,
                            onOpenHealthSettings = {
                                lifecycleScope.launch {
                                    runCatching {
                                        startActivity(healthConnectSource.createSettingsIntent())
                                    }.onFailure {
                                        viewModel.showMessage("无法打开系统健康设置")
                                    }
                                }
                            },
                            onDismiss = { showSettingsDialog = false }
                        )
                    }
                }

                if (showLoginSyncDialog) {
                    LoginSyncDialog(
                        syncRepository = syncRepository,
                        currentSchoolId = selectedSchoolId,
                        onDismiss = { showLoginSyncDialog = false }
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

    private fun stageHomeMealUris(
        uris: List<Uri>,
        viewModel: TodayViewModel,
        cleanupFiles: List<File> = emptyList()
    ) {
        lifecycleScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    uris.map { uri ->
                        val upload = readImageForUpload(uri)
                        val thumbnail = decodeThumbnail(upload.bytes)
                        StagedMealImage(
                            bytes = upload.bytes,
                            mimeType = upload.mimeType,
                            fileName = upload.fileName,
                            thumbnail = thumbnail
                        )
                    }
                }
            }.onSuccess { stagedList ->
                viewModel.stageMealImages(stagedList)
            }.onFailure { error ->
                viewModel.showMessage(
                    error.message ?: "读取图片失败"
                )
            }
            cleanupFiles.forEach { it.delete() }
        }
    }

    private fun decodeThumbnail(bytes: ByteArray): android.graphics.Bitmap? {
        return runCatching {
            val boundsOptions = android.graphics.BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size, boundsOptions)
            val sampleSize = maxOf(1, maxOf(boundsOptions.outWidth, boundsOptions.outHeight) / 256)
            val decodeOptions = android.graphics.BitmapFactory.Options().apply {
                inSampleSize = sampleSize
            }
            android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size, decodeOptions)
        }.getOrNull()
    }

    private fun readImageForUpload(uri: Uri): UploadImage {
        val compressed = ImageCompressor.compressFromUri(this@MainActivity, uri)
        return UploadImage(
            bytes = compressed.bytes,
            mimeType = compressed.mimeType,
            fileName = compressed.fileName
        )
    }

    private suspend fun selectSchoolActivitySource(
        date: String,
        viewModel: TodayViewModel
    ) {
        viewModel.schoolActivitySyncStarted()
        runCatching {
            repository.saveSchoolActivitySource(
                date = date,
                source = "school"
            )
        }.onSuccess {
            viewModel.schoolActivitySyncFinished("已使用学校记录")
        }.onFailure { error ->
            viewModel.schoolActivitySyncFailed(
                error.message ?: "切换学校记录失败"
            )
        }
    }

    private suspend fun syncSchoolActivity(
        date: String,
        viewModel: TodayViewModel
    ) {
        viewModel.schoolActivitySyncStarted()

        runCatching {
            val schoolActivity = repository.loadSchoolActivity(date)
            check(schoolActivity.peWindows.isNotEmpty()) {
                "今天没有学校体育课安排"
            }
            val aggregate = healthConnectSource.readSchoolPeWindows(
                date = LocalDate.parse(date),
                peWindows = schoolActivity.peWindows
            )
            check(aggregate.exerciseMinutes > 0) {
                "体育课时段没有读到运动时长，仍保留学校记录；请确认手环已把运动记录同步到运动健康"
            }

            repository.saveSchoolActivitySource(
                date = date,
                source = "health_connect",
                exerciseMinutes = aggregate.exerciseMinutes,
                steps = aggregate.steps,
                activeEnergyKcal = aggregate.activeEnergyKcal
            )
            aggregate
        }.onSuccess { aggregate ->
            viewModel.schoolActivitySyncFinished(
                "已使用手环记录：" +
                    aggregate.exerciseMinutes +
                    " 分钟"
            )
        }.onFailure { error ->
            viewModel.schoolActivitySyncFailed(
                error.message ?: "读取体育课手环数据失败"
            )
        }
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
            val message = if (aggregate.steps == 0L && aggregate.exerciseMinutes == 0) {
                "已连接运动健康：今日暂无校外运动或步数记录"
            } else {
                "已更新校外运动：${aggregate.exerciseMinutes} 分钟 · ${aggregate.steps} 步"
            }
            viewModel.phoneActivitySyncFinished(
                message = message,
                syncedSteps = aggregate.steps
            )
        }.onFailure { error ->
            viewModel.phoneActivitySyncFailed(
                error.message ?: "手机运动数据同步失败"
            )
        }
    }
}
