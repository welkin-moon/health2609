package uk.lunarlab.health2609.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlin.math.abs
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
    val initialPage = remember {
        destinations.indexOfFirst { it.route == selectedDestination }.coerceAtLeast(0)
    }
    val pagerState = rememberPagerState(
        initialPage = initialPage,
        pageCount = { destinations.size }
    )
    val coroutineScope = rememberCoroutineScope()

    // Sync external destination selection changes to pager
    LaunchedEffect(selectedDestination) {
        val target = destinations.indexOfFirst { it.route == selectedDestination }.coerceAtLeast(0)
        if (pagerState.currentPage != target && !pagerState.isScrollInProgress) {
            pagerState.animateScrollToPage(
                page = target,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessMediumLow
                )
            )
        }
    }

    // Sync pager settling to external destination state
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }.collect { settledPage ->
            val route = destinations[settledPage].route
            if (route != selectedDestination) {
                onDestinationChange(route)
            }
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useNavigationRail = maxWidth >= 600.dp

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = if (useNavigationRail) 104.dp else 0.dp)
        ) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
                userScrollEnabled = !useNavigationRail,
                beyondViewportPageCount = 2
            ) { pageIndex ->
                content(destinations[pageIndex].route, useNavigationRail)
            }
        }

        if (useNavigationRail) {
            FloatingStudentRail(
                pagerState = pagerState,
                onSelectPage = { page ->
                    coroutineScope.launch {
                        pagerState.animateScrollToPage(
                            page = page,
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioNoBouncy,
                                stiffness = Spring.StiffnessMediumLow
                            )
                        )
                    }
                },
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .safeDrawingPadding()
                    .padding(start = 12.dp)
            )
        } else {
            FloatingStudentDock(
                pagerState = pagerState,
                screenWidthPx = with(LocalDensity.current) { maxWidth.toPx() },
                onSelectPage = { page ->
                    coroutineScope.launch {
                        pagerState.animateScrollToPage(
                            page = page,
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioNoBouncy,
                                stiffness = Spring.StiffnessMediumLow
                            )
                        )
                    }
                },
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
    pagerState: PagerState,
    screenWidthPx: Float,
    onSelectPage: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()
    val density = LocalDensity.current
    val slotWidth = 88.dp
    val slotWidthPx = with(density) { slotWidth.toPx() }
    val maxOffsetPx = slotWidthPx * (destinations.size - 1)

    // Realtime progress fraction derived directly from pagerState (1:1 tracking)
    val pageProgress by remember(pagerState) {
        derivedStateOf {
            pagerState.currentPage + pagerState.currentPageOffsetFraction
        }
    }

    var isDockDragging by remember { mutableStateOf(false) }

    val dragState = rememberDraggableState { delta ->
        // Dragging dock drives pager offset in real-time synchronously (跟手)
        val pagerDelta = -delta * (screenWidthPx / slotWidthPx)
        pagerState.dispatchRawDelta(pagerDelta)
    }

    val indicatorOffsetPx = (pageProgress * slotWidthPx).coerceIn(0f, maxOffsetPx)

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(36.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 5.dp,
        shadowElevation = 6.dp
    ) {
        Box(
            modifier = Modifier
                .padding(5.dp)
                .width(slotWidth * destinations.size)
                .height(56.dp)
                .draggable(
                    state = dragState,
                    orientation = Orientation.Horizontal,
                    startDragImmediately = true,
                    onDragStarted = {
                        isDockDragging = true
                    },
                    onDragStopped = { velocity ->
                        isDockDragging = false
                        val targetIndex = (pageProgress + (-velocity / screenWidthPx) * 0.25f)
                            .roundToInt()
                            .coerceIn(destinations.indices)
                        onSelectPage(targetIndex)
                    }
                )
        ) {
            // Single continuous rounded indicator pill with no bounding rect artifact
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
                    // Calculate smooth interpolated emphasis between 0.0f and 1.0f
                    val emphasis = (1f - abs(pageProgress - index)).coerceIn(0f, 1f)

                    DockItem(
                        destination = destination,
                        emphasis = emphasis,
                        modifier = Modifier
                            .width(slotWidth)
                            .fillMaxHeight(),
                        onClick = {
                            if (!isDockDragging) {
                                onSelectPage(index)
                            }
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
    emphasis: Float,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val onSecondary = MaterialTheme.colorScheme.onSecondaryContainer
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant
    val itemColor = lerp(onSurfaceVariant, onSecondary, emphasis)

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(28.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = destination.icon,
            contentDescription = destination.label,
            modifier = Modifier.size(20.dp),
            tint = itemColor
        )
        Spacer(Modifier.width(5.dp))
        Text(
            text = destination.label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (emphasis > 0.5f) FontWeight.SemiBold else FontWeight.Medium,
            color = itemColor
        )
    }
}

@Composable
private fun FloatingStudentRail(
    pagerState: PagerState,
    onSelectPage: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val slotHeight = 70.dp
    val slotHeightPx = with(density) { slotHeight.toPx() }
    val maxOffsetPx = slotHeightPx * (destinations.size - 1)

    val pageProgress by remember(pagerState) {
        derivedStateOf {
            pagerState.currentPage + pagerState.currentPageOffsetFraction
        }
    }

    val animatedOffsetPx by animateFloatAsState(
        targetValue = (pagerState.currentPage * slotHeightPx).coerceIn(0f, maxOffsetPx),
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "rail-indicator"
    )

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(36.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 5.dp,
        shadowElevation = 6.dp
    ) {
        Box(
            modifier = Modifier
                .padding(5.dp)
                .width(74.dp)
                .height(slotHeight * destinations.size)
        ) {
            // Smooth rounded pill background
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(slotHeight)
                    .offset { IntOffset(0, animatedOffsetPx.roundToInt()) },
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.secondaryContainer
            ) {}

            Column(modifier = Modifier.fillMaxSize()) {
                destinations.forEachIndexed { index, destination ->
                    val emphasis = (1f - abs(pageProgress - index)).coerceIn(0f, 1f)
                    val onSecondary = MaterialTheme.colorScheme.onSecondaryContainer
                    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant
                    val itemColor = lerp(onSurfaceVariant, onSecondary, emphasis)

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(slotHeight)
                            .clip(RoundedCornerShape(28.dp))
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = { onSelectPage(index) }
                            ),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = destination.icon,
                            contentDescription = destination.label,
                            modifier = Modifier.size(22.dp),
                            tint = itemColor
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = destination.label,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (emphasis > 0.5f) FontWeight.SemiBold else FontWeight.Medium,
                            color = itemColor
                        )
                    }
                }
            }
        }
    }
}
