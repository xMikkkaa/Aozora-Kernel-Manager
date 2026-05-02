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
    val rootVersion: String
)

object SystemInfoUtils {

    suspend fun fetchSystemInfo(): SystemInfo = withContext(Dispatchers.IO) {
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
            rootVersion = getRootVersion()
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
        val uptimeStr = RootShellHelper.readSystemFile("/proc/uptime").substringBefore(" ")
        val totalSeconds = uptimeStr.toDoubleOrNull()?.toLong() ?: return "-"
        val days = totalSeconds / 86400
        val hours = (totalSeconds % 86400) / 3600
        val minutes = (totalSeconds % 3600) / 60
        
        return buildString {
            if (days > 0) append("${days}d ")
            if (hours > 0 || days > 0) append("${hours}h ")
            append("${minutes}m")
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
