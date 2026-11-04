package com.xaozora.manager.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint

@Composable
fun rememberStrongBlur(): HazeStyle {
    val surfaceContainer = MaterialTheme.colorScheme.surfaceContainer
    return remember(surfaceContainer) {
        HazeStyle(
            blurRadius = 25.dp,
            noiseFactor = 0.1f,
            tints = listOf(HazeTint(surfaceContainer.copy(alpha = 0.25f)))
        )
    }
}