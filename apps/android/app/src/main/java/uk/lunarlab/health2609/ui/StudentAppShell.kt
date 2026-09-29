package uk.lunarlab.health2609.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarToday
import androidx.compose.material.icons.rounded.DirectionsRun
import androidx.compose.material.icons.rounded.Restaurant
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
    StudentDestination("meals", "饮食", Icons.Rounded.Restaurant),
    StudentDestination("activity", "运动", Icons.Rounded.DirectionsRun)
)

@Composable
fun StudentAppShell(
    selectedDestination: String,
    onDestinationChange: (String) -> Unit,
    content: @Composable (String) -> Unit
) {
    val currentDestination = when (selectedDestination) {
        "today", "meals", "activity" -> selectedDestination
        else -> "today"
    }

    Box(modifier = Modifier.fillMaxSize()) {
        content(currentDestination)

        FloatingStudentDock(
            selectedDestination = currentDestination,
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
