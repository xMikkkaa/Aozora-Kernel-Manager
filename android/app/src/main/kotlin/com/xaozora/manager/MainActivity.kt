package com.xaozora.manager

import android.content.Intent
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.os.BatteryManager
import android.os.SystemClock
import android.widget.Toast
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.embedding.android.FlutterActivity
import io.flutter.plugin.common.MethodChannel
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.io.FileReader
import java.util.concurrent.TimeUnit

class MainActivity : FlutterActivity() {
    private val CHANNEL = "com.xaozora.manager/daemon"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        filesDir // Ensure internal storage is initialized
    }

    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)
        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, CHANNEL).setMethodCallHandler { call, result ->
            when (call.method) {
                "checkRoot" -> {
                    Thread {
                        val isRooted = checkRoot()
                        runOnUiThread { result.success(isRooted) }
                    }.start()
                }
                "startMonitorService" -> {
                    startMonitorService()
                    Toast.makeText(this, "✅ Aozora: Root Granted & Active", Toast.LENGTH_SHORT).show()
                    result.success(true)
                }
                "stopMonitorService" -> {
                    stopMonitorService()
                    result.success(true)
                }
                "isMonitorServiceRunning" -> {
                    result.success(MonitorService.isServiceRunning)
                }
                "isDaemonRunning" -> {
                    Thread {
                        val isRunning = isDaemonRunning()
                        runOnUiThread { result.success(isRunning) }
                    }.start()
                }
                "startDaemon" -> {
                    Thread {
                        startDaemon()
                        runOnUiThread { result.success(true) }
                    }.start()
                }
                "stopDaemon" -> {
                    Thread {
                        stopDaemon()
                        runOnUiThread { result.success(true) }
                    }.start()
                }
                "getSystemInfo" -> {
                    // Ambil data Context-dependent di Main Thread untuk mencegah crash
                    val metrics = resources.displayMetrics
                    val resolution = "${metrics.widthPixels}x${metrics.heightPixels}"
                    val bm = getSystemService(Context.BATTERY_SERVICE) as BatteryManager
                    val batteryLevel = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
                    val battery = "$batteryLevel%"

                    Thread {
                        try {
                            val rootData = getRootInfo()
                            val uptimeMillis = SystemClock.elapsedRealtime()
                            val uptime = String.format("%dh %dm", 
                                TimeUnit.MILLISECONDS.toHours(uptimeMillis),
                                TimeUnit.MILLISECONDS.toMinutes(uptimeMillis) % 60
                            )
                            
                            val governor = getGovernor()

                            val info = mapOf(
                                "model" to Build.MODEL,
                                "device" to Build.DEVICE,
                                "android" to Build.VERSION.RELEASE,
                                "selinux" to getSELinuxStatus(),
                                "soc" to Build.BOARD,
                                "ram" to getTotalRAM(),
                                "kernel" to getKernelVersion(),
                                "uptime" to uptime,
                                "battery" to battery,
                                "resolution" to resolution,
                                "governor" to governor,
                                "root_manager" to rootData.first,
                                "root_version" to rootData.second
                            )
                            runOnUiThread { result.success(info) }
                        } catch (e: Exception) {
                            runOnUiThread { result.error("ERROR", e.message, null) }
                        }
                    }.start()
                }
                "checkFileExists" -> {
                    val path = call.argument<String>("path")
                    Thread {
                        var exists = false
                        if (path != null) {
                            try {
                                val p = Runtime.getRuntime().exec(arrayOf("su", "-mm", "-c", "[ -f \"$path\" ]"))
                                val exitCode = p.waitFor()
                                exists = (exitCode == 0)
                            } catch (e: Exception) {
                                exists = false
                            }
                        }
                        runOnUiThread { result.success(exists) }
                    }.start()
                }
                "executeScript" -> {
                    val script = call.argument<String>("script")
                    if (script != null) {
                        Thread {
                            try {
                                val p = Runtime.getRuntime().exec(arrayOf("su", "-mm", "-c", script))
                                p.waitFor()
                                runOnUiThread { result.success(true) }
                            } catch (e: Exception) {
                                runOnUiThread { result.error("EXEC_ERROR", e.message, null) }
                            }
                        }.start()
                    } else {
                        result.error("INVALID", "Script is null", null)
                    }
                }
                "getRamStats" -> {
                    Thread {
                        val stats = getRamStatsMap()
                        runOnUiThread { result.success(stats) }
                    }.start()
                }
                "readSystemFile" -> {
                    val path = call.argument<String>("path")
                    Thread {
                        val content = readFileContent(path)
                        runOnUiThread { result.success(content) }
                    }.start()
                }
                "writeSystemFile" -> {
                    val path = call.argument<String>("path")
                    val value = call.argument<String>("value")
                    Thread {
                        val success = writeRootFile(path, value)
                        runOnUiThread { result.success(success) }
                    }.start()
                }
                else -> result.notImplemented()
            }
        }
    }

    private fun checkRoot(): Boolean {
        var p: Process? = null
        return try {
            p = Runtime.getRuntime().exec(arrayOf("su", "-c", "id"))
            p.waitFor() == 0
        } catch (e: Exception) {
            false
        } finally {
            p?.destroy()
        }
    }

    private fun isDaemonRunning(): Boolean {
        return try {
            // Cek menggunakan pidof, pgrep, atau ps sebagai fallback
            val cmd = "pidof autd > /dev/null || pgrep -x autd > /dev/null || ps -A | grep autd | grep -v grep > /dev/null"
            val p = Runtime.getRuntime().exec(arrayOf("su", "-mm", "-c", cmd))
            val exitCode = p.waitFor()
            exitCode == 0
        } catch (e: Exception) {
            false
        }
    }

    private fun startDaemon() {
        try {
            val cmd = "/system/bin/autd > /dev/null 2>&1 &"
            Runtime.getRuntime().exec(arrayOf("su", "-mm", "-c", cmd))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun stopDaemon() {
        try {
            Runtime.getRuntime().exec(arrayOf("su", "-mm", "-c", "killall autd || pkill -x autd"))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun getRootInfo(): Pair<String, String> {
        try {
            val p = Runtime.getRuntime().exec(arrayOf("su", "--version"))
            val reader = BufferedReader(InputStreamReader(p.inputStream))
            val output = reader.readLine()
            p.waitFor()

            if (output != null) {
                val lower = output.lowercase()
                val name = when {
                    lower.contains("magisk") -> "Magisk"
                    lower.contains("ksu") || lower.contains("kernelsu") -> "KernelSU"
                    lower.contains("apatch") -> "APatch"
                    else -> "SuperUser"
                }
                return Pair(name, output)
            }
        } catch (e: Exception) {
            // Fallthrough to return No Root
        }
        return Pair("No Root", "Access Denied")
    }

    private fun getKernelVersion(): String {
        try {
            val content = File("/proc/version").readText().trim()
            if (content.isNotEmpty()) return content
        } catch (e: Exception) { /* Ignore */ }

        try {
            val p = Runtime.getRuntime().exec(arrayOf("su", "-c", "cat /proc/version"))
            val reader = BufferedReader(InputStreamReader(p.inputStream))
            val content = reader.readText()
            p.waitFor()
            if (content.isNotEmpty()) return content.trim()
        } catch (e: Exception) { /* Ignore */ }

        return System.getProperty("os.version") ?: "Unknown"
    }

    private fun getTotalRAM(): String {
        return try {
            val reader = BufferedReader(FileReader("/proc/meminfo"))
            val line = reader.readLine() // MemTotal:        5866664 kB
            reader.close()
            val parts = line.split("\\s+".toRegex())
            if (parts.size > 1) {
                val kb = parts[1].toLong()
                val gb = kb / (1024.0 * 1024.0)
                String.format("%.1f GB", gb)
            } else {
                "N/A"
            }
        } catch (e: Exception) {
            "N/A"
        }
    }

    private fun getRamStatsMap(): Map<String, Long> {
        var total = 0L
        var available = 0L
        try {
            val reader = BufferedReader(FileReader("/proc/meminfo"))
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                val parts = line!!.split("\\s+".toRegex())
                if (parts.size < 2) continue
                val key = parts[0].replace(":", "")
                val value = parts[1].toLong() // in kB
                
                if (key == "MemTotal") total = value / 1024 // to MB
                if (key == "MemAvailable") available = value / 1024 // to MB
                
                if (total > 0 && available > 0) break
            }
            reader.close()
        } catch (e: Exception) { e.printStackTrace() }
        
        val used = if (total > 0) total - available else 0
        return mapOf("total" to total, "used" to used, "free" to available)
    }

    private fun readFileContent(path: String?): String {
        if (path == null) return ""
        // Try normal read first
        try {
            val f = File(path)
            if (f.exists() && f.canRead()) {
                return f.readText().trim()
            }
        } catch (e: Exception) { /* ignore */ }
        
        // Fallback to root
        return try {
            val p = Runtime.getRuntime().exec(arrayOf("su", "-mm", "-c", "cat \"$path\""))
            val reader = BufferedReader(InputStreamReader(p.inputStream))
            val content = reader.readText()
            p.waitFor()
            content.trim()
        } catch (e: Exception) { "" }
    }

    private fun writeRootFile(path: String?, value: String?): Boolean {
        if (path == null || value == null) return false
        return try {
            val p = Runtime.getRuntime().exec(arrayOf("su", "-mm", "-c", "echo \"$value\" > \"$path\""))
            p.waitFor() == 0
        } catch (e: Exception) { false }
    }

    private fun getSELinuxStatus(): String {
        try {
            val p = Runtime.getRuntime().exec("getenforce")
            val reader = BufferedReader(InputStreamReader(p.inputStream))
            val line = reader.readLine()
            p.waitFor()
            if (!line.isNullOrEmpty()) return line.trim()
        } catch (e: Exception) { /* Ignore */ }

        try {
            val p = Runtime.getRuntime().exec(arrayOf("su", "-c", "getenforce"))
            val reader = BufferedReader(InputStreamReader(p.inputStream))
            val line = reader.readLine()
            p.waitFor()
            if (!line.isNullOrEmpty()) return line.trim()
        } catch (e: Exception) { /* Ignore */ }

        try {
            val p = Runtime.getRuntime().exec(arrayOf("su", "-c", "cat /sys/fs/selinux/enforce"))
            val reader = BufferedReader(InputStreamReader(p.inputStream))
            val line = reader.readLine()
            p.waitFor()
            if (line != null) {
                return if (line.trim() == "1") "Enforcing" else "Permissive"
            }
        } catch (e: Exception) { /* Ignore */ }

        return "Unknown"
    }

    private fun getGovernor(): String {
        return try {
            val p = Runtime.getRuntime().exec(arrayOf("su", "-c", "cat /sys/devices/system/cpu/cpu0/cpufreq/scaling_governor"))
            val reader = BufferedReader(InputStreamReader(p.inputStream))
            val line = reader.readLine()
            p.waitFor()
            line?.trim() ?: "Unknown"
        } catch (e: Exception) { "Unknown" }
    }

    private fun startMonitorService() {
        val serviceIntent = Intent(this, MonitorService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }
    }

    private fun stopMonitorService() {
        val serviceIntent = Intent(this, MonitorService::class.java)
        stopService(serviceIntent)
    }
}
