@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package uk.lunarlab.health2609.feature.today

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DirectionsRun
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.SsidChart
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
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

private val portionOptions = listOf(
    0.0 to "没吃",
    0.25 to "1/4",
    0.5 to "1/2",
    0.75 to "3/4",
    1.0 to "1份"
)

private data class NutritionTotals(
    val energyKcal: Double = 0.0,
    val proteinG: Double = 0.0,
    val fatG: Double = 0.0,
    val carbohydrateG: Double = 0.0,
    val fiberG: Double = 0.0,
    val sodiumMg: Double = 0.0
)

@OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalMaterial3ExpressiveApi::class
)
@Composable
fun TodayScreen(
    state: TodayUiState,
    onPortionChange: (String, Double) -> Unit,
    onGramsChange: (String, Double?) -> Unit,
    onRefresh: () -> Unit,
    onSaveMeal: () -> Unit,
    onPickHomeMealImage: () -> Unit,
    onHomeMealSlotChange: (String) -> Unit,
    onHomeMealNameChange: (Int, String) -> Unit,
    onHomeMealGramsChange: (Int, Double?) -> Unit,
    onRemoveHomeMealItem: (Int) -> Unit,
    onSaveHomeMeal: () -> Unit,
    onActivityMinutesChange: (Int) -> Unit,
    onActivityIntensityChange: (String) -> Unit,
    onSaveActivity: () -> Unit,
    onSyncPhoneActivity: () -> Unit,
    onEnergyReferenceChange: (String) -> Unit,
    onSaveEnergyReference: () -> Unit
) {
    val lunchPreview = remember(state.menu, state.amounts) {
        state.menu?.dishes.orEmpty().fold(NutritionTotals()) { acc, dish ->
            val amount = state.amounts[dish.id] ?: DishAmount()
            val portion = amount.servingMultiplier
            val nutrition = dish.nutritionPerServing
            NutritionTotals(
                energyKcal = acc.energyKcal + (nutrition?.energyKcal ?: 0.0) * portion,
                proteinG = acc.proteinG + (nutrition?.proteinG ?: 0.0) * portion,
                fatG = acc.fatG + (nutrition?.fatG ?: 0.0) * portion,
                carbohydrateG =
                    acc.carbohydrateG + (nutrition?.carbohydrateG ?: 0.0) * portion,
                fiberG = acc.fiberG + (nutrition?.fiberG ?: 0.0) * portion,
                sodiumMg = acc.sodiumMg + (nutrition?.sodiumMg ?: 0.0) * portion
            )
        }
    }

    val prettyDate = remember(state.date) {
        runCatching {
            LocalDate.parse(state.date).format(
                DateTimeFormatter.ofPattern(
                    "M月d日 EEEE",
                    Locale.SIMPLIFIED_CHINESE
                )
            )
        }.getOrDefault(state.date)
    }

    Scaffold(
        topBar = {
            LargeTopAppBar(
                title = {
                    Column {
                        Text(
                            text = "今天",
                            style = MaterialTheme.typography.headlineLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = prettyDate,
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onRefresh, enabled = !state.loading) {
                        Icon(Icons.Rounded.Refresh, contentDescription = "刷新")
                    }
                }
            )
        }
    ) { innerPadding ->
        if (state.loading && state.menu == null) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                LoadingIndicator()
                Spacer(Modifier.height(14.dp))
                Text("正在读取今天的数据")
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(
                start = 18.dp,
                end = 18.dp,
                top = 8.dp,
                bottom = 36.dp
            ),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                DailyOverviewCard(
                    summary = state.summary,
                    energyReferenceInput = state.energyReferenceInput,
                    savingReference = state.savingEnergyReference,
                    onEnergyReferenceChange = onEnergyReferenceChange,
                    onSaveEnergyReference = onSaveEnergyReference
                )
            }

            state.message?.let { message ->
                item {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer
                        ),
                        modifier = Modifier.animateContentSize(
                            animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec()
                        )
                    ) {
                        Text(
                            text = message,
                            modifier = Modifier.padding(16.dp),
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
            }

            item {
                SectionTitle(
                    icon = {
                        Icon(
                            Icons.Rounded.Restaurant,
                            contentDescription = null
                        )
                    },
                    title = "今天午餐",
                    subtitle = "可以按份量快速选，也可以直接改成实际吃下的克数"
                )
            }

            val dishes = state.menu?.dishes.orEmpty()
            if (dishes.isEmpty()) {
                item {
                    Card {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                "管理员还没有录入今天的午餐",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                "菜单保存后，这里会自动出现菜品。",
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                items(dishes, key = { it.id }) { dish ->
                    DishCard(
                        dish = dish,
                        amount = state.amounts[dish.id] ?: DishAmount(),
                        onPortionChange = { onPortionChange(dish.id, it) },
                        onGramsChange = { onGramsChange(dish.id, it) }
                    )
                }

                item {
                    LunchPreviewCard(lunchPreview)
                }

                item {
                    Button(
                        onClick = onSaveMeal,
                        enabled = !state.savingMeal,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(58.dp)
                    ) {
                        Text(
                            if (state.savingMeal) "保存中…"
                            else "记录今天的午餐"
                        )
                    }
                }
            }

            item {
                SectionTitle(
                    icon = {
                        Icon(
                            Icons.Rounded.PhotoCamera,
                            contentDescription = null
                        )
                    },
                    title = "在家吃的",
                    subtitle = "图片直接交给 AGY 识别，不在云端保存原图"
                )
            }

            item {
                HomeMealCard(
                    slot = state.homeMealSlot,
                    draft = state.homeMealDraft,
                    notes = state.homeMealNotes,
                    analyzing = state.analyzingHomeMeal,
                    saving = state.savingHomeMeal,
                    onPickImage = onPickHomeMealImage,
                    onSlotChange = onHomeMealSlotChange,
                    onNameChange = onHomeMealNameChange,
                    onGramsChange = onHomeMealGramsChange,
                    onRemoveItem = onRemoveHomeMealItem,
                    onSave = onSaveHomeMeal
                )
            }

            item {
                SectionTitle(
                    icon = {
                        Icon(
                            Icons.Rounded.DirectionsRun,
                            contentDescription = null
                        )
                    },
                    title = "补记运动",
                    subtitle = "手机数据不足时，可以自己补充当天运动时间和强度"
                )
            }

            item {
                PhoneActivityCard(
                    summary = state.summary,
                    syncing = state.syncingPhoneActivity,
                    onSync = onSyncPhoneActivity
                )
            }

            item {
                ManualActivityCard(
                    minutes = state.manualActivityMinutes,
                    intensity = state.manualActivityIntensity,
                    saving = state.savingActivity,
                    onMinutesChange = onActivityMinutesChange,
                    onIntensityChange = onActivityIntensityChange,
                    onSave = onSaveActivity
                )
            }
        }
    }
}

@Composable
private fun DailyOverviewCard(
    summary: DailySummaryDto?,
    energyReferenceInput: String,
    savingReference: Boolean,
    onEnergyReferenceChange: (String) -> Unit,
    onSaveEnergyReference: () -> Unit
) {
    val nutrition = summary?.nutrition
    val activity = summary?.activity
    val energy = summary?.energy

    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        ),
        modifier = Modifier.animateContentSize(
            animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec()
        )
    ) {
        Column(
            modifier = Modifier.padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Column {
                Text(
                    "今日总览",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Text(
                    "${activity?.totalMinutes ?: 0} / ${activity?.targetMinutes ?: 120} 分钟运动",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }

            LinearProgressIndicator(
                progress = {
                    val total = activity?.totalMinutes ?: 0
                    val target = (activity?.targetMinutes ?: 120).coerceAtLeast(1)
                    (total.toFloat() / target).coerceIn(0f, 1f)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(10.dp)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Metric(
                    value = "${nutrition?.energyKcal?.roundToInt() ?: 0}",
                    label = "已记录 kcal"
                )
                Metric(
                    value = "${activity?.peMinutes ?: 0}",
                    label = "体育课 min"
                )
                Metric(
                    value = "${activity?.outsideMinutes ?: 0}",
                    label = "校外 min"
                )
            }

            HorizontalDivider(
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(
                    alpha = 0.16f
                )
            )

            Text(
                "营养构成",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )

            MacroRow(
                label = "蛋白质",
                grams = nutrition?.proteinG ?: 0.0,
                percent = nutrition?.macroCompositionPercent?.protein ?: 0.0
            )
            MacroRow(
                label = "脂肪",
                grams = nutrition?.fatG ?: 0.0,
                percent = nutrition?.macroCompositionPercent?.fat ?: 0.0
            )
            MacroRow(
                label = "碳水",
                grams = nutrition?.carbohydrateG ?: 0.0,
                percent = nutrition?.macroCompositionPercent?.carbohydrate ?: 0.0
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                SmallMetric(
                    modifier = Modifier.weight(1f),
                    value = String.format(
                        Locale.US,
                        "%.1f g",
                        nutrition?.fiberG ?: 0.0
                    ),
                    label = "膳食纤维"
                )
                SmallMetric(
                    modifier = Modifier.weight(1f),
                    value = "${(nutrition?.sodiumMg ?: 0.0).roundToInt()} mg",
                    label = "钠"
                )
            }

            HorizontalDivider(
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(
                    alpha = 0.16f
                )
            )

            val gap = energy?.referenceGapKcal
            if (gap != null) {
                val wording = if (gap >= 0) {
                    "距你的参考能量还差 ${abs(gap).roundToInt()} kcal"
                } else {
                    "已比你的参考能量高 ${abs(gap).roundToInt()} kcal"
                }
                Text(
                    wording,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    "这里只表示已记录摄入与个人参考值的差，不把它当作减重目标。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Text(
                    "设置一个个人参考能量后，可以显示当天的能量参考差。",
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = energyReferenceInput,
                    onValueChange = onEnergyReferenceChange,
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    label = { Text("参考能量 / kcal") },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number
                    )
                )
                FilledTonalButton(
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
private fun MacroRow(
    label: String,
    grams: Double,
    percent: Double
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            Text(
                String.format(Locale.US, "%.1f g · %.0f%%", grams, percent),
                style = MaterialTheme.typography.labelLarge
            )
        }
        LinearProgressIndicator(
            progress = { (percent / 100.0).toFloat().coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun SmallMetric(
    modifier: Modifier,
    value: String,
    label: String
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f)
        )
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun LunchPreviewCard(totals: NutritionTotals) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                "本次午餐预览",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Metric("${totals.energyKcal.roundToInt()}", "kcal")
                Metric(
                    String.format(Locale.US, "%.1f", totals.proteinG),
                    "蛋白质 g"
                )
                Metric(
                    String.format(Locale.US, "%.1f", totals.carbohydrateG),
                    "碳水 g"
                )
            }
        }
    }
}

@Composable
private fun Metric(
    value: String,
    label: String
) {
    Column {
        Text(
            value,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun DishCard(
    dish: DishDto,
    amount: DishAmount,
    onPortionChange: (Double) -> Unit,
    onGramsChange: (Double?) -> Unit
) {
    Card(
        modifier = Modifier.animateContentSize(
            animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec()
        )
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        dish.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    dish.standardServingGrams?.let { grams ->
                        Text(
                            "标准份 ${grams.roundToInt()} g",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                dish.nutritionPerServing?.energyKcal?.let { energy ->
                    Text(
                        "${energy.roundToInt()} kcal / 份",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                portionOptions.forEach { (portion, label) ->
                    FilterChip(
                        selected = abs(amount.servingMultiplier - portion) < 0.001,
                        onClick = { onPortionChange(portion) },
                        label = { Text(label) }
                    )
                }
            }

            OutlinedTextField(
                value = amount.consumedGrams
                    ?.takeIf { it > 0.0 }
                    ?.roundToInt()
                    ?.toString()
                    ?: "",
                onValueChange = { raw ->
                    onGramsChange(raw.toDoubleOrNull())
                },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("实际吃下 / g") },
                supportingText = {
                    Text(
                        if (dish.standardServingGrams != null) {
                            "会自动换算为 ${String.format(Locale.US, "%.2f", amount.servingMultiplier)} 份"
                        } else {
                            "该菜没有标准份，请优先使用份量选择"
                        }
                    )
                },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number
                )
            )
        }
    }
}

@Composable
private fun HomeMealCard(
    slot: String,
    draft: List<HomeMealDraftItem>,
    notes: List<String>,
    analyzing: Boolean,
    saving: Boolean,
    onPickImage: () -> Unit,
    onSlotChange: (String) -> Unit,
    onNameChange: (Int, String) -> Unit,
    onGramsChange: (Int, Double?) -> Unit,
    onRemoveItem: (Int) -> Unit,
    onSave: () -> Unit
) {
    Card(
        modifier = Modifier.animateContentSize(
            animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec()
        )
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
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

            FilledTonalButton(
                onClick = onPickImage,
                enabled = !analyzing && !saving,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Rounded.PhotoCamera, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(
                    if (analyzing) "AGY 正在识别…"
                    else "选择一张家庭餐照片"
                )
            }

            if (analyzing) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    LoadingIndicator()
                }
            }

            if (notes.isNotEmpty()) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        notes.take(3).forEach { note ->
                            Text(
                                "· $note",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            draft.forEachIndexed { index, item ->
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = item.name,
                                onValueChange = { onNameChange(index, it) },
                                modifier = Modifier.weight(1f),
                                singleLine = true,
                                label = { Text("食物") }
                            )
                            IconButton(
                                onClick = { onRemoveItem(index) }
                            ) {
                                Icon(
                                    Icons.Rounded.Close,
                                    contentDescription = "删除这项"
                                )
                            }
                        }

                        OutlinedTextField(
                            value = item.grams
                                ?.roundToInt()
                                ?.toString()
                                ?: "",
                            onValueChange = {
                                onGramsChange(index, it.toDoubleOrNull())
                            },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            label = { Text("实际吃下 / g") },
                            supportingText = {
                                Text(
                                    "识别置信度 ${(item.confidence * 100).roundToInt()}% · 请按实际情况确认"
                                )
                            },
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Number
                            )
                        )

                        item.nutritionAtSource?.let { nutrition ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    "${nutrition.energyKcal?.roundToInt() ?: 0} kcal",
                                    style = MaterialTheme.typography.labelLarge
                                )
                                Text(
                                    "P ${String.format(Locale.US, "%.1f", nutrition.proteinG ?: 0.0)} g",
                                    style = MaterialTheme.typography.labelMedium
                                )
                                Text(
                                    "C ${String.format(Locale.US, "%.1f", nutrition.carbohydrateG ?: 0.0)} g",
                                    style = MaterialTheme.typography.labelMedium
                                )
                            }
                        }
                    }
                }
            }

            if (draft.isNotEmpty()) {
                Button(
                    onClick = onSave,
                    enabled = !saving && !analyzing,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        if (saving) "正在加入今天…"
                        else "确认并加入今天"
                    )
                }
            }

            Text(
                "原图只在本次请求中经 Cloudflare Tunnel 转给 AGY；保存时只写入你确认后的食物、克数和营养估算。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun PhoneActivityCard(
    summary: DailySummaryDto?,
    syncing: Boolean,
    onSync: () -> Unit
) {
    val activity = summary?.activity

    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        ),
        modifier = Modifier.animateContentSize(
            animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec()
        )
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
                Column {
                    Text(
                        "手机自动记录",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        "只统计管理员配置的在校时段之外",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
                Text(
                    "${activity?.healthConnectOutsideMinutes ?: 0} min",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                SmallMetric(
                    modifier = Modifier.weight(1f),
                    value = "${activity?.peMinutes ?: 0} min",
                    label = "校内体育"
                )
                SmallMetric(
                    modifier = Modifier.weight(1f),
                    value = activity?.activeEnergyKcal
                        ?.let { "${it.roundToInt()} kcal" }
                        ?: "—",
                    label = "校外活动能量"
                )
            }

            FilledTonalButton(
                onClick = onSync,
                enabled = !syncing,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    if (syncing) "正在同步 Health Connect…"
                    else "同步手机校外运动"
                )
            }
        }
    }
}

@Composable
private fun ManualActivityCard(
    minutes: Int,
    intensity: String,
    saving: Boolean,
    onMinutesChange: (Int) -> Unit,
    onIntensityChange: (String) -> Unit,
    onSave: () -> Unit
) {
    Card {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        "自主运动",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        "时长",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    "$minutes 分钟",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Slider(
                value = minutes.toFloat(),
                onValueChange = {
                    onMinutesChange((it / 5f).roundToInt() * 5)
                },
                valueRange = 5f..180f,
                steps = 34
            )

            Text(
                "运动强度",
                style = MaterialTheme.typography.labelLarge
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                IntensityChip(
                    label = "轻度",
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
                    label = "较高",
                    value = "vigorous",
                    selected = intensity == "vigorous",
                    onSelected = onIntensityChange
                )
            }

            Text(
                "手机已有校外运动记录时，后台用两者中的较大时长，避免把同一段运动明显重复计算。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Button(
                onClick = onSave,
                enabled = !saving,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Rounded.SsidChart, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(if (saving) "保存中…" else "加入今天的运动")
            }
        }
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

@Composable
private fun SectionTitle(
    icon: @Composable () -> Unit,
    title: String,
    subtitle: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        icon()
        Column {
            Text(
                title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
