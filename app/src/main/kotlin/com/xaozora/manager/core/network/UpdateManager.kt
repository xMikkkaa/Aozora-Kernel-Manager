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

data class AutdUpdateResult(
    val hasUpdate: Boolean,
    val newVersion: String = "",
    val downloadUrl: String = "",
    val releaseNotes: String = "",
    val expectedSha: String = ""
)

data class UpdateCheckResult(
    val appUpdate: AppUpdateResult,
    val autdUpdate: AutdUpdateResult
)

object UpdateManager {

    private const val APP_REPO_URL = "https://api.github.com/repos/xMikkkaa/Aozora-Kernel-Manager/releases/latest"
    private const val AUTD_REPO_URL = "https://api.github.com/repos/xMikkkaa/Automation-Daemon/releases/latest"

    suspend fun checkUpdates(currentAppVersion: String): UpdateCheckResult = withContext(Dispatchers.IO) {
        val appUpdate = checkAppUpdate(currentAppVersion)
        val autdUpdate = checkAutdUpdate()
        UpdateCheckResult(appUpdate, autdUpdate)
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

    private fun checkAutdUpdate(): AutdUpdateResult {
        if (!RootShellHelper.checkFileExists("/system/bin/autd")) return AutdUpdateResult(false)

        return try {
            val response = httpGet(AUTD_REPO_URL)
            val json = JSONObject(response)
            val newVersion = json.getString("tag_name").replace("v", "")
            val releaseNotes = json.optString("body", "No release notes provided.")
            val assets = json.getJSONArray("assets")
            
            var autdUrl = ""
            var shaUrl = ""
            
            for (i in 0 until assets.length()) {
                val asset = assets.getJSONObject(i)
                val name = asset.getString("name")
                if (name == "autd") autdUrl = asset.getString("browser_download_url")
                if (name == "autd.sha256") shaUrl = asset.getString("browser_download_url")
            }

            if (autdUrl.isNotEmpty() && shaUrl.isNotEmpty()) {
                val expectedSha = httpGet(shaUrl).trim().substringBefore(" ")
                val localSha = RootShellHelper.executeCmdAndGetOutput("sha256sum /system/bin/autd | awk '{print \$1}'").trim()

                if (localSha.isNotEmpty() && localSha != expectedSha) {
                    return AutdUpdateResult(true, newVersion, autdUrl, releaseNotes, expectedSha)
                } else if (localSha == expectedSha && localSha.isNotEmpty()) {
                    RootShellHelper.writeSystemFile("/data/data/com.xaozora.manager/files/autd_version", newVersion)
                }
            }
            AutdUpdateResult(false)
        } catch (e: Exception) {
            AutdUpdateResult(false)
        }
    }

    suspend fun performAppUpdate(apkUrl: String, tempDir: File): Boolean = withContext(Dispatchers.IO) {
        try {
            val apkTempPath = File(tempDir, "aozora_update.apk")
            downloadFile(apkUrl, apkTempPath)

            val shellScript = """
                cp "${apkTempPath.absolutePath}" /data/local/tmp/aozora_update.apk
                chmod 777 /data/local/tmp/aozora_update.apk
                
                OLD_VER=${"$"}(dumpsys package com.xaozora.manager | grep versionName | head -n 1)
                
                am start -a android.intent.action.VIEW -d "file:///data/local/tmp/aozora_update.apk" -t application/vnd.android.package-archive
                rm -f "${apkTempPath.absolutePath}"
                
                (
                  for i in 1 2 3 4 5 6 7 8 9 10 11 12; do
                    sleep 10
                    NEW_VER=${"$"}(dumpsys package com.xaozora.manager | grep versionName | head -n 1)
                    if [ "${"$"}OLD_VER" != "${"$"}NEW_VER" ]; then
                      break
                    fi
                  done
                  rm -f /data/local/tmp/aozora_update.apk
                ) &
            """.trimIndent()

            RootShellHelper.executeCmd(shellScript)
            true
        } catch (e: Exception) {
            false
        }
    }

    suspend fun performAutdUpdate(autdUrl: String, tempDir: File, newVersion: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val autdTempPath = File(tempDir, "autd_update")
            downloadFile(autdUrl, autdTempPath)

            val shellScript = """
                MOD_PROP=${"$"}(grep -il 'id=.*aozora' /data/adb/modules/*/module.prop 2>/dev/null | head -n 1)
                if [ ! -z "${"$"}MOD_PROP" ]; then
                  MOD_DIR=${"$"}(dirname "${"$"}MOD_PROP")
                  
                  cp "${autdTempPath.absolutePath}" "${"$"}MOD_DIR/system/bin/autd"
                  chmod 755 "${"$"}MOD_DIR/system/bin/autd"
                  
                  killall autd 2>/dev/null
                  nohup "${"$"}MOD_DIR/system/bin/autd" > /dev/null 2>&1 &
                  
                  echo "$newVersion" > /data/data/com.xaozora.manager/files/autd_version
                  
                  rm -f "${autdTempPath.absolutePath}"
                  exit 0
                else
                  rm -f "${autdTempPath.absolutePath}"
                  exit 1
                fi
            """.trimIndent()

            RootShellHelper.executeCmd(shellScript)
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
        val url = URL(urlStr)
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "GET"
        conn.setRequestProperty("User-Agent", "Aozora-Kernel-Manager")
        conn.connectTimeout = 10000
        conn.readTimeout = 10000
        return conn.inputStream.bufferedReader().use { it.readText() }
    }

    private fun downloadFile(urlStr: String, dest: File) {
        val url = URL(urlStr)
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "GET"
        conn.setRequestProperty("User-Agent", "Aozora-Kernel-Manager")
        conn.connectTimeout = 15000
        conn.readTimeout = 15000
        conn.inputStream.use { input ->
            FileOutputStream(dest).use { output ->
                input.copyTo(output)
            }
        }
    }
}
