package com.xaozora.manager.core.utils

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class AppInfoItem(
    val packageName: String,
    val name: String,
    val icon: Drawable? = null
)

data class ConfiguredAppParsed(
    @SerializedName("package_name") val packageName: String,
    val mode: String
)

data class ConfiguredApp(
    val app: AppInfoItem,
    val mode: String
)

object AppManagerUtils {
    init {
        System.loadLibrary("native")
    }

    private val gson = Gson()

    private external fun getConfiguredAppsJson(appListPath: String): String

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
        
        val jsonStr = getConfiguredAppsJson(appListFile.absolutePath)
        val parsedApps = gson.fromJson(jsonStr, Array<ConfiguredAppParsed>::class.java)
        
        if (parsedApps == null || parsedApps.isEmpty()) return@withContext emptyList()
        
        parsedApps.mapNotNull { parsed ->
            val matchedApp = installedApps.find { it.packageName == parsed.packageName }
            if (matchedApp != null) ConfiguredApp(matchedApp, parsed.mode) else null
        }
    }
}
