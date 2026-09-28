package uk.lunarlab.health2609.feature.today

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
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
    val carbohydrateG: Double = 0.0
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodayScreen(
    state: TodayUiState,
    onPortionChange: (String, Double) -> Unit,
    onRefresh: () -> Unit,
    onSave: () -> Unit
) {
    val totals = remember(state.menu, state.portions) {
        state.menu?.dishes.orEmpty().fold(NutritionTotals()) { acc, dish ->
            val portion = state.portions[dish.id] ?: 0.0
            val nutrition = dish.nutritionPerServing
            NutritionTotals(
                energyKcal = acc.energyKcal + (nutrition?.energyKcal ?: 0.0) * portion,
                proteinG = acc.proteinG + (nutrition?.proteinG ?: 0.0) * portion,
                fatG = acc.fatG + (nutrition?.fatG ?: 0.0) * portion,
                carbohydrateG = acc.carbohydrateG + (nutrition?.carbohydrateG ?: 0.0) * portion
            )
        }
    }

    val prettyDate = remember(state.date) {
        runCatching {
            LocalDate.parse(state.date).format(
                DateTimeFormatter.ofPattern("M月d日 EEEE", Locale.SIMPLIFIED_CHINESE)
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
        when {
            state.loading && state.menu == null -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(14.dp))
                    Text("正在读取今天的菜单")
                }
            }

            else -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        start = 18.dp,
                        end = 18.dp,
                        top = 8.dp,
                        bottom = 32.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    item {
                        TodaySummaryCard(
                            totals = totals,
                            selectedCount = state.portions.values.count { it > 0.0 },
                            totalCount = state.menu?.dishes?.size ?: 0
                        )
                    }

                    state.message?.let { message ->
                        item {
                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.secondaryContainer
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
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                Icons.Rounded.Restaurant,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Column {
                                Text(
                                    "今天午餐",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    "按你实际吃下的份量记录",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
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
                                selectedPortion = state.portions[dish.id] ?: 0.0,
                                onPortionChange = { onPortionChange(dish.id, it) }
                            )
                        }

                        item {
                            Button(
                                onClick = onSave,
                                enabled = !state.saving,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(54.dp)
                            ) {
                                Text(if (state.saving) "保存中…" else "记录今天的午餐")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TodaySummaryCard(
    totals: NutritionTotals,
    selectedCount: Int,
    totalCount: Int
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Column {
                Text(
                    "今日记录",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Text(
                    "$selectedCount / $totalCount 道午餐菜品",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }

            HorizontalDivider(
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.16f)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Metric("${totals.energyKcal.toInt()}", "kcal")
                Metric(String.format(Locale.US, "%.1f", totals.proteinG), "蛋白质 g")
                Metric(String.format(Locale.US, "%.1f", totals.carbohydrateG), "碳水 g")
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
    selectedPortion: Double,
    onPortionChange: (Double) -> Unit
) {
    Card {
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
                    val grams = dish.standardServingGrams
                    if (grams != null) {
                        Text(
                            "标准份 ${grams.toInt()} g",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                dish.nutritionPerServing?.energyKcal?.let { energy ->
                    Text(
                        "${energy.toInt()} kcal / 份",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                portionOptions.forEach { (portion, label) ->
                    FilterChip(
                        selected = selectedPortion == portion,
                        onClick = { onPortionChange(portion) },
                        label = { Text(label) }
                    )
                }
            }
        }
    }
}
