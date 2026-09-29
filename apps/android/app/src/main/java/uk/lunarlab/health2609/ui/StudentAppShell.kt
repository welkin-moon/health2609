package uk.lunarlab.health2609.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarToday
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

private data class StudentDestination(
    val route: String,
    val label: String,
    val icon: ImageVector
)

private val destinations = listOf(
    StudentDestination("today", "今天", Icons.Rounded.CalendarToday),
    StudentDestination("data", "数据", Icons.Rounded.Security)
)

@Composable
fun StudentAppShell(
    selectedDestination: String,
    onDestinationChange: (String) -> Unit,
    todayContent: @Composable () -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {
        when (selectedDestination) {
            "data" -> DataNotesScreen()
            else -> todayContent()
        }

        FloatingStudentDock(
            selectedDestination = selectedDestination,
            onDestinationChange = onDestinationChange,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 10.dp)
        )
    }
}

@Composable
private fun FloatingStudentDock(
    selectedDestination: String,
    onDestinationChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(34.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 8.dp,
        shadowElevation = 8.dp
    ) {
        Row(
            modifier = Modifier.padding(6.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            destinations.forEach { destination ->
                val selected = destination.route == selectedDestination
                Surface(
                    onClick = { onDestinationChange(destination.route) },
                    shape = RoundedCornerShape(28.dp),
                    color = if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
                    contentColor = if (selected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.animateContentSize(
                        animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec()
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(
                            horizontal = if (selected) 18.dp else 14.dp,
                            vertical = 11.dp
                        ),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = destination.icon,
                            contentDescription = destination.label,
                            modifier = Modifier.size(22.dp)
                        )
                        if (selected) {
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = destination.label,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DataNotesScreen() {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 20.dp,
            end = 20.dp,
            top = 30.dp,
            bottom = 120.dp
        ),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "数据与隐私",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "只收集完成每日记录需要的信息。",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        }

        item {
            PrivacySection(
                icon = Icons.Rounded.CalendarToday,
                title = "校内体育",
                body = "按学校课程和管理员确认的实际活动时间记录，不用手机数据猜测。",
                detail = "这样能避免把课间走动误算成体育课。"
            )
        }

        item {
            PrivacySection(
                icon = Icons.Rounded.CalendarToday,
                title = "校外运动",
                body = "从手机健康数据读取当天活动，并自动排除学校设置的在校时段。",
                detail = "Android 设备通过 Health Connect 读取汇总数据；不会上传运动轨迹。"
            )
        }

        item {
            PrivacySection(
                icon = Icons.Rounded.Security,
                title = "餐食照片",
                body = "照片只用于识别食物。保存前由你确认名称和分量。",
                detail = "最终保存的是食物、分量和营养估算，不保存原始照片。"
            )
        }
    }
}

@Composable
private fun PrivacySection(
    icon: ImageVector,
    title: String,
    body: String,
    detail: String
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.Top
    ) {
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.secondaryContainer
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.padding(12.dp),
                tint = MaterialTheme.colorScheme.onSecondaryContainer
            )
        }

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = body,
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
