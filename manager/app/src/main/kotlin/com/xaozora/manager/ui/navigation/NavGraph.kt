package com.xaozora.manager.ui.navigation

import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import com.xaozora.manager.ui.screens.home.HomeScreen
import com.xaozora.manager.ui.screens.tuning.TuningScreen
import com.xaozora.manager.ui.screens.tweaks.TweaksScreen
import com.xaozora.manager.ui.screens.appmanager.AppManagerScreen
import com.xaozora.manager.ui.screens.about.AboutScreen
import com.xaozora.manager.ui.screens.settings.SettingsScreen
import androidx.compose.material.icons.outlined.Settings
import dev.chrisbanes.haze.HazeState

sealed class Screen(val title: String, val icon: ImageVector) {
    data object Home : Screen("Home", Icons.Rounded.Home)
    data object Tuning : Screen("Tuning", Icons.Rounded.Tune)
    data object Tweaks : Screen("Tweaks", Icons.Outlined.Build)
    data object AppManager : Screen("Apps", Icons.Rounded.Apps)
    data object About : Screen("About", Icons.Outlined.Info)
    data object Settings : Screen("Settings", Icons.Outlined.Settings)
}

@Composable
fun getAvailableScreens(isAutdAvailable: Boolean, isModuleInstalled: Boolean): List<Screen> {
    return remember(isAutdAvailable, isModuleInstalled) {
        mutableListOf<Screen>().apply {
            add(Screen.Home)
            if (isModuleInstalled) add(Screen.Tuning)
            add(Screen.Tweaks)
            if (isAutdAvailable) add(Screen.AppManager)
            add(Screen.Settings)
        }
    }
}

@Composable
fun AozoraNavGraph(
    screens: List<Screen>,
    pagerState: PagerState,
    hazeState: HazeState,
    snackbarHostState: SnackbarHostState,
    isAutdAvailable: Boolean,
    modifier: Modifier = Modifier,
    onAddClick: (() -> Unit) -> Unit = {},
    onDevModeClick: ((onClick: () -> Unit, isDevMode: Boolean) -> Unit) = { _, _ -> },
    onNavigateToBattery: () -> Unit = {}
) {
    HorizontalPager(
        state = pagerState,
        key = { screens.getOrNull(it)?.title ?: it.toString() },
        modifier = modifier
    ) { page ->
        when (screens.getOrNull(page)) {
            Screen.Home -> HomeScreen(hazeState = hazeState, onNavigateToBattery = onNavigateToBattery)
            Screen.Tuning -> TuningScreen(
                hazeState = hazeState, 
                snackbarHostState = snackbarHostState,
                onDevModeClickProvider = onDevModeClick
            )
            Screen.Tweaks -> TweaksScreen(
                hazeState = hazeState, 
                snackbarHostState = snackbarHostState,
                isAutdAvailable = isAutdAvailable
            )
            Screen.AppManager -> AppManagerScreen(
                hazeState = hazeState, 
                snackbarHostState = snackbarHostState,
                onAddClickProvider = onAddClick
            )
            Screen.Settings -> SettingsScreen(hazeState = hazeState, snackbarHostState = snackbarHostState)
            Screen.About -> AboutScreen(hazeState = hazeState, snackbarHostState = snackbarHostState)
            else -> HomeScreen(hazeState = hazeState, onNavigateToBattery = onNavigateToBattery)
        }
    }
}
