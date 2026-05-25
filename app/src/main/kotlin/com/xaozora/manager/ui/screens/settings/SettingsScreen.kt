package com.xaozora.manager.ui.screens.settings

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BrightnessAuto
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.rounded.AutoMode
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import com.xaozora.manager.core.utils.NativeDaemonManager
import com.xaozora.manager.ui.components.GlassCard
import com.xaozora.manager.ui.screens.about.AboutScreen
import com.xaozora.manager.ui.theme.AppThemeMode
import com.xaozora.manager.ui.theme.LocalThemeManager
import dev.chrisbanes.haze.HazeState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun SettingsScreen(
    hazeState: HazeState,
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = remember { context.getSharedPreferences("aozora_prefs", Context.MODE_PRIVATE) }
    val themeManager = LocalThemeManager.current
    val currentTheme by themeManager.themeMode.collectAsState()
    
    var autdEnabled by remember { mutableStateOf(prefs.getBoolean("autd_enabled", true)) }
    var showAbout by remember { mutableStateOf(false) }
    var awakeMethod by remember { mutableStateOf("") }

    LaunchedEffect(autdEnabled) {
        if (autdEnabled) {
            while(true) {
                withContext(Dispatchers.IO) {
                    try {
                        val file = java.io.File("/data/data/com.xaozora.manager/files/autd/autd_awake_method.info")
                        val content = if (file.exists()) file.readText().trim() else ""
                        withContext(Dispatchers.Main) { awakeMethod = content }
                    } catch (e: Exception) {
                    }
                }
                kotlinx.coroutines.delay(2000)
            }
        }
    }

    BackHandler(enabled = showAbout) {
        showAbout = false
    }

    Box(modifier = modifier.fillMaxSize()) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp)
            ) {
                Spacer(modifier = Modifier.padding(WindowInsets.statusBars.asPaddingValues()))
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Settings",
                    style = MaterialTheme.typography.headlineLarge.copy(
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                )
                Spacer(modifier = Modifier.height(24.dp))

                Text("Daemon", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                Spacer(modifier = Modifier.height(12.dp))

                SettingsToggleCard(
                    title = "Aozora Automation Daemon",
                    subtitle = "Enable automatic kernel tuning",
                    icon = Icons.Rounded.AutoMode,
                    checked = autdEnabled,
                    hazeState = hazeState,
                    onCheckedChange = { newVal ->
                        autdEnabled = newVal
                        prefs.edit(commit = true) { putBoolean("autd_enabled", newVal) }
                        scope.launch(Dispatchers.IO) {
                            NativeDaemonManager.extractAndStartDaemon(context, newVal)
                            scope.launch {
                                snackbarHostState.showSnackbar(
                                    if (newVal) "xAozora Daemon (AUTD) Started" else "xAozora Daemon (AUTD Disabled)"
                                )
                            }
                        }
                    },
                    expandableContent = {
                        val textToShow = if (awakeMethod.isBlank()) {
                            "Running AUTD..."
                        } else {
                            "Awake method: $awakeMethod"
                        }
                        
                        Column(modifier = Modifier.padding(top = 16.dp)) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                            Spacer(modifier = Modifier.height(12.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Rounded.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = textToShow,
                                    style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                                )
                            }
                        }
                    }
                )
                
                Spacer(modifier = Modifier.height(24.dp))

                Text("App", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                Spacer(modifier = Modifier.height(12.dp))

                GlassCard(
                    modifier = Modifier.fillMaxWidth().height(70.dp),
                    hazeState = hazeState,
                    shape = RoundedCornerShape(50.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ThemeItem("Auto", Icons.Outlined.BrightnessAuto, currentTheme == AppThemeMode.AUTO) { themeManager.setThemeMode(AppThemeMode.AUTO) }
                        ThemeItem("Light", Icons.Outlined.LightMode, currentTheme == AppThemeMode.LIGHT) { themeManager.setThemeMode(AppThemeMode.LIGHT) }
                        ThemeItem("Dark", Icons.Outlined.DarkMode, currentTheme == AppThemeMode.DARK) { themeManager.setThemeMode(AppThemeMode.DARK) }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))

                SettingsActionCard(
                    title = "About",
                    subtitle = "App info, updates, and credits",
                    icon = Icons.Outlined.Info,
                    hazeState = hazeState,
                    onClick = { showAbout = true }
                )

                Spacer(modifier = Modifier.height(140.dp))
                Spacer(modifier = Modifier.navigationBarsPadding())
            }
        }

        AnimatedVisibility(
            visible = showAbout,
            enter = slideInVertically(initialOffsetY = { it }),
            exit = slideOutVertically(targetOffsetY = { it })
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                AboutScreen(hazeState = hazeState, snackbarHostState = snackbarHostState)
            }
        }
    }
}

@Composable
private fun ThemeItem(label: String, icon: ImageVector, isSelected: Boolean, onClick: () -> Unit) {
    val colorScheme = MaterialTheme.colorScheme
    val bgColor by animateColorAsState(if (isSelected) colorScheme.primaryContainer.copy(alpha = 0.25f) else Color.Transparent, label = "bg")
    val borderColor by animateColorAsState(if (isSelected) colorScheme.primary.copy(alpha = 0.4f) else Color.Transparent, label = "border")
    val contentColor by animateColorAsState(if (isSelected) colorScheme.onPrimaryContainer else colorScheme.secondary, label = "content")

    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(30.dp))
            .background(bgColor)
            .border(1.dp, borderColor, RoundedCornerShape(30.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = if (isSelected) 20.dp else 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = contentColor)
        if (isSelected) {
            Spacer(modifier = Modifier.width(8.dp))
            Text(label, style = MaterialTheme.typography.bodyMedium.copy(color = contentColor, fontWeight = FontWeight.Bold))
        }
    }
}

@Composable
private fun SettingsToggleCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    checked: Boolean,
    hazeState: HazeState,
    onCheckedChange: (Boolean) -> Unit,
    expandableContent: (@Composable () -> Unit)? = null
) {
    val colorScheme = MaterialTheme.colorScheme
    val bgColor by animateColorAsState(if (checked) colorScheme.primaryContainer.copy(alpha = 0.25f) else colorScheme.surfaceContainer, label = "bg")
    val borderColor by animateColorAsState(if (checked) colorScheme.primary.copy(alpha = 0.4f) else colorScheme.outlineVariant.copy(alpha = 0.5f), label = "border")
    val iconBgColor by animateColorAsState(if (checked) colorScheme.primaryContainer else colorScheme.surfaceContainerHighest.copy(alpha = 0.5f), label = "iconBg")
    val iconColor by animateColorAsState(if (checked) colorScheme.onPrimaryContainer else colorScheme.secondary, label = "iconColor")
    
    val switchOffset by animateDpAsState(if (checked) 24.dp else 0.dp, label = "switchOffset")
    val switchBg by animateColorAsState(if (checked) colorScheme.primaryContainer else colorScheme.surfaceContainerHighest, label = "switchBg")
    val thumbColor by animateColorAsState(if (checked) colorScheme.onPrimaryContainer else colorScheme.outline, label = "thumbColor")

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
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.background(iconBgColor, CircleShape).padding(12.dp)) { Icon(icon, contentDescription = null, tint = iconColor) }
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(title, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = if (checked) colorScheme.onPrimaryContainer else colorScheme.onSurface))
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(subtitle, style = MaterialTheme.typography.bodySmall.copy(color = if (checked) colorScheme.onPrimaryContainer.copy(alpha = 0.7f) else colorScheme.onSurfaceVariant))
                    }
                    Box(modifier = Modifier.size(52.dp, 28.dp).background(switchBg, CircleShape).border(1.dp, if (checked) colorScheme.primary.copy(alpha = 0.4f) else colorScheme.outlineVariant.copy(alpha = 0.5f), CircleShape).padding(4.dp), contentAlignment = Alignment.CenterStart) {
                        Box(modifier = Modifier.offset(x = switchOffset).size(20.dp).background(thumbColor, CircleShape))
                    }
                }
                
                if (expandableContent != null) {
                    AnimatedVisibility(visible = checked) {
                        expandableContent()
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsActionCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    hazeState: HazeState,
    onClick: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme

    GlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .clickable { onClick() }
            .border(1.2.dp, colorScheme.outlineVariant.copy(alpha = 0.5f), RoundedCornerShape(24.dp)),
        hazeState = hazeState,
        shape = RoundedCornerShape(24.dp)
    ) {
        Box(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.background(colorScheme.surfaceContainerHighest.copy(alpha = 0.5f), CircleShape).padding(12.dp)) { 
                    Icon(icon, contentDescription = null, tint = colorScheme.secondary) 
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = colorScheme.onSurface))
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(subtitle, style = MaterialTheme.typography.bodySmall.copy(color = colorScheme.onSurfaceVariant))
                }
                Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null, tint = colorScheme.onSurfaceVariant)
            }
        }
    }
}
