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
import com.xaozora.manager.ui.screens.customhelper.CustomHelperScreen
import com.xaozora.manager.ui.screens.appmanager.AppManagerScreen
import com.xaozora.manager.ui.screens.about.AboutScreen
import dev.chrisbanes.haze.HazeState

sealed class Screen(val title: String, val icon: ImageVector) {
    data object Home : Screen("Home", Icons.Rounded.Home)
    data object Tuning : Screen("Tuning", Icons.Rounded.Tune)
    data object Tweaks : Screen("Tweaks", Icons.Outlined.Build)
    data object CustomHelper : Screen("Helper", Icons.Rounded.Extension)
    data object AppManager : Screen("Apps", Icons.Rounded.Apps)
    data object About : Screen("About", Icons.Outlined.Info)
}

@Composable
fun getAvailableScreens(isAutdAvailable: Boolean, isModuleInstalled: Boolean): List<Screen> {
    return remember(isAutdAvailable, isModuleInstalled) {
        mutableListOf<Screen>().apply {
            add(Screen.Home)
            if (isModuleInstalled) add(Screen.Tuning)
            add(Screen.Tweaks)
            add(Screen.CustomHelper)
            if (isAutdAvailable) add(Screen.AppManager)
            add(Screen.About)
        }
    }
}

@Composable
fun AozoraNavGraph(
    screens: List<Screen>,
    pagerState: PagerState,
    hazeState: HazeState,
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier,
    onAddClick: (() -> Unit) -> Unit = {}
) {
    HorizontalPager(
        state = pagerState,
        modifier = modifier
    ) { page ->
        when (screens.getOrNull(page)) {
            Screen.Home -> HomeScreen(hazeState = hazeState)
            Screen.Tuning -> TuningScreen(hazeState = hazeState, snackbarHostState = snackbarHostState)
            Screen.Tweaks -> TweaksScreen(hazeState = hazeState, snackbarHostState = snackbarHostState)
            Screen.CustomHelper -> CustomHelperScreen(hazeState = hazeState, snackbarHostState = snackbarHostState)
            Screen.AppManager -> AppManagerScreen(
                hazeState = hazeState, 
                snackbarHostState = snackbarHostState,
                onAddClickProvider = onAddClick
            )
            Screen.About -> AboutScreen(hazeState = hazeState, snackbarHostState = snackbarHostState)
            else -> HomeScreen(hazeState = hazeState)
        }
    }
}
