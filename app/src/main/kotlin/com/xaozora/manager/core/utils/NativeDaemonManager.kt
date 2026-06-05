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

    private fun suCmd(cmd: String): Boolean {
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", cmd))
            process.waitFor() == 0
        } catch (e: Exception) {
            false
        }
    }

    private fun suCmdOut(cmd: String): String {
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", cmd))
            process.inputStream.bufferedReader().use { it.readText() }
        } catch (e: Exception) {
            ""
        }
    }

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
                val tmpFile = File(context.filesDir, "${DAEMON_FILENAME}_tmp")
                context.assets.open(DAEMON_FILENAME).use { input ->
                    FileOutputStream(tmpFile).use { output ->
                        input.copyTo(output)
                    }
                }
                
                suCmd("killall -9 $DAEMON_FILENAME; pkill -9 $DAEMON_FILENAME")
                kotlinx.coroutines.delay(500)
                
                suCmd("rm -f ${daemonFile.absolutePath}; mv ${tmpFile.absolutePath} ${daemonFile.absolutePath}")
            } catch (e: Exception) {
                Log.e(TAG, "Extraction failed", e)
                return@withLock false
            }

            if (!daemonFile.exists()) return@withLock false

            val executablePath = daemonFile.absolutePath
            File(context.filesDir, "battmon").mkdirs()
            File(context.filesDir, "autd").mkdirs()
            val batteryLogPath = File(context.filesDir, "battmon/battery_logger.jsonl").absolutePath
            
            val autdInfoFile = File(context.filesDir, "autd/autd_awake_method.info")
            if (!autdInfoFile.exists()) autdInfoFile.createNewFile()
            
            suCmd("chmod 666 ${autdInfoFile.absolutePath}")
            suCmd("chmod -R 777 ${context.filesDir.absolutePath}/autd")
            
            suCmd("chmod +x $executablePath")
            
            val shouldEnableBattmon = prefs.getBoolean("battery_monitor_service", true)
            var args = if (shouldEnableBattmon) "--battery-logger $batteryLogPath " else ""
            
            if (shouldEnableAutd) {
                args += "--enable-autd"
            } else {
                args += "--disable-autd"
            }
            
            val startCmd = "cd ${context.filesDir.absolutePath} && nohup $executablePath $args > /dev/null 2>&1 &"
            if (suCmd(startCmd)) {
                kotlinx.coroutines.delay(200)
            }
            
            true
        }
    }

    fun isDaemonRunning(): Boolean {
        val cmd = "pgrep -x $DAEMON_FILENAME"
        val output = suCmdOut(cmd)
        return output.isNotBlank()
    }
}
