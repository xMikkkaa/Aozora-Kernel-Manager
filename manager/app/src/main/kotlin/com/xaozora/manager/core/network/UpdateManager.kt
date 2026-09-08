package com.xaozora.manager.core.network

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

data class AppUpdateResult(
    @SerializedName("has_update") val hasUpdate: Boolean,
    @SerializedName("new_version") val newVersion: String = "",
    @SerializedName("download_url") val downloadUrl: String = "",
    @SerializedName("release_notes") val releaseNotes: String = ""
)

data class UpdateCheckResult(
    @SerializedName("app_update") val appUpdate: AppUpdateResult
)

object UpdateManager {
    init {
        System.loadLibrary("native")
    }

    private val gson = Gson()

    private external fun checkUpdatesJson(currentAppVersion: String): String
    private external fun performAppUpdate(apkUrl: String, apkTempPath: String): Boolean

    suspend fun checkUpdates(currentAppVersion: String): UpdateCheckResult = withContext(Dispatchers.IO) {
        val jsonStr = checkUpdatesJson(currentAppVersion)
        gson.fromJson(jsonStr, UpdateCheckResult::class.java)
    }

    suspend fun performAppUpdate(apkUrl: String, tempDir: File): Boolean = withContext(Dispatchers.IO) {
        val apkTempPath = File(tempDir, "aozora_update.apk").absolutePath
        performAppUpdate(apkUrl, apkTempPath)
    }
}
