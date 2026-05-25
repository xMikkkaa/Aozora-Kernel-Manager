package com.xaozora.manager.core.utils

import com.xaozora.manager.core.shell.RootShellHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

data class SystemInfo(
    val model: String,
    val device: String,
    val android: String,
    val selinux: String,
    val soc: String,
    val ram: String,
    val kernel: String,
    val uptime: String,
    val battery: String,
    val resolution: String,
    val rootManager: String,
    val rootVersion: String,
    val loadAvg: String,
    val entropy: String,
    val capacity: String,
    val governor: String,
    val batteryHealth: String,
    val deepSleep: String,
    val wireguard: String,
    val openGl: String
)

object SystemInfoUtils {

    suspend fun fetchSystemInfo(): SystemInfo = withContext(Dispatchers.IO) {
        val loadAvgRaw = RootShellHelper.readSystemFile("/proc/loadavg")
        val loadAvg = if (loadAvgRaw.isNotBlank()) loadAvgRaw.split(" ").take(3).joinToString(" ") else "-"
        
        val entropy = RootShellHelper.readSystemFile("/proc/sys/kernel/random/entropy_avail").trim()
        
        val elapsed = android.os.SystemClock.elapsedRealtime()
        val uptimeMillis = android.os.SystemClock.uptimeMillis()
        val deepSleepMillis = elapsed - uptimeMillis
        
        val formatTime = { ms: Long ->
            val totalSeconds = ms / 1000
            val days = totalSeconds / 86400
            val hours = (totalSeconds % 86400) / 3600
            val minutes = (totalSeconds % 3600) / 60
            val seconds = totalSeconds % 60
            buildString {
                if (days > 0) append("${days}d ")
                if (hours > 0 || days > 0) append("${hours}h ")
                if (minutes > 0 || hours > 0 || days > 0) append("${minutes}m ")
                append("${seconds}s")
            }.trim()
        }
        val deepSleepTime = formatTime(deepSleepMillis)
        val deepSleepPct = if (elapsed > 0) ((deepSleepMillis.toFloat() / elapsed.toFloat()) * 100).toInt() else 0
        val deepSleep = "$deepSleepTime ($deepSleepPct%)"
        
        val wgVersion = RootShellHelper.readSystemFile("/sys/module/wireguard/version").trim()
        val wireguard = if (wgVersion.isNotBlank()) wgVersion else "Unsupported"
        
        val openGlOutput = RootShellHelper.executeCmdAndGetOutput("dumpsys SurfaceFlinger | grep -i GLES").trim()
        val openGl = if (openGlOutput.contains("GLES:")) {
            openGlOutput.substringAfter("GLES:").trim()
        } else {
            openGlOutput.takeIf { it.isNotBlank() } ?: "-"
        }
        
        val govStr = RootShellHelper.readSystemFile("/sys/devices/system/cpu/cpu0/cpufreq/scaling_governor").trim()
        val governor = if (govStr.isNotBlank()) govStr else "Unknown"
        
        var chargeFull = 0f
        var chargeFullDesign = 0f
        val batPaths = listOf("bms", "battery", "BAT0")
        for (p in batPaths) {
            val fullStr = RootShellHelper.readSystemFile("/sys/class/power_supply/$p/charge_full").trim()
            val designStr = RootShellHelper.readSystemFile("/sys/class/power_supply/$p/charge_full_design").trim()
            if (fullStr.isNotBlank() && designStr.isNotBlank()) {
                chargeFull = fullStr.toFloatOrNull() ?: 0f
                chargeFullDesign = designStr.toFloatOrNull() ?: 0f
                break
            }
        }
        
        var capacity = "Unknown"
        var batteryHealth = "Unknown"
        
        var learnedCapacity = 0f
        try {
            val statsFile = java.io.File("/data/data/com.xaozora.manager/files/battmon/battery_stats.json")
            if (statsFile.exists()) {
                val content = statsFile.readText().trim()
                if (content.isNotBlank()) {
                    val json = org.json.JSONObject(content)
                    learnedCapacity = json.optDouble("last_learned_capacity_mah", 0.0).toFloat()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        if (chargeFullDesign > 0f) {
            val designCapacityMAh = if (chargeFullDesign > 10000) (chargeFullDesign / 1000).toInt() else chargeFullDesign.toInt()
            
            val currentCapacity = if (learnedCapacity > 0f) {
                if (learnedCapacity > 10000f) (learnedCapacity / 1000f) else learnedCapacity
            } else {
                if (chargeFull > 10000f) (chargeFull / 1000f) else chargeFull
            }

            val healthRaw = (currentCapacity / designCapacityMAh * 100).coerceIn(0f, 100f)
            val healthCategory = when {
                healthRaw >= 80f -> "Good"
                healthRaw >= 60f -> "Fair"
                else -> "Poor"
            }
            capacity = "$designCapacityMAh mAh"
            batteryHealth = String.format("%.1f%% (%s)", healthRaw, healthCategory)
        }

        SystemInfo(
            model = getProp("ro.product.model"),
            device = getProp("ro.product.device"),
            android = getProp("ro.build.version.release"),
            selinux = RootShellHelper.executeCmdAndGetOutput("getenforce").takeIf { it.isNotBlank() } ?: "-",
            soc = getSocInfo(),
            ram = getRamInfo(),
            kernel = RootShellHelper.executeCmdAndGetOutput("cat /proc/version").takeIf { it.isNotBlank() } ?: "-",
            uptime = getUptime(),
            battery = getBattery(),
            resolution = getResolution(),
            rootManager = getRootManager(),
            rootVersion = getRootVersion(),
            loadAvg = loadAvg,
            entropy = entropy,
            capacity = capacity,
            governor = governor,
            batteryHealth = batteryHealth,
            deepSleep = deepSleep,
            wireguard = wireguard,
            openGl = openGl
        )
    }

    private fun getProp(key: String): String {
        return RootShellHelper.executeCmdAndGetOutput("getprop $key").takeIf { it.isNotBlank() } ?: "-"
    }

    private fun getSocInfo(): String {
        val board = getProp("ro.board.platform")
        return if (board != "-") board else getProp("ro.hardware")
    }

    private fun getRamInfo(): String {
        val meminfo = RootShellHelper.readSystemFile("/proc/meminfo")
        val memTotalLine = meminfo.lines().find { it.startsWith("MemTotal:") } ?: return "-"
        val kb = memTotalLine.replace(Regex("[^0-9]"), "").toLongOrNull() ?: return "-"
        val gb = kb / 1024.0 / 1024.0
        return "${(gb * 10.0).roundToInt() / 10.0} GB"
    }

    private fun getUptime(): String {
        val elapsed = android.os.SystemClock.elapsedRealtime()
        val totalSeconds = elapsed / 1000
        val days = totalSeconds / 86400
        val hours = (totalSeconds % 86400) / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        
        return buildString {
            if (days > 0) append("${days}d ")
            if (hours > 0 || days > 0) append("${hours}h ")
            if (minutes > 0 || hours > 0 || days > 0) append("${minutes}m ")
            append("${seconds}s")
        }.trim().ifEmpty { "-" }
    }

    private fun getBattery(): String {
        val cap = RootShellHelper.readSystemFile("/sys/class/power_supply/battery/capacity").ifBlank {
            RootShellHelper.readSystemFile("/sys/class/power_supply/bms/capacity")
        }
        return if (cap.isNotBlank()) "$cap%" else "-"
    }

    private fun getResolution(): String {
        val wmOut = RootShellHelper.executeCmdAndGetOutput("wm size")
        return wmOut.substringAfterLast("size:").trim().takeIf { it.isNotBlank() } ?: "-"
    }

    private fun getRootManager(): String {
        val suInfo = RootShellHelper.executeCmdAndGetOutput("su -v").uppercase()
        return when {
            suInfo.contains("MAGISK") -> "Magisk"
            suInfo.contains("KERNELSU") -> "KernelSU"
            suInfo.contains("APATCH") -> "APatch"
            else -> "SU"
        }
    }

    private fun getRootVersion(): String {
        val suInfo = RootShellHelper.executeCmdAndGetOutput("su -v")
        val version = suInfo.substringBefore(":").substringBefore("-").trim()
        return version.takeIf { it.isNotBlank() } ?: "-"
    }
}

data class RealTimeMetrics(
    val cpuLoad: Float,
    val coreFreqs: List<String>,
    val coreProgress: List<Float>,
    val gpuLoad: Float,
    val gpuFreq: String,
    val ramUsed: String,
    val ramTotal: String,
    val ramProgress: Float,
    val swapUsed: String,
    val swapTotal: String,
    val swapProgress: Float,
    val batteryLevel: Int,
    val batteryTemp: String,
    val batteryCurrent: String
)

class HardwarePoller {
    private var lastTotal = 0L
    private var lastIdle = 0L

    suspend fun poll(): RealTimeMetrics = withContext(Dispatchers.IO) {
        // CPU
        var cpuLoad = 0f
        val stat = RootShellHelper.readSystemFile("/proc/stat")
        val cpuLine = stat.lines().firstOrNull { it.startsWith("cpu ") }
        if (cpuLine != null) {
            val parts = cpuLine.trim().split(Regex("\\s+"))
            var total = 0L
            for (i in 1 until parts.size) {
                total += parts[i].toLongOrNull() ?: 0L
            }
            val idle = if (parts.size > 4) parts[4].toLongOrNull() ?: 0L else 0L
            val diffIdle = idle - lastIdle
            val diffTotal = total - lastTotal
            if (diffTotal > 0 && lastTotal > 0) {
                cpuLoad = (1f - (diffIdle.toFloat() / diffTotal.toFloat())).coerceIn(0f, 1f)
            }
            lastTotal = total
            lastIdle = idle
        }
        
        val freqs = mutableListOf<String>()
        val progresses = mutableListOf<Float>()
        
        val policyOut = RootShellHelper.executeCmdAndGetOutput(
            "for p in /sys/devices/system/cpu/cpufreq/policy*; do " +
            "c=\$(cat \$p/related_cpus 2>/dev/null); " +
            "f=\$(cat \$p/scaling_cur_freq 2>/dev/null || echo Offline); " +
            "m=\$(cat \$p/scaling_max_freq 2>/dev/null || echo 1); " +
            "echo \"\$c|\$f|\$m\"; " +
            "done"
        )
        
        val freqsMap = mutableMapOf<Int, String>()
        val progMap = mutableMapOf<Int, Float>()
        
        policyOut.lines().forEach { line ->
            val parts = line.split("|")
            if (parts.size == 3 && parts[0].isNotBlank()) {
                val cpus = parts[0].trim().split(" ")
                val fStr = parts[1].trim()
                val mStr = parts[2].trim()
                val freqVal = fStr.toLongOrNull() ?: 0L
                val maxVal = mStr.toLongOrNull() ?: 1L
                
                val freqFormatted = if (fStr == "Offline" || fStr.isEmpty()) "Offline" else if (fStr.length > 3) "${fStr.dropLast(3)} MHz" else "$fStr MHz"
                val progVal = if (fStr == "Offline" || fStr.isEmpty()) 0f else if (maxVal > 0) (freqVal.toFloat() / maxVal.toFloat()).coerceIn(0f, 1f) else 0f
                
                cpus.forEach { cpuStr ->
                    val cpuId = cpuStr.toIntOrNull()
                    if (cpuId != null) {
                        freqsMap[cpuId] = freqFormatted
                        progMap[cpuId] = progVal
                    }
                }
            }
        }
        
        for (i in 0 until 8) {
            if (freqsMap.containsKey(i)) {
                freqs.add(freqsMap[i]!!)
                progresses.add(progMap[i]!!)
            } else {
                val fStr = RootShellHelper.readSystemFile("/sys/devices/system/cpu/cpu$i/cpufreq/scaling_cur_freq").trim()
                val maxStr = RootShellHelper.readSystemFile("/sys/devices/system/cpu/cpu$i/cpufreq/scaling_max_freq").trim()
                val freqVal = fStr.toLongOrNull() ?: 0L
                val maxVal = maxStr.toLongOrNull() ?: 1L
                
                if (fStr.isEmpty()) {
                    freqs.add("Offline")
                    progresses.add(0f)
                } else {
                    freqs.add(if (fStr.length > 3) "${fStr.dropLast(3)} MHz" else "$fStr MHz")
                    progresses.add(if (maxVal > 0) (freqVal.toFloat() / maxVal.toFloat()).coerceIn(0f, 1f) else 0f)
                }
            }
        }
        
        val gLoadStr = RootShellHelper.readSystemFile("/sys/class/kgsl/kgsl-3d0/gpu_busy_percentage").trim()
        val rawLoadPct = gLoadStr.replace("%", "").trim().toFloatOrNull() ?: 0f
        
        val gFreqStr = RootShellHelper.readSystemFile("/sys/class/kgsl/kgsl-3d0/gpuclk").trim()
        val gFreqVal = gFreqStr.toLongOrNull() ?: RootShellHelper.readSystemFile("/sys/class/kgsl/kgsl-3d0/devfreq/cur_freq").trim().toLongOrNull() ?: 0L
        
        val maxFreqStr = RootShellHelper.readSystemFile("/sys/class/kgsl/kgsl-3d0/max_gpuclk").trim()
        val maxFreqVal = maxFreqStr.toLongOrNull() ?: RootShellHelper.readSystemFile("/sys/class/kgsl/kgsl-3d0/devfreq/max_freq").trim().toLongOrNull() ?: 710000000L
        
        var gpuLoad = 0f
        if (maxFreqVal > 0) {
            val normalizedLoad = (rawLoadPct / 100f) * (gFreqVal.toFloat() / maxFreqVal.toFloat())
            gpuLoad = normalizedLoad.coerceIn(0f, 1f)
        }
        val gpuFreq = if (gFreqVal > 0) "${if (gFreqVal > 1000000) gFreqVal / 1000000 else if (gFreqVal > 1000) gFreqVal / 1000 else gFreqVal} MHz" else "-- MHz"
        
        val meminfo = RootShellHelper.readSystemFile("/proc/meminfo")
        var mTotal = 0L
        var mAvail = 0L
        var sTotal = 0L
        var sFree = 0L
        meminfo.lines().forEach { line ->
            val p = line.split(Regex("\\s+"))
            if (p.size >= 2) {
                val v = p[1].toLongOrNull() ?: 0L
                when (p[0]) {
                    "MemTotal:" -> mTotal = v
                    "MemAvailable:" -> mAvail = v
                    "SwapTotal:" -> sTotal = v
                    "SwapFree:" -> sFree = v
                }
            }
        }
        val mUsed = mTotal - mAvail
        val ramUsed = String.format("%.1f GB", mUsed / 1048576f)
        val ramTotal = String.format("%.1f GB", mTotal / 1048576f)
        val ramProgress = if (mTotal > 0) mUsed.toFloat() / mTotal.toFloat() else 0f
        
        val sUsed = sTotal - sFree
        val swapUsed = String.format("%.1f GB", sUsed / 1048576f)
        val swapTotal = String.format("%.1f GB", sTotal / 1048576f)
        val swapProgress = if (sTotal > 0) sUsed.toFloat() / sTotal.toFloat() else 0f

        val batteryStatusOutput = RootShellHelper.executeCmdAndGetOutput("dumpsys battery")
        var tLevel = 0
        var tTemp = 0.0f
        batteryStatusOutput.lines().forEach { line ->
            val info = line.trim()
            if (info.startsWith("level: ")) {
                tLevel = info.substringAfter("level: ").trim().toIntOrNull() ?: 0
            } else if (info.startsWith("temperature: ")) {
                tTemp = (info.substringAfter("temperature: ").trim().toFloatOrNull() ?: 0f) / 10f
            }
        }
        val batteryLevel = tLevel
        val batteryTemp = String.format("%.1f°C", tTemp)
        
        val currentRaw = RootShellHelper.readSystemFile("/sys/class/power_supply/battery/current_now")
        var batteryCurrent = "-272mA"
        if (currentRaw.isNotBlank()) {
            val ma = currentRaw.trim().toLongOrNull()?.let { if (Math.abs(it) > 10000) it / 1000 else it } ?: 0
            batteryCurrent = "${ma}mA"
        }

        RealTimeMetrics(
            cpuLoad = cpuLoad,
            coreFreqs = freqs,
            coreProgress = progresses,
            gpuLoad = gpuLoad,
            gpuFreq = gpuFreq,
            ramUsed = ramUsed,
            ramTotal = ramTotal,
            ramProgress = ramProgress,
            swapUsed = swapUsed,
            swapTotal = swapTotal,
            swapProgress = swapProgress,
            batteryLevel = batteryLevel,
            batteryTemp = batteryTemp,
            batteryCurrent = batteryCurrent
        )
    }
}
