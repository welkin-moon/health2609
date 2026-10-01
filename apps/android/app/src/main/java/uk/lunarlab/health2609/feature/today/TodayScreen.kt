@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package uk.lunarlab.health2609.feature.today

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ColorLens
import androidx.compose.material.icons.rounded.DirectionsRun
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.HealthAndSafety
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt
import uk.lunarlab.health2609.core.network.DailySummaryDto
import uk.lunarlab.health2609.core.network.DishDto
import uk.lunarlab.health2609.core.network.SchoolActivityDto
import uk.lunarlab.health2609.core.network.StudentSchoolDto
import uk.lunarlab.health2609.core.storage.AppearanceMode

private val portionOptions = listOf(
    0.0 to "没吃",
    0.25 to "¼份",
    0.5 to "半份",
    0.75 to "¾份",
    1.0 to "1份"
)

private fun roundOneDecimal(value: Double): Double =
    (value * 10.0).roundToInt() / 10.0

private data class NutritionTotals(
    val energyKcal: Double = 0.0,
    val proteinG: Double = 0.0,
    val fatG: Double = 0.0,
    val carbohydrateG: Double = 0.0
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun TodayScreen(
    destination: String,
    state: TodayUiState,
    selectedSchoolId: String,
    appearanceMode: AppearanceMode,
    dynamicColor: Boolean,
    wideLayout: Boolean,
    onAppearanceModeChange: (AppearanceMode) -> Unit,
    onDynamicColorChange: (Boolean) -> Unit,
    onSchoolChange: (String) -> Unit,
    onPortionChange: (String, Double) -> Unit,
    onGramsChange: (String, Double?) -> Unit,
    onRefresh: () -> Unit,
    onSaveMeal: () -> Unit,
    onTakeHomeMealPhoto: () -> Unit,
    onPickHomeMealImage: () -> Unit,
    onRemoveStagedMealImage: (String) -> Unit = {},
    onClearStagedMealImages: () -> Unit = {},
    onAnalyzeHomeMeal: () -> Unit = {},
    onHomeMealSlotChange: (String) -> Unit,
    onHomeMealNameChange: (Int, String) -> Unit,
    onHomeMealGramsChange: (Int, Double?) -> Unit,
    onRemoveHomeMealItem: (Int) -> Unit,
    onSaveHomeMeal: () -> Unit,
    onActivityMinutesChange: (Int) -> Unit,
    onActivityIntensityChange: (String) -> Unit,
    onSaveActivity: (String) -> Unit,
    onUseSchoolActivity: () -> Unit,
    onUseWearableSchoolActivity: () -> Unit,
    onRequestHealthPermissions: () -> Unit,
    onSyncPhoneActivity: () -> Unit,
    onEnergyReferenceChange: (String) -> Unit,
    onSaveEnergyReference: () -> Unit
) {
    val lunchPreview = remember(state.menu, state.amounts) {
        val dishes = state.menu?.dishes.orEmpty()
        NutritionTotals(
            energyKcal = dishes.sumOf {
                val amount = state.amounts[it.id] ?: DishAmount()
                (it.nutritionPerServing?.energyKcal ?: 0.0) * amount.servingMultiplier
            },
            proteinG = roundOneDecimal(dishes.sumOf {
                val amount = state.amounts[it.id] ?: DishAmount()
                (it.nutritionPerServing?.proteinG ?: 0.0) * amount.servingMultiplier
            }),
            fatG = roundOneDecimal(dishes.sumOf {
                val amount = state.amounts[it.id] ?: DishAmount()
                (it.nutritionPerServing?.fatG ?: 0.0) * amount.servingMultiplier
            }),
            carbohydrateG = roundOneDecimal(dishes.sumOf {
                val amount = state.amounts[it.id] ?: DishAmount()
                (it.nutritionPerServing?.carbohydrateG ?: 0.0) * amount.servingMultiplier
            })
        )
    }

    val prettyDate = remember(state.date) {
        runCatching {
            LocalDate.parse(state.date).format(
                DateTimeFormatter.ofPattern("M月d日 EEEE", Locale.SIMPLIFIED_CHINESE)
            )
        }.getOrDefault(state.date)
    }

    var showSettingsDialog by rememberSaveable { mutableStateOf(false) }

    val screenTitle = when (destination) {
        "meals" -> "饮食"
        "activity" -> "运动"
        else -> "今天"
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(screenTitle, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                        Text(
                            prettyDate,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { showSettingsDialog = true }) {
                        Icon(Icons.Rounded.Settings, contentDescription = "设置")
                    }
                    IconButton(onClick = onRefresh, enabled = !state.loading) {
                        Icon(Icons.Rounded.Refresh, contentDescription = "更新今天的数据")
                    }
                }
            )
        }
    ) { innerPadding ->
        if (state.loading && state.menu == null && state.summary == null) {
            Column(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                LoadingIndicator()
                Spacer(Modifier.height(12.dp))
                Text(
                    "正在同步今天的数据",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "餐食、运动和学校安排会一起更新",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            return@Scaffold
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                AnimatedVisibility(
                    visible = state.message != null,
                    enter = fadeIn(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)) +
                            expandVertically(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)),
                    exit = fadeOut(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)) +
                           shrinkVertically(animationSpec = spring(stiffness = Spring.StiffnessMediumLow))
                ) {
                    state.message?.let { message ->
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(
                                    horizontal = if (wideLayout) 24.dp else 18.dp,
                                    vertical = 6.dp
                                )
                        ) {
                            StatusMessage(message)
                        }
                    }
                }

                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    if (wideLayout) {
                        WideScreenContent(
                            destination = destination,
                            state = state,
                            selectedSchoolId = selectedSchoolId,
                            lunchPreview = lunchPreview,
                            onPortionChange = onPortionChange,
                            onGramsChange = onGramsChange,
                            onSaveMeal = onSaveMeal,
                            onTakeHomeMealPhoto = onTakeHomeMealPhoto,
                            onPickHomeMealImage = onPickHomeMealImage,
                            onRemoveStagedMealImage = onRemoveStagedMealImage,
                            onClearStagedMealImages = onClearStagedMealImages,
                            onAnalyzeHomeMeal = onAnalyzeHomeMeal,
                            onHomeMealSlotChange = onHomeMealSlotChange,
                            onHomeMealNameChange = onHomeMealNameChange,
                            onHomeMealGramsChange = onHomeMealGramsChange,
                            onRemoveHomeMealItem = onRemoveHomeMealItem,
                            onSaveHomeMeal = onSaveHomeMeal,
                            onActivityMinutesChange = onActivityMinutesChange,
                            onActivityIntensityChange = onActivityIntensityChange,
                            onSaveActivity = onSaveActivity,
                            onUseSchoolActivity = onUseSchoolActivity,
                            onUseWearableSchoolActivity = onUseWearableSchoolActivity,
                            onRequestHealthPermissions = onRequestHealthPermissions,
                            onSyncPhoneActivity = onSyncPhoneActivity,
                            onEnergyReferenceChange = onEnergyReferenceChange,
                            onSaveEnergyReference = onSaveEnergyReference,
                            onOpenSettings = { showSettingsDialog = true }
                        )
                    } else {
                        CompactScreenContent(
                            destination = destination,
                            state = state,
                            selectedSchoolId = selectedSchoolId,
                            lunchPreview = lunchPreview,
                            onPortionChange = onPortionChange,
                            onGramsChange = onGramsChange,
                            onSaveMeal = onSaveMeal,
                            onTakeHomeMealPhoto = onTakeHomeMealPhoto,
                            onPickHomeMealImage = onPickHomeMealImage,
                            onRemoveStagedMealImage = onRemoveStagedMealImage,
                            onClearStagedMealImages = onClearStagedMealImages,
                            onAnalyzeHomeMeal = onAnalyzeHomeMeal,
                            onHomeMealSlotChange = onHomeMealSlotChange,
                            onHomeMealNameChange = onHomeMealNameChange,
                            onHomeMealGramsChange = onHomeMealGramsChange,
                            onRemoveHomeMealItem = onRemoveHomeMealItem,
                            onSaveHomeMeal = onSaveHomeMeal,
                            onActivityMinutesChange = onActivityMinutesChange,
                            onActivityIntensityChange = onActivityIntensityChange,
                            onSaveActivity = onSaveActivity,
                            onUseSchoolActivity = onUseSchoolActivity,
                            onUseWearableSchoolActivity = onUseWearableSchoolActivity,
                            onRequestHealthPermissions = onRequestHealthPermissions,
                            onSyncPhoneActivity = onSyncPhoneActivity,
                            onEnergyReferenceChange = onEnergyReferenceChange,
                            onSaveEnergyReference = onSaveEnergyReference,
                            onOpenSettings = { showSettingsDialog = true }
                        )
                    }
                }
            }
        }
    }

    if (showSettingsDialog) {
        Dialog(
            onDismissRequest = { showSettingsDialog = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            SettingsDialog(
                schools = state.schools,
                selectedSchoolId = selectedSchoolId,
                appearanceMode = appearanceMode,
                dynamicColor = dynamicColor,
                onSchoolChange = onSchoolChange,
                onAppearanceModeChange = onAppearanceModeChange,
                onDynamicColorChange = onDynamicColorChange,
                onOpenHealthSettings = onRequestHealthPermissions,
                onDismiss = { showSettingsDialog = false }
            )
        }
    }
}

@Composable
private fun WideScreenContent(
    destination: String,
    state: TodayUiState,
    selectedSchoolId: String,
    lunchPreview: NutritionTotals,
    onPortionChange: (String, Double) -> Unit,
    onGramsChange: (String, Double?) -> Unit,
    onSaveMeal: () -> Unit,
    onTakeHomeMealPhoto: () -> Unit,
    onPickHomeMealImage: () -> Unit,
    onRemoveStagedMealImage: (String) -> Unit,
    onClearStagedMealImages: () -> Unit,
    onAnalyzeHomeMeal: () -> Unit,
    onHomeMealSlotChange: (String) -> Unit,
    onHomeMealNameChange: (Int, String) -> Unit,
    onHomeMealGramsChange: (Int, Double?) -> Unit,
    onRemoveHomeMealItem: (Int) -> Unit,
    onSaveHomeMeal: () -> Unit,
    onActivityMinutesChange: (Int) -> Unit,
    onActivityIntensityChange: (String) -> Unit,
    onSaveActivity: (String) -> Unit,
    onUseSchoolActivity: () -> Unit,
    onUseWearableSchoolActivity: () -> Unit,
    onRequestHealthPermissions: () -> Unit,
    onSyncPhoneActivity: () -> Unit,
    onEnergyReferenceChange: (String) -> Unit,
    onSaveEnergyReference: () -> Unit,
    onOpenSettings: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        when (destination) {
            "meals" -> {
                LazyColumn(
                    modifier = Modifier
                        .weight(1.1f)
                        .fillMaxHeight(),
                    contentPadding = PaddingValues(top = 8.dp, bottom = 32.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    item {
                        SectionHeading(
                            title = "学校午餐",
                            subtitle = "按实际吃下的分量快速记录",
                            icon = { Icon(Icons.Rounded.Restaurant, contentDescription = null) }
                        )
                    }

                    val dishes = state.menu?.dishes.orEmpty()
                    if (dishes.isEmpty()) {
                        item {
                            EmptyState(
                                title = "今天的午餐还没发布",
                                body = "学校录入后会自动出现在这里。"
                            )
                        }
                    } else {
                        items(dishes, key = { it.id }) { dish ->
                            DishRow(
                                dish = dish,
                                amount = state.amounts[dish.id] ?: DishAmount(),
                                onPortionChange = { onPortionChange(dish.id, it) },
                                onGramsChange = { onGramsChange(dish.id, it) }
                            )
                        }
                        item { LunchSummary(lunchPreview, state.savingMeal, onSaveMeal) }
                    }
                }

                LazyColumn(
                    modifier = Modifier
                        .weight(0.9f)
                        .fillMaxHeight(),
                    contentPadding = PaddingValues(top = 8.dp, bottom = 32.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    item {
                        SectionHeading(
                            title = "其他餐食",
                            subtitle = "拍照识别，再由你确认名称和分量",
                            icon = { Icon(Icons.Rounded.PhotoCamera, contentDescription = null) }
                        )
                    }
                    item {
                        HomeMealPanel(
                            slot = state.homeMealSlot,
                            stagedImages = state.stagedMealImages,
                            draft = state.homeMealDraft,
                            notes = state.homeMealNotes,
                            analyzing = state.analyzingHomeMeal,
                            saving = state.savingHomeMeal,
                            onTakePhoto = onTakeHomeMealPhoto,
                            onPickImage = onPickHomeMealImage,
                            onRemoveStagedImage = onRemoveStagedMealImage,
                            onClearStagedImages = onClearStagedMealImages,
                            onAnalyze = onAnalyzeHomeMeal,
                            onSlotChange = onHomeMealSlotChange,
                            onNameChange = onHomeMealNameChange,
                            onGramsChange = onHomeMealGramsChange,
                            onRemoveItem = onRemoveHomeMealItem,
                            onSave = onSaveHomeMeal
                        )
                    }
                }
            }

            "activity" -> {
                LazyColumn(
                    modifier = Modifier
                        .weight(1.1f)
                        .fillMaxHeight(),
                    contentPadding = PaddingValues(top = 8.dp, bottom = 32.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    item {
                        SectionHeading(
                            title = "运动统计与同步",
                            subtitle = "手机运动数据与学校体育时段记录",
                            icon = { Icon(Icons.Rounded.DirectionsRun, contentDescription = null) }
                        )
                    }
                    item {
                        ActivityStatsCard(
                            summary = state.summary,
                            syncing = state.syncingPhoneActivity,
                            onSync = onSyncPhoneActivity,
                            onRequestHealthPermissions = onRequestHealthPermissions
                        )
                    }
                    item {
                        SchoolPeWindowCard(
                            schoolActivity = state.schoolActivity,
                            syncingSchoolActivity = state.syncingSchoolActivity,
                            onUseSchoolActivity = onUseSchoolActivity,
                            onUseWearableSchoolActivity = onUseWearableSchoolActivity
                        )
                    }
                }

                LazyColumn(
                    modifier = Modifier
                        .weight(0.9f)
                        .fillMaxHeight(),
                    contentPadding = PaddingValues(top = 8.dp, bottom = 32.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    item {
                        SectionHeading(
                            title = "快速补记运动",
                            subtitle = "预设项目、时长滑块与强度选择",
                            icon = { Icon(Icons.Rounded.Edit, contentDescription = null) }
                        )
                    }
                    item {
                        ManualActivityCard(
                            initialType = state.manualActivityType,
                            minutes = state.manualActivityMinutes,
                            intensity = state.manualActivityIntensity,
                            saving = state.savingActivity,
                            onMinutesChange = onActivityMinutesChange,
                            onIntensityChange = onActivityIntensityChange,
                            onSave = onSaveActivity,
                            collapsible = false
                        )
                    }
                }
            }

            else -> {
                LazyColumn(
                    modifier = Modifier
                        .weight(1.1f)
                        .fillMaxHeight(),
                    contentPadding = PaddingValues(top = 8.dp, bottom = 32.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    item {
                        SectionHeading(
                            title = "核心概览",
                            subtitle = "摄入平衡、运动达标与宏量构成",
                            icon = { Icon(Icons.Rounded.Restaurant, contentDescription = null) }
                        )
                    }
                    item {
                        EnergyBalanceCard(summary = state.summary)
                    }
                    item {
                        MoeExerciseProgressCard(summary = state.summary)
                    }
                    item {
                        MacroNutrientCard(summary = state.summary)
                    }
                    item {
                        DailyEnergyReferenceSettingCard(
                            currentReference = state.summary?.energy?.dailyEnergyReferenceKcal,
                            energyReferenceInput = state.energyReferenceInput,
                            savingReference = state.savingEnergyReference,
                            onEnergyReferenceChange = onEnergyReferenceChange,
                            onSaveEnergyReference = onSaveEnergyReference
                        )
                    }
                }

                LazyColumn(
                    modifier = Modifier
                        .weight(0.9f)
                        .fillMaxHeight(),
                    contentPadding = PaddingValues(top = 8.dp, bottom = 32.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    item {
                        SectionHeading(
                            title = "行动与动态",
                            subtitle = "循证健康指导、全天时段与学校配置",
                            icon = { Icon(Icons.Rounded.DirectionsRun, contentDescription = null) }
                        )
                    }
                    item {
                        NextActionCard(summary = state.summary)
                    }
                    item {
                        ActivityTimelineFeed(
                            summary = state.summary,
                            schoolActivity = state.schoolActivity
                        )
                    }
                    item {
                        SchoolEntryCard(
                            schools = state.schools,
                            selectedSchoolId = selectedSchoolId,
                            onOpenSettings = onOpenSettings
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CompactScreenContent(
    destination: String,
    state: TodayUiState,
    selectedSchoolId: String,
    lunchPreview: NutritionTotals,
    onPortionChange: (String, Double) -> Unit,
    onGramsChange: (String, Double?) -> Unit,
    onSaveMeal: () -> Unit,
    onTakeHomeMealPhoto: () -> Unit,
    onPickHomeMealImage: () -> Unit,
    onRemoveStagedMealImage: (String) -> Unit,
    onClearStagedMealImages: () -> Unit,
    onAnalyzeHomeMeal: () -> Unit,
    onHomeMealSlotChange: (String) -> Unit,
    onHomeMealNameChange: (Int, String) -> Unit,
    onHomeMealGramsChange: (Int, Double?) -> Unit,
    onRemoveHomeMealItem: (Int) -> Unit,
    onSaveHomeMeal: () -> Unit,
    onActivityMinutesChange: (Int) -> Unit,
    onActivityIntensityChange: (String) -> Unit,
    onSaveActivity: (String) -> Unit,
    onUseSchoolActivity: () -> Unit,
    onUseWearableSchoolActivity: () -> Unit,
    onRequestHealthPermissions: () -> Unit,
    onSyncPhoneActivity: () -> Unit,
    onEnergyReferenceChange: (String) -> Unit,
    onSaveEnergyReference: () -> Unit,
    onOpenSettings: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 18.dp,
            end = 18.dp,
            top = 8.dp,
            bottom = 126.dp
        ),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        when (destination) {
            "meals" -> {
                item {
                    SectionHeading(
                        title = "学校午餐",
                        subtitle = "按实际吃下的分量快速记录",
                        icon = { Icon(Icons.Rounded.Restaurant, contentDescription = null) }
                    )
                }

                val dishes = state.menu?.dishes.orEmpty()
                if (dishes.isEmpty()) {
                    item {
                        EmptyState(
                            title = "今天的午餐还没发布",
                            body = "学校录入后会自动出现在这里。"
                        )
                    }
                } else {
                    items(dishes, key = { it.id }) { dish ->
                        DishRow(
                            dish = dish,
                            amount = state.amounts[dish.id] ?: DishAmount(),
                            onPortionChange = { onPortionChange(dish.id, it) },
                            onGramsChange = { onGramsChange(dish.id, it) }
                        )
                    }
                    item { LunchSummary(lunchPreview, state.savingMeal, onSaveMeal) }
                }

                item { HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant) }

                item {
                    SectionHeading(
                        title = "其他餐食",
                        subtitle = "拍照识别，再由你确认名称和分量",
                        icon = { Icon(Icons.Rounded.PhotoCamera, contentDescription = null) }
                    )
                }

                item {
                    HomeMealPanel(
                        slot = state.homeMealSlot,
                        stagedImages = state.stagedMealImages,
                        draft = state.homeMealDraft,
                        notes = state.homeMealNotes,
                        analyzing = state.analyzingHomeMeal,
                        saving = state.savingHomeMeal,
                        onTakePhoto = onTakeHomeMealPhoto,
                        onPickImage = onPickHomeMealImage,
                        onRemoveStagedImage = onRemoveStagedMealImage,
                        onClearStagedImages = onClearStagedMealImages,
                        onAnalyze = onAnalyzeHomeMeal,
                        onSlotChange = onHomeMealSlotChange,
                        onNameChange = onHomeMealNameChange,
                        onGramsChange = onHomeMealGramsChange,
                        onRemoveItem = onRemoveHomeMealItem,
                        onSave = onSaveHomeMeal
                    )
                }
            }

            "activity" -> {
                item {
                    SectionHeading(
                        title = "今天的运动",
                        subtitle = "同步手机记录，也可以手动补记",
                        icon = { Icon(Icons.Rounded.DirectionsRun, contentDescription = null) }
                    )
                }

                item {
                    ActivityStatsCard(
                        summary = state.summary,
                        syncing = state.syncingPhoneActivity,
                        onSync = onSyncPhoneActivity,
                        onRequestHealthPermissions = onRequestHealthPermissions
                    )
                }

                item {
                    SchoolPeWindowCard(
                        schoolActivity = state.schoolActivity,
                        syncingSchoolActivity = state.syncingSchoolActivity,
                        onUseSchoolActivity = onUseSchoolActivity,
                        onUseWearableSchoolActivity = onUseWearableSchoolActivity
                    )
                }

                item {
                    ManualActivityCard(
                        initialType = state.manualActivityType,
                        minutes = state.manualActivityMinutes,
                        intensity = state.manualActivityIntensity,
                        saving = state.savingActivity,
                        onMinutesChange = onActivityMinutesChange,
                        onIntensityChange = onActivityIntensityChange,
                        onSave = onSaveActivity,
                        collapsible = true
                    )
                }
            }

            else -> {
                item {
                    NextActionCard(summary = state.summary)
                }
                item {
                    EnergyBalanceCard(summary = state.summary)
                }
                item {
                    MoeExerciseProgressCard(summary = state.summary)
                }
                item {
                    MacroNutrientCard(summary = state.summary)
                }
                item {
                    DailyEnergyReferenceSettingCard(
                        currentReference = state.summary?.energy?.dailyEnergyReferenceKcal,
                        energyReferenceInput = state.energyReferenceInput,
                        savingReference = state.savingEnergyReference,
                        onEnergyReferenceChange = onEnergyReferenceChange,
                        onSaveEnergyReference = onSaveEnergyReference
                    )
                }
                item {
                    ActivityTimelineFeed(
                        summary = state.summary,
                        schoolActivity = state.schoolActivity
                    )
                }
                item {
                    SchoolEntryCard(
                        schools = state.schools,
                        selectedSchoolId = selectedSchoolId,
                        onOpenSettings = onOpenSettings
                    )
                }
            }
        }
    }
}

@Composable
private fun EnergyBalanceCard(
    summary: DailySummaryDto?,
    modifier: Modifier = Modifier
) {
    val nutrition = summary?.nutrition
    val energy = summary?.energy
    val intake = nutrition?.energyKcal?.roundToInt() ?: 0
    val reference = energy?.dailyEnergyReferenceKcal
    val gap = energy?.referenceGapKcal

    Surface(
        shape = RoundedCornerShape(26.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "能量摄入与目标平衡",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                if (reference != null && gap != null) {
                    val gapText = if (gap >= 0) {
                        "距参考还需 " + gap.roundToInt() + " 千卡"
                    } else {
                        "超出参考 " + abs(gap).roundToInt() + " 千卡"
                    }
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (gap >= 0) {
                            MaterialTheme.colorScheme.secondaryContainer
                        } else {
                            MaterialTheme.colorScheme.errorContainer
                        }
                    ) {
                        Text(
                            gapText,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Medium,
                            color = if (gap >= 0) {
                                MaterialTheme.colorScheme.onSecondaryContainer
                            } else {
                                MaterialTheme.colorScheme.onErrorContainer
                            },
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        "今日总摄入",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        intake.toString() + " 千卡",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        "每日参考目标",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        if (reference != null) reference.toString() + " 千卡" else "未设定",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            if (reference != null && reference > 0) {
                val ratio = (intake.toFloat() / reference).coerceIn(0f, 1.5f)
                LinearProgressIndicator(
                    progress = { ratio.coerceAtMost(1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp),
                    trackColor = MaterialTheme.colorScheme.surfaceContainerHigh
                )
            }
        }
    }
}

@Composable
private fun MoeExerciseProgressCard(
    summary: DailySummaryDto?,
    modifier: Modifier = Modifier
) {
    val activity = summary?.activity
    val target = (activity?.targetMinutes ?: 120).coerceAtLeast(1)
    val totalMinutes = activity?.totalMinutes ?: 0
    val progress = (totalMinutes.toFloat() / target).coerceIn(0f, 1f)

    Surface(
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        "教育部 120 分钟运动达标",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        "中小学生日均校内外综合体育活动",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.76f)
                    )
                }
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.12f)
                ) {
                    Text(
                        if (activity?.targetReached == true) "已达标" else "目标 " + target + " 分钟",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                Text(
                    totalMinutes.toString() + " 分钟",
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "完成度 " + (progress * 100).roundToInt().toString() + "%",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium
                )
            }

            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(10.dp),
                trackColor = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.14f)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                OverviewMetric(
                    Modifier.weight(1f),
                    (activity?.peMinutes ?: 0).toString() + " 分钟",
                    "校内体育课"
                )
                OverviewMetric(
                    Modifier.weight(1f),
                    (activity?.outsideMinutes ?: 0).toString() + " 分钟",
                    "校外自主运动"
                )
                OverviewMetric(
                    Modifier.weight(1f),
                    activity?.activeEnergyKcal?.roundToInt()?.let { it.toString() + " 千卡" } ?: "—",
                    "活动消耗"
                )
            }
        }
    }
}

@Composable
private fun MacroNutrientCard(
    summary: DailySummaryDto?,
    modifier: Modifier = Modifier
) {
    val nutrition = summary?.nutrition
    val macro = nutrition?.macroCompositionPercent

    Surface(
        shape = RoundedCornerShape(26.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    "三大宏量营养素供能比",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    "学龄期推荐参考：蛋白质 10%–15% · 脂肪 20%–30% · 碳水 50%–65%",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                NutritionMetric(
                    Modifier.weight(1f),
                    (macro?.protein?.roundToInt() ?: 0).toString() + "%",
                    "蛋白质供能"
                )
                NutritionMetric(
                    Modifier.weight(1f),
                    (macro?.fat?.roundToInt() ?: 0).toString() + "%",
                    "脂肪供能"
                )
                NutritionMetric(
                    Modifier.weight(1f),
                    (macro?.carbohydrate?.roundToInt() ?: 0).toString() + "%",
                    "碳水供能"
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                NutritionMetric(
                    Modifier.weight(1f),
                    String.format(Locale.US, "%.1f 克", nutrition?.fiberG ?: 0.0),
                    "膳食纤维"
                )
                NutritionMetric(
                    Modifier.weight(1f),
                    (nutrition?.sodiumMg?.roundToInt() ?: 0).toString() + " mg",
                    "钠"
                )
                NutritionMetric(
                    Modifier.weight(1f),
                    String.format(Locale.US, "%.1f 克", nutrition?.sugarG ?: 0.0),
                    "糖"
                )
            }
        }
    }
}

@Composable
private fun DailyEnergyReferenceSettingCard(
    currentReference: Int?,
    energyReferenceInput: String,
    savingReference: Boolean,
    onEnergyReferenceChange: (String) -> Unit,
    onSaveEnergyReference: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(26.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    "每日能量参考基准",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                val refText = currentReference?.let { it.toString() + " 千卡" } ?: "未设定"
                Text(
                    "依据学龄儿童性别、年龄与身体活动强度设定（当前基准：" + refText + "）",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            val presets = listOf("1800", "2000", "2200", "2400")
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                presets.forEach { preset ->
                    FilterChip(
                        selected = energyReferenceInput == preset || currentReference?.toString() == preset,
                        onClick = { onEnergyReferenceChange(preset) },
                        label = { Text(preset + " 千卡") }
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = energyReferenceInput,
                    onValueChange = onEnergyReferenceChange,
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    label = { Text("自定义目标（千卡）") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
                Button(
                    onClick = onSaveEnergyReference,
                    enabled = !savingReference
                ) {
                    Text(if (savingReference) "保存中" else "保存")
                }
            }
        }
    }
}

@Composable
private fun NextActionCard(
    summary: DailySummaryDto?,
    modifier: Modifier = Modifier
) {
    val activity = summary?.activity
    val nutrition = summary?.nutrition
    val target = (activity?.targetMinutes ?: 120).coerceAtLeast(1)
    val totalMinutes = activity?.totalMinutes ?: 0
    val remainingMinutes = (target - totalMinutes).coerceAtLeast(0)
    val macro = nutrition?.macroCompositionPercent
    val intake = nutrition?.energyKcal?.roundToInt() ?: 0

    Surface(
        shape = RoundedCornerShape(26.dp),
        color = MaterialTheme.colorScheme.tertiaryContainer,
        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.12f)
                ) {
                    Icon(
                        Icons.Rounded.DirectionsRun,
                        contentDescription = null,
                        modifier = Modifier.padding(8.dp).size(22.dp)
                    )
                }
                Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                    Text(
                        "科学循证行动建议",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        "基于教育部青少年健康标准与膳食指南",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.76f)
                    )
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.16f))

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    "运动指导",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onTertiaryContainer
                )
                val exerciseAdvice = if (activity?.targetReached == true) {
                    "今日累计运动已达 " + totalMinutes + " 分钟，达到教育部每日 120 分钟标准！运动节律良好，建议晚间进行 5-10 分钟轻度拉伸放松，利于骨骼肌恢复与安稳入睡。"
                } else {
                    "今日累计运动 " + totalMinutes + " 分钟，距离教育部 120 分钟日均标准还差 " + remainingMinutes + " 分钟。建议放学后或傍晚安排 30 分钟中高强度运动（如跳绳、慢跑、羽毛球），促进骨量积累与体质增强。"
                }
                Text(
                    exerciseAdvice,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.90f)
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    "膳食与营养",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onTertiaryContainer
                )
                val nutritionAdvice = when {
                    intake == 0 -> {
                        "今日尚未记录完整餐食分量。建议在午餐或晚餐后及时记录，系统将自动分析宏量供能比及微量营养素储备。"
                    }
                    macro != null && macro.protein > 0 && macro.protein < 12.0 -> {
                        "当前蛋白质供能比偏低（" + macro.protein.roundToInt() + "%，参考适宜区间 10%–15%）。晚餐建议适当补充富含优质蛋白的食物，如鸡蛋、鱼虾、瘦肉或豆制品。"
                    }
                    macro != null && macro.fat > 35.0 -> {
                        "当前脂肪供能比较高（" + macro.fat.roundToInt() + "%，参考区间 20%–30%）。后续正餐建议以清淡、蒸煮或凉拌为主，并增加深色蔬菜摄入。"
                    }
                    (nutrition?.sodiumMg ?: 0.0) > 2000 -> {
                        "今日钠摄入已达 " + (nutrition?.sodiumMg ?: 0.0).roundToInt() + " mg，接近单日参考上限。建议晚间饮食保持清淡并注意补足水分。"
                    }
                    else -> {
                        "今日营养摄入结构良好，三大宏量营养素处于均衡推荐区间。请注意保持充足水分与规律睡眠。"
                    }
                }
                Text(
                    nutritionAdvice,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.90f)
                )
            }

            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.08f)
            ) {
                Text(
                    "循证依据：WS/T 578-2017 学龄儿童膳食营养素参考摄入量 · 教育部中小学健康促进行动",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.72f),
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                )
            }
        }
    }
}

@Composable
private fun ActivityTimelineFeed(
    summary: DailySummaryDto?,
    schoolActivity: SchoolActivityDto?,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(26.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                "校内外活动时间线",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )

            val schoolWindows = schoolActivity?.schoolDayWindows.orEmpty()
            val peWindows = schoolActivity?.peWindows.orEmpty()
            val outsideMinutes = summary?.activity?.outsideMinutes ?: 0

            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                val timeSpan = schoolWindows.joinToString("、") { it.startTime + "–" + it.endTime }.ifBlank { "08:00–17:00" }
                TimelineItemRow(
                    time = timeSpan,
                    title = "在校学习与作息",
                    subtitle = "学校常规教学作息时段"
                )

                if (peWindows.isEmpty()) {
                    TimelineItemRow(
                        time = "校内课时",
                        title = "今日无体育课排期",
                        subtitle = "依据校历与当日课表"
                    )
                } else {
                    peWindows.forEach { pe ->
                        val recorded = pe.schoolRecordedMinutes?.let { " · 记录 " + it + " 分钟" } ?: ""
                        TimelineItemRow(
                            time = pe.startTime + "–" + pe.endTime,
                            title = "校内体育课",
                            subtitle = "排课安排" + recorded
                        )
                    }
                }

                TimelineItemRow(
                    time = "放学 / 课后",
                    title = "校外自主活动",
                    subtitle = "已记录 " + outsideMinutes + " 分钟（自主锻炼或手环同步）"
                )

                val sourceText = if (schoolActivity?.selectedSource == "health_connect") {
                    "智能手环 (Health Connect)"
                } else {
                    "学校排课与日常考勤"
                }
                TimelineItemRow(
                    time = "数据源",
                    title = "体育课时采纳：" + sourceText,
                    subtitle = "可在“运动”页面一键切换数据采纳源"
                )
            }
        }
    }
}

@Composable
private fun TimelineItemRow(
    time: String,
    title: String,
    subtitle: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top
    ) {
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh
        ) {
            Text(
                time,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
            )
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SchoolEntryCard(
    schools: List<StudentSchoolDto>,
    selectedSchoolId: String,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val currentSchoolName = schools.firstOrNull { it.id == selectedSchoolId }?.name ?: "默认示范学校"

    Surface(
        shape = RoundedCornerShape(26.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    "学校与班级信息",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    "当前关联：" + currentSchoolName,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium
                )
            }

            Text(
                "校内午餐菜谱与体育课程由所在学校统一发布。如转校或体验其他学校数据，可随时点击设置切换。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            OutlinedButton(
                onClick = onOpenSettings,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Rounded.Settings, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("切换学校 / 设置主题")
            }
        }
    }
}

@Composable
private fun ActivityStatsCard(
    summary: DailySummaryDto?,
    syncing: Boolean,
    onSync: () -> Unit,
    onRequestHealthPermissions: () -> Unit,
    modifier: Modifier = Modifier
) {
    val activity = summary?.activity

    Surface(
        shape = RoundedCornerShape(26.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(
                        (activity?.totalMinutes ?: 0).toString() + " 分钟",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        "今天已记录的运动",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    FilledTonalButton(onClick = onSync, enabled = !syncing) {
                        Text(if (syncing) "正在更新" else "更新手机里的运动")
                    }
                    TextButton(
                        onClick = onRequestHealthPermissions,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                    ) {
                        Text("申请 / 打开健康权限设置")
                    }
                }
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ActivityMetric(
                    modifier = Modifier.weight(1f),
                    value = (activity?.peMinutes ?: 0).toString() + " 分钟",
                    label = "校内体育"
                )
                ActivityMetric(
                    modifier = Modifier.weight(1f),
                    value = (activity?.outsideMinutes ?: 0).toString() + " 分钟",
                    label = "校外运动"
                )
                ActivityMetric(
                    modifier = Modifier.weight(1f),
                    value = activity?.activeEnergyKcal?.roundToInt()?.let { it.toString() + " 千卡" } ?: "—",
                    label = "活动消耗"
                )
            }
        }
    }
}

@Composable
private fun SchoolPeWindowCard(
    schoolActivity: SchoolActivityDto?,
    syncingSchoolActivity: Boolean,
    onUseSchoolActivity: () -> Unit,
    onUseWearableSchoolActivity: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(26.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    "学校体育与作息",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                val schoolWindowText = schoolActivity?.schoolDayWindows
                    ?.joinToString("、") { it.startTime + "–" + it.endTime }
                    ?.takeIf { it.isNotBlank() }
                    ?: "学校暂未设置在校时段"
                Text(
                    "在校时段 " + schoolWindowText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.76f)
                )
            }

            if (schoolActivity?.peWindows.isNullOrEmpty()) {
                Text(
                    "今天没有体育课安排。",
                    style = MaterialTheme.typography.bodyMedium
                )
            } else {
                schoolActivity?.peWindows.orEmpty().forEach { window ->
                    val schoolMinutes = window.schoolRecordedMinutes
                        ?.let { " · 学校记录 " + it + " 分钟" }
                        ?: ""
                    Text(
                        window.startTime + "–" + window.endTime + schoolMinutes,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = schoolActivity?.selectedSource != "health_connect",
                    onClick = onUseSchoolActivity,
                    enabled = !syncingSchoolActivity,
                    label = { Text("学校记录") }
                )
                FilterChip(
                    selected = schoolActivity?.selectedSource == "health_connect",
                    onClick = onUseWearableSchoolActivity,
                    enabled = !syncingSchoolActivity &&
                        !schoolActivity?.peWindows.isNullOrEmpty(),
                    label = {
                        Text(
                            if (syncingSchoolActivity) "正在读取手环"
                            else "手环记录"
                        )
                    }
                )
            }

            val sourceDetail = if (
                schoolActivity?.selectedSource == "health_connect"
            ) {
                schoolActivity.wearableMinutes?.let {
                    "当前采用手环在体育课时段记录的 " + it + " 分钟。"
                } ?: "已选择手环记录。"
            } else {
                "当前采用学校记录的 " +
                    (schoolActivity?.schoolRecordedMinutes ?: 0) +
                    " 分钟。"
            }
            Text(
                sourceDetail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.76f)
            )
        }
    }
}

@Composable
private fun ManualActivityCard(
    initialType: String = "自主运动",
    minutes: Int,
    intensity: String,
    saving: Boolean,
    onMinutesChange: (Int) -> Unit,
    onIntensityChange: (String) -> Unit,
    onSave: (String) -> Unit,
    collapsible: Boolean = false,
    modifier: Modifier = Modifier
) {
    var showManual by rememberSaveable { mutableStateOf(!collapsible) }
    var activityType by rememberSaveable(initialType) { mutableStateOf(initialType) }

    Surface(
        shape = RoundedCornerShape(26.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (collapsible) {
                TextButton(
                    onClick = { showManual = !showManual },
                    contentPadding = PaddingValues(horizontal = 0.dp, vertical = 4.dp)
                ) {
                    Icon(Icons.Rounded.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(if (showManual) "收起补记" else "补记一次运动")
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Rounded.Edit, contentDescription = null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "课外与自主运动补记",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            AnimatedVisibility(visible = showManual) {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text("运动类型", style = MaterialTheme.typography.labelLarge)

                    val presetTypes = listOf("跑步", "跳绳", "羽毛球", "篮球", "自主运动")
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        presetTypes.forEach { type ->
                            FilterChip(
                                selected = activityType == type,
                                onClick = { activityType = type },
                                label = { Text(type) }
                            )
                        }
                    }

                    OutlinedTextField(
                        value = activityType,
                        onValueChange = { activityType = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text("运动项目") }
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("运动时长", style = MaterialTheme.typography.labelLarge)
                        Text(
                            minutes.toString() + " 分钟",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    Slider(
                        value = minutes.toFloat(),
                        onValueChange = { onMinutesChange((it / 5f).roundToInt() * 5) },
                        valueRange = 5f..180f,
                        steps = 34
                    )

                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        IntensityChip(
                            label = "轻松",
                            value = "light",
                            selected = intensity == "light",
                            onSelected = onIntensityChange
                        )
                        IntensityChip(
                            label = "中等",
                            value = "moderate",
                            selected = intensity == "moderate",
                            onSelected = onIntensityChange
                        )
                        IntensityChip(
                            label = "较累",
                            value = "vigorous",
                            selected = intensity == "vigorous",
                            onSelected = onIntensityChange
                        )
                    }

                    Text(
                        "如果手机已经记到同一段运动，会尽量避免重复累加。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Button(
                        onClick = { onSave(activityType.ifBlank { "自主运动" }) },
                        enabled = !saving,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(if (saving) "正在保存" else "保存这次运动")
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsDialog(
    schools: List<StudentSchoolDto>,
    selectedSchoolId: String,
    appearanceMode: AppearanceMode,
    dynamicColor: Boolean,
    onSchoolChange: (String) -> Unit,
    onAppearanceModeChange: (AppearanceMode) -> Unit,
    onDynamicColorChange: (Boolean) -> Unit,
    onOpenHealthSettings: () -> Unit,
    onDismiss: () -> Unit
) {
    Surface(
        modifier = Modifier
            .padding(horizontal = 20.dp, vertical = 24.dp)
            .widthIn(max = 480.dp)
            .fillMaxWidth()
            .heightIn(max = 680.dp),
        shape = RoundedCornerShape(32.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 6.dp,
        shadowElevation = 12.dp
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = 20.dp, vertical = 20.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        "应用设置",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "多端同步 · 就读学校 · 外观显示 · 权限管理",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(Icons.Rounded.Close, contentDescription = "关闭设置")
                }
            }

            // 1. E2EE Sync Group
            SettingsGroupCard(title = "账号与多端同步") {
                SettingsItemRow(
                    icon = { Icon(Icons.Rounded.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                    title = "端对端加密同步",
                    subtitle = "基于 Cloudflare D1 存储，端侧 AES-256-GCM 硬件加密",
                    trailing = {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Text(
                                "已就绪",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                SettingsItemRow(
                    icon = { Icon(Icons.Rounded.Sync, contentDescription = null, tint = MaterialTheme.colorScheme.secondary) },
                    title = "媒体极速微缩同步",
                    subtitle = "餐食图片仅同步 ≤160px 低位深微缩图，零流量负担"
                )
            }

            // 2. School Group
            SettingsGroupCard(title = "学校关联") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SettingsItemRow(
                        icon = { Icon(Icons.Rounded.School, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary) },
                        title = "就读学校",
                        subtitle = "关联食堂菜谱与体育课程安排"
                    )

                    if (schools.isEmpty()) {
                        Text(
                            "暂无可选学校，正在使用默认学校。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 48.dp)
                        )
                    } else {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 48.dp)
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            schools.forEach { school ->
                                FilterChip(
                                    selected = selectedSchoolId == school.id,
                                    onClick = { onSchoolChange(school.id) },
                                    label = { Text(school.name) }
                                )
                            }
                        }
                    }
                }
            }

            // 3. Appearance Group
            SettingsGroupCard(title = "外观与色彩") {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SettingsItemRow(
                        icon = { Icon(Icons.Rounded.ColorLens, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                        title = "主题深浅",
                        subtitle = "切换浅色、深色或随系统切换"
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 48.dp)
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(
                            AppearanceMode.SYSTEM to "跟随系统",
                            AppearanceMode.LIGHT to "浅色",
                            AppearanceMode.DARK to "深色"
                        ).forEach { (mode, label) ->
                            FilterChip(
                                selected = appearanceMode == mode,
                                onClick = { onAppearanceModeChange(mode) },
                                label = { Text(label) }
                            )
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                    SettingsItemRow(
                        icon = {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.secondaryContainer
                            ) {
                                Box(modifier = Modifier.size(24.dp), contentAlignment = Alignment.Center) {
                                    Text("M", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSecondaryContainer)
                                }
                            }
                        },
                        title = "Monet 动态色彩自适应",
                        subtitle = "根据系统壁纸动态演变界面与桌面图标底色",
                        trailing = {
                            Switch(
                                checked = dynamicColor,
                                onCheckedChange = onDynamicColorChange
                            )
                        }
                    )
                }
            }

            // 4. Health & Permissions Group
            SettingsGroupCard(title = "运动健康服务") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SettingsItemRow(
                        icon = { Icon(Icons.Rounded.HealthAndSafety, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                        title = "Health Connect 权限",
                        subtitle = "若系统授权未自动弹出，可直接打开系统设置页面开启权限"
                    )

                    OutlinedButton(
                        onClick = onOpenHealthSettings,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 48.dp)
                    ) {
                        Text("打开系统运动健康设置")
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsGroupCard(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                title,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            content()
        }
    }
}

@Composable
private fun SettingsItemRow(
    icon: @Composable () -> Unit,
    title: String,
    subtitle: String,
    trailing: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
            modifier = Modifier.size(38.dp)
        ) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                icon()
            }
        }

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium
            )
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        trailing?.invoke()
    }
}

@Composable
private fun OverviewMetric(modifier: Modifier, value: String, label: String) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.72f)
        )
    }
}

@Composable
private fun NutritionMetric(modifier: Modifier, value: String, label: String) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun StatusMessage(message: String, modifier: Modifier = Modifier) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        modifier = modifier
    ) {
        Text(message, modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp))
    }
}

@Composable
private fun SectionHeading(title: String, subtitle: String, icon: @Composable () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
            Row(modifier = Modifier.padding(10.dp)) { icon() }
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun EmptyState(title: String, body: String) {
    Surface(shape = RoundedCornerShape(22.dp), color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
            Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun DishRow(
    dish: DishDto,
    amount: DishAmount,
    onPortionChange: (Double) -> Unit,
    onGramsChange: (Double?) -> Unit
) {
    val currentMultiplier = amount.servingMultiplier.toFloat().coerceIn(0f, 2f)
    val standardGrams = dish.standardServingGrams ?: 150.0
    val currentGrams = (standardGrams * currentMultiplier).roundToInt()
    val currentEnergy = ((dish.nutritionPerServing?.energyKcal ?: 0.0) * currentMultiplier).roundToInt()

    val portionTitle = when {
        currentMultiplier <= 0.05f -> "0.0x (没吃)"
        currentMultiplier in 0.45f..0.55f -> "0.5x (半份)"
        currentMultiplier in 0.95f..1.05f -> "1.0x (标准一份)"
        currentMultiplier in 1.45f..1.55f -> "1.5x (一份半)"
        currentMultiplier in 1.95f..2.0f -> "2.0x (两份/双倍)"
        else -> String.format(Locale.US, "%.2fx", currentMultiplier)
    }

    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.animateContentSize(animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec())
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        dish.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        "基准一份约 " + standardGrams.roundToInt() + " 克 · " +
                            (dish.nutritionPerServing?.energyKcal?.roundToInt() ?: 0) + " 千卡",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (currentMultiplier > 0.05f) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerHighest
                    }
                ) {
                    Text(
                        portionTitle,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (currentMultiplier > 0.05f) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "进食分量 (0x - 2x)",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "吃下约 " + currentGrams + " 克 · " + currentEnergy + " 千卡",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Slider(
                    value = currentMultiplier,
                    onValueChange = { newVal ->
                        val snapped = (newVal * 4f).roundToInt() / 4f
                        onPortionChange(snapped.toDouble())
                    },
                    valueRange = 0f..2f,
                    steps = 7,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    listOf(
                        0.0 to "0x 没吃",
                        0.5 to "0.5x 半份",
                        1.0 to "1.0x 一份",
                        1.5 to "1.5x 多吃",
                        2.0 to "2.0x 两份"
                    ).forEach { (stepValue, stepLabel) ->
                        TextButton(
                            onClick = { onPortionChange(stepValue) },
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
                            modifier = Modifier.height(28.dp)
                        ) {
                            Text(
                                stepLabel,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (abs(currentMultiplier - stepValue) < 0.05f) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LunchSummary(totals: NutritionTotals, saving: Boolean, onSave: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.tertiaryContainer,
        contentColor = MaterialTheme.colorScheme.onTertiaryContainer
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text("午餐合计", style = MaterialTheme.typography.labelLarge)
                Text(
                    totals.energyKcal.roundToInt().toString() + " 千卡 · 蛋白质 " +
                        String.format(Locale.US, "%.1f", totals.proteinG) + " 克 · 脂肪 " +
                        String.format(Locale.US, "%.1f", totals.fatG) + " 克 · 碳水 " +
                        String.format(Locale.US, "%.1f", totals.carbohydrateG) + " 克",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            Button(onClick = onSave, enabled = !saving) {
                Text(if (saving) "保存中" else "保存午餐")
            }
        }
    }
}

@Composable
private fun HomeMealPanel(
    slot: String,
    stagedImages: List<StagedMealImage>,
    draft: List<HomeMealDraftItem>,
    notes: List<String>,
    analyzing: Boolean,
    saving: Boolean,
    onTakePhoto: () -> Unit,
    onPickImage: () -> Unit,
    onRemoveStagedImage: (String) -> Unit,
    onClearStagedImages: () -> Unit,
    onAnalyze: () -> Unit,
    onSlotChange: (String) -> Unit,
    onNameChange: (Int, String) -> Unit,
    onGramsChange: (Int, Double?) -> Unit,
    onRemoveItem: (Int) -> Unit,
    onSave: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf(
                "breakfast" to "早餐",
                "lunch" to "午餐",
                "dinner" to "晚餐"
            ).forEach { (value, label) ->
                FilterChip(
                    selected = slot == value,
                    onClick = { onSlotChange(value) },
                    label = { Text(label) }
                )
            }
        }

        // Multi-image staging section
        if (stagedImages.isNotEmpty()) {
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "已选餐食照片 (${stagedImages.size}/5 张)",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        TextButton(
                            onClick = onClearStagedImages,
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            modifier = Modifier.height(28.dp),
                            enabled = !analyzing
                        ) {
                            Text("清空全部", style = MaterialTheme.typography.labelSmall)
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        stagedImages.forEach { staged ->
                            Box(
                                modifier = Modifier
                                    .size(80.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .border(
                                        1.dp,
                                        MaterialTheme.colorScheme.outlineVariant,
                                        RoundedCornerShape(14.dp)
                                    )
                            ) {
                                if (staged.thumbnail != null) {
                                    Image(
                                        bitmap = staged.thumbnail.asImageBitmap(),
                                        contentDescription = "餐食图片",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                } else {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            Icons.Rounded.Restaurant,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                if (!analyzing) {
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .padding(4.dp)
                                            .size(24.dp)
                                    ) {
                                        IconButton(
                                            onClick = { onRemoveStagedImage(staged.id) },
                                            modifier = Modifier.fillMaxSize()
                                        ) {
                                            Icon(
                                                Icons.Rounded.Close,
                                                contentDescription = "移除",
                                                modifier = Modifier.size(14.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        if (stagedImages.size < 5 && !analyzing) {
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                                modifier = Modifier.size(80.dp),
                                onClick = onTakePhoto
                            ) {
                                Column(
                                    modifier = Modifier.fillMaxSize(),
                                    verticalArrangement = Arrangement.Center,
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Icon(
                                        Icons.Rounded.PhotoCamera,
                                        contentDescription = "加拍",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(22.dp)
                                    )
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        "加拍一张",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = onAnalyze,
                            enabled = !analyzing && !saving,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Rounded.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(
                                if (analyzing) "正在综合分析…" else "开始分析 (${stagedImages.size} 张照片)"
                            )
                        }

                        if (stagedImages.size < 5) {
                            OutlinedButton(
                                onClick = onPickImage,
                                enabled = !analyzing && !saving
                            ) {
                                Text("相册添加")
                            }
                        }
                    }
                }
            }
        } else {
            // No staged images yet
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = onTakePhoto,
                    enabled = !analyzing && !saving,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Rounded.PhotoCamera, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("拍照记录")
                }
                FilledTonalButton(
                    onClick = onPickImage,
                    enabled = !analyzing && !saving,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Rounded.AddPhotoAlternate, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("相册选择")
                }
            }
            Text(
                "支持拍摄全桌、局部单菜特写或不同角度（最多5张），视觉大模型将综合识别并自动去重合并。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
            )
        }

        if (analyzing) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.padding(vertical = 4.dp)
            ) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                Text(
                    if (stagedImages.size > 1) "正在多图全景识别食物与估算分量…" else "正在识别食物与估算分量…",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        notes.firstOrNull()?.let { note ->
            Text(note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        draft.forEachIndexed { index, item ->
            val sourceGrams = (item.sourceGrams ?: 150.0).coerceAtLeast(10.0)
            val currentGrams = item.grams ?: sourceGrams
            val currentMultiplier = (currentGrams / sourceGrams).toFloat().coerceIn(0f, 2f)

            Surface(
                shape = RoundedCornerShape(22.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = item.name,
                            onValueChange = { onNameChange(index, it) },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            label = { Text("食物名称") }
                        )
                        IconButton(onClick = { onRemoveItem(index) }) {
                            Icon(Icons.Rounded.Close, contentDescription = "移除")
                        }
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "分量 (0x - 2x)",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                String.format(Locale.US, "%.2fx (约 %d 克)", currentMultiplier, currentGrams.roundToInt()),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        Slider(
                            value = currentMultiplier,
                            onValueChange = { newVal ->
                                val snapped = (newVal * 4f).roundToInt() / 4f
                                val updatedGrams = (sourceGrams * snapped).roundToInt().toDouble()
                                onGramsChange(index, updatedGrams)
                            },
                            valueRange = 0f..2f,
                            steps = 7,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            listOf(
                                0.0 to "0x 没吃",
                                0.5 to "0.5x 半份",
                                1.0 to "1.0x 整份",
                                1.5 to "1.5x",
                                2.0 to "2.0x 双份"
                            ).forEach { (stepValue, stepLabel) ->
                                TextButton(
                                    onClick = {
                                        val updatedGrams = (sourceGrams * stepValue).roundToInt().toDouble()
                                        onGramsChange(index, updatedGrams)
                                    },
                                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
                                    modifier = Modifier.height(26.dp)
                                ) {
                                    Text(
                                        stepLabel,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (abs(currentMultiplier - stepValue) < 0.05f) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                        }
                                    )
                                }
                            }
                        }
                    }

                    item.nutritionAtSource?.let { nutrition ->
                        val factor = currentMultiplier.toDouble()
                        Text(
                            ((nutrition.energyKcal ?: 0.0) * factor).roundToInt().toString() +
                                " 千卡 · 蛋白质 " +
                                String.format(Locale.US, "%.1f", (nutrition.proteinG ?: 0.0) * factor) +
                                " 克 · 碳水 " +
                                String.format(Locale.US, "%.1f", (nutrition.carbohydrateG ?: 0.0) * factor) +
                                " 克",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        if (draft.isNotEmpty()) {
            Button(onClick = onSave, enabled = !saving && !analyzing) {
                Text(if (saving) "正在保存" else "确认并记入今天")
            }
            Text(
                "名称和分量由你最后确认；确认前不会记进今天。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ActivityMetric(
    modifier: Modifier,
    value: String,
    label: String
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun IntensityChip(
    label: String,
    value: String,
    selected: Boolean,
    onSelected: (String) -> Unit
) {
    FilterChip(
        selected = selected,
        onClick = { onSelected(value) },
        label = { Text(label) }
    )
}
