package com.xaozora.manager.core.utils

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import com.xaozora.manager.core.shell.RootShellHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class AppInfoItem(
    val packageName: String,
    val name: String,
    val icon: Drawable
)

data class ConfiguredApp(
    val app: AppInfoItem,
    val mode: String
)

object AppManagerUtils {

    suspend fun getInstalledApps(context: Context): List<AppInfoItem> = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val packages = pm.getInstalledApplications(PackageManager.GET_META_DATA)
        
        packages.filter { appInfo ->
            (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) == 0 || 
            (appInfo.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
        }.map { appInfo ->
            AppInfoItem(
                packageName = appInfo.packageName,
                name = pm.getApplicationLabel(appInfo).toString(),
                icon = pm.getApplicationIcon(appInfo)
            )
        }.sortedBy { it.name.lowercase() }
    }

    suspend fun getConfiguredApps(context: Context): List<ConfiguredApp> = withContext(Dispatchers.IO) {
        val installedApps = getInstalledApps(context)
        val appListFile = java.io.File(context.filesDir, "autd/applist")
        val appListStr = RootShellHelper.readSystemFile(appListFile.absolutePath)
        
        if (appListStr.isBlank()) return@withContext emptyList()
        
        appListStr.lines().mapNotNull { line ->
            val trimmed = line.trim()
            val mode = when {
                trimmed.endsWith("_p") -> "p"
                trimmed.endsWith("_g") -> "g"
                trimmed.endsWith("_g2") -> "g2"
                else -> return@mapNotNull null
            }
            val packageName = trimmed.substringBeforeLast("_$mode")
            val matchedApp = installedApps.find { it.packageName == packageName }
            if (matchedApp != null) ConfiguredApp(matchedApp, mode) else null
        }
    }
}
