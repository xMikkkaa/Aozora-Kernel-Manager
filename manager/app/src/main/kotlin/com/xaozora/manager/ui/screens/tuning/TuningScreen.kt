package com.xaozora.manager.ui.screens.tuning

import android.graphics.BlurMaskFilter
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Balance
import androidx.compose.material.icons.rounded.BatterySaver
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.RocketLaunch
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material.icons.rounded.VideogameAsset
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.navigationBarsPadding
import com.xaozora.manager.core.shell.RootShellHelper
import com.xaozora.manager.ui.components.GlassCard
import com.xaozora.manager.ui.components.ProfileEditorDialog
import dev.chrisbanes.haze.HazeState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.cos
import kotlin.math.sin

data class TuningProfile(
    val id: String,
    val name: String,
    val icon: ImageVector
)

val profiles = listOf(
    TuningProfile("powersave", "Power Save", Icons.Rounded.BatterySaver),
    TuningProfile("balance", "Balance", Icons.Rounded.Balance),
    TuningProfile("gaming", "Gaming", Icons.Rounded.SportsEsports),
    TuningProfile("gaming2", "Gaming 2", Icons.Rounded.VideogameAsset),
    TuningProfile("performance", "Performance", Icons.Rounded.RocketLaunch),
    TuningProfile("cachecleaner", "Cache Cleaner", Icons.Rounded.CleaningServices)
)

@Composable
fun TuningScreen(
    hazeState: HazeState,
    snackbarHostState: SnackbarHostState,
    onDevModeClickProvider: ((onClick: () -> Unit, isDevMode: Boolean) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isAutdAvailable by remember { mutableStateOf(false) }
    var moduleName by remember { mutableStateOf<String?>(null) }
    var moduleVersion by remember { mutableStateOf<String?>(null) }
    val profileAvailability = remember { mutableStateMapOf<String, Boolean>() }
    var processingProfile by remember { mutableStateOf<String?>(null) }
    var activeProfileId by remember { mutableStateOf<String?>(null) }

    var isDeveloperMode by remember { mutableStateOf(false) }
    var hasModifiedProfiles by remember { mutableStateOf(false) }
    var editingProfile by remember { mutableStateOf<TuningProfile?>(null) }
    var editingProfileContent by remember { mutableStateOf("") }
    var moduleBasePath by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(onDevModeClickProvider, isDeveloperMode, moduleName) {
        if (moduleName != null) {
            onDevModeClickProvider?.invoke({
                if (isDeveloperMode) {
                    isDeveloperMode = false
                    if (hasModifiedProfiles) {
                        scope.launch {
                            snackbarHostState.showSnackbar("Profile modified. Please reboot to apply changes.")
                        }
                    }
                } else {
                    isDeveloperMode = true
                    hasModifiedProfiles = false
                }
            }, isDeveloperMode)
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "profile_shadow")
    val angle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2.0 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "angle"
    )

    val prefs = remember { context.getSharedPreferences("aozora_prefs", android.content.Context.MODE_PRIVATE) }
    var isLoading by remember { mutableStateOf(true) }
    
    DisposableEffect(context) {
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { sharedPreferences, key ->
            if (key == "autd_enabled") {
                scope.launch(Dispatchers.IO) {
                    val autdExists = RootShellHelper.checkFileExists("${context.filesDir.path}/xaozora_daemon")
                    val enabled = sharedPreferences.getBoolean("autd_enabled", true)
                    isAutdAvailable = autdExists && enabled
                }
            }
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose {
            prefs.unregisterOnSharedPreferenceChangeListener(listener)
        }
    }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val autdExists = RootShellHelper.checkFileExists("${context.filesDir.path}/xaozora_daemon")
            isAutdAvailable = autdExists && prefs.getBoolean("autd_enabled", true)

            val modulePropPath = RootShellHelper.executeCmdAndGetOutput(
                "grep -il 'id=.*aozora' /data/adb/modules/*/module.prop 2>/dev/null | head -n 1"
            ).trim()
            
            val propOutput = if (modulePropPath.isNotEmpty()) {
                RootShellHelper.executeCmdAndGetOutput("cat $modulePropPath")
            } else ""
            
            if (propOutput.isNotBlank() && propOutput.contains("aozora", ignoreCase = true)) {
                moduleBasePath = modulePropPath.substringBeforeLast("/") + "/system/bin"
                var mName = "Aozora Module"
                var mVersion = "Unknown"
                propOutput.lines().forEach { line ->
                    if (line.startsWith("name=")) mName = line.substringAfter("name=").trim()
                    if (line.startsWith("version=")) mVersion =
                        line.substringAfter("version=").trim()
                }
                moduleName = mName
                moduleVersion = mVersion
            }

            profiles.forEach { profile ->
                profileAvailability[profile.id] =
                    RootShellHelper.checkFileExists("/system/bin/${profile.id}")
            }
        }
        isLoading = false
    }

    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    val lifecycleState by lifecycleOwner.lifecycle.currentStateFlow.collectAsState()

    LaunchedEffect(isAutdAvailable, lifecycleState) {
        if (lifecycleState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED)) {
            withContext(Dispatchers.IO) {
                if (isAutdAvailable) {
                    while (true) {
                        try {
                            val status = java.io.File(context.filesDir, "autd/autd_base_mode").readText().trim()
                            if (status.isNotBlank()) activeProfileId = status
                        } catch (e: Exception) {}
                        delay(1000)
                    }
                } else {
                    activeProfileId = prefs.getString("manual_active_profile", null)
                }
            }
        }
    }

    val visibleProfiles = profiles.filter {
        if (it.id == "gaming" || it.id == "gaming2") profileAvailability[it.id] == true else true
    }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column {
                    Spacer(modifier = Modifier.padding(WindowInsets.statusBars.asPaddingValues()))
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Tuning Dashboard",
                        style = MaterialTheme.typography.headlineLarge.copy(
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        ),
                        modifier = Modifier.padding(bottom = 24.dp)
                    )


                    moduleName?.let { name ->
                        GlassCard(
                            hazeState = hazeState,
                            shape = RoundedCornerShape(20.dp),
                            modifier = Modifier.fillMaxWidth().padding(bottom = if (isDeveloperMode) 12.dp else 24.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(20.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .background(
                                            MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                                            CircleShape
                                        )
                                        .padding(12.dp)
                                ) {
                                    Icon(
                                        Icons.Rounded.Memory,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(32.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(16.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = name,
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "Version: $moduleVersion",
                                        style = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    )
                                }
                                Icon(
                                    Icons.Rounded.CheckCircle,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }

                        if (isDeveloperMode) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 24.dp)
                                    .background(
                                        MaterialTheme.colorScheme.tertiary.copy(alpha = 0.15f),
                                        RoundedCornerShape(16.dp)
                                    )
                                    .border(
                                        1.dp,
                                        MaterialTheme.colorScheme.tertiary.copy(alpha = 0.3f),
                                        RoundedCornerShape(16.dp)
                                    )
                                    .padding(horizontal = 16.dp, vertical = 12.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Rounded.Edit,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.tertiary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column {
                                        Text(
                                            text = "Developer Mode",
                                            style = MaterialTheme.typography.labelLarge.copy(
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.tertiary
                                            )
                                        )
                                        Text(
                                            text = "Tap a profile card to edit its shell script",
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (isLoading) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 40.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        androidx.compose.material3.CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    }
                }
            } else {
                items(visibleProfiles) { profile ->
                val isAvailable = profileAvailability[profile.id] == true
                val isActive = activeProfileId == profile.id
                val isProcessing = processingProfile == profile.id
                val colorScheme = MaterialTheme.colorScheme

                val borderColor by animateColorAsState(
                    targetValue = if (isProcessing || isActive) colorScheme.primary else colorScheme.outlineVariant,
                    label = "borderColor"
                )
                val borderWidth by animateDpAsState(
                    targetValue = if (isProcessing || isActive) 2.dp else 1.dp,
                    label = "borderWidth"
                )

                val primaryShadowColor = colorScheme.primary.copy(alpha = 0.4f)
                val tertiaryShadowColor = colorScheme.tertiary.copy(alpha = 0.3f)

                Box(
                    modifier = Modifier
                        .alpha(if (isAvailable) 1f else 0.5f)
                        .aspectRatio(1f)
                        .then(
                            if (isProcessing || isActive) Modifier.drawBehind {
                                val offsetX = 8.dp.toPx() * cos(angle)
                                val offsetY = 8.dp.toPx() * sin(angle)
                                drawIntoCanvas { canvas ->
                                    val paint = Paint().apply {
                                        color = primaryShadowColor
                                        @Suppress("DEPRECATION")
                                        asFrameworkPaint().maskFilter = BlurMaskFilter(16.dp.toPx(), BlurMaskFilter.Blur.NORMAL)
                                    }
                                    canvas.drawRoundRect(
                                        left = offsetX, top = offsetY,
                                        right = size.width + offsetX, bottom = size.height + offsetY,
                                        radiusX = 28.dp.toPx(), radiusY = 28.dp.toPx(),
                                        paint = paint
                                    )
                                    paint.color = tertiaryShadowColor
                                    canvas.drawRoundRect(
                                        left = -offsetX, top = -offsetY,
                                        right = size.width - offsetX, bottom = size.height - offsetY,
                                        radiusX = 28.dp.toPx(), radiusY = 28.dp.toPx(),
                                        paint = paint
                                    )
                                }
                            } else Modifier
                        )
                        .clip(RoundedCornerShape(28.dp))
                        .clickable(enabled = isAvailable && processingProfile == null) {
                            if (isDeveloperMode && profile.id != "cachecleaner") {
                                moduleBasePath?.let { basePath ->
                                    scope.launch(Dispatchers.IO) {
                                        val content = RootShellHelper.executeCmdAndGetOutput("cat $basePath/${profile.id}")
                                        editingProfileContent = content
                                        editingProfile = profile
                                    }
                                }
                            } else if (profile.id == "cachecleaner") {
                                scope.launch(Dispatchers.IO) {
                                    processingProfile = profile.id
                                    try {
                                        delay(600)
                                        RootShellHelper.executeCmd("/system/bin/cachecleaner")
                                    } catch (e: Exception) {} finally {
                                        processingProfile = null
                                    }
                                    scope.launch { snackbarHostState.showSnackbar("Cache cleaned successfully!") }
                                }
                            } else if (isAutdAvailable) {
                                scope.launch(Dispatchers.IO) {
                                    val autdDir = "${context.filesDir.absolutePath}/autd"
                                    val writeCmd = "rm -f $autdDir/autd_base_mode; echo -n '${profile.id}' > $autdDir/autd_base_mode"
                                    
                                    if (RootShellHelper.executeCmd(writeCmd)) {
                                        activeProfileId = profile.id
                                        scope.launch { snackbarHostState.showSnackbar("Profile ${profile.name} applied") }
                                    } else {
                                        scope.launch { snackbarHostState.showSnackbar("Failed to write profile via root shell") }
                                    }
                                }
                            } else {
                                scope.launch(Dispatchers.IO) {
                                    processingProfile = profile.id
                                    try {
                                        delay(600)
                                        RootShellHelper.executeCmd("/system/bin/${profile.id}")
                                        activeProfileId = profile.id
                                        prefs.edit().putString("manual_active_profile", profile.id).apply()
                                    } catch (e: Exception) {} finally {
                                        processingProfile = null
                                    }
                                    scope.launch { snackbarHostState.showSnackbar("Profile ${profile.name} applied") }
                                }
                            }
                        }
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(colorScheme.surfaceVariant, RoundedCornerShape(28.dp))
                            .background(if (isProcessing || isActive) colorScheme.primary.copy(alpha = 0.1f) else Color.Transparent, RoundedCornerShape(28.dp))
                            .border(borderWidth, borderColor, RoundedCornerShape(28.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                            modifier = Modifier.padding(16.dp)
                        ) {
                            if (isProcessing) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(32.dp),
                                    color = colorScheme.secondary,
                                    strokeWidth = 3.dp
                                )
                            } else {
                                Icon(
                                    imageVector = if (isAvailable) profile.icon else Icons.Rounded.ErrorOutline,
                                    contentDescription = profile.name,
                                    tint = if (isAvailable) (if (isActive) colorScheme.primary else colorScheme.onSurfaceVariant) else colorScheme.error,
                                    modifier = Modifier.size(40.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = if (isAvailable) profile.name else "Not Found",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = if (isAvailable) colorScheme.onSurfaceVariant else colorScheme.error
                                )
                            )
                            if (isAvailable && profile.id != "cachecleaner") {
                                Text(
                                    text = if (isDeveloperMode) "Tap to edit" else if (isProcessing) "Applying..." else if (isActive) "ACTIVE" else if (isAutdAvailable) "Tap to activate" else "",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontFamily = if (isActive && !isDeveloperMode) FontFamily.Monospace else null,
                                        color = if (isDeveloperMode) colorScheme.tertiary else if (isActive || isProcessing) colorScheme.primary else colorScheme.outline,
                                        fontWeight = if (isDeveloperMode || isActive || isProcessing) FontWeight.Bold else FontWeight.Normal
                                    )
                                )
                            }
                        }
                    }
                }
            }
            }

            item(span = { GridItemSpan(maxLineSpan) }) {
                Spacer(modifier = Modifier.height(220.dp))
                Spacer(modifier = Modifier.navigationBarsPadding())
            }
        }
    }

    editingProfile?.let { profile ->
        ProfileEditorDialog(
            hazeState = hazeState,
            profileName = profile.name,
            profileId = profile.id,
            initialContent = editingProfileContent,
            onDismiss = { editingProfile = null },
            onSave = { newContent ->
                moduleBasePath?.let { basePath ->
                    scope.launch(Dispatchers.IO) {
                        val escaped = newContent.replace("'", "'\"'\"'")
                        val writeCmd = "printf '%s' '$escaped' > $basePath/${profile.id}"
                        if (RootShellHelper.executeCmd(writeCmd)) {
                            hasModifiedProfiles = true
                            scope.launch { snackbarHostState.showSnackbar("Profile ${profile.name} saved successfully!") }
                        } else {
                            scope.launch { snackbarHostState.showSnackbar("Failed to save profile ${profile.name}") }
                        }
                    }
                }
                editingProfile = null
            }
        )
    }
}