package com.xaozora.manager.ui.screens.home

import android.graphics.BlurMaskFilter
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AdminPanelSettings
import androidx.compose.material.icons.rounded.Android
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Thermostat
import androidx.compose.material.icons.rounded.BatteryFull
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.DeveloperBoard
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.PowerOff
import androidx.compose.material.icons.rounded.Screenshot
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Sensors
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SettingsSystemDaydream
import androidx.compose.material.icons.rounded.Smartphone
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import com.xaozora.manager.core.shell.RootShellHelper
import com.xaozora.manager.core.utils.SystemInfo
import com.xaozora.manager.core.utils.SystemInfoUtils
import com.xaozora.manager.ui.components.GlassCard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.cos
import kotlin.math.sin
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.draw.alpha
import androidx.compose.foundation.layout.navigationBarsPadding
import com.xaozora.manager.R
import dev.chrisbanes.haze.HazeState



@Composable
fun HomeScreen(
    hazeState: HazeState,
    modifier: Modifier = Modifier,
    onNavigateToBattery: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var systemInfo by remember { mutableStateOf<SystemInfo?>(null) }
    var isDaemonRunning by remember { mutableStateOf(false) }
    var isAutdAvailable by remember { mutableStateOf(false) }
    var daemonMethod by remember { mutableStateOf("Checking Daemon...") }

    var cpuLoad by remember { mutableStateOf(0.45f) }
    var gpuFreq by remember { mutableStateOf("400 MHz") }
    var gpuLoad by remember { mutableStateOf(0.2f) }
    var ramUsed by remember { mutableStateOf("4.2 GB") }
    var ramTotal by remember { mutableStateOf("8.0 GB") }
    var swapUsed by remember { mutableStateOf("1.1 GB") }
    var swapTotal by remember { mutableStateOf("2.0 GB") }
    var ramProgress by remember { mutableStateOf(0f) }
    var swapProgress by remember { mutableStateOf(0f) }
    
    var coreFreqs by remember { mutableStateOf(List(8) { "0 MHz" }) }
    var coreProgress by remember { mutableStateOf(List(8) { 0f }) }
    
    var batteryLevel by remember { mutableStateOf(85) }
    var batteryCurrent by remember { mutableStateOf("-272mA") }
    var batteryTemp by remember { mutableStateOf("33.3°C") }

    val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current
    val lifecycleState by lifecycleOwner.lifecycle.currentStateFlow.collectAsState()

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            systemInfo = SystemInfoUtils.fetchSystemInfo()
            isAutdAvailable = RootShellHelper.checkFileExists("${context.filesDir.path}/xaozora_daemon")
        }
        com.xaozora.manager.core.utils.NativeDaemonManager.extractAndStartDaemon(context)
    }

    LaunchedEffect(lifecycleState) {
        if (lifecycleState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED)) {
            while (true) {
                val running = withContext(Dispatchers.IO) { RootShellHelper.executeCmd("ps -A | grep 'xaozora_daemon.*--enable-autd' > /dev/null") }
                isDaemonRunning = running
                delay(3000)
            }
        }
    }

    LaunchedEffect(isAutdAvailable, lifecycleState) {
        if (isAutdAvailable && lifecycleState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED)) {
            withContext(Dispatchers.IO) {
                while (true) {
                    val infoPath = "${context.filesDir.path}/autd/autd_awake_method.info"
                    val method = try { java.io.File(infoPath).readText().trim() } catch (e: Exception) { "" }
                    daemonMethod = if (method.isNotBlank() && !method.contains("No such file", true) && !method.contains("error", true)) {
                        method
                    } else {
                        "Daemon info unavailable"
                    }
                    delay(2000)
                }
            }
        }
    }

    val hardwarePoller = remember { com.xaozora.manager.core.utils.HardwarePoller() }
    var showSystemInfoDialog by remember { androidx.compose.runtime.mutableStateOf(false) }
    
    var showCpuControlDialog by remember { androidx.compose.runtime.mutableStateOf(false) }
    var littleConfig by remember { androidx.compose.runtime.mutableStateOf<com.xaozora.manager.core.utils.CpuClusterConfig?>(null) }
    var bigConfig by remember { androidx.compose.runtime.mutableStateOf<com.xaozora.manager.core.utils.CpuClusterConfig?>(null) }
    
    var showGpuControlDialog by remember { androidx.compose.runtime.mutableStateOf(false) }
    var gpuConfig by remember { androidx.compose.runtime.mutableStateOf<com.xaozora.manager.core.utils.GpuConfig?>(null) }
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(showCpuControlDialog) {
        if (showCpuControlDialog) {
            withContext(Dispatchers.IO) {
                littleConfig = com.xaozora.manager.core.utils.CpuControlUtils.getClusterConfig(0, "LITTLE")
                bigConfig = com.xaozora.manager.core.utils.CpuControlUtils.getClusterConfig(4, "BIG")
            }
        }
    }

    LaunchedEffect(showGpuControlDialog) {
        if (showGpuControlDialog) {
            withContext(Dispatchers.IO) {
                gpuConfig = com.xaozora.manager.core.utils.GpuControlUtils.getGpuConfig()
            }
        }
    }

    LaunchedEffect(lifecycleState) {
        if (lifecycleState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED)) {
            while (true) {
                val metrics = hardwarePoller.poll()
                cpuLoad = metrics.cpuLoad
                coreFreqs = metrics.coreFreqs
                coreProgress = metrics.coreProgress
                gpuLoad = metrics.gpuLoad
                gpuFreq = metrics.gpuFreq
                ramUsed = metrics.ramUsed
                ramTotal = metrics.ramTotal
                ramProgress = metrics.ramProgress
                swapUsed = metrics.swapUsed
                swapTotal = metrics.swapTotal
                swapProgress = metrics.swapProgress
                batteryLevel = metrics.batteryLevel
                batteryTemp = metrics.batteryTemp
                batteryCurrent = metrics.batteryCurrent
                
                delay(1000)
            }
        }
    }

    val info = systemInfo ?: SystemInfo(
        "Loading...", "-", "-", "-", "-", "-", "-", "-", "-", "-", "Checking...", "...", "-", "-", "-", "-", "-", "-", "-", "-"
    )

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(modifier = Modifier.padding(WindowInsets.statusBars.asPaddingValues()))
            Spacer(modifier = Modifier.height(16.dp))
            HeroCard(deviceModel = info.model, hazeState = hazeState)
    
            Spacer(modifier = Modifier.height(8.dp))

        GlassCard(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .clickable { showSystemInfoDialog = true },
            hazeState = hazeState,
            shape = RoundedCornerShape(20.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(Icons.Rounded.Info, contentDescription = null, modifier = Modifier.size(24.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(12.dp))
                Text("Show System Info", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.primary)
            }
        }

        if (showSystemInfoDialog) {
            com.xaozora.manager.ui.components.SystemInfoDialog(
                hazeState = hazeState,
                info = info,
                onDismiss = { showSystemInfoDialog = false }
            )
        }

        if (showCpuControlDialog && littleConfig != null && bigConfig != null) {
            com.xaozora.manager.ui.components.CpuControlDialog(
                hazeState = hazeState,
                littleConfig = littleConfig!!,
                bigConfig = bigConfig!!,
                onDismiss = {
                    showCpuControlDialog = false
                    littleConfig = null
                    bigConfig = null
                },
                onApply = { lMin, lMax, lGov, bMin, bMax, bGov ->
                    coroutineScope.launch(Dispatchers.IO) {
                        com.xaozora.manager.core.utils.CpuControlUtils.applyClusterConfig(0, lMin, lMax, lGov)
                        com.xaozora.manager.core.utils.CpuControlUtils.applyClusterConfig(4, bMin, bMax, bGov)
                    }
                    showCpuControlDialog = false
                    littleConfig = null
                    bigConfig = null
                }
            )
        }

        if (showGpuControlDialog && gpuConfig != null) {
            com.xaozora.manager.ui.components.GpuControlDialog(
                hazeState = hazeState,
                gpuConfig = gpuConfig!!,
                onDismiss = { 
                    showGpuControlDialog = false
                    gpuConfig = null
                },
                onApply = { minF, maxF, gov, boost ->
                    coroutineScope.launch(Dispatchers.IO) {
                        com.xaozora.manager.core.utils.GpuControlUtils.applyGpuConfig(minF, maxF, gov, boost)
                    }
                    showGpuControlDialog = false
                    gpuConfig = null
                }
            )
        }

        Text(
            text = "System Metrics",
            style = MaterialTheme.typography.titleLarge.copy(
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            ),
            modifier = Modifier.padding(top = 8.dp)
        )

        Row(
            modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Max),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            GlassCard(modifier = Modifier.weight(1f).fillMaxHeight().clickable { showCpuControlDialog = true }, hazeState = hazeState, shape = RoundedCornerShape(20.dp)) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val animatedCpuLoad by animateFloatAsState(targetValue = cpuLoad, animationSpec = tween(800), label = "cpu")
                    CircularProgressIndicator(
                        progress = { animatedCpuLoad },
                        modifier = Modifier.size(48.dp),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text("CPU", style = MaterialTheme.typography.labelMedium)
                        Text("${(cpuLoad * 100).toInt()}% Load", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                    }
                }
            }
            GlassCard(modifier = Modifier.weight(1f).fillMaxHeight().clickable { showGpuControlDialog = true }, hazeState = hazeState, shape = RoundedCornerShape(20.dp)) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val animatedGpuLoad by animateFloatAsState(targetValue = gpuLoad, animationSpec = tween(800), label = "gpu")
                    CircularProgressIndicator(
                        progress = { animatedGpuLoad },
                        modifier = Modifier.size(48.dp),
                        color = MaterialTheme.colorScheme.tertiary,
                        trackColor = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.2f)
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text("GPU", style = MaterialTheme.typography.labelMedium)
                        Text(gpuFreq, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                    }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Max),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            GlassCard(modifier = Modifier.weight(1f).fillMaxHeight(), hazeState = hazeState, shape = RoundedCornerShape(20.dp)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("RAM: $ramUsed / $ramTotal", style = MaterialTheme.typography.labelMedium)
                    Spacer(modifier = Modifier.height(8.dp))
                    val animatedRamProgress by animateFloatAsState(targetValue = ramProgress, animationSpec = tween(800), label = "ram")
                    LinearProgressIndicator(
                        progress = { animatedRamProgress },
                        modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                    )
                }
            }
            
            val isZramEmpty = swapTotal == "0.0 GB" || swapTotal == "0 GB"
            if (!isZramEmpty) {
                GlassCard(modifier = Modifier.weight(1f).fillMaxHeight(), hazeState = hazeState, shape = RoundedCornerShape(20.dp)) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("ZRAM: $swapUsed / $swapTotal", style = MaterialTheme.typography.labelMedium)
                        Spacer(modifier = Modifier.height(8.dp))
                        val animatedSwapProgress by animateFloatAsState(targetValue = swapProgress, animationSpec = tween(800), label = "zram")
                        LinearProgressIndicator(
                            progress = { animatedSwapProgress },
                            modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                            color = MaterialTheme.colorScheme.tertiary,
                            trackColor = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.2f)
                        )
                    }
                }
            }
        }

        GlassCard(modifier = Modifier.fillMaxWidth(), hazeState = hazeState, shape = RoundedCornerShape(20.dp)) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Core Frequencies", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                Spacer(modifier = Modifier.height(12.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Column(modifier = Modifier.weight(1f)) {
                        coreFreqs.take(4).forEachIndexed { index, freq ->
                            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text("CPU $index", style = MaterialTheme.typography.labelSmall, modifier = Modifier.width(40.dp))
                                val prog = coreProgress.getOrElse(index) { 0f }
                                val animatedCoreProgress by animateFloatAsState(targetValue = prog, animationSpec = tween(800), label = "core_$index")
                                LinearProgressIndicator(
                                    progress = { animatedCoreProgress },
                                    modifier = Modifier.weight(1f).padding(horizontal = 8.dp).height(4.dp).clip(RoundedCornerShape(2.dp)),
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(freq, style = MaterialTheme.typography.labelSmall, modifier = Modifier.width(60.dp), textAlign = TextAlign.End)
                            }
                        }
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        coreFreqs.drop(4).forEachIndexed { index, freq ->
                            val coreId = index + 4
                            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text("CPU $coreId", style = MaterialTheme.typography.labelSmall, modifier = Modifier.width(40.dp))
                                val prog = coreProgress.getOrElse(coreId) { 0f }
                                val animatedCoreProgress by animateFloatAsState(targetValue = prog, animationSpec = tween(800), label = "core_$coreId")
                                LinearProgressIndicator(
                                    progress = { animatedCoreProgress },
                                    modifier = Modifier.weight(1f).padding(horizontal = 8.dp).height(4.dp).clip(RoundedCornerShape(2.dp)),
                                    color = MaterialTheme.colorScheme.tertiary
                                )
                                Text(freq, style = MaterialTheme.typography.labelSmall, modifier = Modifier.width(60.dp), textAlign = TextAlign.End)
                            }
                        }
                    }
                }
            }
        }

        GlassCard(
            modifier = Modifier.fillMaxWidth().clickable { onNavigateToBattery() },
            hazeState = hazeState, 
            shape = RoundedCornerShape(20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(imageVector = Icons.Rounded.BatteryChargingFull, contentDescription = "Battery", tint = Color(0xFF4CAF50), modifier = Modifier.size(28.dp))
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("$batteryLevel%", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(imageVector = Icons.Rounded.Bolt, contentDescription = "Current", tint = Color(0xFFFF9800), modifier = Modifier.size(28.dp))
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(batteryCurrent, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(imageVector = Icons.Rounded.Thermostat, contentDescription = "Temperature", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(28.dp))
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(batteryTemp, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                }
            }
        }
        
        Spacer(modifier = Modifier.height(140.dp))
        Spacer(modifier = Modifier.navigationBarsPadding())
        }
    }
}

@Composable
private fun HeroCard(deviceModel: String, hazeState: HazeState) {
    val infiniteTransition = rememberInfiniteTransition(label = "hero_border")
    val angle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "angle"
    )

    val primaryColor = MaterialTheme.colorScheme.primary
    val tertiaryColor = MaterialTheme.colorScheme.tertiary

    GlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .drawWithContent {
                drawContent()
                val center = androidx.compose.ui.geometry.Offset(size.width / 2, size.height / 2)
                val shader = android.graphics.SweepGradient(
                    center.x, center.y,
                    intArrayOf(
                        android.graphics.Color.TRANSPARENT,
                        primaryColor.toArgb(),
                        tertiaryColor.toArgb(),
                        android.graphics.Color.TRANSPARENT
                    ),
                    floatArrayOf(0f, 0.4f, 0.6f, 1f)
                )
                val matrix = android.graphics.Matrix()
                matrix.postRotate(angle, center.x, center.y)
                shader.setLocalMatrix(matrix)

                drawRoundRect(
                    brush = androidx.compose.ui.graphics.ShaderBrush(shader),
                    size = size,
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(20.dp.toPx(), 20.dp.toPx()),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx())
                )
            },
        hazeState = hazeState,
        shape = RoundedCornerShape(20.dp)
    ) {
        Box(modifier = Modifier.fillMaxWidth().height(160.dp)) {
            Image(
                painter = painterResource(id = R.drawable.kai), 
                contentDescription = "Aozora Logo",
                contentScale = ContentScale.Fit,
                colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(MaterialTheme.colorScheme.primary),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(180.dp)
                    .offset(x = 20.dp, y = 30.dp)
                    .alpha(0.1f)
            )
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "Running on",
                    style = MaterialTheme.typography.labelLarge.copy(
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        letterSpacing = MaterialTheme.typography.labelLarge.letterSpacing * 1.2
                    )
                )
                Text(
                    text = deviceModel,
                    style = MaterialTheme.typography.headlineLarge.copy(
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold
                    )
                )
                Spacer(modifier = Modifier.height(12.dp))
                Box(
                    modifier = Modifier
                        .background(
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                            shape = RoundedCornerShape(12.dp)
                        )
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = "Aozora Kernel Manager",
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
        }
    }
}


@Composable
private fun QuickStatCard(
    modifier: Modifier = Modifier,
    title: String,
    value: String,
    icon: ImageVector,
    iconColor: Color,
    hazeState: HazeState
) {
    GlassCard(
        modifier = modifier,
        hazeState = hazeState,
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(imageVector = icon, contentDescription = title, tint = iconColor, modifier = Modifier.size(28.dp))
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
            )
        }
    }
}

@Composable
private fun InfoTile(
    modifier: Modifier = Modifier,
    title: String,
    value: String,
    icon: ImageVector,
    color: Color? = null,
    isMonospace: Boolean = false,
    hazeState: HazeState
) {
    val activeColor = color ?: MaterialTheme.colorScheme.primary
    GlassCard(
        modifier = modifier,
        hazeState = hazeState,
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .background(activeColor.copy(alpha = 0.2f), RoundedCornerShape(10.dp))
                    .padding(8.dp)
            ) {
                Icon(imageVector = icon, contentDescription = title, tint = activeColor, modifier = Modifier.size(18.dp))
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = value,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontWeight = FontWeight.Bold,
                        fontFamily = if (isMonospace) FontFamily.Monospace else null
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun FullWidthCard(
    title: String,
    value: String,
    icon: ImageVector,
    isError: Boolean = false,
    hazeState: HazeState,
    trailingContent: @Composable (() -> Unit)? = null
) {
    val activeColor = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        hazeState = hazeState,
        shape = RoundedCornerShape(20.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .background(activeColor.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
                    .padding(12.dp)
            ) {
                Icon(imageVector = icon, contentDescription = title, tint = activeColor, modifier = Modifier.size(24.dp))
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelMedium.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = value,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        color = activeColor
                    )
                )
            }
            if (trailingContent != null) {
                Spacer(modifier = Modifier.width(16.dp))
                trailingContent()
            }
        }
    }
}


