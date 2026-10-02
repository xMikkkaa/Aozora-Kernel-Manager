package com.xaozora.manager.core.utils

import com.google.gson.Gson

import com.google.gson.annotations.SerializedName

data class CpuClusterConfig(
    val id: Int,
    val name: String,
    @SerializedName("available_freqs") val availableFreqs: List<String>,
    @SerializedName("available_governors") val availableGovernors: List<String>,
    @SerializedName("min_freq") var minFreq: String,
    @SerializedName("max_freq") var maxFreq: String,
    var governor: String
)

data class CpuPolicySelection(
    val policyId: Int,
    val minFreq: String,
    val maxFreq: String,
    val governor: String
)

object CpuControlUtils {
    init {
        System.loadLibrary("native")
    }

    private val gson = Gson()

    private external fun getClusterConfigJson(clusterCpuId: Int, name: String): String
    external fun applyClusterConfig(clusterCpuId: Int, minFreq: String, maxFreq: String, governor: String)
    private external fun getCpuPoliciesJson(): String

    fun getClusterConfig(clusterCpuId: Int, name: String): CpuClusterConfig {
        val jsonStr = getClusterConfigJson(clusterCpuId, name)
        return gson.fromJson(jsonStr, CpuClusterConfig::class.java)
    }

    fun getCpuPolicies(): List<Int> {
        val jsonStr = getCpuPoliciesJson()
        return gson.fromJson(jsonStr, Array<Int>::class.java)?.toList() ?: listOf(0)
    }

    fun getClusterConfigs(): List<CpuClusterConfig> {
        val policies = getCpuPolicies()
        return policies.mapIndexed { index, policy ->
            val label = when {
                policies.size == 1 -> "CPU"
                index == 0 -> "LITTLE"
                index == policies.size - 1 -> "BIG"
                else -> "MID"
            }
            getClusterConfig(policy, label)
        }
    }
}
