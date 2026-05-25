package com.xaozora.manager.core.utils

import com.xaozora.manager.core.shell.RootShellHelper

data class CpuClusterConfig(
    val id: Int,
    val name: String,
    val availableFreqs: List<String>,
    val availableGovernors: List<String>,
    var minFreq: String,
    var maxFreq: String,
    var governor: String
)

object CpuControlUtils {
    
    fun getClusterConfig(clusterCpuId: Int, name: String): CpuClusterConfig {
        val basePath = "/sys/devices/system/cpu/cpu$clusterCpuId/cpufreq"
        
        val availableFreqsStr = RootShellHelper.readSystemFile("$basePath/scaling_available_frequencies").trim()
        val availableFreqs = if (availableFreqsStr.isNotBlank()) availableFreqsStr.split("\\s+".toRegex()).filter { it.isNotBlank() }.toMutableList() else mutableListOf()
        
        val availableGovsStr = RootShellHelper.readSystemFile("$basePath/scaling_available_governors").trim()
        val availableGovernors = if (availableGovsStr.isNotBlank()) availableGovsStr.split("\\s+".toRegex()).filter { it.isNotBlank() } else emptyList()
        
        val minFreq = RootShellHelper.readSystemFile("$basePath/scaling_min_freq").trim()
        val maxFreq = RootShellHelper.readSystemFile("$basePath/scaling_max_freq").trim()
        val governor = RootShellHelper.readSystemFile("$basePath/scaling_governor").trim()

        if (minFreq.isNotBlank() && !availableFreqs.contains(minFreq)) availableFreqs.add(minFreq)
        if (maxFreq.isNotBlank() && !availableFreqs.contains(maxFreq)) availableFreqs.add(maxFreq)
        availableFreqs.sortBy { it.toLongOrNull() ?: 0L }

        return CpuClusterConfig(
            id = clusterCpuId,
            name = name,
            availableFreqs = availableFreqs,
            availableGovernors = availableGovernors,
            minFreq = minFreq,
            maxFreq = maxFreq,
            governor = governor
        )
    }

    fun applyClusterConfig(clusterCpuId: Int, minFreq: String, maxFreq: String, governor: String) {
        val basePath = "/sys/devices/system/cpu/cpu$clusterCpuId/cpufreq"

        RootShellHelper.executeCmd("chmod 644 $basePath/scaling_governor")
        RootShellHelper.executeCmd("echo $governor > $basePath/scaling_governor")
        RootShellHelper.executeCmd("chmod 444 $basePath/scaling_governor")

        RootShellHelper.executeCmd("chmod 644 $basePath/scaling_max_freq")
        RootShellHelper.executeCmd("echo $maxFreq > $basePath/scaling_max_freq")
        RootShellHelper.executeCmd("chmod 444 $basePath/scaling_max_freq")

        RootShellHelper.executeCmd("chmod 644 $basePath/scaling_min_freq")
        RootShellHelper.executeCmd("echo $minFreq > $basePath/scaling_min_freq")
        RootShellHelper.executeCmd("chmod 444 $basePath/scaling_min_freq")
    }
}
