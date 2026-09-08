package com.xaozora.manager.core.utils

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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
    @SerializedName("root_manager") val rootManager: String,
    @SerializedName("root_version") val rootVersion: String,
    @SerializedName("load_avg") val loadAvg: String,
    val entropy: String,
    val capacity: String,
    val governor: String,
    @SerializedName("battery_health") val batteryHealth: String,
    @SerializedName("deep_sleep") val deepSleep: String,
    val wireguard: String,
    @SerializedName("open_gl") val openGl: String
)

data class RealTimeMetrics(
    @SerializedName("cpu_load") val cpuLoad: Float,
    @SerializedName("core_freqs") val coreFreqs: List<String>,
    @SerializedName("core_progress") val coreProgress: List<Float>,
    @SerializedName("gpu_load") val gpuLoad: Float,
    @SerializedName("gpu_freq") val gpuFreq: String,
    @SerializedName("ram_used") val ramUsed: String,
    @SerializedName("ram_total") val ramTotal: String,
    @SerializedName("ram_progress") val ramProgress: Float,
    @SerializedName("swap_used") val swapUsed: String,
    @SerializedName("swap_total") val swapTotal: String,
    @SerializedName("swap_progress") val swapProgress: Float,
    @SerializedName("battery_level") val batteryLevel: Int,
    @SerializedName("battery_temp") val batteryTemp: String,
    @SerializedName("battery_current") val batteryCurrent: String
)

object SystemInfoUtils {
    init {
        System.loadLibrary("native")
    }

    private val gson = Gson()

    private external fun fetchSystemInfoJson(): String
    private external fun pollHardwareJson(): String
    
    @JvmStatic
    external fun updateSystemState(batLevel: Int, isScreenOn: Boolean)

    suspend fun fetchSystemInfo(): SystemInfo = withContext(Dispatchers.IO) {
        val jsonStr = fetchSystemInfoJson()
        gson.fromJson(jsonStr, SystemInfo::class.java)
    }

    suspend fun pollHardware(): RealTimeMetrics = withContext(Dispatchers.IO) {
        val jsonStr = pollHardwareJson()
        gson.fromJson(jsonStr, RealTimeMetrics::class.java)
    }
}
