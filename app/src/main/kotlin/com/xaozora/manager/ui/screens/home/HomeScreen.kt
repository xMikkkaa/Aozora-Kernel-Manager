package com.xaozora.manager.ui.screens.home

import android.graphics.BlurMaskFilter
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.height
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
import androidx.compose.material.icons.rounded.DeveloperBoard
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.PowerOff
import androidx.compose.material.icons.rounded.Screenshot
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Sensors
import androidx.compose.material.icons.rounded.SettingsSystemDaydream
import androidx.compose.material.icons.rounded.Smartphone
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var systemInfo by remember { mutableStateOf<SystemInfo?>(null) }
    var isDaemonRunning by remember { mutableStateOf(false) }
    var isAutdAvailable by remember { mutableStateOf(false) }
    var daemonMethod by remember { mutableStateOf("Checking Daemon...") }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            systemInfo = SystemInfoUtils.fetchSystemInfo()
            isAutdAvailable = RootShellHelper.checkFileExists("/system/bin/autd")
            isDaemonRunning = RootShellHelper.executeCmd("pidof autd > /dev/null")
        }
    }

    LaunchedEffect(isAutdAvailable) {
        if (isAutdAvailable) {
            withContext(Dispatchers.IO) {
                while (true) {
                    val infoPath = "${context.filesDir.path}/autd_awake_method.info"
                    val method = RootShellHelper.readSystemFile(infoPath).trim()
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

    val info = systemInfo ?: SystemInfo(
        "Loading...", "-", "-", "-", "-", "-", "-", "-", "-", "-", "Checking...", "..."
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
            HeroCard(deviceModel = info.model)
    
            Spacer(modifier = Modifier.height(8.dp))

        if (isAutdAvailable) {
            DaemonServiceCard(
                isRunning = isDaemonRunning,
                hazeState = hazeState,
                onClick = {
                    scope.launch(Dispatchers.IO) {
                        if (isDaemonRunning) {
                            RootShellHelper.executeCmd("killall autd")
                        } else {
                            RootShellHelper.executeCmd("/system/bin/autd > /dev/null 2>&1 &")
                        }
                        isDaemonRunning = RootShellHelper.executeCmd("pidof autd > /dev/null")
                    }
                }
            )
        }

        Text(
            text = "System Dashboard",
            style = MaterialTheme.typography.titleLarge.copy(
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            ),
            modifier = Modifier.padding(top = 8.dp)
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            QuickStatCard(Modifier.weight(1f), "Battery", info.battery, Icons.Rounded.BatteryChargingFull, Color(0xFF4CAF50), hazeState)
            QuickStatCard(Modifier.weight(1f), "RAM", info.ram, Icons.Rounded.Memory, MaterialTheme.colorScheme.primary, hazeState)
            QuickStatCard(Modifier.weight(1f), "Uptime", info.uptime, Icons.Rounded.Timer, Color(0xFFFF9800), hazeState)
        }

        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                InfoTile(Modifier.weight(1f), "Android", info.android, Icons.Rounded.Android, Color(0xFF81C784), hazeState = hazeState)
                InfoTile(Modifier.weight(1f), "Codename", info.device, Icons.Rounded.Smartphone, hazeState = hazeState)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                InfoTile(Modifier.weight(1f), "SoC", info.soc, Icons.Rounded.DeveloperBoard, hazeState = hazeState)
                InfoTile(Modifier.weight(1f), "Display", info.resolution, Icons.Rounded.Screenshot, hazeState = hazeState)
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            InfoTile(
                Modifier.weight(1f),
                "Root Access",
                "${info.rootManager} ${info.rootVersion}",
                Icons.Rounded.AdminPanelSettings,
                isMonospace = true,
                hazeState = hazeState
            )
            InfoTile(
                Modifier.weight(1f),
                "SELinux",
                info.selinux,
                Icons.Rounded.Security,
                color = if (info.selinux.equals("Enforcing", true)) Color(0xFF4CAF50) else MaterialTheme.colorScheme.error,
                hazeState = hazeState
            )
        }

        if (isAutdAvailable) {
            FullWidthCard(
                title = "Daemon Method",
                value = daemonMethod,
                icon = Icons.Rounded.Sensors,
                isError = daemonMethod == "Daemon info unavailable" || daemonMethod == "Checking Daemon...",
                hazeState = hazeState
            )
        }

        FullWidthCard(
            title = "Kernel Information",
            value = info.kernel.ifBlank { "-" },
            icon = Icons.Rounded.SettingsSystemDaydream,
            hazeState = hazeState
        )
        
        Spacer(modifier = Modifier.height(140.dp))
        Spacer(modifier = Modifier.navigationBarsPadding())
        }
    }
}

@Composable
private fun HeroCard(deviceModel: String) {
    val infiniteTransition = rememberInfiniteTransition(label = "hero_shadow")
    val angle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2.0 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "angle"
    )

    val primaryColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
    val tertiaryColor = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.3f)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(200.dp)
            .drawBehind {
                val offsetX = 10.dp.toPx() * cos(angle)
                val offsetY = 10.dp.toPx() * sin(angle)
                drawIntoCanvas { canvas ->
                    val paint = Paint().apply {
                        color = primaryColor
                        @Suppress("DEPRECATION")
                        asFrameworkPaint().maskFilter = BlurMaskFilter(20.dp.toPx(), BlurMaskFilter.Blur.NORMAL)
                    }
                    canvas.drawRoundRect(
                        left = offsetX, top = offsetY,
                        right = size.width + offsetX, bottom = size.height + offsetY,
                        radiusX = 32.dp.toPx(), radiusY = 32.dp.toPx(),
                        paint = paint
                    )
                    paint.color = tertiaryColor
                    canvas.drawRoundRect(
                        left = -offsetX, top = -offsetY,
                        right = size.width - offsetX, bottom = size.height - offsetY,
                        radiusX = 32.dp.toPx(), radiusY = 32.dp.toPx(),
                        paint = paint
                    )
                }
            }
            .clip(RoundedCornerShape(32.dp))
            .background(
                brush = Brush.linearGradient(
                    colors = listOf(Color(0xFF311B92), Color(0xFF039BE5))
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(28.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "Running on",
                style = MaterialTheme.typography.labelLarge.copy(
                    color = Color.White.copy(alpha = 0.7f),
                    letterSpacing = MaterialTheme.typography.labelLarge.letterSpacing * 1.2
                )
            )
            Text(
                text = deviceModel,
                style = MaterialTheme.typography.headlineMedium.copy(
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            )
            Spacer(modifier = Modifier.height(8.dp))
            Box(
                modifier = Modifier
                    .background(
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
                        shape = RoundedCornerShape(16.dp)
                    )
                    .border(1.dp, Color.White.copy(alpha = 0.24f), RoundedCornerShape(16.dp))
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(
                    text = "Aozora Kernel Manager",
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
        Image(
            painter = painterResource(id = R.drawable.kai), 
            contentDescription = "Aozora Logo",
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .size(180.dp)
                .align(Alignment.BottomEnd)
                .offset(x = 20.dp, y = 20.dp) 
                .alpha(0.25f) 
        )
    }
}

@Composable
private fun DaemonServiceCard(isRunning: Boolean, hazeState: HazeState, onClick: () -> Unit) {
    val colorScheme = MaterialTheme.colorScheme
    
    GlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .clickable(onClick = onClick),
        hazeState = hazeState,
        shape = RoundedCornerShape(24.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(if (isRunning) colorScheme.primaryContainer.copy(alpha = 0.25f) else Color.Transparent)
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(
                        color = if (isRunning) colorScheme.primary.copy(alpha = 0.2f) else colorScheme.error.copy(alpha = 0.2f),
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isRunning) Icons.Rounded.Memory else Icons.Rounded.PowerOff,
                    contentDescription = null,
                    tint = if (isRunning) colorScheme.primary else colorScheme.error,
                    modifier = Modifier.size(24.dp)
                )
            }
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Daemon Service",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = if (isRunning) colorScheme.onPrimaryContainer else colorScheme.onSurface
                    )
                )
                Text(
                    text = if (isRunning) "Running (autd)" else "Stopped",
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = if (isRunning) colorScheme.onPrimaryContainer.copy(alpha = 0.7f) else colorScheme.onSurfaceVariant
                    )
                )
            }

            Box(
                modifier = Modifier
                    .size(width = 52.dp, height = 28.dp)
                    .background(
                        color = if (isRunning) colorScheme.primary else colorScheme.surfaceContainerHighest,
                        shape = CircleShape
                    )
                    .border(
                        width = 1.dp,
                        color = if (isRunning) colorScheme.primary else colorScheme.outlineVariant.copy(alpha = 0.5f),
                        shape = CircleShape
                    )
                    .padding(4.dp),
                contentAlignment = if (isRunning) Alignment.CenterEnd else Alignment.CenterStart
            ) {
                Box(
                    modifier = Modifier
                        .size(20.dp)
                        .background(if (isRunning) colorScheme.onPrimary else colorScheme.outline, CircleShape)
                )
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
    hazeState: HazeState
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
        }
    }
}
