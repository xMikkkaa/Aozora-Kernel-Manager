package com.xaozora.manager

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import android.util.Log
import com.xaozora.manager.core.shell.RootShellHelper
import com.xaozora.manager.services.MonitorService
import com.xaozora.manager.ui.components.AozoraBottomNav
import com.xaozora.manager.ui.navigation.AozoraNavGraph
import com.xaozora.manager.ui.navigation.getAvailableScreens
import com.xaozora.manager.ui.theme.AozoraKernelManagerTheme
import com.xaozora.manager.ui.theme.AppThemeMode
import com.xaozora.manager.ui.theme.LocalThemeManager
import com.xaozora.manager.ui.theme.ThemeManager
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        WindowCompat.setDecorFitsSystemWindows(window, false)

        val themeManager = ThemeManager(this)

        setContent {
            val context = LocalContext.current
            val themeMode by themeManager.themeMode.collectAsState()
            var isAutdAvailable by remember { mutableStateOf(false) }
            var isModuleInstalled by remember { mutableStateOf(false) }

            val permissionLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.RequestPermission()
            ) { isGranted ->
                if (isGranted) {
                    Log.d("MainActivity", "Notification permission granted")
                } else {
                    Log.w("MainActivity", "Notification permission denied")
                }
            }

            LaunchedEffect(Unit) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    if (ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.POST_NOTIFICATIONS
                        ) != PackageManager.PERMISSION_GRANTED
                    ) {
                        permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                }
            }

            val isSystemDark = isSystemInDarkTheme()
            val isDark = when (themeMode) {
                AppThemeMode.DARK -> true
                AppThemeMode.LIGHT -> false
                AppThemeMode.AUTO -> isSystemDark
            }

            androidx.compose.runtime.LaunchedEffect(Unit) {
                withContext(Dispatchers.IO) {
                    isAutdAvailable = RootShellHelper.checkFileExists("/system/bin/autd")
                    val propOutput = RootShellHelper.executeCmdAndGetOutput(
                        "grep -l 'id=.*aozora' /data/adb/modules/*/module.prop 2>/dev/null"
                    )
                    isModuleInstalled = propOutput.isNotBlank()

                    if (isAutdAvailable && !MonitorService.isServiceRunning) {
                        Log.d("MainActivity", "Starting MonitorService...")
                        val serviceIntent = Intent(this@MainActivity, MonitorService::class.java)
                        startForegroundService(serviceIntent)
                    }
                }
            }

            CompositionLocalProvider(LocalThemeManager provides themeManager) {
                AozoraKernelManagerTheme(darkTheme = isDark, dynamicColor = true) {
                    val screens = getAvailableScreens(isAutdAvailable, isModuleInstalled)
                    val pagerState = rememberPagerState(pageCount = { screens.size })
                    val coroutineScope = rememberCoroutineScope()
                    val hazeState = remember { HazeState() }
                    val snackbarHostState = remember { SnackbarHostState() }

                    Scaffold(
                        modifier = Modifier
                            .fillMaxSize()
                            .hazeSource(state = hazeState),
                        contentWindowInsets = WindowInsets(0, 0, 0, 0),
                        snackbarHost = {
                            SnackbarHost(snackbarHostState) { data ->
                                val colorScheme = MaterialTheme.colorScheme
                                val isError = data.visuals.message.contains("Failed", ignoreCase = true) ||
                                        data.visuals.message.contains("Error", ignoreCase = true)

                                val glassStyle = remember(colorScheme, isError) {
                                    HazeStyle(
                                        blurRadius = 25.dp,
                                        noiseFactor = 0.1f,
                                        tints = listOf(
                                            HazeTint(
                                                if (isError) colorScheme.errorContainer.copy(alpha = 0.3f)
                                                else colorScheme.primaryContainer.copy(alpha = 0.3f)
                                            )
                                        )
                                    )
                                }

                                Box(
                                    modifier = Modifier
                                        .padding(16.dp)
                                        .clip(RoundedCornerShape(50))
                                        .hazeEffect(
                                            state = hazeState,
                                            style = glassStyle
                                        )
                                        .background(Color.Transparent)
                                        .border(
                                            width = 1.dp,
                                            color = if (isError) colorScheme.error.copy(alpha = 0.5f)
                                            else colorScheme.primary.copy(alpha = 0.5f),
                                            shape = RoundedCornerShape(50)
                                        )
                                        .padding(horizontal = 20.dp, vertical = 12.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = data.visuals.message,
                                        color = if (isError) colorScheme.onErrorContainer
                                        else colorScheme.onPrimaryContainer,
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.W600)
                                    )
                                }
                            }
                        },
                        bottomBar = {
                            AozoraBottomNav(
                                screens = screens,
                                selectedIndex = pagerState.currentPage,
                                onItemSelected = { index ->
                                    coroutineScope.launch { pagerState.animateScrollToPage(index) }
                                },
                                isVisible = true,
                                hazeState = hazeState
                            )
                        }
                    ) { innerPadding ->
                        AozoraNavGraph(
                            screens = screens,
                            pagerState = pagerState,
                            hazeState = hazeState,
                            snackbarHostState = snackbarHostState,
                            modifier = Modifier.padding(bottom = innerPadding.calculateBottomPadding())
                        )
                    }
                }
            }
        }
    }
}
