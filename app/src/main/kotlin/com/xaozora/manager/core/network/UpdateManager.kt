package com.xaozora.manager.core.network

import com.xaozora.manager.core.shell.RootShellHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

data class AppUpdateResult(
    val hasUpdate: Boolean,
    val newVersion: String = "",
    val downloadUrl: String = "",
    val releaseNotes: String = ""
)

data class UpdateCheckResult(
    val appUpdate: AppUpdateResult
)

object UpdateManager {

    private const val APP_REPO_URL = "https://api.github.com/repos/xMikkkaa/Aozora-Kernel-Manager/releases/latest"

    suspend fun checkUpdates(currentAppVersion: String): UpdateCheckResult = withContext(Dispatchers.IO) {
        val appUpdate = checkAppUpdate(currentAppVersion)
        UpdateCheckResult(appUpdate)
    }

    private fun checkAppUpdate(currentAppVersion: String): AppUpdateResult {
        return try {
            val response = httpGet(APP_REPO_URL)
            val json = JSONObject(response)
            val newVersion = json.getString("tag_name").replace("v", "")
            val releaseNotes = json.optString("body", "No release notes provided.")
            
            if (isVersionGreater(newVersion, currentAppVersion)) {
                val assets = json.getJSONArray("assets")
                var downloadUrl = ""
                for (i in 0 until assets.length()) {
                    val asset = assets.getJSONObject(i)
                    if (asset.getString("name").endsWith(".apk")) {
                        downloadUrl = asset.getString("browser_download_url")
                        break
                    }
                }
                if (downloadUrl.isNotEmpty()) {
                    return AppUpdateResult(true, newVersion, downloadUrl, releaseNotes)
                }
            }
            AppUpdateResult(false)
        } catch (e: Exception) {
            AppUpdateResult(false)
        }
    }



    suspend fun performAppUpdate(apkUrl: String, tempDir: File): Boolean = withContext(Dispatchers.IO) {
        try {
            val apkTempPath = File(tempDir, "aozora_update.apk")
            downloadFile(apkUrl, apkTempPath)

            val shellScript = """
                cp "${apkTempPath.absolutePath}" /data/local/tmp/aozora_update.apk
                chmod 777 /data/local/tmp/aozora_update.apk
                
                (
                  pm install -r -d /data/local/tmp/aozora_update.apk
                  
                  rm -f "${apkTempPath.absolutePath}"
                  rm -f /data/local/tmp/aozora_update.apk
                  
                  sleep 1
                  am start -n com.xaozora.manager/.MainActivity
                ) >/dev/null 2>&1 &
            """.trimIndent()

            RootShellHelper.executeCmd(shellScript)
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun isVersionGreater(newVer: String, oldVer: String): Boolean {
        val v1 = newVer.split(".").map { it.toIntOrNull() ?: 0 }
        val v2 = oldVer.split(".").map { it.toIntOrNull() ?: 0 }
        val maxLen = maxOf(v1.size, v2.size)
        
        for (i in 0 until maxLen) {
            val n1 = v1.getOrNull(i) ?: 0
            val n2 = v2.getOrNull(i) ?: 0
            if (n1 > n2) return true
            if (n1 < n2) return false
        }
        return false
    }

    private fun httpGet(urlStr: String): String {
        var url = URL(urlStr)
        var redirectCount = 0
        
        while (redirectCount < 5) {
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.setRequestProperty("User-Agent", "Aozora-Kernel-Manager")
            conn.connectTimeout = 10000
            conn.readTimeout = 10000
            
            val status = conn.responseCode
            if (status == HttpURLConnection.HTTP_MOVED_TEMP || 
                status == HttpURLConnection.HTTP_MOVED_PERM || 
                status == HttpURLConnection.HTTP_SEE_OTHER ||
                status == 307 || status == 308) {
                
                val newUrl = conn.getHeaderField("Location")
                url = URL(newUrl)
                redirectCount++
            } else {
                return conn.inputStream.bufferedReader().use { it.readText() }
            }
        }
        return ""
    }

    private fun downloadFile(urlStr: String, dest: File) {
        var url = URL(urlStr)
        var redirectCount = 0
        
        while (redirectCount < 5) {
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.setRequestProperty("User-Agent", "Aozora-Kernel-Manager")
            conn.connectTimeout = 15000
            conn.readTimeout = 15000
            
            val status = conn.responseCode
            if (status == HttpURLConnection.HTTP_MOVED_TEMP || 
                status == HttpURLConnection.HTTP_MOVED_PERM || 
                status == HttpURLConnection.HTTP_SEE_OTHER ||
                status == 307 || status == 308) {
                
                val newUrl = conn.getHeaderField("Location")
                url = URL(newUrl)
                redirectCount++
            } else {
                conn.inputStream.use { input ->
                    FileOutputStream(dest).use { output ->
                        input.copyTo(output)
                    }
                }
                break
            }
        }
    }
}
