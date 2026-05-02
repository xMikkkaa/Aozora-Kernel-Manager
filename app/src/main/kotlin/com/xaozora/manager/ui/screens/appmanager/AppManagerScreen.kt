package com.xaozora.manager.ui.screens.appmanager

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Android
import androidx.compose.material.icons.rounded.RocketLaunch
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material.icons.rounded.VideogameAsset
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.xaozora.manager.core.shell.RootShellHelper
import com.xaozora.manager.core.utils.AppInfoItem
import com.xaozora.manager.core.utils.AppManagerUtils
import com.xaozora.manager.core.utils.ConfiguredApp
import com.xaozora.manager.ui.components.GlassCard
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppManagerScreen(
    hazeState: HazeState,
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var configuredApps by remember { mutableStateOf(emptyList<ConfiguredApp>()) }
    var allApps by remember { mutableStateOf(emptyList<AppInfoItem>()) }
    var isLoadingApps by remember { mutableStateOf(true) }

    var showAddSheet by remember { mutableStateOf(false) }
    var appToEdit by remember { mutableStateOf<ConfiguredApp?>(null) }

    fun refreshApps() {
        scope.launch(Dispatchers.IO) {
            val apps = AppManagerUtils.getConfiguredApps(context)
            withContext(Dispatchers.Main) {
                configuredApps = apps
                isLoadingApps = false
            }
        }
    }

    LaunchedEffect(Unit) {
        refreshApps()
        withContext(Dispatchers.IO) {
            val installed = AppManagerUtils.getInstalledApps(context)
            withContext(Dispatchers.Main) {
                allApps = installed
            }
        }
    }

    val addAppToConfig = { packageName: String ->
        scope.launch(Dispatchers.IO) {
            val cmd = "echo \"${packageName}_p\" >> /data/data/com.xaozora.manager/files/applist"
            val success = RootShellHelper.executeCmd(cmd)
            if (success) {
                scope.launch { snackbarHostState.showSnackbar("App added successfully") }
                refreshApps()
            } else {
                scope.launch { snackbarHostState.showSnackbar("Failed to add app") }
            }
        }
    }

    val updateAppConfig = { packageName: String, newMode: String ->
        scope.launch(Dispatchers.IO) {
            val cmd = "sed -i '/^${packageName}_/d' /data/data/com.xaozora.manager/files/applist; echo \"${packageName}_$newMode\" >> /data/data/com.xaozora.manager/files/applist"
            val success = RootShellHelper.executeCmd(cmd)
            if (success) {
                scope.launch { snackbarHostState.showSnackbar("Profile updated for $packageName") }
                refreshApps()
            } else {
                scope.launch { snackbarHostState.showSnackbar("Failed to update profile") }
            }
        }
    }

    val removeAppFromConfig = { packageName: String ->
        scope.launch(Dispatchers.IO) {
            val cmd = "sed -i '/^${packageName}_/d' /data/data/com.xaozora.manager/files/applist"
            val success = RootShellHelper.executeCmd(cmd)
            if (success) {
                scope.launch { snackbarHostState.showSnackbar("App removed from list") }
                refreshApps()
            } else {
                scope.launch { snackbarHostState.showSnackbar("Failed to remove app") }
            }
        }
    }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = Color.Transparent,
            floatingActionButton = {
                GlassCard(
                    modifier = Modifier
                        .padding(bottom = 96.dp)
                        .size(56.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .clickable { showAddSheet = true },
                hazeState = hazeState,
                shape = RoundedCornerShape(16.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f))
                            .border(
                                width = 1.dp,
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
                                shape = RoundedCornerShape(16.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Add, 
                            contentDescription = "Add App",
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }
        ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
        ) {
            Spacer(modifier = Modifier.padding(WindowInsets.statusBars.asPaddingValues()))
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "App Manager",
                style = MaterialTheme.typography.headlineLarge.copy(
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            )
            Spacer(modifier = Modifier.height(24.dp))

            if (isLoadingApps) {
                Box(modifier = Modifier.fillMaxWidth().padding(top = 40.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
            } else if (configuredApps.isEmpty()) {
                Box(modifier = Modifier.fillMaxWidth().padding(top = 40.dp), contentAlignment = Alignment.Center) {
                    Text("No apps configured", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                configuredApps.forEach { config ->
                    ConfiguredAppItem(
                        config = config,
                        hazeState = hazeState,
                        onClick = { appToEdit = config }
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                }
            }
            Spacer(modifier = Modifier.height(100.dp))
        }
    }
    }

    if (showAddSheet) {
        val surfaceContainer = MaterialTheme.colorScheme.surfaceContainer
        val sheetStyle = remember(surfaceContainer) {
            HazeStyle(
                blurRadius = 25.dp,
                noiseFactor = 0.1f,
                tints = listOf(HazeTint(surfaceContainer.copy(alpha = 0.25f)))
            )
        }

        ModalBottomSheet(
            onDismissRequest = { showAddSheet = false },
            containerColor = Color.Transparent,
            dragHandle = null
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp))
                    .hazeEffect(state = hazeState, style = sheetStyle)
                    .border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp)
                    )
                    .background(Color.Transparent)
            ) {
                Column {
                    Box(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), contentAlignment = Alignment.Center) {
                        BottomSheetDefaults.DragHandle()
                    }
                    AddAppSheetContent(
                        allApps = allApps,
                        existingApps = configuredApps.map { it.app.packageName },
                        onAppSelected = { pkg ->
                            showAddSheet = false
                            addAppToConfig(pkg)
                        }
                    )
                }
            }
        }
    }

    appToEdit?.let { config ->
        val surfaceContainer = MaterialTheme.colorScheme.surfaceContainer
        val sheetStyle = remember(surfaceContainer) {
            HazeStyle(
                blurRadius = 25.dp,
                noiseFactor = 0.1f,
                tints = listOf(HazeTint(surfaceContainer.copy(alpha = 0.25f)))
            )
        }

        ModalBottomSheet(
            onDismissRequest = { appToEdit = null },
            containerColor = Color.Transparent,
            dragHandle = null
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp))
                    .hazeEffect(state = hazeState, style = sheetStyle)
                    .border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp)
                    )
                    .background(Color.Transparent)
            ) {
                Column {
                    Box(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), contentAlignment = Alignment.Center) {
                        BottomSheetDefaults.DragHandle()
                    }
                    EditAppSheetContent(
                        config = config,
                        onUpdateMode = { pkg, mode ->
                            appToEdit = null
                            updateAppConfig(pkg, mode)
                        },
                        onRemove = { pkg ->
                            appToEdit = null
                            removeAppFromConfig(pkg)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun ConfiguredAppItem(config: ConfiguredApp, hazeState: HazeState, onClick: () -> Unit) {
    val colorScheme = MaterialTheme.colorScheme
    val badgeColor = when (config.mode) {
        "g" -> Color(0xFFFFC107)
        "g2" -> Color(0xFFFF5252)
        else -> Color(0xFF448AFF)
    }
    val badgeText = when (config.mode) {
        "g" -> "Gaming"
        "g2" -> "Gaming+"
        else -> "Perf"
    }

    GlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick),
        hazeState = hazeState,
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AppIcon(drawable = config.app.icon, modifier = Modifier.size(48.dp))
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = config.app.name,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    maxLines = 1
                )
                Text(
                    text = config.app.packageName,
                    style = MaterialTheme.typography.bodySmall.copy(color = colorScheme.onSurfaceVariant),
                    maxLines = 1
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .background(badgeColor.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
                    .border(1.dp, badgeColor.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    text = badgeText,
                    style = MaterialTheme.typography.labelSmall.copy(color = badgeColor, fontWeight = FontWeight.Bold)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditAppSheetContent(config: ConfiguredApp, onUpdateMode: (String, String) -> Unit, onRemove: (String) -> Unit) {
    var selectedMode by remember { mutableStateOf(config.mode) }
    val modes = listOf("p" to "Perf", "g" to "Game", "g2" to "Game+")
    val colorScheme = MaterialTheme.colorScheme

    Column(modifier = Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            AppIcon(drawable = config.app.icon, modifier = Modifier.size(56.dp))
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = config.app.name, style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold), maxLines = 1)
                Text(text = config.app.packageName, style = MaterialTheme.typography.bodyMedium.copy(color = colorScheme.onSurfaceVariant), maxLines = 1)
            }
        }
        Spacer(modifier = Modifier.height(32.dp))
        Text("Select Mode", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
        Spacer(modifier = Modifier.height(16.dp))
        
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            modes.forEachIndexed { index, (modeValue, modeLabel) ->
                SegmentedButton(
                    selected = selectedMode == modeValue,
                    onClick = { selectedMode = modeValue; onUpdateMode(config.app.packageName, modeValue) },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = modes.size),
                    icon = {
                        if (selectedMode == modeValue) {
                            Icon(
                                imageVector = when (modeValue) { "p" -> Icons.Rounded.RocketLaunch; "g" -> Icons.Rounded.SportsEsports; else -> Icons.Rounded.VideogameAsset },
                                contentDescription = null, modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                ) { Text(modeLabel) }
            }
        }
        Spacer(modifier = Modifier.height(32.dp))
        OutlinedButton(
            onClick = { onRemove(config.app.packageName) },
            modifier = Modifier.fillMaxWidth().height(52.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = colorScheme.error),
            border = androidx.compose.foundation.BorderStroke(1.dp, colorScheme.error)
        ) {
            Icon(Icons.Outlined.DeleteOutline, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Remove from list")
        }
        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun AddAppSheetContent(allApps: List<AppInfoItem>, existingApps: List<String>, onAppSelected: (String) -> Unit) {
    var searchQuery by remember { mutableStateOf("") }
    val colorScheme = MaterialTheme.colorScheme

    val filteredApps = remember(searchQuery, allApps, existingApps) {
        allApps.filter { app ->
            !existingApps.contains(app.packageName) &&
            (app.name.contains(searchQuery, ignoreCase = true) || app.packageName.contains(searchQuery, ignoreCase = true))
        }
    }

    Column(modifier = Modifier.fillMaxWidth().fillMaxHeight(0.85f)) {
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            placeholder = { Text("Search App") },
            leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
            shape = RoundedCornerShape(16.dp),
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = colorScheme.primary, unfocusedBorderColor = colorScheme.outlineVariant)
        )
        LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 32.dp)) {
            items(filteredApps, key = { it.packageName }) { app ->
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { onAppSelected(app.packageName) }.padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AppIcon(drawable = app.icon, modifier = Modifier.size(40.dp))
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text(text = app.name, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold))
                        Text(text = app.packageName, style = MaterialTheme.typography.bodySmall.copy(color = colorScheme.onSurfaceVariant))
                    }
                }
            }
        }
    }
}

@Composable
private fun AppIcon(drawable: android.graphics.drawable.Drawable?, modifier: Modifier = Modifier) {
    val bitmap = remember(drawable) { try { drawable?.toBitmap(150, 150)?.asImageBitmap() } catch (e: Exception) { null } }
    if (bitmap != null) {
        Image(bitmap = bitmap, contentDescription = null, modifier = modifier.clip(CircleShape))
    } else {
        Box(modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant, CircleShape), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.Android, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}