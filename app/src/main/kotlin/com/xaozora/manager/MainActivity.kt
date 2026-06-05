package com.xaozora.manager

import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.os.Bundle
import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
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
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import android.util.Log
import android.content.Context
import java.io.File
import java.io.FileOutputStream
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.xaozora.manager.core.shell.RootShellHelper
import com.xaozora.manager.core.network.UpdateManager
import com.xaozora.manager.core.network.UpdateCheckResult
import com.xaozora.manager.services.MonitorService
import com.xaozora.manager.ui.components.AozoraBottomNav
import com.xaozora.manager.ui.components.UpdateDialog
import com.xaozora.manager.ui.navigation.AozoraNavGraph
import com.xaozora.manager.ui.navigation.getAvailableScreens
import com.xaozora.manager.ui.navigation.Screen
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
        val splashScreen = installSplashScreen()
        
        super.onCreate(savedInstanceState)
        
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            @Suppress("DEPRECATION")
            window.isStatusBarContrastEnforced = false
            @Suppress("DEPRECATION")
            window.isNavigationBarContrastEnforced = false
        }
        
        splashScreen.setOnExitAnimationListener { it.remove() }

        val navigateToTuning = intent?.action == "android.service.quicksettings.action.QS_TILE_PREFERENCES"

        val themeManager = ThemeManager(this)

        setContent {
            val context = LocalContext.current
            val themeMode by themeManager.themeMode.collectAsState()
            var isAutdAvailable by remember { mutableStateOf(false) }
            var isModuleInstalled by remember { mutableStateOf(false) }
            var showUpdateDialog by remember { mutableStateOf(false) }
            var updateCheckResult by remember { mutableStateOf<UpdateCheckResult?>(null) }
            var appVersion by remember { mutableStateOf("") }

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

            val mainScope = rememberCoroutineScope()
            DisposableEffect(context) {
                val prefs = context.getSharedPreferences("aozora_prefs", Context.MODE_PRIVATE)
                val listener = SharedPreferences.OnSharedPreferenceChangeListener { sharedPreferences, key ->
                    if (key == "autd_enabled") {
                        mainScope.launch(Dispatchers.IO) {
                            val autdExists = RootShellHelper.checkFileExists("${context.filesDir.path}/xaozora_daemon")
                            isAutdAvailable = autdExists && sharedPreferences.getBoolean("autd_enabled", true)
                        }
                    }
                }
                prefs.registerOnSharedPreferenceChangeListener(listener)
                onDispose {
                    prefs.unregisterOnSharedPreferenceChangeListener(listener)
                }
            }

            androidx.compose.runtime.LaunchedEffect(Unit) {
                withContext(Dispatchers.IO) {
                    val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
                    appVersion = packageInfo.versionName ?: ""
                    
                    val prefs = context.getSharedPreferences("aozora_prefs", Context.MODE_PRIVATE)
                    
                    com.xaozora.manager.core.utils.NativeDaemonManager.extractAndStartDaemon(context)

                    isAutdAvailable = RootShellHelper.checkFileExists("${context.filesDir.path}/xaozora_daemon") && prefs.getBoolean("autd_enabled", true)
                    
                    val propOutput = RootShellHelper.executeCmdAndGetOutput(
                        "grep -l 'id=.*aozora' /data/adb/modules/*/module.prop 2>/dev/null"
                    )
                    isModuleInstalled = propOutput.isNotBlank()

                    if (!MonitorService.isServiceRunning) {
                        try {
                            Log.d("MainActivity", "Starting MonitorService...")
                            val serviceIntent = Intent(this@MainActivity, MonitorService::class.java)
                            startForegroundService(serviceIntent)
                        } catch (e: Exception) {
                            Log.e("MainActivity", "Failed to start MonitorService", e)
                        }
                    }

                    if (prefs.getBoolean("auto_check_updates", true)) {
                        try {
                            val result = UpdateManager.checkUpdates(appVersion)
                            if (result.appUpdate.hasUpdate) {
                                withContext(Dispatchers.Main) {
                                    updateCheckResult = result
                                    showUpdateDialog = true
                                }
                            }
                        } catch (e: Exception) {
                            Log.e("MainActivity", "Auto update check failed", e)
                        }
                    }
                }
            }

            CompositionLocalProvider(LocalThemeManager provides themeManager) {
                AozoraKernelManagerTheme(darkTheme = isDark, dynamicColor = true) {
                    var showSplash by remember { mutableStateOf(!navigateToTuning) }

                    if (showSplash) {
                        com.xaozora.manager.ui.components.SplashScreen(
                            onSplashFinished = { showSplash = false }
                        )
                    } else {
                    val screens = getAvailableScreens(isAutdAvailable, isModuleInstalled)
                    val pagerState = rememberPagerState(pageCount = { screens.size })
                    val coroutineScope = rememberCoroutineScope()

                    if (navigateToTuning) {
                        LaunchedEffect(screens) {
                            val tuningIndex = screens.indexOfFirst { it is Screen.Tuning }
                            if (tuningIndex >= 0) {
                                pagerState.scrollToPage(tuningIndex)
                            }
                        }
                    }
                    val hazeState = remember { HazeState() }
                    val batteryHazeState = remember { HazeState() }
                    val snackbarHostState = remember { SnackbarHostState() }
                    var onAddClickAction by remember { mutableStateOf<(() -> Unit)?>(null) }
                    var onDevModeClickAction by remember { mutableStateOf<(() -> Unit)?>(null) }
                    var isDevMode by remember { mutableStateOf(false) }
                    var showBatteryScreen by remember { mutableStateOf(false) }

                    Box(
                        modifier = Modifier.fillMaxSize()
                    ) {
                        Scaffold(
                            modifier = Modifier.fillMaxSize(),
                            containerColor = Color.Transparent,
                            contentWindowInsets = WindowInsets(0, 0, 0, 0)
                        ) { innerPadding ->
                            AozoraNavGraph(
                                screens = screens,
                                pagerState = pagerState,
                                hazeState = hazeState,
                                snackbarHostState = snackbarHostState,
                                isAutdAvailable = isAutdAvailable,
                                onAddClick = { action -> onAddClickAction = action },
                                onDevModeClick = { action, devMode ->
                                    onDevModeClickAction = action
                                    isDevMode = devMode
                                },
                                onNavigateToBattery = { showBatteryScreen = true },
                                modifier = Modifier
                                    .hazeSource(state = hazeState)
                                    .padding(bottom = innerPadding.calculateBottomPadding())
                            )
                        }

                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.BottomCenter
                        ) {
                            AozoraBottomNav(
                                screens = screens,
                                selectedIndex = pagerState.currentPage,
                                onItemSelected = { index ->
                                    coroutineScope.launch { 
                                        pagerState.scrollToPage(index)
                                    }
                                },
                                isVisible = true,
                                hazeState = hazeState,
                                onAddClick = if (screens.getOrNull(pagerState.currentPage) == Screen.AppManager) {
                                    onAddClickAction
                                } else null,
                                onDevModeClick = if (screens.getOrNull(pagerState.currentPage) == Screen.Tuning) {
                                    onDevModeClickAction
                                } else null,
                                isDevMode = isDevMode
                            )
                        }

                        SnackbarHost(
                            hostState = snackbarHostState,
                            modifier = Modifier.align(Alignment.BottomCenter)
                        ) { data ->
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
                                    .padding(bottom = 110.dp)
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
                                    .padding(horizontal = 24.dp, vertical = 14.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = data.visuals.message,
                                    color = if (isError) colorScheme.onErrorContainer
                                    else colorScheme.onPrimaryContainer,
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontWeight = androidx.compose.ui.text.font.FontWeight.W600,
                                        letterSpacing = 0.2.sp
                                    )
                                )
                            }
                        }

                        if (showBatteryScreen) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .hazeSource(state = batteryHazeState)
                            ) {
                                com.xaozora.manager.ui.screens.battery.BatteryScreen(
                                    hazeState = batteryHazeState,
                                    onBack = { showBatteryScreen = false }
                                )
                            }
                        }
                    }

                    if (showUpdateDialog && updateCheckResult != null) {
                        val result = updateCheckResult!!
                        val isAppUpdate = result.appUpdate.hasUpdate

                        val newVersionDisplay = "App: v${result.appUpdate.newVersion}"

                        val changelogDisplay = "App Update Notes:\n${result.appUpdate.releaseNotes}\n\n"

                        UpdateDialog(
                            hazeState = hazeState,
                            currentVersion = appVersion,
                            newVersion = newVersionDisplay,
                            changelog = changelogDisplay,
                            onDismiss = { showUpdateDialog = false },
                            onUpdate = {
                                showUpdateDialog = false
                                coroutineScope.launch {
                                    snackbarHostState.showSnackbar("Downloading update in background...")

                                    if (isAppUpdate) {
                                        UpdateManager.performAppUpdate(result.appUpdate.downloadUrl, context.cacheDir)
                                    }
                                }
                            }
                        )
                    }
                    }
                }
            }
        }
    }
}

