package uk.lunarlab.health2609.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarToday
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

private data class StudentDestination(
    val route: String,
    val label: String
)

private val destinations = listOf(
    StudentDestination("today", "今天"),
    StudentDestination("data", "数据")
)

@Composable
fun StudentAppShell(
    selectedDestination: String,
    onDestinationChange: (String) -> Unit,
    todayContent: @Composable () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            when (selectedDestination) {
                "data" -> DataNotesScreen()
                else -> todayContent()
            }
        }

        NavigationBar {
            destinations.forEach { destination ->
                NavigationBarItem(
                    selected = selectedDestination == destination.route,
                    onClick = { onDestinationChange(destination.route) },
                    icon = {
                        Icon(
                            imageVector = when (destination.route) {
                                "data" -> Icons.Rounded.Security
                                else -> Icons.Rounded.CalendarToday
                            },
                            contentDescription = destination.label
                        )
                    },
                    label = { Text(destination.label) }
                )
            }
        }
    }
}

@Composable
private fun DataNotesScreen() {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text(
                text = "数据与隐私",
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.SemiBold
            )
        }

        item {
            Text(
                text = "这个 Demo 只上传完成健康汇总所需的数据，不上传 Health Connect 的原始轨迹。",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyLarge
            )
        }

        item {
            DataNoteCard(
                title = "校内体育",
                body = "来自学校课程表和管理员确认的实际活动分钟，不用手机运动记录反推。"
            )
        }

        item {
            DataNoteCard(
                title = "校外运动",
                body = "手机本地读取 Health Connect，并先排除管理员配置的在校时段，再上传当天汇总。"
            )
        }

        item {
            DataNoteCard(
                title = "家庭餐照片",
                body = "图片仅转发给 AGY 做结构化识别；学生确认后只保存食品、份量与营养结果。"
            )
        }
    }
}

@Composable
private fun DataNoteCard(
    title: String,
    body: String
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        ),
        shape = MaterialTheme.shapes.largeIncreased
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = body,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}
