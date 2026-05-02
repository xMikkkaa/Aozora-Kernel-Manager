package com.xaozora.manager.ui.screens.tweaks

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.Games
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import com.xaozora.manager.core.shell.RootShellHelper
import com.xaozora.manager.ui.components.GlassCard
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun TweaksScreen(
    hazeState: HazeState,
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()

    var ramTotal by remember { mutableIntStateOf(1) }
    var ramUsed by remember { mutableIntStateOf(0) }
    var ramFree by remember { mutableIntStateOf(0) }

    var bypassCharging by remember { mutableStateOf(false) }
    var optimizeGameThread by remember { mutableStateOf(false) }
    var isAutdAvailable by remember { mutableStateOf(false) }

    var showFlushDialog by remember { mutableStateOf(false) }

    suspend fun fetchRamStats() = withContext(Dispatchers.IO) {
        val meminfo = RootShellHelper.readSystemFile("/proc/meminfo")
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

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            isAutdAvailable = RootShellHelper.checkFileExists("/system/bin/autd")
            bypassCharging = RootShellHelper.readSystemFile("/sys/class/power_supply/battery/input_suspend").trim() == "1"
            optimizeGameThread = RootShellHelper.readSystemFile("/data/data/com.xaozora.manager/files/autd_opt_allow").trim() == "1"
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
                  fstrim -v /data;
                ) &
            """.trimIndent()
            
            val success = RootShellHelper.executeCmd(cmd)
            if (success) {
                scope.launch {
                    snackbarHostState.showSnackbar("Flush RAM & Cache Cleared Successfully!")
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
        val surfaceContainer = MaterialTheme.colorScheme.surfaceContainer
        val dialogStyle = remember(surfaceContainer) {
            HazeStyle(
                blurRadius = 25.dp,
                noiseFactor = 0.1f,
                tints = listOf(HazeTint(surfaceContainer.copy(alpha = 0.25f)))
            )
        }

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
                        .background(Color.Transparent)
                        .clickable(enabled = false) {},
                ) {
                    Column(modifier = Modifier.padding(24.dp)) {
                        Text(
                            text = "Flush RAM?",
                            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "This will clear system cache and trim storage.",
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

            Spacer(modifier = Modifier.height(24.dp))
            Text("Quick Toggles", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
            Spacer(modifier = Modifier.height(12.dp))

            CustomToggleCard(
                title = "Bypass Charging",
                subtitle = "Stop charging while plugged in to reduce heat.",
                icon = Icons.Rounded.BatteryChargingFull,
                checked = bypassCharging,
                hazeState = hazeState,
                onCheckedChange = { newVal ->
                    scope.launch(Dispatchers.IO) {
                        if (RootShellHelper.writeSystemFile("/sys/class/power_supply/battery/input_suspend", if (newVal) "1" else "0")) {
                            bypassCharging = newVal
                            scope.launch {
                                snackbarHostState.showSnackbar(if (newVal) "Bypass Charging Enabled" else "Bypass Charging Disabled")
                            }
                        } else {
                            scope.launch {
                                snackbarHostState.showSnackbar("Failed to toggle Bypass Charging")
                            }
                        }
                    }
                }
            )

            if (isAutdAvailable) {
                Spacer(modifier = Modifier.height(12.dp))
                CustomToggleCard(
                    title = "Optimize Game Thread",
                    subtitle = "Prioritize game processes for better performance.",
                    icon = Icons.Rounded.Games,
                    checked = optimizeGameThread,
                    hazeState = hazeState,
                    onCheckedChange = { newVal ->
                        scope.launch(Dispatchers.IO) {
                            if (RootShellHelper.writeSystemFile("/data/data/com.xaozora.manager/files/autd_opt_allow", if (newVal) "1" else "0")) {
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
                    }
                )
            }
            
            Spacer(modifier = Modifier.height(100.dp))
        }
    }
}

@Composable
private fun CustomToggleCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    checked: Boolean,
    hazeState: HazeState,
    onCheckedChange: (Boolean) -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    val bgColor by animateColorAsState(if (checked) colorScheme.primaryContainer.copy(alpha = 0.25f) else colorScheme.surfaceContainer, label = "bg")
    val borderColor by animateColorAsState(if (checked) colorScheme.primary.copy(alpha = 0.4f) else colorScheme.outlineVariant.copy(alpha = 0.5f), label = "border")
    val iconBgColor by animateColorAsState(if (checked) colorScheme.primary.copy(alpha = 0.2f) else colorScheme.surfaceContainerHighest.copy(alpha = 0.5f), label = "iconBg")
    val iconColor by animateColorAsState(if (checked) colorScheme.primary else colorScheme.secondary, label = "iconColor")
    
    val switchOffset by animateDpAsState(if (checked) 24.dp else 0.dp, label = "switchOffset")
    val switchBg by animateColorAsState(if (checked) colorScheme.primary else colorScheme.surfaceContainerHighest, label = "switchBg")
    val thumbColor by animateColorAsState(if (checked) colorScheme.onPrimary else colorScheme.outline, label = "thumbColor")

    GlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .clickable { onCheckedChange(!checked) }
            .border(1.2.dp, borderColor, RoundedCornerShape(24.dp)),
        hazeState = hazeState,
        shape = RoundedCornerShape(24.dp)
    ) {
        Box(modifier = Modifier.fillMaxWidth().background(if (checked) bgColor else Color.Transparent).padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.background(iconBgColor, CircleShape).padding(12.dp)) { Icon(icon, contentDescription = null, tint = iconColor) }
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = if (checked) colorScheme.onPrimaryContainer else colorScheme.onSurface))
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(subtitle, style = MaterialTheme.typography.bodySmall.copy(color = if (checked) colorScheme.onPrimaryContainer.copy(alpha = 0.7f) else colorScheme.onSurfaceVariant))
                }
                Box(modifier = Modifier.size(52.dp, 28.dp).background(switchBg, CircleShape).border(1.dp, if (checked) colorScheme.primary else colorScheme.outlineVariant.copy(alpha = 0.5f), CircleShape).padding(4.dp), contentAlignment = Alignment.CenterStart) {
                    Box(modifier = Modifier.offset(x = switchOffset).size(20.dp).background(thumbColor, CircleShape))
                }
            }
        }
    }
}
