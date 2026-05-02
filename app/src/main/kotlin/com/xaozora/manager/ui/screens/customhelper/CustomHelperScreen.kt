package com.xaozora.manager.ui.screens.customhelper

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.FolderZip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.compose.ui.platform.LocalView
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
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.DigestInputStream
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

@Composable
fun CustomHelperScreen(
    hazeState: HazeState,
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var selectedZipUri by remember { mutableStateOf<Uri?>(null) }
    var selectedFileName by remember { mutableStateOf<String?>(null) }
    var useAutd by remember { mutableStateOf(false) }
    var isAutdReady by remember { mutableStateOf(false) }
    var isInstalling by remember { mutableStateOf(false) }
    var statusText by remember { mutableStateOf("Select a module ZIP to begin") }

    var showDownloadDialog by remember { mutableStateOf(false) }
    var downloadProgress by remember { mutableFloatStateOf(0f) }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            selectedZipUri = uri
            selectedFileName = getFileName(context, uri)
            statusText = "Selected: $selectedFileName\nReady to install."
            scope.launch { snackbarHostState.showSnackbar("File selected: $selectedFileName") }
        }
    }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val autdFile = File(context.filesDir, "autd")
            val shaFile = File(context.filesDir, "autd.sha256")
            if (autdFile.exists() && shaFile.exists()) {
                isAutdReady = true
            }
        }
    }

    val verifyOrDownloadAutd: (Boolean) -> Unit = { checked ->
        if (!checked) {
            useAutd = false
        } else {
            statusText = "Verifying AUTD binary..."
            scope.launch(Dispatchers.IO) {
                val startTime = System.currentTimeMillis()
                try {
                    val autdFile = File(context.filesDir, "autd")
                    
                    val response = httpGet("https://api.github.com/repos/xMikkkaa/Automation-Daemon/releases/latest")
                    val json = JSONObject(response)
                    val autdTag = json.getString("tag_name").replace("v", "")
                    val assets = json.getJSONArray("assets")

                    var autdUrl = ""
                    var shaUrl = ""
                    for (i in 0 until assets.length()) {
                        val asset = assets.getJSONObject(i)
                        if (asset.getString("name") == "autd") autdUrl = asset.getString("browser_download_url")
                        if (asset.getString("name") == "autd.sha256") shaUrl = asset.getString("browser_download_url")
                    }

                    if (autdUrl.isEmpty() || shaUrl.isEmpty()) throw Exception("Release assets not found")

                    val remoteSha = httpGet(shaUrl).trim().substringBefore(" ")
                    
                    var needsDownload = true
                    if (autdFile.exists()) {
                        val localSha = hashFile(autdFile)
                        if (localSha == remoteSha) needsDownload = false
                    }

                    if (!needsDownload) {
                        withContext(Dispatchers.Main) {
                            useAutd = true
                            isAutdReady = true
                            statusText = "AUTD binary verified and ready."
                        }
                        RootShellHelper.writeSystemFile("/data/data/com.xaozora.manager/files/autd_version", autdTag)
                        return@launch
                    }

                    withContext(Dispatchers.Main) { 
                        downloadProgress = 0f
                        showDownloadDialog = true 
                    }

                    val tempFile = File(context.filesDir, "autd_temp")
                    try {
                        downloadFile(autdUrl, tempFile) { p -> downloadProgress = p }

                        val downloadedSha = hashFile(tempFile)
                        if (downloadedSha != remoteSha) {
                            throw Exception("Hash mismatch: $downloadedSha != $remoteSha")
                        }

                        tempFile.renameTo(autdFile)
                        File(context.filesDir, "autd.sha256").writeText(remoteSha)
                        RootShellHelper.writeSystemFile("/data/data/com.xaozora.manager/files/autd_version", autdTag)

                        val elapsed = System.currentTimeMillis() - startTime
                        if (elapsed < 1500) delay(1500 - elapsed)

                        withContext(Dispatchers.Main) {
                            showDownloadDialog = false
                            useAutd = true
                            isAutdReady = true
                            statusText = "AUTD downloaded and verified."
                        }
                    } finally {
                        if (tempFile.exists()) tempFile.delete()
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        showDownloadDialog = false
                        useAutd = false
                        statusText = "Failed to verify/download AUTD: ${e.message}"
                    }
                }
            }
        }
    }

    val installModule = {
        if (selectedZipUri != null) {
            isInstalling = true
            statusText = "Preparing installation..."
            
            scope.launch(Dispatchers.IO) {
                val tempZipFile = File(context.cacheDir, "aozora_update.zip")
                try {
                    val autdFile = File(context.filesDir, "autd")
                    if (useAutd && (!autdFile.exists() || autdFile.length() == 0L)) {
                        throw Exception("AUTD binary missing or empty.")
                    }

                    withContext(Dispatchers.Main) { statusText = "Processing ZIP structure..." }

                    val inputStream = context.contentResolver.openInputStream(selectedZipUri!!) ?: throw Exception("Could not open selected ZIP")
                    val zipInputStream = ZipInputStream(inputStream)
                    val zipOutputStream = ZipOutputStream(FileOutputStream(tempZipFile))

                    var entry: ZipEntry?
                    var autdFoundInZip = false
                    while (zipInputStream.nextEntry.also { entry = it } != null) {
                        val name = entry!!.name
                        
                        if (name.startsWith("webroot/")) {
                            zipInputStream.closeEntry()
                            continue
                        }

                        if (useAutd && (name == "system/bin/autd" || name == "./system/bin/autd")) {
                            autdFoundInZip = true
                            zipInputStream.closeEntry()
                            continue
                        }

                        zipOutputStream.putNextEntry(ZipEntry(name))
                        zipInputStream.copyTo(zipOutputStream)
                        zipOutputStream.closeEntry()
                        zipInputStream.closeEntry()
                    }

                    if (useAutd) {
                        val msg = if (autdFoundInZip) "Replacing existing AUTD in ZIP..." else "Injecting AUTD binary..."
                        withContext(Dispatchers.Main) { statusText = msg }

                        zipOutputStream.putNextEntry(ZipEntry("system/bin/autd"))
                        autdFile.inputStream().use { it.copyTo(zipOutputStream) }
                        zipOutputStream.closeEntry()
                    }

                    zipOutputStream.close()
                    zipInputStream.close()

                    withContext(Dispatchers.Main) { statusText = "Executing root installation..." }

                    val shellScript = """
                        cp "${tempZipFile.absolutePath}" /data/local/tmp/aozora_update.zip
                        chmod 755 /data/local/tmp/aozora_update.zip
                        
                        EXISTING=${'$'}(grep -il 'id=.*aozora' /data/adb/modules/*/module.prop 2>/dev/null | head -n 1)
                        if [ ! -z "${'$'}EXISTING" ]; then
                          MOD_DIR=${'$'}(dirname "${'$'}EXISTING")
                          echo "Cleaning old module at ${'$'}MOD_DIR"
                          rm -rf "${'$'}MOD_DIR"
                        fi

                        if [ -f /data/adb/magisk/magisk ]; then
                           echo "Detected Magisk"
                           magisk --install-module /data/local/tmp/aozora_update.zip
                        elif [ -f /data/adb/ksu/bin/ksud ]; then
                           echo "Detected KernelSU"
                           /data/adb/ksu/bin/ksud module install /data/local/tmp/aozora_update.zip
                        elif [ -f /data/adb/ap/bin/apatch ]; then
                           echo "Detected APatch"
                           /data/adb/ap/bin/apatch module install /data/local/tmp/aozora_update.zip
                        else
                           echo "Error: No supported root manager found (Magisk/KSU/APatch)"
                           exit 1
                        fi

                        rm -f /data/local/tmp/aozora_update.zip
                    """.trimIndent()

                    val result = RootShellHelper.executeCmdAndGetOutput("$shellScript 2>&1")
                    
                    withContext(Dispatchers.Main) {
                        isInstalling = false
                        statusText = "--- Installation Output ---\n$result"
                        selectedZipUri = null
                        selectedFileName = null
                        snackbarHostState.showSnackbar("Installation finished")
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        isInstalling = false
                        statusText = "Installation Failed:\n${e.message}"
                        snackbarHostState.showSnackbar("Installation failed: ${e.message}")
                    }
                } finally {
                    if (tempZipFile.exists()) tempZipFile.delete()
                }
            }
        }
    }

    if (showDownloadDialog) {
        val surfaceContainer = MaterialTheme.colorScheme.surfaceContainer
        val dialogStyle = remember(surfaceContainer) {
            HazeStyle(
                blurRadius = 25.dp,
                noiseFactor = 0.1f,
                tints = listOf(HazeTint(surfaceContainer.copy(alpha = 0.25f)))
            )
        }

        Dialog(onDismissRequest = { }) {
            (LocalView.current.parent as? DialogWindowProvider)?.window?.setDimAmount(0f)

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(28.dp))
                    .hazeEffect(state = hazeState, style = dialogStyle)
                    .border(1.2.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), RoundedCornerShape(28.dp))
                    .background(Color.Transparent)
            ) {
                Column(modifier = Modifier.padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Outlined.CloudDownload, contentDescription = null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Downloading AUTD", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold))
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Fetching latest binary from GitHub...", textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurfaceVariant))
                    Spacer(modifier = Modifier.height(24.dp))
                    LinearProgressIndicator(
                        progress = { downloadProgress },
                        modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(8.dp)),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("${(downloadProgress * 100).toInt()}%", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary))
                }
            }
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
                .padding(horizontal = 24.dp, vertical = 16.dp)
        ) {
            Spacer(modifier = Modifier.padding(WindowInsets.statusBars.asPaddingValues()))
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Custom Helper Installer",
                style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Bold, color = colorScheme.primary)
            )
        Spacer(modifier = Modifier.height(24.dp))

        GlassCard(
            hazeState = hazeState,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable(enabled = !isInstalling) { filePickerLauncher.launch("application/zip") }
        ) {
            Column(modifier = Modifier.padding(24.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Outlined.FolderZip, contentDescription = null, modifier = Modifier.size(48.dp), tint = colorScheme.primary)
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = selectedFileName ?: "Tap to select Module ZIP",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    textAlign = TextAlign.Center
                )
            }
        }
        Spacer(modifier = Modifier.height(24.dp))

        GlassCard(
            hazeState = hazeState,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Inject AUTD Daemon", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                Spacer(modifier = Modifier.height(4.dp))
                Text("Downloads & injects latest binary from GitHub", style = MaterialTheme.typography.bodySmall.copy(color = colorScheme.onSurfaceVariant))
            }
            Switch(
                checked = useAutd,
                onCheckedChange = { verifyOrDownloadAutd(it) },
                enabled = selectedZipUri != null && !isInstalling,
                colors = SwitchDefaults.colors(checkedThumbColor = colorScheme.onPrimary, checkedTrackColor = colorScheme.primary)
            )
            }
        }
        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = installModule,
            enabled = selectedZipUri != null && !isInstalling,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(12.dp)
        ) {
            if (isInstalling) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp), color = colorScheme.onPrimary, strokeWidth = 2.5.dp)
            } else {
                Text("Install Module")
            }
        }
        Spacer(modifier = Modifier.height(24.dp))

        GlassCard(
            hazeState = hazeState,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        ) {
            Box(modifier = Modifier.fillMaxWidth().background(Color(0xFF1E1E1E).copy(alpha = 0.5f)).padding(16.dp)) {
            SelectionContainer {
                Text(statusText, style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace, color = Color(0xFF00E676), fontSize = 12.sp))
            }
            }
        }
        Spacer(modifier = Modifier.height(100.dp))
        }
    }
}

private fun httpGet(urlStr: String): String {
    val conn = URL(urlStr).openConnection() as HttpURLConnection
    conn.requestMethod = "GET"
    conn.setRequestProperty("User-Agent", "Aozora-Native")
    return conn.inputStream.bufferedReader().use { it.readText() }
}

private fun downloadFile(urlStr: String, dest: File, onProgress: (Float) -> Unit) {
    val conn = URL(urlStr).openConnection() as HttpURLConnection
    conn.requestMethod = "GET"
    conn.setRequestProperty("User-Agent", "Aozora-Native")
    val total = conn.contentLength
    conn.inputStream.use { input ->
        FileOutputStream(dest).use { output ->
            val buffer = ByteArray(8192)
            var downloaded = 0L
            var bytesRead: Int
            while (input.read(buffer).also { bytesRead = it } != -1) {
                output.write(buffer, 0, bytesRead)
                downloaded += bytesRead
                if (total > 0) onProgress(downloaded.toFloat() / total.toFloat())
            }
        }
    }
}

private fun hashFile(file: File): String {
    val md = MessageDigest.getInstance("SHA-256")
    DigestInputStream(file.inputStream(), md).use { while (it.read(ByteArray(8192)) != -1) { } }
    return md.digest().joinToString("") { "%02x".format(it) }
}

private fun getFileName(context: Context, uri: Uri): String {
    var result: String? = null
    if (uri.scheme == "content") {
        context.contentResolver.query(uri, null, null, null, null)?.use {
            if (it.moveToFirst()) result = it.getString(it.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME))
        }
    }
    return result ?: uri.path?.substringAfterLast('/') ?: "Unknown file"
}
