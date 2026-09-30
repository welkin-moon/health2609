package uk.lunarlab.health2609.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
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
    val currentDestination = selectedDestination.takeIf {
        destinations.any { destination -> destination.route == it }
    } ?: "today"

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useNavigationRail = maxWidth >= 600.dp

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = if (useNavigationRail) 104.dp else 0.dp)
        ) {
            AnimatedContent(
                targetState = currentDestination,
                transitionSpec = {
                    val from = destinations.indexOfFirst { it.route == initialState }
                    val to = destinations.indexOfFirst { it.route == targetState }
                    val direction = if (to >= from) 1 else -1
                    (
                        slideInHorizontally(
                            animationSpec = tween(220),
                            initialOffsetX = { width -> direction * width / 10 }
                        ) + fadeIn(animationSpec = tween(150))
                    ).togetherWith(
                        slideOutHorizontally(
                            animationSpec = tween(180),
                            targetOffsetX = { width -> -direction * width / 12 }
                        ) + fadeOut(animationSpec = tween(120))
                    )
                },
                label = "student-destination"
            ) { destination ->
                content(destination, useNavigationRail)
            }
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
    val slotWidth = 88.dp
    val slotWidthPx = with(density) { slotWidth.toPx() }
    val maxOffsetPx = slotWidthPx * (destinations.size - 1)

    var dragging by remember { mutableStateOf(false) }
    var dragOffsetPx by remember { mutableFloatStateOf(0f) }
    var dragIndex by remember { mutableIntStateOf(selectedIndex) }

    val settledOffsetPx by animateFloatAsState(
        targetValue = selectedIndex * slotWidthPx,
        animationSpec = spring(
            dampingRatio = 0.84f,
            stiffness = 430f
        ),
        label = "dock-indicator"
    )

    val dragState = rememberDraggableState { delta ->
        dragOffsetPx = (dragOffsetPx + delta).coerceIn(0f, maxOffsetPx)
        dragIndex = (dragOffsetPx / slotWidthPx)
            .roundToInt()
            .coerceIn(destinations.indices)
    }

    val indicatorOffsetPx = if (dragging) dragOffsetPx else settledOffsetPx
    val visualIndex = if (dragging) dragIndex else selectedIndex

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(36.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 5.dp,
        shadowElevation = 5.dp
    ) {
        Box(
            modifier = Modifier
                .padding(5.dp)
                .width(slotWidth * destinations.size)
                .height(56.dp)
                .draggable(
                    state = dragState,
                    orientation = Orientation.Horizontal,
                    startDragImmediately = false,
                    onDragStarted = {
                        dragging = true
                        dragOffsetPx = settledOffsetPx
                        dragIndex = selectedIndex
                    },
                    onDragStopped = { velocity ->
                        val projected = (dragOffsetPx + velocity * 0.035f)
                            .coerceIn(0f, maxOffsetPx)
                        val targetIndex = (projected / slotWidthPx)
                            .roundToInt()
                            .coerceIn(destinations.indices)
                        dragging = false
                        dragIndex = targetIndex
                        if (targetIndex != selectedIndex) {
                            onDestinationChange(destinations[targetIndex].route)
                        }
                    }
                )
        ) {
            Surface(
                modifier = Modifier
                    .width(slotWidth)
                    .fillMaxHeight()
                    .offset { IntOffset(indicatorOffsetPx.roundToInt(), 0) },
                shape = RoundedCornerShape(28.dp),
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
                        onClick = {
                            if (!dragging) onDestinationChange(destination.route)
                        }
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
        modifier = modifier
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = destination.icon,
            contentDescription = destination.label,
            modifier = Modifier.size(20.dp),
            tint = if (selected) {
                MaterialTheme.colorScheme.onSecondaryContainer
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            }
        )
        Spacer(Modifier.width(5.dp))
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
    val slotHeight = 70.dp
    val slotHeightPx = with(density) { slotHeight.toPx() }
    val maxOffsetPx = slotHeightPx * (destinations.size - 1)

    var dragging by remember { mutableStateOf(false) }
    var dragOffsetPx by remember { mutableFloatStateOf(0f) }
    var dragIndex by remember { mutableIntStateOf(selectedIndex) }

    val settledOffsetPx by animateFloatAsState(
        targetValue = selectedIndex * slotHeightPx,
        animationSpec = spring(
            dampingRatio = 0.84f,
            stiffness = 430f
        ),
        label = "rail-indicator"
    )

    val dragState = rememberDraggableState { delta ->
        dragOffsetPx = (dragOffsetPx + delta).coerceIn(0f, maxOffsetPx)
        dragIndex = (dragOffsetPx / slotHeightPx)
            .roundToInt()
            .coerceIn(destinations.indices)
    }

    val indicatorOffsetPx = if (dragging) dragOffsetPx else settledOffsetPx
    val visualIndex = if (dragging) dragIndex else selectedIndex

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(36.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 5.dp,
        shadowElevation = 5.dp
    ) {
        Box(
            modifier = Modifier
                .padding(5.dp)
                .width(74.dp)
                .height(slotHeight * destinations.size)
                .draggable(
                    state = dragState,
                    orientation = Orientation.Vertical,
                    startDragImmediately = false,
                    onDragStarted = {
                        dragging = true
                        dragOffsetPx = settledOffsetPx
                        dragIndex = selectedIndex
                    },
                    onDragStopped = { velocity ->
                        val projected = (dragOffsetPx + velocity * 0.035f)
                            .coerceIn(0f, maxOffsetPx)
                        val targetIndex = (projected / slotHeightPx)
                            .roundToInt()
                            .coerceIn(destinations.indices)
                        dragging = false
                        dragIndex = targetIndex
                        if (targetIndex != selectedIndex) {
                            onDestinationChange(destinations[targetIndex].route)
                        }
                    }
                )
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(slotHeight)
                    .offset { IntOffset(0, indicatorOffsetPx.roundToInt()) },
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.secondaryContainer
            ) {}

            Column(modifier = Modifier.fillMaxSize()) {
                destinations.forEachIndexed { index, destination ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(slotHeight)
                            .clickable {
                                if (!dragging) onDestinationChange(destination.route)
                            },
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        val selected = index == visualIndex
                        Icon(
                            imageVector = destination.icon,
                            contentDescription = destination.label,
                            modifier = Modifier.size(22.dp),
                            tint = if (selected) {
                                MaterialTheme.colorScheme.onSecondaryContainer
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                        Spacer(Modifier.height(2.dp))
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
