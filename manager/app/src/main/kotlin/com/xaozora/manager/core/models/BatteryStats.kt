package com.xaozora.manager.core.models

import com.google.gson.annotations.SerializedName

data class BatteryStats(
    @SerializedName("last_learned_capacity_mah") val lastLearnedCapacityMah: Double = 0.0,
    @SerializedName("time_on_battery_realtime_ms") val timeOnBatteryRealtimeMs: Long = 0,
    @SerializedName("time_on_battery_uptime_ms") val timeOnBatteryUptimeMs: Long = 0,
    @SerializedName("time_on_battery_screen_off_ms") val timeOnBatteryScreenOffMs: Long = 0,
    @SerializedName("time_on_battery_screen_doze_ms") val timeOnBatteryScreenDozeMs: Long = 0,
    @SerializedName("total_run_time_realtime_ms") val totalRunTimeRealtimeMs: Long = 0,
    @SerializedName("total_run_time_uptime_ms") val totalRunTimeUptimeMs: Long = 0,
    @SerializedName("discharge_mah") val dischargeMah: Double = 0.0,
    @SerializedName("screen_off_discharge_mah") val screenOffDischargeMah: Double = 0.0,
    @SerializedName("screen_doze_discharge_mah") val screenDozeDischargeMah: Double = 0.0,
    @SerializedName("screen_on_discharge_mah") val screenOnDischargeMah: Double = 0.0,
    @SerializedName("device_light_doze_discharge_mah") val deviceLightDozeDischargeMah: Double = 0.0,
    @SerializedName("device_deep_doze_discharge_mah") val deviceDeepDozeDischargeMah: Double = 0.0,
    @SerializedName("start_clock_time") val startClockTime: String = "",
    @SerializedName("connectivity_changes") val connectivityChanges: Int = 0,
    @SerializedName("total_full_wakelock_time_ms") val totalFullWakelockTimeMs: Long = 0,
    @SerializedName("screen_on_duration_ms") val screenOnDurationMs: Long = 0,
    @SerializedName("deep_sleep_ms") val deepSleepMs: Long = 0,
    @SerializedName("awake_screen_off_ms") val awakeScreenOffMs: Long = 0,
    @SerializedName("active_drain_rate_per_hr") val activeDrainRatePerHr: Double = 0.0,
    @SerializedName("idle_drain_rate_per_hr") val idleDrainRatePerHr: Double = 0.0
)
