package com.xaozora.manager.core.utils

import android.content.Context
import android.util.Log
import com.xaozora.manager.core.shell.RootShellHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

object NativeDaemonManager {
    private const val DAEMON_FILENAME = "xaozora_daemon"
    private const val TAG = "NativeDaemonManager"
    private val daemonMutex = Mutex()

    suspend fun extractAndStartDaemon(context: Context, enableAutd: Boolean? = null) = withContext(Dispatchers.IO) {
        daemonMutex.withLock {
            val daemonFile = File(context.filesDir, DAEMON_FILENAME)
            
            val prefs = context.getSharedPreferences("aozora_prefs", Context.MODE_PRIVATE)
            val shouldEnableAutd = enableAutd ?: prefs.getBoolean("autd_enabled", true)

            if (enableAutd == null && isDaemonRunning()) {
                Log.d(TAG, "Daemon is already running, skipping startup.")
                return@withLock true
            }

            Log.d(TAG, "Starting daemon. enableAutd: $shouldEnableAutd")

            try {
                context.assets.open(DAEMON_FILENAME).use { input ->
                    FileOutputStream(daemonFile).use { output ->
                        input.copyTo(output)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Extraction failed", e)
                return@withLock false
            }

            if (!daemonFile.exists()) return@withLock false

            val executablePath = daemonFile.absolutePath
            File(context.filesDir, "battmon").mkdirs()
            File(context.filesDir, "autd").mkdirs()
            val batteryLogPath = File(context.filesDir, "battmon/battery_logger.jsonl").absolutePath
            
            RootShellHelper.executeCmd("chmod +x $executablePath")
            
            RootShellHelper.executeCmd("pkill -9 $DAEMON_FILENAME; killall -9 $DAEMON_FILENAME")
            kotlinx.coroutines.delay(300)
            
            var args = "--battery-logger $batteryLogPath"
            if (shouldEnableAutd) {
                args = "--enable-autd $args"
            }
            
            val startCmd = "nohup $executablePath $args > /dev/null 2>&1 &"
            if (RootShellHelper.executeCmd(startCmd)) {
                kotlinx.coroutines.delay(200)
                withContext(Dispatchers.Main) {
                    val message = if (shouldEnableAutd) "xAozora Daemon Started" else "xAozora Daemon Started (AUTD Disabled)"
                    android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_SHORT).show()
                }
            }
            
            true
        }
    }

    fun isDaemonRunning(): Boolean {
        val cmd = "pgrep -x $DAEMON_FILENAME"
        val output = RootShellHelper.executeCmdAndGetOutput(cmd)
        return output.isNotBlank()
    }
}
