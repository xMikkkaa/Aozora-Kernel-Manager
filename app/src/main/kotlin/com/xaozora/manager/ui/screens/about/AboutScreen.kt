package com.xaozora.manager.ui.screens.about

import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.BlurMaskFilter
import android.widget.Toast
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.RowScope
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
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Update
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import com.xaozora.manager.R
import com.xaozora.manager.core.shell.RootShellHelper
import com.xaozora.manager.core.network.UpdateManager
import com.xaozora.manager.core.network.UpdateCheckResult
import com.xaozora.manager.ui.components.GlassCard
import com.xaozora.manager.ui.components.UpdateDialog
import dev.chrisbanes.haze.HazeState
import androidx.compose.material3.SnackbarHostState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.core.content.edit
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun AboutScreen(
    hazeState: HazeState,
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var appVersion by remember { mutableStateOf("Loading...") }
    var autoCheckUpdates by remember { mutableStateOf(true) }
    var showUpdateDialog by remember { mutableStateOf(false) }
    var isCheckingUpdate by remember { mutableStateOf(false) }
    var updateCheckResult by remember { mutableStateOf<UpdateCheckResult?>(null) }

    val prefs = remember { context.getSharedPreferences("aozora_prefs", Context.MODE_PRIVATE) }

    val infiniteTransition = rememberInfiniteTransition(label = "about_shadow")
    val angle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2.0 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "angle"
    )

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            try {
                val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
                appVersion = packageInfo.versionName ?: "Unknown"
            } catch (_: Exception) {
                appVersion = "Unknown"
            }

            autoCheckUpdates = prefs.getBoolean("auto_check_updates", true)
        }
    }

    val launchUrl: (String) -> Unit = { url ->
        try {
            val intent = Intent(Intent.ACTION_VIEW, url.toUri())
            context.startActivity(intent)
        } catch (_: Exception) {
            Toast.makeText(context, "Could not open URL", Toast.LENGTH_SHORT).show()
        }
    }

    val colorScheme = MaterialTheme.colorScheme

    Surface(
        modifier = modifier.fillMaxSize(),
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
                text = "About",
                style = MaterialTheme.typography.headlineLarge.copy(
                    fontWeight = FontWeight.Bold,
                    color = colorScheme.primary
                )
            )
        Spacer(modifier = Modifier.height(24.dp))

        ProfileCard(
            name = "Kaiyaa77",
            role = "Kernel Developer",
            description = "Creator and Maintainer of Aozora Kernel.",
            imageRes = R.drawable.kai,
            actionIcon = Icons.AutoMirrored.Rounded.Send,
            actionLabel = "View Telegram Profile",
            onActionClick = { launchUrl("https://t.me/Yuu507") },
            hazeState = hazeState,
            angle = angle
        )
        Spacer(modifier = Modifier.height(16.dp))

        ProfileCard(
            name = "xMikkkaa",
            role = "LIP",
            description = "Creator of Aozora Kernel Manager.",
            imageUrl = "https://avatars.githubusercontent.com/xMikkkaa",
            actionIcon = Icons.Outlined.Code,
            actionLabel = "View GitHub Profile",
            onActionClick = { launchUrl("https://github.com/xMikkkaa") },
            hazeState = hazeState,
            angle = angle
        )
        Spacer(modifier = Modifier.height(16.dp))

        ProfileCard(
            name = "Aris",
            role = "Lead Tester",
            description = "Lead tester ensuring system stability.",
            imageUrl = "https://avatars.githubusercontent.com/Risuue",
            actionIcon = Icons.AutoMirrored.Rounded.Send,
            actionLabel = "View Telegram Profile",
            onActionClick = { launchUrl("https://t.me/Aris0103") },
            hazeState = hazeState,
            angle = angle
        )
        Spacer(modifier = Modifier.height(24.dp))

        Text("Updates", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
        Spacer(modifier = Modifier.height(12.dp))

        AboutToggleCard(
            title = "Auto Check for Updates",
            subtitle = "Automatically check for app and daemon updates on startup.",
            icon = Icons.Rounded.Update,
            checked = autoCheckUpdates,
            hazeState = hazeState,
            onCheckedChange = { newVal ->
                autoCheckUpdates = newVal
                scope.launch(Dispatchers.IO) {
                    prefs.edit { putBoolean("auto_check_updates", newVal) }
                    scope.launch {
                        snackbarHostState.showSnackbar(
                            if (newVal) "Auto Update Check Enabled" else "Auto Update Check Disabled"
                        )
                    }
                }
            }
        )
        Spacer(modifier = Modifier.height(16.dp))

        GlassyButton(
            onClick = {
                scope.launch(Dispatchers.Main) {
                    isCheckingUpdate = true
                    try {
                        val result = UpdateManager.checkUpdates(appVersion)
                        updateCheckResult = result
                        if (result.appUpdate.hasUpdate) {
                            showUpdateDialog = true
                        } else {
                            snackbarHostState.showSnackbar("You are on the latest version.")
                        }
                    } catch (_: Exception) {
                        snackbarHostState.showSnackbar("Failed to check for updates.")
                    } finally {
                        isCheckingUpdate = false
                    }
                }
            },
            enabled = !isCheckingUpdate,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (isCheckingUpdate) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp), color = colorScheme.primary, strokeWidth = 2.dp)
                Spacer(modifier = Modifier.width(12.dp))
                Text("Checking...", color = colorScheme.onPrimaryContainer, style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold))
            } else {
                Icon(Icons.Rounded.Update, contentDescription = null, tint = colorScheme.primary)
                Spacer(modifier = Modifier.width(12.dp))
                Text("Check for Updates", color = colorScheme.onPrimaryContainer, style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold))
            }
        }
        Spacer(modifier = Modifier.height(32.dp))

        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Aozora Kernel Manager", style = MaterialTheme.typography.bodyMedium.copy(color = colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold))
            Spacer(modifier = Modifier.height(4.dp))
            Text("v$appVersion", style = MaterialTheme.typography.labelSmall.copy(color = colorScheme.onSurfaceVariant.copy(alpha = 0.7f)))
        }
        Spacer(modifier = Modifier.height(140.dp))
        Spacer(modifier = Modifier.navigationBarsPadding())
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
                scope.launch {
                    snackbarHostState.showSnackbar("Downloading update in background...")
                    
                    if (isAppUpdate) {
                        UpdateManager.performAppUpdate(result.appUpdate.downloadUrl, context.cacheDir)
                    }
                }
            }
        )
    }
}

@Composable
private fun ProfileCard(
    name: String,
    role: String,
    description: String,
    imageUrl: String? = null,
    imageRes: Int? = null,
    actionIcon: ImageVector,
    actionLabel: String,
    onActionClick: () -> Unit,
    hazeState: HazeState,
    angle: Float
) {
    val colorScheme = MaterialTheme.colorScheme
    val profileImage = rememberNetworkImage(url = imageUrl)
    
    val primaryShadowColor = colorScheme.primary.copy(alpha = 0.4f)
    val tertiaryShadowColor = colorScheme.tertiary.copy(alpha = 0.3f)

    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        hazeState = hazeState,
        shape = RoundedCornerShape(28.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(112.dp)
                    .background(
                        Brush.linearGradient(
                            listOf(
                                colorScheme.primary,
                                colorScheme.tertiary
                            )
                        ), CircleShape
                    )
                    .padding(4.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .drawBehind {
                            val offsetX = 8.dp.toPx() * cos(angle)
                            val offsetY = 8.dp.toPx() * sin(angle)
                            drawIntoCanvas { canvas ->
                                val paint = Paint().apply {
                                    color = primaryShadowColor
                                    @Suppress("DEPRECATION")
                                    asFrameworkPaint().maskFilter =
                                        BlurMaskFilter(16.dp.toPx(), BlurMaskFilter.Blur.NORMAL)
                                }
                                canvas.drawCircle(
                                    center = Offset(
                                        size.width / 2 + offsetX,
                                        size.height / 2 + offsetY
                                    ),
                                    radius = size.width / 2,
                                    paint = paint
                                )
                                paint.color = tertiaryShadowColor
                                canvas.drawCircle(
                                    center = Offset(
                                        size.width / 2 - offsetX,
                                        size.height / 2 - offsetY
                                    ),
                                    radius = size.width / 2,
                                    paint = paint
                                )
                            }
                        }
                        .background(colorScheme.surfaceContainerHighest, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    if (imageRes != null) {
                        Image(
                            painter = painterResource(id = imageRes),
                            contentDescription = "Profile Picture",
                            modifier = Modifier.fillMaxSize().padding(16.dp).clip(CircleShape),
                            contentScale = ContentScale.Fit,
                            colorFilter = ColorFilter.tint(colorScheme.primary)
                        )
                    } else if (profileImage != null) {
                        Image(
                            bitmap = profileImage,
                            contentDescription = "Profile Picture",
                            modifier = Modifier.fillMaxSize().clip(CircleShape),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Text(
                            text = name.first().uppercase(),
                            style = MaterialTheme.typography.displayMedium.copy(color = colorScheme.primary, fontWeight = FontWeight.Bold)
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(20.dp))
            Text(name, style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold, color = colorScheme.onSurface))
            Spacer(modifier = Modifier.height(6.dp))
            Text(role, style = MaterialTheme.typography.bodyLarge.copy(color = colorScheme.primary, fontWeight = FontWeight.SemiBold))
            Spacer(modifier = Modifier.height(16.dp))
            Text(description, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyMedium.copy(color = colorScheme.onSurfaceVariant))
            Spacer(modifier = Modifier.height(28.dp))
            
            GlassyButton(
                onClick = onActionClick,
                modifier = Modifier.height(48.dp).padding(horizontal = 16.dp)
            ) {
                Icon(actionIcon, contentDescription = null, tint = colorScheme.primary, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(10.dp))
                Text(actionLabel, color = colorScheme.onPrimaryContainer, style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold))
            }
        }
    }
}

@Composable
private fun rememberNetworkImage(url: String?): ImageBitmap? {
    var bitmap by remember { mutableStateOf<ImageBitmap?>(null) }
    
    LaunchedEffect(url) {
        if (url != null) {
            withContext(Dispatchers.IO) {
                try {
                    val connection = java.net.URL(url).openConnection() as java.net.HttpURLConnection
                    connection.doInput = true
                    connection.connect()
                    val decoded = BitmapFactory.decodeStream(connection.inputStream)
                    bitmap = decoded?.asImageBitmap()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }
    return bitmap
}

@Composable
private fun GlassyButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    val alpha = if (enabled) 0.25f else 0.1f
    
    Box(
        modifier = modifier
            .height(52.dp)
            .clip(CircleShape)
            .background(colorScheme.primaryContainer.copy(alpha = alpha))
            .border(
                width = 1.dp,
                color = colorScheme.primary.copy(alpha = if (enabled) 0.4f else 0.2f),
                shape = CircleShape
            )
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.padding(horizontal = 24.dp)
        ) {
            content()
        }
    }
}

@Composable
private fun AboutToggleCard(
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
                    Box(modifier = Modifier.offset { IntOffset(switchOffset.roundToPx(), 0) }.size(20.dp).background(thumbColor, CircleShape))
                }
            }
        }
    }
}
