package com.xaozora.manager.core.utils

import com.xaozora.manager.core.shell.RootShellHelper

data class GpuConfig(
    val availableFreqs: List<String>,
    val availableGovernors: List<String>,
    var minFreq: String,
    var maxFreq: String,
    var governor: String,
    var adrenoBoostSupported: Boolean,
    var adrenoBoost: String // 0, 1, 2, 3
)

object GpuControlUtils {
    private const val BASE_PATH = "/sys/kernel/gpu"
    private const val DEVFREQ_PATH = "/sys/class/kgsl/kgsl-3d0/devfreq"

    fun getGpuConfig(): GpuConfig {
        val availableFreqsStr = RootShellHelper.readSystemFile("$BASE_PATH/gpu_freq_table").trim()
        val availableFreqs = if (availableFreqsStr.isNotBlank()) availableFreqsStr.split("\\s+".toRegex()).filter { it.isNotBlank() }.toMutableList() else mutableListOf()

        val availableGovsStr = RootShellHelper.readSystemFile("$BASE_PATH/gpu_available_governor").trim()
        val availableGovernors = if (availableGovsStr.isNotBlank()) availableGovsStr.split("\\s+".toRegex()).filter { it.isNotBlank() } else emptyList()

        val minFreq = RootShellHelper.readSystemFile("$BASE_PATH/gpu_min_clock").trim()
        val maxFreq = RootShellHelper.readSystemFile("$BASE_PATH/gpu_max_clock").trim()
        val governor = RootShellHelper.readSystemFile("$BASE_PATH/gpu_governor").trim()

        if (minFreq.isNotBlank() && !availableFreqs.contains(minFreq)) availableFreqs.add(minFreq)
        if (maxFreq.isNotBlank() && !availableFreqs.contains(maxFreq)) availableFreqs.add(maxFreq)
        availableFreqs.sortBy { it.toLongOrNull() ?: 0L }

        val adrenoBoostSupported = RootShellHelper.checkFileExists("$DEVFREQ_PATH/adrenoboost")
        val adrenoBoost = if (adrenoBoostSupported) RootShellHelper.readSystemFile("$DEVFREQ_PATH/adrenoboost").trim() else "0"

        return GpuConfig(
            availableFreqs = availableFreqs,
            availableGovernors = availableGovernors,
            minFreq = minFreq,
            maxFreq = maxFreq,
            governor = governor,
            adrenoBoostSupported = adrenoBoostSupported,
            adrenoBoost = adrenoBoost
        )
    }

    fun applyGpuConfig(minFreq: String, maxFreq: String, governor: String, adrenoBoost: String?) {
        // Governor
        RootShellHelper.executeCmd("chmod 644 $BASE_PATH/gpu_governor")
        RootShellHelper.executeCmd("echo $governor > $BASE_PATH/gpu_governor")
        RootShellHelper.executeCmd("chmod 444 $BASE_PATH/gpu_governor")

        // Max freq
        RootShellHelper.executeCmd("chmod 644 $BASE_PATH/gpu_max_clock")
        RootShellHelper.executeCmd("echo $maxFreq > $BASE_PATH/gpu_max_clock")
        RootShellHelper.executeCmd("chmod 444 $BASE_PATH/gpu_max_clock")

        // Min freq
        RootShellHelper.executeCmd("chmod 644 $BASE_PATH/gpu_min_clock")
        RootShellHelper.executeCmd("echo $minFreq > $BASE_PATH/gpu_min_clock")
        RootShellHelper.executeCmd("chmod 444 $BASE_PATH/gpu_min_clock")

        if (adrenoBoost != null && RootShellHelper.checkFileExists("$DEVFREQ_PATH/adrenoboost")) {
            RootShellHelper.executeCmd("chmod 644 $DEVFREQ_PATH/adrenoboost")
            RootShellHelper.executeCmd("echo $adrenoBoost > $DEVFREQ_PATH/adrenoboost")
            RootShellHelper.executeCmd("chmod 444 $DEVFREQ_PATH/adrenoboost")
        }
    }
}
