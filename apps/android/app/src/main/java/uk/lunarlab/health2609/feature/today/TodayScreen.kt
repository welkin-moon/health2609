@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package uk.lunarlab.health2609.feature.today

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DirectionsRun
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt
import uk.lunarlab.health2609.core.network.DailySummaryDto
import uk.lunarlab.health2609.core.network.DishDto
import uk.lunarlab.health2609.core.network.SchoolActivityDto
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
    appearanceMode: AppearanceMode,
    dynamicColor: Boolean,
    onAppearanceModeChange: (AppearanceMode) -> Unit,
    onDynamicColorChange: (Boolean) -> Unit,
    onPortionChange: (String, Double) -> Unit,
    onGramsChange: (String, Double?) -> Unit,
    onRefresh: () -> Unit,
    onSaveMeal: () -> Unit,
    onTakeHomeMealPhoto: () -> Unit,
    onPickHomeMealImage: () -> Unit,
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

    var showAppearanceSheet by rememberSaveable { mutableStateOf(false) }

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
                    IconButton(onClick = { showAppearanceSheet = true }) {
                        Icon(Icons.Rounded.Palette, contentDescription = "外观")
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

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(innerPadding),
            contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 8.dp, bottom = 126.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            state.message?.let { message -> item { StatusMessage(message) } }

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
                            draft = state.homeMealDraft,
                            notes = state.homeMealNotes,
                            analyzing = state.analyzingHomeMeal,
                            saving = state.savingHomeMeal,
                            onTakePhoto = onTakeHomeMealPhoto,
                            onPickImage = onPickHomeMealImage,
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
                        ActivityPanel(
                            summary = state.summary,
                            schoolActivity = state.schoolActivity,
                            syncing = state.syncingPhoneActivity,
                            syncingSchoolActivity = state.syncingSchoolActivity,
                            initialType = state.manualActivityType,
                            minutes = state.manualActivityMinutes,
                            intensity = state.manualActivityIntensity,
                            saving = state.savingActivity,
                            onSync = onSyncPhoneActivity,
                            onUseSchoolActivity = onUseSchoolActivity,
                            onUseWearableSchoolActivity = onUseWearableSchoolActivity,
                            onMinutesChange = onActivityMinutesChange,
                            onIntensityChange = onActivityIntensityChange,
                            onSave = onSaveActivity
                        )
                    }
                }

                else -> {
                    item {
                        TodayOverview(
                            summary = state.summary,
                            energyReferenceInput = state.energyReferenceInput,
                            savingReference = state.savingEnergyReference,
                            onEnergyReferenceChange = onEnergyReferenceChange,
                            onSaveEnergyReference = onSaveEnergyReference
                        )
                    }
                }
            }
        }
    }

    if (showAppearanceSheet) {
        ModalBottomSheet(
            onDismissRequest = { showAppearanceSheet = false }
        ) {
            AppearanceSheet(
                appearanceMode = appearanceMode,
                dynamicColor = dynamicColor,
                onAppearanceModeChange = onAppearanceModeChange,
                onDynamicColorChange = onDynamicColorChange
            )
        }
    }
}

@Composable
private fun AppearanceSheet(
    appearanceMode: AppearanceMode,
    dynamicColor: Boolean,
    onAppearanceModeChange: (AppearanceMode) -> Unit,
    onDynamicColorChange: (Boolean) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 24.dp, end = 24.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                "外观",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                "跟随系统，也可以固定浅色或深色。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
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

        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text("使用壁纸配色", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Android 12 及以上可从系统壁纸取色。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = dynamicColor,
                    onCheckedChange = onDynamicColorChange
                )
            }
        }
    }
}

@Composable
private fun TodayOverview(
    summary: DailySummaryDto?,
    energyReferenceInput: String,
    savingReference: Boolean,
    onEnergyReferenceChange: (String) -> Unit,
    onSaveEnergyReference: () -> Unit
) {
    val nutrition = summary?.nutrition
    val activity = summary?.activity
    val energy = summary?.energy
    val target = (activity?.targetMinutes ?: 120).coerceAtLeast(1)
    val totalMinutes = activity?.totalMinutes ?: 0
    val progress = (totalMinutes.toFloat() / target).coerceIn(0f, 1f)
    val macro = nutrition?.macroCompositionPercent
    var editReference by rememberSaveable { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Surface(
            shape = RoundedCornerShape(32.dp),
            color = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
        ) {
            Column(
                modifier = Modifier.padding(22.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("今天动了", style = MaterialTheme.typography.labelLarge)
                        Text(
                            totalMinutes.toString() + " 分钟",
                            style = MaterialTheme.typography.displaySmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Text(
                        if (activity?.targetReached == true) "已达标" else "目标 " + target + " 分钟",
                        style = MaterialTheme.typography.titleSmall
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
                    horizontalArrangement = Arrangement.spacedBy(18.dp)
                ) {
                    OverviewMetric(
                        Modifier.weight(1f),
                        (activity?.peMinutes ?: 0).toString() + " 分钟",
                        "学校体育"
                    )
                    OverviewMetric(
                        Modifier.weight(1f),
                        (activity?.outsideMinutes ?: 0).toString() + " 分钟",
                        "校外运动"
                    )
                }
            }
        }

        Surface(
            shape = RoundedCornerShape(30.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("今天吃了", style = MaterialTheme.typography.labelLarge)
                        Text(
                            (nutrition?.energyKcal?.roundToInt() ?: 0).toString() + " 千卡",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    energy?.referenceGapKcal?.let { gap ->
                        val label = if (gap >= 0) {
                            "距参考 " + gap.roundToInt() + " 千卡"
                        } else {
                            "超出参考 " + abs(gap).roundToInt() + " 千卡"
                        }
                        Text(
                            label,
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("供能构成", style = MaterialTheme.typography.titleSmall)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        NutritionMetric(
                            Modifier.weight(1f),
                            (macro?.protein?.roundToInt() ?: 0).toString() + "%",
                            "蛋白质"
                        )
                        NutritionMetric(
                            Modifier.weight(1f),
                            (macro?.fat?.roundToInt() ?: 0).toString() + "%",
                            "脂肪"
                        )
                        NutritionMetric(
                            Modifier.weight(1f),
                            (macro?.carbohydrate?.roundToInt() ?: 0).toString() + "%",
                            "碳水"
                        )
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
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

                TextButton(
                    onClick = { editReference = !editReference },
                    contentPadding = PaddingValues(horizontal = 0.dp, vertical = 2.dp)
                ) {
                    Icon(Icons.Rounded.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        energy?.dailyEnergyReferenceKcal?.let {
                            "每日参考 " + it + " 千卡"
                        } ?: "设置每日参考"
                    )
                }

                AnimatedVisibility(editReference) {
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
                            label = { Text("每日参考（千卡）") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                        )
                        Button(onClick = onSaveEnergyReference, enabled = !savingReference) {
                            Text(if (savingReference) "保存中" else "保存")
                        }
                    }
                }
            }
        }
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
private fun StatusMessage(message: String) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
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
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.animateContentSize(animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec())
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(dish.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                val details = buildList {
                    dish.standardServingGrams?.let { add("一份约 " + it.roundToInt().toString() + " 克") }
                    dish.nutritionPerServing?.energyKcal?.let { add(it.roundToInt().toString() + " 千卡") }
                }
                if (details.isNotEmpty()) {
                    Text(
                        details.joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                portionOptions.forEach { (portion, label) ->
                    FilterChip(
                        selected = abs(amount.servingMultiplier - portion) < 0.001,
                        onClick = { onPortionChange(portion) },
                        label = { Text(label) }
                    )
                }
            }

            var showExactGrams by rememberSaveable(dish.id) { mutableStateOf(false) }
            TextButton(
                onClick = { showExactGrams = !showExactGrams },
                contentPadding = PaddingValues(horizontal = 0.dp, vertical = 0.dp)
            ) {
                Text(if (showExactGrams) "收起精确克数" else "精确填写克数")
            }
            AnimatedVisibility(showExactGrams) {
                OutlinedTextField(
                    value = amount.consumedGrams?.let { if (it == 0.0) "0" else it.roundToInt().toString() } ?: "",
                    onValueChange = { onGramsChange(it.toDoubleOrNull()) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("实际克数") },
                    suffix = { Text("克") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
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
    draft: List<HomeMealDraftItem>,
    notes: List<String>,
    analyzing: Boolean,
    saving: Boolean,
    onTakePhoto: () -> Unit,
    onPickImage: () -> Unit,
    onSlotChange: (String) -> Unit,
    onNameChange: (Int, String) -> Unit,
    onGramsChange: (Int, Double?) -> Unit,
    onRemoveItem: (Int) -> Unit,
    onSave: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
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
                Text(if (analyzing) "正在识别…" else "拍照")
            }
            FilledTonalButton(
                onClick = onPickImage,
                enabled = !analyzing && !saving,
                modifier = Modifier.weight(1f)
            ) {
                Text("从相册选择")
            }
        }

        if (analyzing) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                LoadingIndicator(modifier = Modifier.size(24.dp))
                Text("正在整理食物和分量", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        notes.firstOrNull()?.let { note ->
            Text(note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        draft.forEachIndexed { index, item ->
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
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
                            label = { Text("食物") }
                        )
                        IconButton(onClick = { onRemoveItem(index) }) {
                            Icon(Icons.Rounded.Close, contentDescription = "移除")
                        }
                    }
                    OutlinedTextField(
                        value = item.grams?.roundToInt()?.toString() ?: "",
                        onValueChange = { onGramsChange(index, it.toDoubleOrNull()) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text("分量") },
                        suffix = { Text("克") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )

                    item.nutritionAtSource?.let { nutrition ->
                        val factor = if (
                            item.sourceGrams != null &&
                            item.sourceGrams > 0.0 &&
                            item.grams != null
                        ) {
                            (item.grams / item.sourceGrams).coerceIn(0.0, 10.0)
                        } else {
                            1.0
                        }

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
private fun ActivityPanel(
    summary: DailySummaryDto?,
    schoolActivity: SchoolActivityDto?,
    syncing: Boolean,
    syncingSchoolActivity: Boolean,
    initialType: String = "自主运动",
    minutes: Int,
    intensity: String,
    saving: Boolean,
    onSync: () -> Unit,
    onUseSchoolActivity: () -> Unit,
    onUseWearableSchoolActivity: () -> Unit,
    onMinutesChange: (Int) -> Unit,
    onIntensityChange: (String) -> Unit,
    onSave: (String) -> Unit
) {
    val activity = summary?.activity
    var showManual by rememberSaveable { mutableStateOf(false) }
    var activityType by rememberSaveable(initialType) { mutableStateOf(initialType) }

    Surface(
        shape = RoundedCornerShape(26.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
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
                FilledTonalButton(onClick = onSync, enabled = !syncing) {
                    Text(if (syncing) "正在更新" else "更新手机里的运动")
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

            Surface(
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(
                            "学校体育",
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

            TextButton(
                onClick = { showManual = !showManual },
                contentPadding = PaddingValues(horizontal = 0.dp, vertical = 4.dp)
            ) {
                Icon(Icons.Rounded.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(if (showManual) "收起补记" else "补记一次运动")
            }

            AnimatedVisibility(showManual) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
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
                        enabled = !saving
                    ) {
                        Text(if (saving) "正在保存" else "保存这次运动")
                    }
                }
            }
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
