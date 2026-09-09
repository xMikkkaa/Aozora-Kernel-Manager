package com.xaozora.manager.ui.screens.preparation

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.xaozora.manager.R
import com.xaozora.manager.core.shell.RootShellHelper
import com.xaozora.manager.ui.components.GlassCard
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

enum class PreparationStatus { CHECKING, GRANTED, MISSING }

private data class PreparationItem(
    val step: Int,
    val title: String,
    val description: String,
    val icon: ImageVector,
    val status: PreparationStatus,
    val critical: Boolean = false,
    val actionLabel: String? = null,
    val onAction: (() -> Unit)? = null
)

@Composable
fun PreparationScreen(
    hazeState: HazeState,
    modifier: Modifier = Modifier,
    onContinue: () -> Unit,
    onExit: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val lifecycleState by lifecycleOwner.lifecycle.currentStateFlow.collectAsState()

    var rootStatus by remember { mutableStateOf(PreparationStatus.CHECKING) }
    var moduleStatus by remember { mutableStateOf(PreparationStatus.CHECKING) }
    var daemonStatus by remember { mutableStateOf(PreparationStatus.CHECKING) }
    var notifStatus by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                PreparationStatus.GRANTED
            } else {
                PreparationStatus.CHECKING
            }
        )
    }

    val notificationLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        notifStatus = if (granted) PreparationStatus.GRANTED else PreparationStatus.MISSING
    }

    fun refreshNotification() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            notifStatus = PreparationStatus.GRANTED
            return
        }
        notifStatus = if (
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
            == PackageManager.PERMISSION_GRANTED
        ) PreparationStatus.GRANTED else PreparationStatus.MISSING
    }

    LaunchedEffect(lifecycleState) {
        if (lifecycleState.isAtLeast(Lifecycle.State.RESUMED)) {
            while (true) {
                refreshNotification()
                withContext(Dispatchers.IO) {
                    val isRooted = RootShellHelper.executeCmdAndGetOutput("id -u").trim() == "0"
                    rootStatus = if (isRooted) PreparationStatus.GRANTED else PreparationStatus.MISSING

                    val moduleInstalled = RootShellHelper.executeCmdAndGetOutput(
                        "grep -l 'id=.*aozora' /data/adb/modules/*/module.prop 2>/dev/null"
                    ).isNotBlank()
                    moduleStatus = if (moduleInstalled) PreparationStatus.GRANTED else PreparationStatus.MISSING

                    val daemonPresent = RootShellHelper.checkFileExists("${context.filesDir.path}/xaozora_daemon")
                    daemonStatus = if (daemonPresent) PreparationStatus.GRANTED else PreparationStatus.MISSING
                }
                delay(2000)
            }
        }
    }

    val items = remember(rootStatus, moduleStatus, notifStatus, daemonStatus) {
        listOf(
            PreparationItem(
                step = 1,
                title = "Root Access",
                description = "Superuser (su) via Magisk, KernelSU, or APatch. Required to control the kernel.",
                icon = Icons.Rounded.Security,
                status = rootStatus,
                critical = true
            ),
            PreparationItem(
                step = 2,
                title = "Kernel Helper Module",
                description = "Install the Aozora Kernel Helper module to enable the tuning profiles.",
                icon = Icons.Rounded.Extension,
                status = moduleStatus
            ),
            PreparationItem(
                step = 3,
                title = "Notifications",
                description = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                    "No permission needed on this Android version."
                } else {
                    "Used by the monitoring service and battery drain alerts."
                },
                icon = Icons.Rounded.Notifications,
                status = notifStatus,
                actionLabel = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) "Grant" else null,
                onAction = { notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) }
            ),
            PreparationItem(
                step = 4,
                title = "AUTD Daemon",
                description = "Bundled background daemon that powers automation and the app manager.",
                icon = Icons.Rounded.Memory,
                status = daemonStatus
            )
        )
    }

    Box(modifier = modifier.fillMaxSize()) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .hazeSource(state = hazeState),
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

                Header()

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Required Access",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )

                items.forEach { item ->
                    PreparationItemCard(item = item, hazeState = hazeState)
                }

                Spacer(modifier = Modifier.height(24.dp))
                Spacer(modifier = Modifier.height(100.dp))
                Spacer(modifier = Modifier.navigationBarsPadding())
            }
        }

        ConfirmButton(
            modifier = Modifier.align(Alignment.BottomCenter),
            hazeState = hazeState,
            rootStatus = rootStatus,
            onContinue = onContinue,
            onExit = onExit
        )
    }
}

@Composable
private fun Header() {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Image(
            painter = painterResource(id = R.drawable.kai),
            contentDescription = "Aozora Logo",
            modifier = Modifier.size(96.dp),
            colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.primary)
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "Preparation",
            style = MaterialTheme.typography.headlineMedium.copy(
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "Welcome to Aozora Kernel Manager.\nGrant the permissions below to set things up.",
            style = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurfaceVariant),
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun PreparationItemCard(
    item: PreparationItem,
    hazeState: HazeState
) {
    val colorScheme = MaterialTheme.colorScheme
    val statusColor = when {
        item.status == PreparationStatus.GRANTED -> colorScheme.primary
        item.status == PreparationStatus.MISSING && item.critical -> colorScheme.error
        else -> colorScheme.onSurfaceVariant
    }

    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        hazeState = hazeState,
        shape = RoundedCornerShape(20.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .background(statusColor.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
                    .padding(12.dp)
            ) {
                Icon(
                    imageVector = item.icon,
                    contentDescription = item.title,
                    tint = statusColor,
                    modifier = Modifier.size(24.dp)
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Step ${item.step}",
                    style = MaterialTheme.typography.labelSmall.copy(color = colorScheme.onSurfaceVariant)
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = item.description,
                    style = MaterialTheme.typography.bodySmall.copy(color = colorScheme.onSurfaceVariant)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            TrailingIndicator(item = item, statusColor = statusColor)
        }
    }
}

@Composable
private fun TrailingIndicator(
    item: PreparationItem,
    statusColor: Color
) {
    val colorScheme = MaterialTheme.colorScheme
    val showAction = item.status == PreparationStatus.MISSING && item.actionLabel != null && item.onAction != null

    if (showAction) {
        Box(
            modifier = Modifier
                .clip(CircleShape)
                .background(colorScheme.primary.copy(alpha = 0.15f))
                .border(width = 1.dp, color = colorScheme.primary.copy(alpha = 0.4f), shape = CircleShape)
                .clickable { item.onAction?.invoke() }
                .padding(horizontal = 14.dp, vertical = 7.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = item.actionLabel!!,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold,
                    color = colorScheme.primary
                )
            )
        }
        return
    }

    when (item.status) {
        PreparationStatus.GRANTED -> Icon(
            imageVector = Icons.Rounded.CheckCircle,
            contentDescription = "Granted",
            tint = statusColor,
            modifier = Modifier.size(28.dp)
        )

        PreparationStatus.MISSING -> Icon(
            imageVector = Icons.Rounded.ErrorOutline,
            contentDescription = "Not granted",
            tint = statusColor,
            modifier = Modifier.size(28.dp)
        )

        PreparationStatus.CHECKING -> CircularProgressIndicator(
            modifier = Modifier.size(24.dp),
            color = colorScheme.onSurfaceVariant,
            strokeWidth = 2.dp
        )
    }
}

@Composable
private fun ConfirmButton(
    modifier: Modifier = Modifier,
    hazeState: HazeState,
    rootStatus: PreparationStatus,
    onContinue: () -> Unit,
    onExit: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    val surfaceContainer = colorScheme.surfaceContainer
    val glassStyle = remember(surfaceContainer) {
        HazeStyle(
            blurRadius = 25.dp,
            noiseFactor = 0.1f,
            tints = listOf(HazeTint(surfaceContainer.copy(alpha = 0.25f)))
        )
    }

    val isGranted = rootStatus == PreparationStatus.GRANTED
    val isChecking = rootStatus == PreparationStatus.CHECKING
    val borderColor = when {
        isChecking -> colorScheme.outlineVariant.copy(alpha = 0.5f)
        isGranted -> colorScheme.primary.copy(alpha = 0.5f)
        else -> colorScheme.error.copy(alpha = 0.5f)
    }
    val contentColor = when {
        isChecking -> colorScheme.onSurfaceVariant
        isGranted -> colorScheme.primary
        else -> colorScheme.error
    }
    val label = when {
        isChecking -> "Checking..."
        isGranted -> "OK"
        else -> "Exit"
    }
    val buttonIcon = if (isGranted) Icons.Rounded.CheckCircle else Icons.Rounded.Close

    Column(
        modifier = modifier
            .navigationBarsPadding()
            .padding(start = 32.dp, end = 32.dp, bottom = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (!isGranted && !isChecking) {
            Text(
                text = "Root access is required to continue.",
                style = MaterialTheme.typography.bodySmall.copy(color = colorScheme.error),
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(10.dp))
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .clip(CircleShape)
                .hazeEffect(state = hazeState, style = glassStyle)
                .background(
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) Color.Transparent
                    else surfaceContainer
                )
                .border(width = 1.2.dp, color = borderColor, shape = CircleShape)
                .clickable(enabled = !isChecking) {
                    if (isGranted) onContinue() else onExit()
                },
            contentAlignment = Alignment.Center
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                if (!isChecking) {
                    Icon(
                        imageVector = buttonIcon,
                        contentDescription = null,
                        tint = contentColor,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                }
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontWeight = FontWeight.Bold,
                        color = contentColor
                    )
                )
            }
        }
    }
}
