@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package uk.lunarlab.health2609.feature.today

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.FastOutSlowInEasing
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
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.NetworkCheck
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.CloudSync
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material3.*
import uk.lunarlab.health2609.core.sync.SyncState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.key
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import uk.lunarlab.health2609.core.network.ApiFactory
import uk.lunarlab.health2609.BuildConfig
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
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
import uk.lunarlab.health2609.core.network.IntensityMinutesDto
import uk.lunarlab.health2609.core.network.DishDto
import uk.lunarlab.health2609.core.network.SchoolActivityDto
import uk.lunarlab.health2609.core.network.StudentSchoolDto
import uk.lunarlab.health2609.core.storage.AppearanceMode
import uk.lunarlab.health2609.core.storage.UserProfile

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
    hasBottomDock: Boolean = !wideLayout,
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
    customApiBaseUrl: String? = null,
    onCustomApiBaseUrlChange: (String?) -> Unit = {},
    onClearMessage: () -> Unit = {},
    onAddManualHomeMealItem: () -> Unit = {},
    userProfile: UserProfile = UserProfile(),
    expenditureOverrideKcal: Int? = null,
    onOpenSettings: () -> Unit = {},
    onNavigate: (String) -> Unit = {}
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

    if (state.loading && state.menu == null && state.summary == null) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            LoadingIndicator()
            Spacer(Modifier.height(12.dp))
            Text(
                "正在读取今天的记录",
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
        return
    }

    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            if (destination == "today") {
                Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(onClick = { onNavigate("meals") }, modifier = Modifier.weight(1f)) { Text("记录餐食") }
                        FilledTonalButton(onClick = { onNavigate("activity") }, modifier = Modifier.weight(1f)) { Text("记录运动") }
                    }
                    Text("当前为共享试用空间，请勿记录敏感内容。",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            AnimatedVisibility(
                visible = state.message != null,
                enter = fadeIn(animationSpec = tween(180, easing = FastOutSlowInEasing)) +
                        expandVertically(animationSpec = tween(180, easing = FastOutSlowInEasing)),
                exit = fadeOut(animationSpec = tween(180, easing = FastOutSlowInEasing)) +
                       shrinkVertically(animationSpec = tween(180, easing = FastOutSlowInEasing))
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
                        StatusMessage(message, onDismiss = onClearMessage)
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
                            onOpenSettings = onOpenSettings,
                            onAddManualHomeMealItem = onAddManualHomeMealItem,
                            userProfile = userProfile,
                            expenditureOverrideKcal = expenditureOverrideKcal
                        )
                    } else {
                        CompactScreenContent(
                            destination = destination,
                            state = state,
                            selectedSchoolId = selectedSchoolId,
                            hasBottomDock = hasBottomDock,
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
                            onOpenSettings = onOpenSettings,
                            onAddManualHomeMealItem = onAddManualHomeMealItem,
                            userProfile = userProfile,
                            expenditureOverrideKcal = expenditureOverrideKcal
                        )
                    }
                }
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
    onOpenSettings: () -> Unit,
    onAddManualHomeMealItem: () -> Unit = {},
    userProfile: UserProfile = UserProfile(),
    expenditureOverrideKcal: Int? = null
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
                                body = "学校还未发布今日午餐。你可以在下方拍照或手动记录。"
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
                            onSave = onSaveHomeMeal,
                            onAddManualItem = onAddManualHomeMealItem
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
                            title = "运动记录",
                            subtitle = "校内体育与校外运动一起查看",
                            icon = { Icon(Icons.Rounded.DirectionsRun, contentDescription = null) }
                        )
                    }
                    item {
                        ActivityStatsCard(
                            summary = state.summary,
                            syncedSteps = state.syncedSteps,
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
                            title = "补记运动",
                            subtitle = "选择项目，填写实际时长和强度",
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
                            title = "今日记录",
                            subtitle = "看看今天记录了什么",
                            icon = { Icon(Icons.Rounded.Restaurant, contentDescription = null) }
                        )
                    }
                    item {
                        EnergyBalanceCard(summary = state.summary, userProfile = userProfile, expenditureOverrideKcal = expenditureOverrideKcal)
                    }
                    item {
                        MoeExerciseProgressCard(
                            summary = state.summary,
                            syncedSteps = state.syncedSteps
                        )
                    }
                    item {
                        MacroNutrientCard(summary = state.summary)
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
                            title = "接下来",
                            subtitle = "补全记录，查看学校安排",
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
                            selectedSchoolId = selectedSchoolId
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
    hasBottomDock: Boolean = true,
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
    onOpenSettings: () -> Unit,
    onAddManualHomeMealItem: () -> Unit = {},
    userProfile: UserProfile = UserProfile(),
    expenditureOverrideKcal: Int? = null
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 18.dp,
            end = 18.dp,
            top = 8.dp,
            bottom = if (hasBottomDock) 150.dp else 36.dp
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
                            body = "学校发布后可在这里选择，也可以在下方手动记录午餐。"
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
                        onSave = onSaveHomeMeal,
                        onAddManualItem = onAddManualHomeMealItem
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
                        syncedSteps = state.syncedSteps,
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
                    EnergyBalanceCard(summary = state.summary, userProfile = userProfile, expenditureOverrideKcal = expenditureOverrideKcal)
                }
                item {
                    MoeExerciseProgressCard(
                        summary = state.summary,
                        syncedSteps = state.syncedSteps
                    )
                }
                item {
                    MacroNutrientCard(summary = state.summary)
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
                        selectedSchoolId = selectedSchoolId
                    )
                }
                item {
                    Spacer(Modifier.height(16.dp))
                }
            }
        }
    }
}

@Composable
private fun EnergyBalanceCard(
    summary: DailySummaryDto?,
    userProfile: UserProfile,
    expenditureOverrideKcal: Int?,
    modifier: Modifier = Modifier
) {
    val balance = energyBalance(summary, userProfile, expenditureOverrideKcal)
    val hasFood = (summary?.nutrition?.recordedFoodItems ?: 0) > 0
    val unknown = (summary?.nutrition?.unknownEnergyItems ?: 0) > 0
    Surface(
        shape = RoundedCornerShape(26.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = modifier
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("能量平衡", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(balance.status, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("估算全天消耗", style = MaterialTheme.typography.labelMedium)
                    Text(balance.expenditureKcal?.let { "$it 千卡" } ?: "待填写",
                        style = MaterialTheme.typography.titleMedium)
                }
                Text("−", style = MaterialTheme.typography.titleLarge)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(if (unknown) "已知部分摄入" else "已记录摄入", style = MaterialTheme.typography.labelMedium)
                    Text(if (hasFood) "${balance.knownIntakeKcal} 千卡" else "待记录",
                        style = MaterialTheme.typography.titleMedium)
                }
            }
            Text(balance.explanation, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            summary?.energy?.dailyEnergyReferenceKcal?.let {
                Text("每日摄入目标：$it 千卡 · 单独的计划值", style = MaterialTheme.typography.bodySmall)
            }
            Text("修改估算或目标：应用设置 → 能量设置", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun MoeExerciseProgressCard(
    summary: DailySummaryDto?,
    syncedSteps: Long? = null,
    modifier: Modifier = Modifier
) {
    val activity = summary?.activity
    val totalMinutes = activity?.totalMinutes ?: 0
    val targetMinutes = activity?.targetMinutes?.takeIf { it > 0 } ?: 120
    val steps = activity?.steps ?: syncedSteps ?: 0L
    val intensity = activity?.intensityMinutes ?: IntensityMinutesDto()
    val vigorousMinutes = intensity.vigorous
    val moderateMinutes = intensity.moderate
    val lightMinutes = intensity.light
    val activeKcal = activity?.activeEnergyKcal?.roundToInt()
        ?: activity?.manuallyEstimatedActiveEnergyKcal?.takeIf { it > 0 }?.roundToInt()

    val intensityStatus = if (totalMinutes > 0) "已记录" else "还未记录"

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
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        "今天的运动",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        "关注中高强度运动、今日步数与能量消耗",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.76f)
                    )
                }
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.12f)
                ) {
                    Text(
                        intensityStatus,
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
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        totalMinutes.toString() + " 分钟",
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "累计运动时间",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                    )
                }
                if (steps > 0L) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    ) {
                        Text(
                            "$steps 步",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            "今日步数",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.76f)
                        )
                    }
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        activeKcal?.let { it.toString() + " 千卡" } ?: "—",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "估算活动消耗",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.76f)
                    )
                }
            }

            LinearProgressIndicator(
                progress = { (totalMinutes.toFloat() / targetMinutes).coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(10.dp)
                    .clip(RoundedCornerShape(5.dp)),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.14f)
            )
            Text(
                "累计运动 $totalMinutes / 当日目标 $targetMinutes 分钟",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.76f)
            )

            // Intensity breakdown metrics
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                OverviewMetric(
                    Modifier.weight(1f),
                    vigorousMinutes.toString() + " 分钟",
                    "高强度"
                )
                OverviewMetric(
                    Modifier.weight(1f),
                    moderateMinutes.toString() + " 分钟",
                    "中等强度"
                )
                OverviewMetric(
                    Modifier.weight(1f),
                    lightMinutes.toString() + " 分钟",
                    "轻松日常"
                )
            }

            if ((activity?.peMinutes ?: 0) > 0 || (activity?.outsideMinutes ?: 0) > 0) {
                Text(
                    "全天累计 " + totalMinutes + " 分钟 · 含校内体育 " + (activity?.peMinutes ?: 0) + " 分钟，校外 " + (activity?.outsideMinutes ?: 0) + " 分钟",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.72f)
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
                    "营养构成",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    "来自已保存的餐食，记录越完整，构成越有参考价值",
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
private fun NextActionCard(
    summary: DailySummaryDto?,
    modifier: Modifier = Modifier
) {
    val activity = summary?.activity
    val nutrition = summary?.nutrition
    val totalMinutes = activity?.totalMinutes ?: 0
    val intensity = activity?.intensityMinutes ?: IntensityMinutesDto()
    val mvpaMinutes = intensity.moderate + intensity.vigorous
    val intake = nutrition?.energyKcal?.roundToInt() ?: 0

    Surface(
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Rounded.AutoAwesome,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        "记录提醒",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Text(
                    "今天",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }

            val motionAdvice = if (totalMinutes > 0) {
                "已记录 $totalMinutes 分钟运动，其中中高强度 $mvpaMinutes 分钟。还有遗漏的话，可以去运动页补记。"
            } else {
                "今天还没有运动记录，可以同步手机记录，也可以手动补记。"
            }
            val nutritionAdvice = if (intake > 0) {
                "这里只汇总已保存的餐食。别忘了补上早餐、晚餐和加餐。"
            } else {
                "今天还没有餐食记录。去饮食页选择学校午餐，或记录其他餐食。"
            }

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    motionAdvice,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    nutritionAdvice,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
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
                val timeSpan = schoolWindows.joinToString("、") { it.startTime + "–" + it.endTime }.ifBlank { "未提供" }
                TimelineItemRow(
                    time = timeSpan,
                    title = "在校时段",
                    subtitle = "学校提供的作息安排"
                )

                if (peWindows.isEmpty()) {
                    TimelineItemRow(
                        time = "校内课时",
                        title = "今天没有体育课安排",
                        subtitle = "以学校发布的课表为准"
                    )
                } else {
                    peWindows.forEach { pe ->
                        val recorded = pe.schoolRecordedMinutes?.let { " · 记录 " + it + " 分钟" } ?: ""
                        TimelineItemRow(
                            time = pe.startTime + "–" + pe.endTime,
                            title = "校内体育课",
                            subtitle = "课表安排" + recorded
                        )
                    }
                }

                TimelineItemRow(
                    time = "放学 / 课后",
                    title = "校外运动",
                    subtitle = "已记录 " + outsideMinutes + " 分钟（自主锻炼或手环同步）"
                )

                val sourceText = if (schoolActivity?.selectedSource == "health_connect") {
                    "手机运动健康记录"
                } else {
                    "学校提供的体育记录"
                }
                TimelineItemRow(
                    time = "数据源",
                    title = "体育课数据：" + sourceText,
                    subtitle = "可在“运动”页选择记录来源"
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
    modifier: Modifier = Modifier
) {
    val currentSchoolName = schools.firstOrNull { it.id == selectedSchoolId }?.name ?: "未选择学校"

    Surface(
        shape = RoundedCornerShape(26.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    "我的学校",
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
                "学校发布午餐菜单和体育安排，可在右上角设置中切换学校。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ActivityStatsCard(
    summary: DailySummaryDto?,
    syncedSteps: Long? = null,
    syncing: Boolean,
    onSync: () -> Unit,
    onRequestHealthPermissions: () -> Unit,
    modifier: Modifier = Modifier
) {
    val activity = summary?.activity
    val totalMinutes = activity?.totalMinutes ?: 0
    val steps = activity?.steps ?: syncedSteps ?: 0L
    val activeEnergy = activity?.activeEnergyKcal?.roundToInt()
        ?: activity?.manuallyEstimatedActiveEnergyKcal?.takeIf { it > 0 }?.roundToInt()

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
                    Row(
                        verticalAlignment = Alignment.Bottom,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            "$totalMinutes 分钟",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold
                        )
                        if (steps > 0L) {
                            Text(
                                "$steps 步",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    Text(
                        "今天已记录的运动 · 步数与活动统计",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    FilledTonalButton(onClick = onSync, enabled = !syncing) {
                        Text(if (syncing) "正在更新" else "同步运动记录")
                    }
                    TextButton(
                        onClick = onRequestHealthPermissions,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                    ) {
                        Text("管理运动权限")
                    }
                }
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ActivityMetric(
                    modifier = Modifier.weight(1f),
                    value = if (steps > 0L) "$steps 步" else "—",
                    label = "今日步数"
                )
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
                    value = activeEnergy?.let { "$it 千卡" } ?: "—",
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
    val presetTypesScrollState = rememberScrollState()
    val intensityScrollState = rememberScrollState()

    Surface(
        shape = RoundedCornerShape(26.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .padding(18.dp)
                .animateContentSize(
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioNoBouncy,
                        stiffness = Spring.StiffnessMediumLow
                    )
                ),
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
                        "手动记录运动",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            AnimatedVisibility(
                visible = showManual,
                enter = expandVertically(
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessMediumLow
                    )
                ) + fadeIn(
                    animationSpec = spring(
                        stiffness = Spring.StiffnessMediumLow
                    )
                ),
                exit = shrinkVertically(
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioNoBouncy,
                        stiffness = Spring.StiffnessMediumLow
                    )
                ) + fadeOut(
                    animationSpec = spring(
                        stiffness = Spring.StiffnessMediumLow
                    )
                )
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text("运动类型", style = MaterialTheme.typography.labelLarge)

                    val presetTypes = listOf("跑步", "跳绳", "羽毛球", "篮球", "自主运动")
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(presetTypesScrollState),
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
                        Text("$minutes 分钟", style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                    }
                    Slider(
                        value = minutes.toFloat().coerceIn(5f, 300f),
                        onValueChange = { onMinutesChange((it / 5f).roundToInt() * 5) },
                        valueRange = 5f..300f,
                        steps = 58
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(15, 30, 45, 60).forEach { preset ->
                            FilterChip(selected = minutes == preset,
                                onClick = { onMinutesChange(preset) },
                                label = { Text("$preset 分钟") })
                        }
                    }
                    Text("运动强度", style = MaterialTheme.typography.labelLarge)
                    Row(
                        modifier = Modifier.horizontalScroll(intensityScrollState),
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
                        when (intensity) {
                            "light" -> "轻松：呼吸变化不大。已同步的运动不用再补记。"
                            "vigorous" -> "较累：呼吸明显加快。已同步的运动不用再补记。"
                            else -> "中等：呼吸比平时快。已同步的运动不用再补记。"
                        },
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
internal fun SettingsDialog(
    schools: List<StudentSchoolDto>,
    selectedSchoolId: String,
    appearanceMode: AppearanceMode,
    dynamicColor: Boolean,
    customApiBaseUrl: String? = null,
    userProfile: UserProfile = UserProfile(),
    onSchoolChange: (String) -> Unit,
    onAppearanceModeChange: (AppearanceMode) -> Unit,
    onDynamicColorChange: (Boolean) -> Unit,
    onCustomApiBaseUrlChange: (String?) -> Unit = {},
    onUserProfileChange: suspend (UserProfile) -> Unit = {},
    currentEnergyTargetKcal: Int? = null,
    energyTargetLoaded: Boolean = false,
    expenditureOverrideKcal: Int? = null,
    savingEnergySettings: Boolean = false,
    onSaveEnergySettings: suspend (Int?, Int?) -> Result<Unit>,
    syncState: SyncState? = null,
    onOpenSyncDialog: () -> Unit = {},
    onTriggerSync: () -> Unit = {},
    isSyncing: Boolean = false,
    onOpenHealthSettings: () -> Unit,
    onDismiss: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    var serverInput by remember(customApiBaseUrl) {
        mutableStateOf(customApiBaseUrl ?: ApiFactory.currentBaseUrl)
    }
    var testingConnection by remember { mutableStateOf(false) }
    var testFeedback by remember { mutableStateOf<String?>(null) }
    var testSuccess by remember { mutableStateOf(false) }

    var ageText by rememberSaveable { mutableStateOf(userProfile.age.toString()) }
    var heightText by rememberSaveable {
        mutableStateOf(if (userProfile.heightCm % 1.0 == 0.0) userProfile.heightCm.toInt().toString() else userProfile.heightCm.toString())
    }
    var weightText by rememberSaveable {
        mutableStateOf(if (userProfile.weightKg % 1.0 == 0.0) userProfile.weightKg.toInt().toString() else userProfile.weightKg.toString())
    }

    var gender by rememberSaveable { mutableStateOf(userProfile.gender) }
    var profileFeedback by remember { mutableStateOf<String?>(null) }
    var savingProfile by remember { mutableStateOf(false) }
    var showServerSettings by rememberSaveable { mutableStateOf(false) }
    val draftProfile = profileFromInputs(ageText, gender, heightText, weightText)
    val profileChanged = draftProfile != userProfile

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
        Column(modifier = Modifier.fillMaxWidth()) {
            // Sticky Header: X button always visible on top!
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 16.dp, top = 18.dp, bottom = 12.dp),
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
                        "身体信息、能量、学校和外观",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(
                    onClick = onDismiss,
                    enabled = !savingEnergySettings,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(Icons.Rounded.Close, contentDescription = "关闭设置")
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

            // Scrollable Content
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // 1. Profile & Energy Recommendations
                SettingsGroupCard(title = "身体信息") {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        SettingsItemRow(
                            icon = { Icon(Icons.Rounded.HealthAndSafety, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                            title = "按需填写",
                            subtitle = "保存在这台手机"
                        )

                        // Keep all profile edits local until the user saves.
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FilterChip(
                                selected = gender == "male",
                                onClick = {
                                    gender = "male"
                                    profileFeedback = null
                                },
                                enabled = !savingProfile,
                                label = { Text("男生") }
                            )
                            FilterChip(
                                selected = gender == "female",
                                onClick = {
                                    gender = "female"
                                    profileFeedback = null
                                },
                                enabled = !savingProfile,
                                label = { Text("女生") }
                            )
                            FilterChip(
                                selected = gender == "neutral",
                                onClick = {
                                    gender = "neutral"
                                    profileFeedback = null
                                },
                                enabled = !savingProfile,
                                label = { Text("不指定") }
                            )
                        }

                        // Age, Height, Weight inputs
                        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                            val singleColumn = maxWidth < 280.dp || LocalDensity.current.fontScale > 1.1f
                            FlowRow(
                                maxItemsInEachRow = if (singleColumn) 1 else 3,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                OutlinedTextField(
                                    value = ageText,
                                    enabled = !savingProfile,
                                    onValueChange = { input ->
                                        ageText = input
                                        profileFeedback = null
                                    },
                                    label = { Text("年龄") },
                                    suffix = { Text("岁") },
                                    modifier = Modifier.weight(1f),
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                                )
                                OutlinedTextField(
                                    value = heightText,
                                    enabled = !savingProfile,
                                    onValueChange = { input ->
                                        heightText = input
                                        profileFeedback = null
                                    },
                                    label = { Text("身高") },
                                    suffix = { Text("cm") },
                                    modifier = Modifier.weight(1.2f),
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                                )
                                OutlinedTextField(
                                    value = weightText,
                                    enabled = !savingProfile,
                                    onValueChange = { input ->
                                        weightText = input
                                        profileFeedback = null
                                    },
                                    label = { Text("体重") },
                                    suffix = { Text("kg") },
                                    modifier = Modifier.weight(1.2f),
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                                )
                            }
                        }

                        if (draftProfile == null) {
                            Text("请输入年龄 6–25 岁、身高 80–230 cm、体重 20–200 kg。",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                        }
                        Button(
                            onClick = {
                                val profile = draftProfile ?: return@Button
                                savingProfile = true
                                coroutineScope.launch {
                                    try {
                                        onUserProfileChange(profile)
                                        profileFeedback = "身体信息已保存"
                                    } catch (error: Exception) {
                                        profileFeedback = "暂时未能保存，请重试。"
                                    } finally {
                                        savingProfile = false
                                    }
                                }
                            },
                            enabled = !savingProfile && draftProfile != null && profileChanged,
                            modifier = Modifier.fillMaxWidth()
                        ) { Text(if (savingProfile) "保存中…" else "保存身体信息") }
                        profileFeedback?.let { Text(it, style = MaterialTheme.typography.bodySmall) }

                        // Saved profile reference stays separate from the edit draft.
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f),
                            modifier = Modifier
                                .fillMaxWidth()

                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(
                                        "BMI ${userProfile.bmi} · 按当前信息计算",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                }

                                Text(
                                    "身体信息仅用于展示计算值。未成年人不使用成人体重分类，也不自动设置减重目标。",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f)
                                )


                            }
                        }
                    }
                }

                SettingsGroupCard(title = "能量设置") {
                    key(selectedSchoolId, customApiBaseUrl) {
                        if (energyTargetLoaded) {
                            var expenditureText by rememberSaveable { mutableStateOf(expenditureOverrideKcal?.toString() ?: "") }
                            var targetText by rememberSaveable { mutableStateOf(currentEnergyTargetKcal?.toString() ?: "") }
                            var savedExpenditureText by rememberSaveable { mutableStateOf(expenditureText) }
                            var savedTargetText by rememberSaveable { mutableStateOf(targetText) }
                            var energyFeedback by remember { mutableStateOf<String?>(null) }
                            val energyChanged = expenditureText != savedExpenditureText || targetText != savedTargetText
                            val validEnergy = listOf(expenditureText, targetText).all { it.isBlank() || it.toIntOrNull() in 500..6000 }

                        Text("能量平衡 = 估算全天消耗 − 已记录摄入。摄入目标单独保存，用于计划。",
                            style = MaterialTheme.typography.bodySmall)
                        OutlinedTextField(
                            value = expenditureText,
                            onValueChange = { expenditureText = it.filter(Char::isDigit); energyFeedback = null },
                            label = { Text("全天消耗估算（千卡）") },
                            supportingText = { Text(userProfile.estimatedDailyExpenditureKcal?.let {
                                "留空使用成人粗略估算：$it 千卡/天（轻活动系数 1.4）"
                            } ?: "留空则不估算；成长阶段和未指定性别不套用成人公式") },
                            singleLine = true, enabled = !savingEnergySettings,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = targetText,
                            onValueChange = { targetText = it.filter(Char::isDigit); energyFeedback = null },
                            label = { Text("每日摄入目标（可选，千卡）") },
                            supportingText = { Text("留空并保存可移除目标；目标不参与消耗估算") },
                            singleLine = true, enabled = !savingEnergySettings,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Text("手动值范围 500–6000 千卡；消耗估算仅保存在这台设备。全天消耗已包含活动，不再叠加运动热量。",
                            style = MaterialTheme.typography.bodySmall)
                        if (!validEnergy) Text("请填写 500–6000 千卡，或留空。", color = MaterialTheme.colorScheme.error)
                        Button(
                            onClick = {
                                coroutineScope.launch {
                                    onSaveEnergySettings(targetText.toIntOrNull(), expenditureText.toIntOrNull())
                                        .onSuccess {
                                            savedTargetText = targetText
                                            savedExpenditureText = expenditureText
                                            energyFeedback = "能量设置已保存"
                                        }.onFailure { energyFeedback = if (it is EnergySettingsSaveException) it.message else ApiFactory.formatErrorMessage(it) }
                                }
                            },
                            enabled = !savingEnergySettings && validEnergy && energyChanged,
                            modifier = Modifier.fillMaxWidth()
                        ) { Text(if (savingEnergySettings) "保存中…" else "保存能量设置") }
                        energyFeedback?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                        } else {
                            Text("暂未读取能量设置，请关闭设置并刷新今天的记录。")
                        }
                    }
                }

                // 3. School Group
                SettingsGroupCard(title = "学校关联") {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        SettingsItemRow(
                            icon = { Icon(Icons.Rounded.School, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary) },
                            title = "就读学校",
                            subtitle = "关联食堂菜谱与体育课程安排"
                        )

                        if (schools.isEmpty()) {
                            Text(
                                "暂时没有可选学校，请稍后刷新。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier
                            )
                        } else {
                            FlowRow(
                                modifier = Modifier
                                    .fillMaxWidth()

                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                schools.forEach { school ->
                                    FilterChip(
                                        selected = selectedSchoolId == school.id,
                                        onClick = { onSchoolChange(school.id) },
                                        enabled = !savingEnergySettings,
                                        label = { Text(school.name) }
                                    )
                                }
                            }
                        }
                    }
                }

                // 4. Appearance Group
                SettingsGroupCard(title = "外观与色彩") {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        SettingsItemRow(
                            icon = { Icon(Icons.Rounded.ColorLens, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                            title = "主题",
                            subtitle = "选择你习惯的显示方式"
                        )

                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
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
                            title = "跟随壁纸配色",
                            subtitle = "界面颜色随系统壁纸变化",
                            trailing = {
                                Switch(
                                    checked = dynamicColor,
                                    onCheckedChange = onDynamicColorChange
                                )
                            }
                        )
                    }
                }

                // 5. Health & Permissions Group
                SettingsGroupCard(title = "运动健康服务") {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        SettingsItemRow(
                            icon = { Icon(Icons.Rounded.HealthAndSafety, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                            title = "运动数据权限",
                            subtitle = "允许读取系统运动记录后，即可同步"
                        )

                        OutlinedButton(
                            onClick = onOpenHealthSettings,
                            modifier = Modifier
                                .fillMaxWidth()

                        ) {
                            Text("打开系统运动健康设置")
                        }
                    }
                }

                // 2. Server Connection Group
                SettingsGroupCard(title = "服务器设置") {
                    TextButton(onClick = { showServerSettings = !showServerSettings }) {
                        Text(if (showServerSettings) "收起服务器设置" else "修改服务器地址")
                    }
                    if (showServerSettings) Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        SettingsItemRow(
                            icon = { Icon(Icons.Rounded.Dns, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                            title = "服务地址",
                            subtitle = "连接其他服务器时再修改"
                        )

                        OutlinedTextField(
                            value = serverInput,
                            onValueChange = {
                                serverInput = it
                                testFeedback = null
                            },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            label = { Text("服务器地址") },
                            placeholder = { Text("https://h2609.lunarlab.uk/") },
                            textStyle = MaterialTheme.typography.bodySmall
                        )

                        if (testFeedback != null) {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = if (testSuccess) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        if (testSuccess) Icons.Rounded.CheckCircle else Icons.Rounded.Error,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                        tint = if (testSuccess) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onErrorContainer
                                    )
                                    Text(
                                        testFeedback.orEmpty(),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (testSuccess) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onErrorContainer
                                    )
                                }
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    testingConnection = true
                                    testFeedback = "正在检查连接…"
                                    coroutineScope.launch {
                                        val result = ApiFactory.testConnection(serverInput)
                                        testingConnection = false
                                        testSuccess = result.success
                                        testFeedback = result.message
                                    }
                                },
                                enabled = !savingEnergySettings && !testingConnection && ApiFactory.isValidBaseUrl(serverInput.trim()),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(if (testingConnection) "检查中…" else "检查连接")
                            }

                            Button(
                                enabled = !savingEnergySettings && !testingConnection && ApiFactory.isValidBaseUrl(serverInput.trim()),
                                onClick = {
                                    val clean = serverInput.trim()
                                    val target = if (clean == BuildConfig.API_BASE_URL.trim()) null else clean
                                    ApiFactory.customBaseUrl = target
                                    onCustomApiBaseUrlChange(target)
                                    testFeedback = "已保存并切换至当前地址"
                                    testSuccess = true
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("保存地址")
                            }
                        }

                        if (serverInput.trim() != BuildConfig.API_BASE_URL.trim()) {
                            TextButton(
                                enabled = !savingEnergySettings,
                                onClick = {
                                    serverInput = BuildConfig.API_BASE_URL
                                    ApiFactory.customBaseUrl = null
                                    onCustomApiBaseUrlChange(null)
                                    testFeedback = "已恢复官方默认地址"
                                    testSuccess = true
                                },
                                modifier = Modifier.align(Alignment.End)
                            ) {
                                Text("恢复默认地址")
                            }
                        }
                    }
                }

                SettingsGroupCard(title = "账号与跨设备同步") {
                    Text("跨设备同步尚未完成，当前版本不提供个人记录的加密备份。",
                        style = MaterialTheme.typography.bodyMedium)
                    Text("餐食与运动仍使用试用服务保存，身体信息和外观保存在这台手机。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedButton(onClick = onOpenSyncDialog, modifier = Modifier.fillMaxWidth()) {
                        Text("查看同步说明")
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
private fun StatusMessage(
    message: String,
    onDismiss: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        tonalElevation = 2.dp,
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                Icons.Rounded.Info,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Text(
                message,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f)
            )
            if (onDismiss != null) {
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        Icons.Rounded.Close,
                        contentDescription = "关闭提示",
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
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
                        "实际吃下的分量",
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

                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    listOf(
                        0.0 to "没吃",
                        0.5 to "半份",
                        1.0 to "1份",
                        1.5 to "1½份",
                        2.0 to "2份"
                    ).forEach { (stepValue, stepLabel) ->
                        TextButton(
                            onClick = { onPortionChange(stepValue) },
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
                            modifier = Modifier.heightIn(min = 48.dp)
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
    onSave: () -> Unit,
    onAddManualItem: () -> Unit = {}
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
                    enabled = !saving && !analyzing,
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
                            modifier = Modifier.heightIn(min = 48.dp),
                            enabled = !analyzing
                        ) {
                            Text("清空照片", style = MaterialTheme.typography.labelSmall)
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
                                            .size(48.dp)
                                    ) {
                                        IconButton(
                                            onClick = { onRemoveStagedImage(staged.id) },
                                            modifier = Modifier.fillMaxSize()
                                        ) {
                                            Icon(
                                                Icons.Rounded.Close,
                                                contentDescription = "移除这张照片",
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

                    Button(onClick = onAnalyze, enabled = !analyzing && !saving,
                        modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Rounded.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(if (analyzing) "识别中…" else "识别这餐")
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (stagedImages.size < 5) {
                            OutlinedButton(onClick = onPickImage, enabled = !analyzing && !saving) { Text("添加照片") }
                        }
                        TextButton(onClick = onAddManualItem, enabled = !analyzing && !saving) { Text("手动添加菜品") }
                    }
                }
            }
        } else {
            // No staged images yet
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onTakePhoto,
                    enabled = !analyzing && !saving,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Rounded.PhotoCamera, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("拍照")
                }
                FilledTonalButton(
                    onClick = onPickImage,
                    enabled = !analyzing && !saving,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Rounded.AddPhotoAlternate, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("相册")
                }
            }
            TextButton(onClick = onAddManualItem, enabled = !analyzing && !saving) {
                Icon(Icons.Rounded.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("手动记录")
            }
            Text(
                "最多选 5 张同一餐的照片。识别后可以修改菜名和分量。",
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
                    if (stagedImages.size > 1) "正在识别这餐的照片…" else "正在识别照片…",
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
                            enabled = !saving && !analyzing,
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            label = { Text("菜名") },
                            placeholder = { Text("例如：番茄炒蛋") }
                        )
                        IconButton(onClick = { onRemoveItem(index) }, enabled = !saving && !analyzing) {
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
                                "调整分量",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                if (item.grams == null) "分量待确认" else String.format(Locale.US, "%.2f份 · 约%d克", currentMultiplier, currentGrams.roundToInt()),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        Slider(
                            value = currentMultiplier,
                            enabled = !saving && !analyzing,
                            onValueChange = { newVal ->
                                val snapped = (newVal * 4f).roundToInt() / 4f
                                val updatedGrams = (sourceGrams * snapped).roundToInt().toDouble()
                                onGramsChange(index, updatedGrams)
                            },
                            valueRange = 0f..2f,
                            steps = 7,
                            modifier = Modifier.fillMaxWidth()
                        )

                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            listOf(
                                0.0 to "没吃",
                                0.5 to "半份",
                                1.0 to "1份",
                                1.5 to "1½份",
                                2.0 to "2份"
                            ).forEach { (stepValue, stepLabel) ->
                                TextButton(
                                    onClick = {
                                        val updatedGrams = (sourceGrams * stepValue).roundToInt().toDouble()
                                        onGramsChange(index, updatedGrams)
                                    },
                                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
                                    enabled = !saving && !analyzing,
                                    modifier = Modifier.heightIn(min = 48.dp)
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onSave,
                    enabled = !saving && !analyzing && draft.all { it.name.isNotBlank() },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(if (saving) "正在保存" else "保存这餐")
                }
                OutlinedButton(
                    onClick = onAddManualItem,
                    enabled = !saving && !analyzing
                ) {
                    Text("添加菜品")
                }
            }
            Text(
                "保存后会替换当天同一餐次的记录。手动添加的菜品没有营养估算，暂不计入营养汇总。",
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
