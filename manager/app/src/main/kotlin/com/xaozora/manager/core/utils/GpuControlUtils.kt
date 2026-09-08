package com.xaozora.manager.core.utils

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName

data class GpuConfig(
    @SerializedName("available_freqs") val availableFreqs: List<String>,
    @SerializedName("available_governors") val availableGovernors: List<String>,
    @SerializedName("min_freq") var minFreq: String,
    @SerializedName("max_freq") var maxFreq: String,
    var governor: String,
    @SerializedName("adreno_boost_supported") var adrenoBoostSupported: Boolean,
    @SerializedName("adreno_boost") var adrenoBoost: String
)

object GpuControlUtils {
    init {
        System.loadLibrary("native")
    }

    private val gson = Gson()

    private external fun getGpuConfigJson(): String
    external fun applyGpuConfig(minFreq: String, maxFreq: String, governor: String, adrenoBoost: String?)

    fun getGpuConfig(): GpuConfig {
        val jsonStr = getGpuConfigJson()
        return gson.fromJson(jsonStr, GpuConfig::class.java)
    }
}
