package uk.lunarlab.health2609.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.consume
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

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
    content: @Composable (String, Boolean) -> Unit
) {
    val currentDestination = when (selectedDestination) {
        "today", "meals", "activity" -> selectedDestination
        else -> "today"
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        // Width based rather than device based: an unfolded foldable/tablet gets a rail,
        // while split screen automatically falls back to the compact bottom dock.
        val useNavigationRail = maxWidth >= 600.dp

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = if (useNavigationRail) 104.dp else 0.dp)
        ) {
            content(currentDestination, useNavigationRail)
        }

        if (useNavigationRail) {
            FloatingStudentRail(
                selectedDestination = currentDestination,
                onDestinationChange = onDestinationChange,
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .safeDrawingPadding()
                    .padding(start = 12.dp)
            )
        } else {
            FloatingStudentDock(
                selectedDestination = currentDestination,
                onDestinationChange = onDestinationChange,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(horizontal = 12.dp, vertical = 10.dp)
            )
        }
    }
}

@Composable
private fun FloatingStudentDock(
    selectedDestination: String,
    onDestinationChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val selectedIndex = destinations.indexOfFirst { it.route == selectedDestination }
        .coerceAtLeast(0)
    val density = LocalDensity.current
    val slotWidth = 92.dp
    val dockWidth = slotWidth * destinations.size
    val slotWidthPx = with(density) { slotWidth.toPx() }

    var dragging by remember { mutableStateOf(false) }
    var dragX by remember { mutableFloatStateOf(0f) }
    var dragIndex by remember { mutableIntStateOf(selectedIndex) }

    val settledOffset by animateFloatAsState(
        targetValue = selectedIndex * slotWidthPx,
        animationSpec = spring(
            dampingRatio = 0.82f,
            stiffness = 520f
        ),
        label = "dock-indicator"
    )
    val indicatorOffset = if (dragging) {
        (dragX - slotWidthPx / 2f)
            .coerceIn(0f, slotWidthPx * (destinations.size - 1))
    } else {
        settledOffset
    }
    val visualIndex = if (dragging) dragIndex else selectedIndex

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(38.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 8.dp,
        shadowElevation = 8.dp
    ) {
        Box(
            modifier = Modifier
                .padding(6.dp)
                .width(dockWidth)
                .height(58.dp)
                .pointerInput(onDestinationChange) {
                    fun updateDrag(position: Offset) {
                        dragX = position.x.coerceIn(0f, size.width.toFloat())
                        dragIndex = (
                            dragX / (size.width.toFloat() / destinations.size)
                        ).toInt().coerceIn(destinations.indices)
                    }

                    detectHorizontalDragGestures(
                        onDragStart = { position ->
                            dragging = true
                            updateDrag(position)
                        },
                        onHorizontalDrag = { change, amount ->
                            change.consume()
                            updateDrag(
                                Offset(
                                    x = dragX + amount,
                                    y = change.position.y
                                )
                            )
                        },
                        onDragCancel = {
                            dragging = false
                            dragIndex = selectedIndex
                        },
                        onDragEnd = {
                            val destination = destinations[dragIndex].route
                            dragging = false
                            onDestinationChange(destination)
                        }
                    )
                }
        ) {
            Surface(
                modifier = Modifier
                    .width(slotWidth)
                    .fillMaxHeight()
                    .offsetPx(x = indicatorOffset, y = 0f),
                shape = RoundedCornerShape(29.dp),
                color = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
            ) {}

            Row(
                modifier = Modifier.fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                destinations.forEachIndexed { index, destination ->
                    DockItem(
                        destination = destination,
                        selected = index == visualIndex,
                        modifier = Modifier
                            .width(slotWidth)
                            .fillMaxHeight(),
                        onClick = { onDestinationChange(destination.route) }
                    )
                }
            }
        }
    }
}

@Composable
private fun DockItem(
    destination: StudentDestination,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Row(
        modifier = modifier.clickable(onClick = onClick).padding(horizontal = 10.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = destination.icon,
            contentDescription = destination.label,
            modifier = Modifier.size(21.dp),
            tint = if (selected) {
                MaterialTheme.colorScheme.onSecondaryContainer
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            }
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = destination.label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            color = if (selected) {
                MaterialTheme.colorScheme.onSecondaryContainer
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            }
        )
    }
}

@Composable
private fun FloatingStudentRail(
    selectedDestination: String,
    onDestinationChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val selectedIndex = destinations.indexOfFirst { it.route == selectedDestination }
        .coerceAtLeast(0)
    val density = LocalDensity.current
    val slotHeight = 74.dp
    val railHeight = slotHeight * destinations.size
    val slotHeightPx = with(density) { slotHeight.toPx() }

    var dragging by remember { mutableStateOf(false) }
    var dragY by remember { mutableFloatStateOf(0f) }
    var dragIndex by remember { mutableIntStateOf(selectedIndex) }

    val settledOffset by animateFloatAsState(
        targetValue = selectedIndex * slotHeightPx,
        animationSpec = spring(
            dampingRatio = 0.82f,
            stiffness = 520f
        ),
        label = "rail-indicator"
    )
    val indicatorOffset = if (dragging) {
        (dragY - slotHeightPx / 2f)
            .coerceIn(0f, slotHeightPx * (destinations.size - 1))
    } else {
        settledOffset
    }
    val visualIndex = if (dragging) dragIndex else selectedIndex

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(38.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 8.dp,
        shadowElevation = 8.dp
    ) {
        Box(
            modifier = Modifier
                .padding(6.dp)
                .width(76.dp)
                .height(railHeight)
                .pointerInput(onDestinationChange) {
                    fun updateDrag(position: Offset) {
                        dragY = position.y.coerceIn(0f, size.height.toFloat())
                        dragIndex = (
                            dragY / (size.height.toFloat() / destinations.size)
                        ).toInt().coerceIn(destinations.indices)
                    }

                    detectVerticalDragGestures(
                        onDragStart = { position ->
                            dragging = true
                            updateDrag(position)
                        },
                        onVerticalDrag = { change, amount ->
                            change.consume()
                            updateDrag(
                                Offset(
                                    x = change.position.x,
                                    y = dragY + amount
                                )
                            )
                        },
                        onDragCancel = {
                            dragging = false
                            dragIndex = selectedIndex
                        },
                        onDragEnd = {
                            val destination = destinations[dragIndex].route
                            dragging = false
                            onDestinationChange(destination)
                        }
                    )
                }
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(slotHeight)
                    .offsetPx(x = 0f, y = indicatorOffset),
                shape = RoundedCornerShape(30.dp),
                color = MaterialTheme.colorScheme.secondaryContainer
            ) {}

            Column(modifier = Modifier.fillMaxSize()) {
                destinations.forEachIndexed { index, destination ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(slotHeight)
                            .clickable { onDestinationChange(destination.route) },
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        val selected = index == visualIndex
                        Icon(
                            imageVector = destination.icon,
                            contentDescription = destination.label,
                            modifier = Modifier.size(23.dp),
                            tint = if (selected) {
                                MaterialTheme.colorScheme.onSecondaryContainer
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                        Spacer(Modifier.height(3.dp))
                        Text(
                            text = destination.label,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                            color = if (selected) {
                                MaterialTheme.colorScheme.onSecondaryContainer
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }
                }
            }
        }
    }
}

private fun Modifier.offsetPx(x: Float, y: Float): Modifier =
    this.then(
        Modifier
            .padding(0.dp)
            .run {
                androidx.compose.ui.layout.layout { measurable, constraints ->
                    val placeable = measurable.measure(constraints)
                    layout(placeable.width, placeable.height) {
                        placeable.placeRelative(
                            x.roundToInt(),
                            y.roundToInt()
                        )
                    }
                }
            }
    )
