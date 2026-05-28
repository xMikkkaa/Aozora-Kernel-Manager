package com.xaozora.manager.ui.screens.battery

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.BatteryFull
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.google.gson.Gson
import com.xaozora.manager.core.models.BatteryStats
import com.xaozora.manager.core.shell.RootShellHelper
import com.xaozora.manager.ui.components.BatteryDialog
import com.xaozora.manager.ui.components.GlassCard
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.compose.ui.platform.LocalView
import dev.chrisbanes.haze.haze
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.graphics.graphicsLayer

@Composable
fun BatteryScreen(
    hazeState: HazeState,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var batteryStats by remember { mutableStateOf<BatteryStats?>(null) }
    val prefs = context.getSharedPreferences("aozora_prefs", Context.MODE_PRIVATE)
    var showPercentageDialog by remember { mutableStateOf(false) }
    var resetPercentage by remember { mutableStateOf(prefs.getFloat("battery_reset_percentage", 90f)) }

    val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current
    val lifecycleState by lifecycleOwner.lifecycle.currentStateFlow.collectAsState()

    LaunchedEffect(lifecycleState) {
        if (lifecycleState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED)) {
            while (true) {
                withContext(Dispatchers.IO) {
                    try {
                        val file = java.io.File(context.filesDir, "battmon/battery_stats.json")
                        if (file.exists()) {
                            val content = file.readText().trim()
                            if (content.isNotBlank()) {
                                android.util.Log.d("BatteryScreen", "Raw JSON: $content")
                                val stats = Gson().fromJson(content, BatteryStats::class.java)
                                batteryStats = stats
                                android.util.Log.d("BatteryScreen", "Parsed stats: $stats")
                            } else {
                                android.util.Log.w("BatteryScreen", "JSON file is empty")
                            }
                        } else {
                            android.util.Log.w("BatteryScreen", "JSON file does not exist at ${file.absolutePath}")
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("BatteryScreen", "Error reading battery stats", e)
                    }
                }
                delay(5000)
            }
        }
    }

    BackHandler { onBack() }
    
    AnimatedVisibility(
        visible = true,
        enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 16.dp, start = 16.dp, end = 16.dp, bottom = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Rounded.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colorScheme.primary)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Battery Info",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    )
                }

                val pagerState = rememberPagerState(pageCount = { 2 })
                
                TabRow(
                    selectedTabIndex = pagerState.currentPage,
                    containerColor = Color.Transparent,
                    indicator = { tabPositions ->
                        TabRowDefaults.SecondaryIndicator(
                            modifier = Modifier.tabIndicatorOffset(tabPositions[pagerState.currentPage]),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                ) {
                    Tab(
                        selected = pagerState.currentPage == 0,
                        onClick = { coroutineScope.launch { pagerState.animateScrollToPage(0) } },
                        text = { Text("Stats") },
                        icon = { Icon(Icons.Rounded.BatteryFull, contentDescription = null) }
                    )
                    Tab(
                        selected = pagerState.currentPage == 1,
                        onClick = { coroutineScope.launch { pagerState.animateScrollToPage(1) } },
                        text = { Text("Option") },
                        icon = { Icon(Icons.Rounded.Settings, contentDescription = null) }
                    )
                }

                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize()
                ) { page ->
                    when (page) {
                        0 -> BatteryStatsTab(stats = batteryStats, hazeState = hazeState)
                        1 -> BatteryOptionTab(
                            hazeState = hazeState,
                            resetPercentage = resetPercentage,
                            onShowPercentageDialog = { showPercentageDialog = true }
                        )
                    }
                }
            }

            if (showPercentageDialog) {
                BatteryDialog(
                    hazeState = hazeState,
                    onDismiss = { showPercentageDialog = false }
                ) {
                    Text(
                        text = "Set Reset Percentage",
                        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Reset battery stats when percentage reaches at or above:",
                        style = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "${String.format("%.0f", resetPercentage)}%",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary),
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    )
                    Slider(
                        value = resetPercentage,
                        onValueChange = { resetPercentage = it },
                        valueRange = 50f..100f,
                        steps = 50
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = { showPercentageDialog = false }) {
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
                                        prefs.edit().putFloat("battery_reset_percentage", resetPercentage).apply()
                                        showPercentageDialog = false
                                    }
                                )
                                .padding(horizontal = 24.dp, vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "Save",
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

@Composable
fun BatteryStatsTab(stats: BatteryStats?, hazeState: HazeState) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (stats == null) {
            Box(modifier = Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Column
        }

        StatSectionCard(title = "Screen On", hazeState = hazeState) {
            StatRow("Duration", formatDuration(stats.screenOnDurationMs))
            StatRow("Battery Drained", String.format("%.2f mAh", stats.screenOnDischargeMah))
            StatRow("Active Drain Rate", String.format("%.2f %%/h", stats.activeDrainRatePerHr))
        }


        StatSectionCard(title = "Screen Off", hazeState = hazeState) {
            val screenOffDuration = stats.timeOnBatteryScreenOffMs
            val deepSleepMs = stats.deepSleepMs
            val awakeMs = stats.awakeScreenOffMs
            
            val deepSleepPct = if (screenOffDuration > 0) (deepSleepMs.toFloat() / screenOffDuration * 100).coerceIn(0f, 100f) else 0f
            val awakePct = if (screenOffDuration > 0) (awakeMs.toFloat() / screenOffDuration * 100).coerceIn(0f, 100f) else 0f

            StatRow("Duration", formatDuration(screenOffDuration))
            StatRow("Battery Drained", String.format("%.2f mAh", stats.screenOffDischargeMah))
            StatRow("Deep Sleep", "${formatDuration(deepSleepMs)} (${String.format("%.1f", deepSleepPct)}%)")
            StatRow("Awake Time", "${formatDuration(awakeMs)} (${String.format("%.1f", awakePct)}%)")
            StatRow("Idle Drain Rate", String.format("%.2f %%/h", stats.idleDrainRatePerHr))
        }

        StatSectionCard(title = "Raw Stats (Since Last Charge)", hazeState = hazeState) {
            StatRow("Total Run Time", formatDuration(stats.totalRunTimeRealtimeMs))
            StatRow("Total Run Time (Uptime)", formatDuration(stats.totalRunTimeUptimeMs))
            StatRow("Time on Battery", formatDuration(stats.timeOnBatteryRealtimeMs))
            StatRow("Start Clock Time", stats.startClockTime)
            StatRow("Last Learned Capacity", String.format("%.0f mAh", stats.lastLearnedCapacityMah))
            StatRow("Total Discharge", String.format("%.2f mAh", stats.dischargeMah))
            StatRow("Screen Doze Discharge", String.format("%.2f mAh", stats.screenDozeDischargeMah))
            StatRow("Device Light Doze", String.format("%.2f mAh", stats.deviceLightDozeDischargeMah))
            StatRow("Device Deep Doze", String.format("%.2f mAh", stats.deviceDeepDozeDischargeMah))
            StatRow("Connectivity Changes", stats.connectivityChanges.toString())
            StatRow("Total Full Wakelock", formatDuration(stats.totalFullWakelockTimeMs))
        }

        Spacer(modifier = Modifier.height(100.dp))
    }
}

@Composable
fun BatteryOptionTab(hazeState: HazeState, resetPercentage: Float, onShowPercentageDialog: () -> Unit) {
    val context = LocalContext.current
    val prefs = context.getSharedPreferences("aozora_prefs", Context.MODE_PRIVATE)

    var monitorService by remember { mutableStateOf(prefs.getBoolean("battery_monitor_service", true)) }
    var resetOnPercentage by remember { mutableStateOf(prefs.getBoolean("battery_reset_on_percentage", false)) }
    var resetOnPluggedIn by remember { mutableStateOf(prefs.getBoolean("battery_reset_plugged_in", false)) }
    var resetOnReboot by remember { mutableStateOf(prefs.getBoolean("battery_reset_reboot", false)) }
    var notifyIdleDrain by remember { mutableStateOf(prefs.getBoolean("battery_notify_idle_drain", false)) }
    var idleDrainWarning by remember { mutableStateOf(prefs.getFloat("battery_idle_drain_warning", 2.0f)) }
    var tempUnit by remember { mutableStateOf(prefs.getString("battery_temp_unit", "C") ?: "C") }
    var showNotifIcon by remember { mutableStateOf(prefs.getBoolean("battery_show_notif_icon", false)) }
    var showWattage by remember { mutableStateOf(prefs.getBoolean("battery_show_wattage", false)) }

    val coroutineScope = rememberCoroutineScope()

    fun <T> savePref(key: String, value: T) {
        val editor = prefs.edit()
        when (value) {
            is Boolean -> editor.putBoolean(key, value)
            is Float -> editor.putFloat(key, value)
            is String -> editor.putString(key, value)
        }
        editor.apply()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        OptionToggleCard(
            title = "Battery Monitor Service",
            subtitle = "Runs a background service to collect detailed battery statistics.",
            checked = monitorService,
            hazeState = hazeState
        ) {
            monitorService = it
            savePref("battery_monitor_service", it)
            coroutineScope.launch(Dispatchers.IO) {
                com.xaozora.manager.core.utils.NativeDaemonManager.extractAndStartDaemon(context)
                withContext(Dispatchers.Main) {
                    val msg = if (it) "xAozora Daemon (BATTMON) Started" else "xAozora Daemon (BATTMON) Stopped"
                    android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show()
                }
            }
        }

        OptionToggleCard(
            title = "Reset Stats at/above Percentage",
            subtitle = "Automatically resets battery statistics when charge level reaches a certain percentage.",
            checked = resetOnPercentage,
            hazeState = hazeState
        ) {
            resetOnPercentage = it; savePref("battery_reset_on_percentage", it)
        }
        if (resetOnPercentage) {
            GlassCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .clickable { onShowPercentageDialog() }
                    .border(1.2.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), RoundedCornerShape(24.dp)),
                hazeState = hazeState,
                shape = RoundedCornerShape(24.dp)
            ) {
                Box(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.background(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f), CircleShape).padding(12.dp)) { 
                            Icon(androidx.compose.material.icons.Icons.Rounded.Settings, contentDescription = null, tint = MaterialTheme.colorScheme.secondary) 
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Reset Stats Percentage", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface))
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("Trigger reset when battery reaches: ${String.format("%.0f", resetPercentage)}%", style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant))
                        }
                    }
                }
            }
        }

        OptionToggleCard(
            title = "Reset Stats when Plugged In",
            subtitle = "Clears current battery statistics every time the charger is connected.",
            checked = resetOnPluggedIn,
            hazeState = hazeState
        ) {
            resetOnPluggedIn = it; savePref("battery_reset_plugged_in", it)
        }

        OptionToggleCard(
            title = "Reset Stats on Reboot/Shutdown",
            subtitle = "Wipes the battery statistics when the device restarts or turns off.",
            checked = resetOnReboot,
            hazeState = hazeState
        ) {
            resetOnReboot = it; savePref("battery_reset_reboot", it)
        }

        OptionToggleCard(
            title = "Notify if Idle Drain is High",
            subtitle = "Sends an alert if battery drain exceeds the specified warning threshold while screen is off.",
            checked = notifyIdleDrain,
            hazeState = hazeState
        ) {
            notifyIdleDrain = it; savePref("battery_notify_idle_drain", it)
        }
        if (notifyIdleDrain) {
            OptionSliderCard(
                title = "Idle Drain Warning (%)",
                subtitle = "Set the threshold percentage per hour for high idle drain warnings.",
                value = idleDrainWarning,
                range = 0.5f..10.0f,
                hazeState = hazeState
            ) {
                idleDrainWarning = it; savePref("battery_idle_drain_warning", it)
            }
        }

        GlassCard(modifier = Modifier.fillMaxWidth(), hazeState = hazeState, shape = RoundedCornerShape(20.dp)) {
            Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Temperature Unit", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold))
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Choose the unit for battery temperature.", style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant))
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { tempUnit = "C"; savePref("battery_temp_unit", "C") },
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = if (tempUnit == "C") MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f) else Color.Transparent,
                            contentColor = if (tempUnit == "C") MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.secondary
                        ),
                        border = androidx.compose.foundation.BorderStroke(1.dp, if (tempUnit == "C") MaterialTheme.colorScheme.primary.copy(alpha = 0.4f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                        modifier = Modifier.padding(end = 8.dp)
                    ) { Text("°C") }
                    OutlinedButton(
                        onClick = { tempUnit = "F"; savePref("battery_temp_unit", "F") },
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = if (tempUnit == "F") MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f) else Color.Transparent,
                            contentColor = if (tempUnit == "F") MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.secondary
                        ),
                        border = androidx.compose.foundation.BorderStroke(1.dp, if (tempUnit == "F") MaterialTheme.colorScheme.primary.copy(alpha = 0.4f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    ) { Text("°F") }
                }
            }
        }

        OptionToggleCard(
            title = "Show Battery % as Notification Icon",
            subtitle = "Displays the current battery percentage continuously in the status bar notification area.",
            checked = showNotifIcon,
            hazeState = hazeState
        ) {
            showNotifIcon = it; savePref("battery_show_notif_icon", it)
        }

        OptionToggleCard(
            title = "Show Charging Ampere and Wattage",
            subtitle = "Note: Not 100% accurate, for reference only.",
            checked = showWattage,
            hazeState = hazeState
        ) {
            showWattage = it; savePref("battery_show_wattage", it)
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = {
                coroutineScope.launch(Dispatchers.IO) {
                    val path = "${context.filesDir.path}/battmon/battery_stats.json"
                    val baselinePath = "${context.filesDir.path}/battmon/battery_stats_baseline.json"
                    RootShellHelper.executeCmd("cp $path $baselinePath")
                    savePref("battery_last_reset", System.currentTimeMillis())
                    val statsFile = java.io.File(path)
                    if (statsFile.exists()) statsFile.writeText("{}")
                }
            },
            modifier = Modifier.fillMaxWidth().height(56.dp).border(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.4f), RoundedCornerShape(16.dp)),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.25f),
                contentColor = MaterialTheme.colorScheme.error
            ),
            shape = RoundedCornerShape(16.dp),
            elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp)
        ) {
            Text("Reset Stats", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
        }

        Spacer(modifier = Modifier.height(100.dp))
    }
}

@Composable
fun StatSectionCard(title: String, hazeState: HazeState, content: @Composable ColumnScope.() -> Unit) {
    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        hazeState = hazeState,
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary),
                modifier = Modifier.padding(bottom = 12.dp)
            )
            content()
        }
    }
}

@Composable
fun StatRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurfaceVariant))
        Text(text = value, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold))
    }
}

@Composable
fun OptionToggleCard(title: String, subtitle: String = "", checked: Boolean, hazeState: HazeState, onCheckedChange: (Boolean) -> Unit) {
    val colorScheme = MaterialTheme.colorScheme
    val bgColor by androidx.compose.animation.animateColorAsState(if (checked) colorScheme.primaryContainer.copy(alpha = 0.25f) else colorScheme.surfaceContainer, label = "bg")
    val borderColor by androidx.compose.animation.animateColorAsState(if (checked) colorScheme.primary.copy(alpha = 0.4f) else colorScheme.outlineVariant.copy(alpha = 0.5f), label = "border")
    val iconBgColor by androidx.compose.animation.animateColorAsState(if (checked) colorScheme.primaryContainer else colorScheme.surfaceContainerHighest.copy(alpha = 0.5f), label = "iconBg")
    val iconColor by androidx.compose.animation.animateColorAsState(if (checked) colorScheme.onPrimaryContainer else colorScheme.secondary, label = "iconColor")
    
    val switchOffset by androidx.compose.animation.core.animateDpAsState(if (checked) 24.dp else 0.dp, label = "switchOffset")
    val switchBg by androidx.compose.animation.animateColorAsState(if (checked) colorScheme.primaryContainer else colorScheme.surfaceContainerHighest, label = "switchBg")
    val thumbColor by androidx.compose.animation.animateColorAsState(if (checked) colorScheme.onPrimaryContainer else colorScheme.outline, label = "thumbColor")

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
                Box(modifier = Modifier.background(iconBgColor, CircleShape).padding(12.dp)) { 
                    Icon(androidx.compose.material.icons.Icons.Rounded.Settings, contentDescription = null, tint = iconColor) 
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = if (checked) colorScheme.onPrimaryContainer else colorScheme.onSurface))
                    if (subtitle.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(subtitle, style = MaterialTheme.typography.bodySmall.copy(color = if (checked) colorScheme.onPrimaryContainer.copy(alpha = 0.7f) else colorScheme.onSurfaceVariant))
                    }
                }
                Box(modifier = Modifier.size(52.dp, 28.dp).background(switchBg, CircleShape).border(1.dp, if (checked) colorScheme.primary.copy(alpha = 0.4f) else colorScheme.outlineVariant.copy(alpha = 0.5f), CircleShape).padding(4.dp), contentAlignment = Alignment.CenterStart) {
                    Box(modifier = Modifier.offset(x = switchOffset).size(20.dp).background(thumbColor, CircleShape))
                }
            }
        }
    }
}

@Composable
fun OptionSliderCard(title: String, subtitle: String = "", value: Float, range: ClosedFloatingPointRange<Float>, hazeState: HazeState, onValueChange: (Float) -> Unit) {
    GlassCard(modifier = Modifier.fillMaxWidth(), hazeState = hazeState, shape = RoundedCornerShape(20.dp)) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column(modifier = Modifier.weight(1f).padding(end = 16.dp)) {
                    Text(text = title, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold))
                    if (subtitle.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(text = subtitle, style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant))
                    }
                }
                Text(text = String.format("%.1f", value), style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary))
            }
            Spacer(modifier = Modifier.height(8.dp))
            Slider(value = value, onValueChange = onValueChange, valueRange = range)
        }
    }
}

fun formatDuration(ms: Long): String {
    if (ms <= 0) return "0s"
    val totalSecs = ms / 1000
    val h = totalSecs / 3600
    val m = (totalSecs % 3600) / 60
    val s = totalSecs % 60
    
    return buildString {
        if (h > 0) append("${h}h ")
        if (m > 0 || h > 0) append("${m}m ")
        append("${s}s")
    }.trim()
}
