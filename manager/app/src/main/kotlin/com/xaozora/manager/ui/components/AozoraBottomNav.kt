package com.xaozora.manager.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.xaozora.manager.ui.navigation.Screen
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect

@Composable
fun AozoraBottomNav(
    screens: List<Screen>,
    selectedIndex: Int,
    onItemSelected: (Int) -> Unit,
    isVisible: Boolean,
    hazeState: HazeState,
    modifier: Modifier = Modifier,
    onAddClick: (() -> Unit)? = null,
    onDevModeClick: (() -> Unit)? = null,
    isDevMode: Boolean = false,
) {
    val density = LocalDensity.current

    val primaryScreens = remember(screens) { screens.filter { it != Screen.Settings } }

    val surfaceContainer = MaterialTheme.colorScheme.surfaceContainer
    val strongBlurStyle = remember(surfaceContainer) {
        HazeStyle(
            blurRadius = 25.dp,
            noiseFactor = 0.1f,
            tints = listOf(HazeTint(surfaceContainer.copy(alpha = 0.25f)))
        )
    }

    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.BottomCenter
    ) {
        AnimatedVisibility(
            visible = onAddClick != null,
            enter = fadeIn() + slideInVertically { it / 2 },
            exit = fadeOut() + slideOutVertically { it / 2 },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 40.dp, bottom = 110.dp)
                .navigationBarsPadding()
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .hazeEffect(state = hazeState, style = strongBlurStyle)
                    .background(if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) Color.Transparent else MaterialTheme.colorScheme.surfaceContainer)
                    .border(
                        width = 1.2.dp,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(16.dp)
                    )
                    .clickable { onAddClick?.invoke() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.Add,
                    contentDescription = "Add",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }

        AnimatedVisibility(
            visible = onDevModeClick != null,
            enter = fadeIn() + slideInVertically { it / 2 },
            exit = fadeOut() + slideOutVertically { it / 2 },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 40.dp, bottom = 110.dp)
                .navigationBarsPadding()
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .hazeEffect(state = hazeState, style = strongBlurStyle)
                    .background(if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) Color.Transparent else MaterialTheme.colorScheme.surfaceContainer)
                    .border(
                        width = 1.2.dp,
                        color = if (isDevMode) MaterialTheme.colorScheme.error.copy(alpha = 0.5f)
                        else MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(16.dp)
                    )
                    .clickable { onDevModeClick?.invoke() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isDevMode) Icons.Rounded.Close else Icons.Rounded.Edit,
                    contentDescription = if (isDevMode) "Exit Developer Mode" else "Enter Developer Mode",
                    tint = if (isDevMode) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                )
            }
        }

        AnimatedVisibility(
            visible = isVisible,
            enter = slideInVertically(animationSpec = tween(200)) { it },
            exit = slideOutVertically(animationSpec = tween(200)) { it },
            modifier = Modifier.navigationBarsPadding()
        ) {
            Box(
                modifier = Modifier
                    .padding(start = 40.dp, end = 40.dp, bottom = 20.dp)
                    .fillMaxWidth()
                    .height(70.dp)
            ) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .clip(CircleShape)
                        .hazeEffect(state = hazeState, style = strongBlurStyle)
                        .background(if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) Color.Transparent else MaterialTheme.colorScheme.surfaceContainer)
                        .border(
                            width = 1.2.dp,
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                            shape = CircleShape
                        )
                )

                Row(
                    modifier = Modifier.fillMaxSize(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val scrollState = rememberScrollState()
                    val itemOffsets = remember { mutableStateMapOf<Int, Int>() }
                    val itemWidths = remember { mutableStateMapOf<Int, Int>() }
                    var rowWidth by remember { mutableIntStateOf(0) }

                    LaunchedEffect(selectedIndex) {
                        kotlinx.coroutines.delay(200)

                        val offset = itemOffsets[selectedIndex] ?: return@LaunchedEffect
                        val width = itemWidths[selectedIndex] ?: return@LaunchedEffect
                        if (rowWidth <= 0) return@LaunchedEffect

                        val itemCenter = offset + width / 2
                        val targetScroll = (itemCenter - rowWidth / 2).coerceIn(0, scrollState.maxValue)
                        scrollState.animateScrollTo(
                            targetScroll,
                            animationSpec = tween(durationMillis = 300, easing = androidx.compose.animation.core.FastOutSlowInEasing)
                        )
                    }

                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 8.dp)
                            .onGloballyPositioned { coordinates ->
                                rowWidth = coordinates.size.width
                            }
                            .horizontalScroll(scrollState),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        primaryScreens.forEachIndexed { index, screen ->
                            Box(
                                modifier = Modifier.onGloballyPositioned { coordinates ->
                                    itemOffsets[index] = coordinates.size.width.let {
                                        coordinates.parentLayoutCoordinates?.let { parent ->
                                            val pos = parent.localPositionOf(coordinates, Offset.Zero)
                                            pos.x.toInt()
                                        } ?: 0
                                    }
                                    itemWidths[index] = coordinates.size.width
                                }
                            ) {
                                NavItem(
                                    screen = screen,
                                    isSelected = selectedIndex == index,
                                    onClick = { onItemSelected(index) }
                                )
                            }
                        }
                    }

                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .height(24.dp)
                            .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    )

                    Row(
                        modifier = Modifier.padding(start = 4.dp, end = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        NavItem(
                            screen = Screen.Settings,
                            isSelected = selectedIndex == screens.indexOf(Screen.Settings),
                            onClick = { onItemSelected(screens.indexOf(Screen.Settings)) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun NavItem(
    screen: Screen,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val fastSpec = tween<Float>(durationMillis = 150)
    val fastIntSizeSpec = tween<androidx.compose.ui.unit.IntSize>(durationMillis = 150)
    val fastDpSpec = tween<androidx.compose.ui.unit.Dp>(durationMillis = 150)
    
    val fadeSpec = tween<Color>(durationMillis = 300, delayMillis = if (isSelected) 150 else 0)
    val borderFadeSpec = tween<Color>(durationMillis = 300, delayMillis = if (isSelected) 150 else 0)

    val backgroundColor by animateColorAsState(
        targetValue = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f) else Color.Transparent,
        animationSpec = fadeSpec,
        label = "nav_bg_color"
    )
    val contentColor by animateColorAsState(
        targetValue = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.secondary,
        animationSpec = if (isSelected) fadeSpec else tween(durationMillis = 150),
        label = "nav_content_color"
    )
    val borderColor by animateColorAsState(
        targetValue = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.4f) else Color.Transparent,
        animationSpec = borderFadeSpec,
        label = "nav_border_color"
    )
    val paddingHorizontal by animateDpAsState(
        targetValue = if (isSelected) 20.dp else 16.dp,
        animationSpec = fastDpSpec,
        label = "nav_padding"
    )

    Row(
        modifier = Modifier
            .graphicsLayer {
                clip = true
                shape = CircleShape
            }
            .animateContentSize(animationSpec = fastIntSizeSpec)
            .background(backgroundColor)
            .border(
                width = 1.dp,
                color = borderColor,
                shape = CircleShape
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = paddingHorizontal, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = screen.icon,
            contentDescription = screen.title,
            tint = contentColor
        )
        androidx.compose.animation.AnimatedVisibility(
            visible = isSelected,
            enter = fadeIn(animationSpec = fastSpec),
            exit = fadeOut(animationSpec = fastSpec)
        ) {
            Row {
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = screen.title,
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontWeight = FontWeight.Bold,
                        color = contentColor
                    )
                )
            }
        }
    }
}
