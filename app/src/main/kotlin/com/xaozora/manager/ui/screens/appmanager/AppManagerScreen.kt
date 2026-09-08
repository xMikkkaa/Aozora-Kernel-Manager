package com.xaozora.manager.ui.screens.appmanager

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.rounded.Android
import androidx.compose.material.icons.rounded.RocketLaunch
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material.icons.rounded.VideogameAsset
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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
import dev.chrisbanes.haze.hazeSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Immutable
private data class ShugyoCounts(val perf: Int, val gaming: Int, val gamingPlus: Int)

private fun shugyoLabel(shugyoProfile: String): String = when (shugyoProfile) {
    "g" -> "Gaming"
    "g2" -> "Gaming+"
    else -> "Perf"
}

private fun shugyoIcon(shugyoProfile: String): ImageVector = when (shugyoProfile) {
    "g" -> Icons.Rounded.SportsEsports
    "g2" -> Icons.Rounded.VideogameAsset
    else -> Icons.Rounded.RocketLaunch
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppManagerScreen(
    hazeState: HazeState,
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier,
    onAddClickProvider: ((() -> Unit) -> Unit)? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var senshuTachi by remember { mutableStateOf(emptyList<ConfiguredApp>()) }
    var allApps by remember { mutableStateOf(emptyList<AppInfoItem>()) }
    var busy by remember { mutableStateOf(true) }
    var gamingExists by remember { mutableStateOf(false) }
    var gaming2Exists by remember { mutableStateOf(false) }

    var showAddSheet by remember { mutableStateOf(false) }
    var appToEdit by remember { mutableStateOf<ConfiguredApp?>(null) }

    LaunchedEffect(onAddClickProvider) {
        onAddClickProvider?.invoke {
            showAddSheet = true
        }
    }

    fun refreshApps() {
        scope.launch(Dispatchers.IO) {
            val gExists = RootShellHelper.checkFileExists("/system/bin/gaming")
            val g2Exists = RootShellHelper.checkFileExists("/system/bin/gaming2")

            val apps = AppManagerUtils.getConfiguredApps(context)
            val correctedApps = apps.map { config ->
                if ((config.shugyoProfile == "g" && !gExists) || (config.shugyoProfile == "g2" && !g2Exists)) {
                    val packageName = config.app.packageName
                    val appListPath = "${context.filesDir.absolutePath}/autd/applist"
                    val cmd = "sed -i '/^${packageName}_/d' $appListPath; echo \"${packageName}_p\" >> $appListPath"
                    RootShellHelper.executeCmd(cmd)
                    config.copy(shugyoProfile = "p")
                } else {
                    config
                }
            }

            withContext(Dispatchers.Main) {
                gamingExists = gExists
                gaming2Exists = g2Exists
                senshuTachi = correctedApps
                busy = false
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

    val addAppToConfig = { app: AppInfoItem ->
        scope.launch(Dispatchers.IO) {
            val packageName = app.packageName
            val appListPath = "${context.filesDir.absolutePath}/autd/applist"
            val cmd = "echo \"${packageName}_p\" >> $appListPath"
            if (RootShellHelper.executeCmd(cmd)) {
                scope.launch { snackbarHostState.showSnackbar("App added: ${app.name}") }
                refreshApps()
            } else {
                scope.launch { snackbarHostState.showSnackbar("Failed to add app") }
            }
        }
    }

    val updateAppConfig = { app: AppInfoItem, shugyoProfile: String ->
        scope.launch(Dispatchers.IO) {
            val packageName = app.packageName
            val appListPath = "${context.filesDir.absolutePath}/autd/applist"
            val cmd = "sed -i '/^${packageName}_/d' $appListPath; echo \"${packageName}_$shugyoProfile\" >> $appListPath"
            if (RootShellHelper.executeCmd(cmd)) {
                scope.launch { snackbarHostState.showSnackbar("Profile changed to ${shugyoLabel(shugyoProfile)} for ${app.name}") }
                refreshApps()
            } else {
                scope.launch { snackbarHostState.showSnackbar("Failed to update profile") }
            }
        }
    }

    val removeAppFromConfig = { packageName: String ->
        scope.launch(Dispatchers.IO) {
            val appListPath = "${context.filesDir.absolutePath}/autd/applist"
            val cmd = "sed -i '/^${packageName}_/d' $appListPath"
            if (RootShellHelper.executeCmd(cmd)) {
                scope.launch { snackbarHostState.showSnackbar("App removed from list") }
                refreshApps()
            } else {
                scope.launch { snackbarHostState.showSnackbar("Failed to remove app") }
            }
        }
    }

    val counts = remember(senshuTachi) {
        ShugyoCounts(
            perf = senshuTachi.count { it.shugyoProfile == "p" },
            gaming = senshuTachi.count { it.shugyoProfile == "g" },
            gamingPlus = senshuTachi.count { it.shugyoProfile == "g2" }
        )
    }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .hazeSource(state = hazeState),
            contentPadding = PaddingValues(
                start = 24.dp,
                end = 24.dp,
                top = WindowInsets.statusBars.asPaddingValues(LocalDensity.current).calculateTopPadding() + 16.dp,
                bottom = 140.dp
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item(key = "dojo_header") {
                Column {
                    Text(
                        text = "Game Dojo",
                        style = MaterialTheme.typography.headlineLarge.copy(
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (busy) "Memuat arena..." else "${senshuTachi.size} senshu terdaftar",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }

            item(key = "dojo_stats") {
                DojoStatsCard(counts = counts, hazeState = hazeState)
            }

            if (busy) {
                item(key = "dojo_loading") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 40.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    }
                }
            } else if (senshuTachi.isEmpty()) {
                item(key = "dojo_empty") {
                    DojoEmptyCard(hazeState = hazeState, onAdd = { showAddSheet = true })
                }
            } else {
                items(senshuTachi, key = { it.app.packageName }) { config ->
                    SenshuCard(
                        name = config.app.name,
                        packageName = config.app.packageName,
                        icon = config.app.icon,
                        shugyoProfile = config.shugyoProfile,
                        hazeState = hazeState,
                        onClick = { appToEdit = config }
                    )
                }
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
            dragHandle = null,
            contentWindowInsets = { WindowInsets(0, 0, 0, 0) },
            scrimColor = Color.Transparent
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .hazeEffect(state = hazeState, style = sheetStyle)
                    .background(if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) Color.Transparent else MaterialTheme.colorScheme.surfaceContainer)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp))
                        .border(
                            width = 1.dp,
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                            shape = RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp)
                        )
                        .background(if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) Color.Transparent else MaterialTheme.colorScheme.surfaceContainer)
                ) {
                    Box(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), contentAlignment = Alignment.Center) {
                        BottomSheetDefaults.DragHandle()
                    }
                    AddAppSheetContent(
                        allApps = allApps,
                        existingApps = senshuTachi.map { it.app.packageName },
                        onAppSelected = { app ->
                            showAddSheet = false
                            addAppToConfig(app)
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
            dragHandle = null,
            contentWindowInsets = { WindowInsets(0, 0, 0, 0) },
            scrimColor = Color.Transparent
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .hazeEffect(state = hazeState, style = sheetStyle)
                    .background(if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) Color.Transparent else MaterialTheme.colorScheme.surfaceContainer)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp))
                        .border(
                            width = 1.dp,
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                            shape = RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp)
                        )
                        .background(if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) Color.Transparent else MaterialTheme.colorScheme.surfaceContainer)
                ) {
                    Box(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), contentAlignment = Alignment.Center) {
                        BottomSheetDefaults.DragHandle()
                    }
                    EditAppSheetContent(
                        config = config,
                        onUpdateMode = { _, shugyoProfile ->
                            appToEdit = null
                            updateAppConfig(config.app, shugyoProfile)
                        },
                        onRemove = { pkg ->
                            appToEdit = null
                            removeAppFromConfig(pkg)
                        },
                        gamingExists = gamingExists,
                        gaming2Exists = gaming2Exists
                    )
                }
            }
        }
    }
}

@Composable
private fun DojoStatsCard(counts: ShugyoCounts, hazeState: HazeState) {
    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        hazeState = hazeState,
        shape = RoundedCornerShape(24.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            DojoStat(value = counts.perf, label = "Perf")
            DojoStat(value = counts.gaming, label = "Gaming")
            DojoStat(value = counts.gamingPlus, label = "Gaming+")
        }
    }
}

@Composable
private fun DojoStat(value: Int, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value.toString(),
            style = MaterialTheme.typography.headlineMedium.copy(
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium.copy(
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        )
    }
}

@Composable
private fun DojoEmptyCard(hazeState: HazeState, onAdd: () -> Unit) {
    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        hazeState = hazeState,
        shape = RoundedCornerShape(24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Rounded.SportsEsports,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(48.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Belum ada senshu",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Tambahkan game untuk mengatur shugyoProfile tiap judul",
                style = MaterialTheme.typography.bodySmall.copy(
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            )
            Spacer(modifier = Modifier.height(16.dp))
            OutlinedButton(onClick = onAdd) {
                Text("Tambah game")
            }
        }
    }
}

@Composable
private fun SenshuCard(
    name: String,
    packageName: String,
    icon: android.graphics.drawable.Drawable?,
    shugyoProfile: String,
    hazeState: HazeState,
    onClick: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    val chipColors = when (shugyoProfile) {
        "g" -> AssistChipDefaults.assistChipColors(
            containerColor = colorScheme.primaryContainer,
            labelColor = colorScheme.onPrimaryContainer,
            leadingIconContentColor = colorScheme.onPrimaryContainer
        )
        "g2" -> AssistChipDefaults.assistChipColors(
            containerColor = colorScheme.errorContainer,
            labelColor = colorScheme.onErrorContainer,
            leadingIconContentColor = colorScheme.onErrorContainer
        )
        else -> AssistChipDefaults.assistChipColors(
            containerColor = colorScheme.tertiaryContainer,
            labelColor = colorScheme.onTertiaryContainer,
            leadingIconContentColor = colorScheme.onTertiaryContainer
        )
    }

    GlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onClick),
        hazeState = hazeState,
        shape = RoundedCornerShape(20.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AppIcon(drawable = icon, modifier = Modifier.size(48.dp))
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = packageName,
                    style = MaterialTheme.typography.bodySmall.copy(color = colorScheme.onSurfaceVariant),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(8.dp))
                AssistChip(
                    onClick = onClick,
                    label = { Text(shugyoLabel(shugyoProfile)) },
                    leadingIcon = {
                        Icon(
                            imageVector = shugyoIcon(shugyoProfile),
                            contentDescription = null,
                            modifier = Modifier.size(AssistChipDefaults.IconSize)
                        )
                    },
                    colors = chipColors,
                    border = null
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditAppSheetContent(
    config: ConfiguredApp,
    onUpdateMode: (String, String) -> Unit,
    onRemove: (String) -> Unit,
    gamingExists: Boolean,
    gaming2Exists: Boolean
) {
    var selectedProfile by remember { mutableStateOf(config.shugyoProfile) }
    val profiles = remember(gamingExists, gaming2Exists) {
        listOfNotNull(
            "p" to "Perf",
            if (gamingExists) "g" to "Gaming" else null,
            if (gaming2Exists) "g2" to "Gaming+" else null
        )
    }
    val colorScheme = MaterialTheme.colorScheme

    Column(modifier = Modifier.fillMaxWidth().padding(24.dp).navigationBarsPadding(), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            AppIcon(drawable = config.app.icon, modifier = Modifier.size(56.dp))
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = config.app.name,
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(text = config.app.packageName, style = MaterialTheme.typography.bodyMedium.copy(color = colorScheme.onSurfaceVariant), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Spacer(modifier = Modifier.height(24.dp))
        ElevatedCard(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                Text(
                    text = "shugyoProfile",
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                )
                Spacer(modifier = Modifier.height(12.dp))
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    profiles.forEachIndexed { index, (profileValue, profileLabel) ->
                        SegmentedButton(
                            selected = selectedProfile == profileValue,
                            onClick = { selectedProfile = profileValue; onUpdateMode(config.app.packageName, profileValue) },
                            shape = SegmentedButtonDefaults.itemShape(index = index, count = profiles.size),
                            icon = {
                                if (selectedProfile == profileValue) {
                                    Icon(
                                        imageVector = shugyoIcon(profileValue),
                                        contentDescription = null, modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        ) { Text(profileLabel) }
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(24.dp))
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
private fun AddAppSheetContent(allApps: List<AppInfoItem>, existingApps: List<String>, onAppSelected: (AppInfoItem) -> Unit) {
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
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                bottom = WindowInsets.navigationBars.asPaddingValues(LocalDensity.current).calculateBottomPadding() + 32.dp
            )
        ) {
            items(filteredApps, key = { it.packageName }) { app ->
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { onAppSelected(app) }.padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AppIcon(drawable = app.icon, modifier = Modifier.size(40.dp))
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text(
                            text = app.name,
                            style = MaterialTheme.typography.bodyLarge.copy(
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        )
                        Text(text = app.packageName, style = MaterialTheme.typography.bodySmall.copy(color = colorScheme.onSurfaceVariant))
                    }
                }
            }
        }
    }
}

@Composable
private fun AppIcon(drawable: android.graphics.drawable.Drawable?, modifier: Modifier = Modifier) {
    val bitmap = remember(drawable) { try { drawable?.toBitmap(150, 150)?.asImageBitmap() } catch (_: Exception) { null } }
    if (bitmap != null) {
        Image(bitmap = bitmap, contentDescription = null, modifier = modifier.clip(CircleShape))
    } else {
        Box(modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant, CircleShape), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.Android, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
