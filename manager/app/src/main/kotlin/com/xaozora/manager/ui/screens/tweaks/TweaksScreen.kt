package com.xaozora.manager.ui.screens.tweaks

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.BatterySaver
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.Games
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.compose.foundation.layout.navigationBarsPadding
import com.xaozora.manager.core.shell.RootShellHelper
import com.xaozora.manager.core.utils.SystemInfoUtils
import com.xaozora.manager.ui.components.GlassCard
import com.xaozora.manager.ui.components.ToggleCard
import com.xaozora.manager.ui.components.rememberStrongBlur
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeEffect
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

private const val CUSTOM_BYPASS_NODE = "/sys/kernel/bypass_charge/bypass_charging"
private const val DEFAULT_BYPASS_NODE = "/sys/class/power_supply/battery/input_suspend"
private const val CURRENT_LIMIT_NODE = "/sys/class/power_supply/battery/constant_charge_current_max"
private const val CURRENT_LIMIT_VALUE = "100000"
private const val MANUAL_BYPASS_BACKUP = "autd/manual_bypass_backup"

@Composable
fun TweaksScreen(
    hazeState: HazeState,
    snackbarHostState: SnackbarHostState,
    isAutdAvailable: Boolean,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var ramTotal by remember { mutableIntStateOf(1) }
    var ramUsed by remember { mutableIntStateOf(0) }
    var ramFree by remember { mutableIntStateOf(0) }

    var optimizeGameThread by remember { mutableStateOf(false) }
    var idleCharging by remember { mutableStateOf(false) }

    var isSupportedKernel by remember { mutableStateOf(false) }
    var isHydraSupported by remember { mutableStateOf(false) }
    var hydraEnabled by remember { mutableStateOf(false) }
    var bypassNode by remember { mutableStateOf("") }
    var manualBypassCharging by remember { mutableStateOf(false) }

    var isChargeControlSupported by remember { mutableStateOf(false) }
    var chargeControlEnabled by remember { mutableStateOf(false) }
    var chargeStopLevel by remember { mutableIntStateOf(80) }
    var chargeResumeLevel by remember { mutableIntStateOf(75) }

    var showFlushDialog by remember { mutableStateOf(false) }

    suspend fun nudgeChargeDaemon() = withContext(Dispatchers.IO) {
        try {
            val batteryManager = context.getSystemService(android.content.Context.BATTERY_SERVICE) as android.os.BatteryManager
            val level = batteryManager.getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_CAPACITY)
            val powerManager = context.getSystemService(android.content.Context.POWER_SERVICE) as android.os.PowerManager
            SystemInfoUtils.updateSystemState(level, powerManager.isInteractive)
        } catch (e: Exception) {
        }
    }

    suspend fun fetchRamStats() = withContext(Dispatchers.IO) {
        val meminfo = try { java.io.File("/proc/meminfo").readText() } catch (e: Exception) { "" }
        var total = 0
        var free = 0
        var available = 0
        var cached = 0
        meminfo.lines().forEach { line ->
            when {
                line.startsWith("MemTotal:") -> total = line.substringAfter(":").replace(Regex("[^0-9]"), "").toIntOrNull() ?: 0
                line.startsWith("MemFree:") -> free = line.substringAfter(":").replace(Regex("[^0-9]"), "").toIntOrNull() ?: 0
                line.startsWith("MemAvailable:") -> available = line.substringAfter(":").replace(Regex("[^0-9]"), "").toIntOrNull() ?: 0
                line.startsWith("Cached:") -> cached = line.substringAfter(":").replace(Regex("[^0-9]"), "").toIntOrNull() ?: 0
            }
        }
        val finalFree = if (available > 0) available else (free + cached)
        val totalMb = total / 1024
        val freeMb = finalFree / 1024
        val usedMb = totalMb - freeMb

        withContext(Dispatchers.Main) {
            ramTotal = totalMb.coerceAtLeast(1)
            ramFree = freeMb
            ramUsed = usedMb.coerceAtLeast(0)
        }
    }

    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    val lifecycleState by lifecycleOwner.lifecycle.currentStateFlow.collectAsState()

    LaunchedEffect(lifecycleState) {
        if (!lifecycleState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED)) {
            return@LaunchedEffect
        }
        withContext(Dispatchers.IO) {
            optimizeGameThread = try { java.io.File(context.filesDir, "autd/autd_opt_allow").readText().trim() == "1" } catch (e: Exception) { false }
            idleCharging = try { java.io.File(context.filesDir, "autd/autd_idle_charging").readText().trim() == "1" } catch (e: Exception) { false }
            hydraEnabled = try { java.io.File(context.filesDir, "autd/autd_hydra_enable").readText().trim() != "0" } catch (e: Exception) { false }
            
            isHydraSupported = RootShellHelper.checkFileExists("/proc/sys/kernel/hydra_pid")
            
            val customBypassNode = CUSTOM_BYPASS_NODE
            val defaultBypassNode = DEFAULT_BYPASS_NODE
            bypassNode = when {
                RootShellHelper.checkFileExists(customBypassNode) -> customBypassNode
                RootShellHelper.checkFileExists(defaultBypassNode) -> defaultBypassNode
                else -> ""
            }
            isSupportedKernel = bypassNode.isNotEmpty()

            if (isSupportedKernel) {
                manualBypassCharging = RootShellHelper.readSystemFile(bypassNode).trim() == "1" ||
                    java.io.File(context.filesDir, MANUAL_BYPASS_BACKUP).exists()
            }

            val autdDir = java.io.File(context.filesDir, "autd")
            chargeControlEnabled = try { java.io.File(autdDir, "autd_charge_limit_enable").readText().trim() == "1" } catch (e: Exception) { false }
            val savedStop = try { java.io.File(autdDir, "autd_charge_limit_stop").readText().trim().toIntOrNull() } catch (e: Exception) { null } ?: 80
            chargeStopLevel = savedStop.coerceIn(30, 100)
            val savedResume = try { java.io.File(autdDir, "autd_charge_limit_resume").readText().trim().toIntOrNull() } catch (e: Exception) { null } ?: (chargeStopLevel - 5)
            chargeResumeLevel = savedResume.coerceIn(20, chargeStopLevel - 1)
            isChargeControlSupported = RootShellHelper.checkFileExists(defaultBypassNode)
        }
        while (true) {
            fetchRamStats()
            delay(2000)
        }
    }

    val flushRam = {
        scope.launch(Dispatchers.IO) {
            val cmd = """
                for P in ${'$'}(pidof com.xaozora.manager); do 
                    echo -1000 > /proc/${'$'}P/oom_score_adj 2>/dev/null; 
                done;

                for p in /proc/[0-9]*; do
                    read oom < "${'$'}p/oom_score_adj" 2>/dev/null
                    [ "${'$'}{oom:-0}" -ge 500 ] && echo "${'$'}{p##*/}"
                done | xargs -r kill -9 2>/dev/null;

                sync; 
                echo 3 > /proc/sys/vm/drop_caches;
                [ -f /proc/sys/vm/compact_memory ] && echo 1 > /proc/sys/vm/compact_memory;

                (
                  pm list packages -3 | cut -d':' -f2 | grep -v "com.xaozora.manager" | while read -r app; do
                      am force-stop "${'$'}app"
                  done;
                ) &
            """.trimIndent()
            
            val success = RootShellHelper.executeCmd(cmd)
            if (success) {
                scope.launch {
                    snackbarHostState.showSnackbar("RAM flushed and cache cleared.")
                }
            } else {
                scope.launch {
                    snackbarHostState.showSnackbar("Failed to flush RAM")
                }
            }
            delay(1500)
            fetchRamStats()
        }
    }

    if (showFlushDialog) {
        val dialogStyle = rememberStrongBlur()

        Dialog(
            onDismissRequest = { showFlushDialog = false },
            properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
        ) {
            (LocalView.current.parent as? DialogWindowProvider)?.window?.setDimAmount(0f)

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { showFlushDialog = false },
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.85f)
                        .clip(RoundedCornerShape(32.dp))
                        .hazeEffect(state = hazeState, style = dialogStyle)
                        .border(1.2.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), RoundedCornerShape(32.dp))
                        .background(if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) Color.Transparent else androidx.compose.material3.MaterialTheme.colorScheme.surfaceContainer)
                        .clickable(enabled = false) {},
                ) {
                    Column(modifier = Modifier.padding(24.dp)) {
                        Text(
                            text = "Flush RAM?",
                            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "This will force-stop third-party apps, kill high-memory background processes, clear cached data, and trim storage. Unsaved app state may be lost.",
                            style = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            TextButton(onClick = { showFlushDialog = false }) {
                                Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Box(
                                modifier = Modifier
                                    .graphicsLayer {
                                        clip = true
                                        shape = CircleShape
                                    }
                                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f))
                                    .border(
                                        width = 1.dp,
                                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
                                        shape = CircleShape
                                    )
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null,
                                        onClick = {
                                            showFlushDialog = false
                                            flushRam()
                                        }
                                    )
                                    .padding(horizontal = 24.dp, vertical = 10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    "Flush",
                                    style = MaterialTheme.typography.labelLarge.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    val ramPercent = (ramUsed.toFloat() / ramTotal.toFloat()).coerceIn(0f, 1f)
    val colorScheme = MaterialTheme.colorScheme

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Spacer(modifier = Modifier.padding(WindowInsets.statusBars.asPaddingValues()))
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "System Tweaks",
                style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Bold, color = colorScheme.primary)
            )
            Spacer(modifier = Modifier.height(24.dp))

            GlassCard(
                hazeState = hazeState,
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("RAM Status", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                        Box(
                            modifier = Modifier
                                .shadow(8.dp, RoundedCornerShape(12.dp), spotColor = colorScheme.primary.copy(alpha = 0.3f))
                                .background(Brush.linearGradient(listOf(colorScheme.primary, colorScheme.tertiary)), RoundedCornerShape(12.dp))
                                .clickable { showFlushDialog = true }
                                .padding(10.dp)
                        ) {
                            Icon(Icons.Rounded.CleaningServices, contentDescription = "Flush RAM", tint = colorScheme.onPrimary, modifier = Modifier.size(20.dp))
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    LinearProgressIndicator(
                        progress = { ramPercent },
                        modifier = Modifier.fillMaxWidth().height(12.dp).clip(RoundedCornerShape(8.dp)),
                        color = if (ramPercent > 0.85f) colorScheme.error else colorScheme.primary,
                        trackColor = colorScheme.surfaceContainerHighest
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Used: ${ramUsed}MB", style = MaterialTheme.typography.bodyMedium.copy(color = colorScheme.onSurfaceVariant, fontWeight = FontWeight.Medium))
                        Text("Free: ${ramFree}MB", style = MaterialTheme.typography.bodyMedium.copy(color = colorScheme.primary, fontWeight = FontWeight.Bold))
                    }
                }
            }

            if (isAutdAvailable) {
                Spacer(modifier = Modifier.height(24.dp))
                Text("Quick Toggles", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                Spacer(modifier = Modifier.height(12.dp))


                ToggleCard(
                    title = "Optimize Game Thread",
                    subtitle = "Prioritize game processes for better performance.",
                    icon = Icons.Rounded.Games,
                    checked = optimizeGameThread,
                    hazeState = hazeState,
                    onCheckedChange = { newVal ->
                        scope.launch(Dispatchers.IO) {
                            if (RootShellHelper.writeSystemFile("${context.filesDir.path}/autd/autd_opt_allow", if (newVal) "1" else "0")) {
                                optimizeGameThread = newVal
                                scope.launch {
                                    val status = if (newVal) "Enabled" else "Disabled"
                                    snackbarHostState.showSnackbar("Game Thread Optimization $status")
                                }
                            } else {
                                scope.launch {
                                    snackbarHostState.showSnackbar("Failed to toggle Game Thread Optimization")
                                }
                            }
                        }
                    },
                    expandableContent = {
                        if (isHydraSupported) {
                            Column(modifier = Modifier.padding(top = 16.dp)) {
                                HorizontalDivider(color = colorScheme.outlineVariant.copy(alpha = 0.5f))
                                Spacer(modifier = Modifier.height(12.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "Kernel Level Optimize (HYDRA)",
                                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, color = colorScheme.onSurface)
                                        )
                                        Text(
                                            text = "Offloads thread tuning to the kernel for zero-latency affinity control.",
                                            style = MaterialTheme.typography.bodySmall.copy(color = colorScheme.onSurfaceVariant)
                                        )
                                    }
                                    Switch(
                                        checked = hydraEnabled,
                                        onCheckedChange = { newVal ->
                                            scope.launch(Dispatchers.IO) {
                                                if (RootShellHelper.writeSystemFile("${context.filesDir.path}/autd/autd_hydra_enable", if (newVal) "1" else "0")) {
                                                    hydraEnabled = newVal
                                                }
                                            }
                                        }
                                    )
                                }
                                Spacer(modifier = Modifier.height(12.dp))
                                HorizontalDivider(color = colorScheme.outlineVariant.copy(alpha = 0.5f))
                                Spacer(modifier = Modifier.height(12.dp))
                            }
                        }
                    }
                )
                if (isChargeControlSupported) {
                    Spacer(modifier = Modifier.height(12.dp))
                    ToggleCard(
                        title = "Charging Control",
                        subtitle = if (chargeControlEnabled) {
                            "Pauses charging at $chargeStopLevel% and resumes at $chargeResumeLevel%."
                        } else {
                            "Pause charging at your chosen battery level."
                        },
                        icon = Icons.Rounded.BatterySaver,
                        checked = chargeControlEnabled,
                        hazeState = hazeState,
                        showExpandableWhenOff = true,
                        onCheckedChange = { newVal ->
                            scope.launch(Dispatchers.IO) {
                                val written = RootShellHelper.writeSystemFile(
                                    "${context.filesDir.path}/autd/autd_charge_limit_enable",
                                    if (newVal) "1" else "0"
                                )
                                if (written) {
                                    if (newVal && manualBypassCharging) {
                                        if (bypassNode.isNotEmpty()) {
                                            disableManualBypass(context, bypassNode)
                                        }
                                        manualBypassCharging = false
                                    }
                                    chargeControlEnabled = newVal
                                    nudgeChargeDaemon()
                                    scope.launch {
                                        val status = if (newVal) "Enabled" else "Disabled"
                                        snackbarHostState.showSnackbar("Charging Control $status")
                                    }
                                } else {
                                    scope.launch {
                                        snackbarHostState.showSnackbar("Failed to toggle Charging Control")
                                    }
                                }
                            }
                        },
                        expandableContent = {
                            ChargeLimitSliders(
                                stopLevel = chargeStopLevel,
                                resumeLevel = chargeResumeLevel,
                                onStopChange = { newStop ->
                                    chargeStopLevel = newStop.coerceIn(30, 100)
                                    if (chargeResumeLevel >= chargeStopLevel) {
                                        chargeResumeLevel = (chargeStopLevel - 5).coerceAtLeast(20)
                                    }
                                },
                                onResumeChange = { newResume ->
                                    chargeResumeLevel = newResume.coerceIn(20, chargeStopLevel - 1)
                                },
                                onCommit = {
                                    scope.launch(Dispatchers.IO) {
                                        val dir = "${context.filesDir.path}/autd"
                                        RootShellHelper.writeSystemFile("$dir/autd_charge_limit_stop", chargeStopLevel.toString())
                                        RootShellHelper.writeSystemFile("$dir/autd_charge_limit_resume", chargeResumeLevel.toString())
                                        nudgeChargeDaemon()
                                    }
                                }
                            )
                        }
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                ToggleCard(
                    title = "Bypass Charging While Gaming",
                    subtitle = "Stop charging to reduce heat while gaming.",
                    icon = Icons.Rounded.BatteryChargingFull,
                    checked = idleCharging,
                    hazeState = hazeState,
                    onCheckedChange = { newVal ->
                        scope.launch(Dispatchers.IO) {
                            if (RootShellHelper.writeSystemFile("${context.filesDir.path}/autd/autd_idle_charging", if (newVal) "1" else "0")) {
                                idleCharging = newVal
                                scope.launch {
                                    val status = if (newVal) "Enabled" else "Disabled"
                                    snackbarHostState.showSnackbar("Bypass Charging While Gaming $status")
                                }
                            } else {
                                scope.launch {
                                    snackbarHostState.showSnackbar("Failed to toggle Bypass Charging While Gaming")
                                }
                            }
                        }
                    }
                )
                Spacer(modifier = Modifier.height(12.dp))
            }
            
            if (isSupportedKernel && !(isAutdAvailable && chargeControlEnabled)) {
                if (!isAutdAvailable) {
                    Spacer(modifier = Modifier.height(24.dp))
                    Text("Quick Toggles", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                    Spacer(modifier = Modifier.height(12.dp))
                }
                
                ToggleCard(
                    title = "Manual Bypass Charging",
                    subtitle = "Stop charging to reduce heat while plugged in.",
                    icon = Icons.Rounded.BatteryChargingFull,
                    checked = manualBypassCharging,
                    hazeState = hazeState,
                    onCheckedChange = { newVal ->
                        scope.launch(Dispatchers.IO) {
                            val success = if (newVal) {
                                enableManualBypass(context, bypassNode)
                            } else {
                                disableManualBypass(context, bypassNode)
                            }
                            if (success) {
                                manualBypassCharging = newVal
                                scope.launch {
                                    val status = if (newVal) "Enabled" else "Disabled"
                                    snackbarHostState.showSnackbar("Manual Bypass Charging $status")
                                }
                            } else {
                                scope.launch {
                                    snackbarHostState.showSnackbar("Failed to toggle Bypass Charging")
                                }
                            }
                        }
                    }
                )
                Spacer(modifier = Modifier.height(12.dp))
            }
            
            Spacer(modifier = Modifier.height(140.dp))
            Spacer(modifier = Modifier.navigationBarsPadding())
        }
    }
}

private suspend fun enableManualBypass(context: android.content.Context, bypassNode: String): Boolean {
    if (!RootShellHelper.writeSystemFile(bypassNode, "1")) return false

    if (bypassNode == DEFAULT_BYPASS_NODE) {
        delay(1500)
        val status = RootShellHelper.executeCmdAndGetOutput("cat /sys/class/power_supply/battery/status").trim()
        if (status == "Discharging") {
            RootShellHelper.writeSystemFile(bypassNode, "0")
            val original = RootShellHelper.readSystemFile(CURRENT_LIMIT_NODE).trim()
            if (original.isNotEmpty()) {
                val backup = java.io.File(context.filesDir, MANUAL_BYPASS_BACKUP)
                val saved = runCatching {
                    backup.parentFile?.mkdirs()
                    backup.writeText(original)
                }.isSuccess
                if (saved) {
                    RootShellHelper.writeSystemFile(CURRENT_LIMIT_NODE, CURRENT_LIMIT_VALUE)
                }
            }
        }
    }

    return true
}

private fun disableManualBypass(context: android.content.Context, bypassNode: String): Boolean {
    val backup = java.io.File(context.filesDir, MANUAL_BYPASS_BACKUP)
    if (backup.exists()) {
        val original = runCatching { backup.readText().trim() }.getOrElse { "" }
        val restored = original.isEmpty() || RootShellHelper.writeSystemFile(CURRENT_LIMIT_NODE, original)
        backup.delete()
        return restored
    }

    return RootShellHelper.writeSystemFile(bypassNode, "0")
}

@Composable
private fun ChargeLimitSliders(
    stopLevel: Int,
    resumeLevel: Int,
    onStopChange: (Int) -> Unit,
    onResumeChange: (Int) -> Unit,
    onCommit: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme

    Column(modifier = Modifier.padding(top = 16.dp)) {
        HorizontalDivider(color = colorScheme.outlineVariant.copy(alpha = 0.5f))
        Spacer(modifier = Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Stop charging at",
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, color = colorScheme.onSurface)
            )
            Text(
                text = "$stopLevel%",
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, color = colorScheme.primary)
            )
        }
        Slider(
            value = stopLevel.toFloat(),
            onValueChange = { onStopChange(it.roundToInt()) },
            onValueChangeFinished = onCommit,
            valueRange = 30f..100f,
            steps = 69
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Resume charging at",
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, color = colorScheme.onSurface)
            )
            Text(
                text = "$resumeLevel%",
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, color = colorScheme.primary)
            )
        }
        Slider(
            value = resumeLevel.coerceAtMost(stopLevel - 1).toFloat(),
            onValueChange = { onResumeChange(it.roundToInt()) },
            onValueChangeFinished = onCommit,
            valueRange = 20f..(stopLevel - 1).toFloat(),
            steps = (stopLevel - 22).coerceAtLeast(0)
        )
        Text(
            text = "Keeps the battery between $resumeLevel% and $stopLevel% while plugged in.",
            style = MaterialTheme.typography.bodySmall.copy(color = colorScheme.onSurfaceVariant)
        )
    }
}
