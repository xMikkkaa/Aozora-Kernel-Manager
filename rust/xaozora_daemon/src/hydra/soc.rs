/*
 * Copyright 2026 xMikkkaa
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

use std::fs;

use super::primitives::{shell_output, setvalue, setvalue_unlocked};

pub fn detect_soc() -> u8 {
    let mut info = String::new();
    if let Ok(cpuinfo) = fs::read_to_string("/proc/cpuinfo") {
        info.push_str(&cpuinfo);
    }
    let platform = shell_output("getprop ro.board.platform");
    info.push_str(&platform);
    let hardware = shell_output("getprop ro.hardware");
    info.push_str(&hardware);

    let lower = info.to_lowercase();

    if lower.contains("mediatek") || lower.contains("dimensity") || lower.contains("mt6") || lower.contains("mt8") {
        1
    } else if lower.contains("qcom")
        || lower.contains("snapdragon")
        || lower.contains("msm")
        || lower.contains("sdm")
        || lower.contains("sm8")
        || lower.contains("sm7")
        || lower.contains("sm6")
        || lower.contains("sm4")
    {
        2
    } else if lower.contains("exynos") {
        3
    } else if lower.contains("unisoc") || lower.contains("sprd") || lower.contains("ums9") || lower.contains("ums5") {
        4
    } else if lower.contains("gs") || lower.contains("tensor") || lower.contains("zuma") || lower.contains("zephyr") {
        5
    } else if lower.contains("tegra") || lower.contains("te18") || lower.contains("te19") {
        6
    } else {
        2
    }
}

fn scan_platform_dirs(pattern: &str) -> Option<String> {
    if let Ok(entries) = fs::read_dir("/sys/devices/platform") {
        for entry in entries.flatten() {
            let name = entry.file_name().to_string_lossy().to_string();
            if name.contains(pattern) {
                return Some(entry.path().to_string_lossy().to_string());
            }
        }
    }
    None
}

fn mediatek_apply(mode: u8) {
    let ged = "/sys/module/ged/parameters";
    let ged_files = [
        "gx_boost_on",
        "gx_game_mode",
        "ged_smart_boost",
        "enable_gpu_boost",
        "ged_boost_enable",
        "gx_frc_mode",
        "cpu_boost_policy",
        "boost_extra",
    ];
    let ged_val = if mode == 4 { "1" } else { "0" };
    for f in ged_files {
        setvalue_unlocked(ged_val, &format!("{}/{}", ged, f));
    }

    let pnpmgr = "/sys/pnpmgr";
    if mode == 4 {
        setvalue_unlocked("1", &format!("{}/mwn", pnpmgr));
        setvalue_unlocked("1", &format!("{}/boost_enable", pnpmgr));
        setvalue("turbo", &format!("{}/boost_mode", pnpmgr));
    } else {
        setvalue_unlocked("0", &format!("{}/mwn", pnpmgr));
        setvalue_unlocked("0", &format!("{}/boost_enable", pnpmgr));
        setvalue("0", &format!("{}/boost_mode", pnpmgr));
    }

    let hal = "/sys/kernel/ged/hal";
    let hal_vals: (&str, &str, &str, &str) = if mode == 4 {
        ("2", "1", "16", "100")
    } else {
        ("4", "2", "8", "-1")
    };
    setvalue(hal_vals.0, &format!("{}/loading_base_dvfs_step", hal));
    setvalue(hal_vals.1, &format!("{}/loading_stride_size", hal));
    setvalue(hal_vals.2, &format!("{}/loading_window_size", hal));
    setvalue(hal_vals.3, &format!("{}/gpu_boost_level", hal));

    setvalue("1", "/proc/cpufreq/cpufreq_cci_mode");
    if mode == 4 {
        setvalue("3", "/proc/cpufreq/cpufreq_power_mode");
        setvalue("1", "/sys/kernel/fpsgo/fbt/ultra_rescue");
        setvalue("0", "/sys/kernel/fpsgo/fbt/thrm_enable");
        setvalue("1", "/sys/module/mtk_fpsgo/parameters/xgf_uboost");
        setvalue("1", "/proc/cpufreq/cpufreq_sched_disable");
        setvalue("1", "/sys/devices/platform/boot_dramboost/dramboost/dramboost");
    } else if mode == 2 {
        setvalue("1", "/proc/cpufreq/cpufreq_power_mode");
        setvalue("0", "/sys/kernel/fpsgo/fbt/ultra_rescue");
        setvalue("1", "/sys/kernel/fpsgo/fbt/thrm_enable");
        setvalue("0", "/sys/module/mtk_fpsgo/parameters/xgf_uboost");
        setvalue("0", "/proc/cpufreq/cpufreq_sched_disable");
        setvalue("0", "/sys/devices/platform/boot_dramboost/dramboost/dramboost");
    } else {
        setvalue("0", "/proc/cpufreq/cpufreq_power_mode");
        setvalue("0", "/sys/kernel/fpsgo/fbt/ultra_rescue");
        setvalue("1", "/sys/kernel/fpsgo/fbt/thrm_enable");
        setvalue("0", "/sys/module/mtk_fpsgo/parameters/xgf_uboost");
        setvalue("0", "/proc/cpufreq/cpufreq_sched_disable");
        setvalue("0", "/sys/devices/platform/boot_dramboost/dramboost/dramboost");
    }

    setvalue("2", "/sys/devices/system/cpu/eas/enable");
    setvalue("stop 0", "/proc/mtk_batoc_throttling/battery_oc_protect_stop");

    if mode == 4 {
        setvalue("0", "/sys/devices/platform/10012000.dvfsrc/helio-dvfsrc/dvfsrc_req_ddr_opp");
        setvalue("0", "/sys/kernel/helio-dvfsrc/dvfsrc_force_vcore_dvfs_opp");
    } else {
        setvalue("-1", "/sys/devices/platform/10012000.dvfsrc/helio-dvfsrc/dvfsrc_req_ddr_opp");
        setvalue("-1", "/sys/kernel/helio-dvfsrc/dvfsrc_force_vcore_dvfs_opp");
    }

    let pow_lim = "/proc/gpufreq/gpufreq_power_limited";
    let lim_val = if mode == 4 { "1" } else { "0" };
    for key in [
        "ignore_batt_oc",
        "ignore_batt_percent",
        "ignore_low_batt",
        "ignore_thermal_protect",
        "ignore_pbm_limited",
    ] {
        setvalue(lim_val, &format!("{} {}", pow_lim, key));
    }

    if mode == 4 {
        setvalue("0", "/proc/gpufreqv2/fix_target_opp_index");
    } else {
        setvalue("-1", "/proc/gpufreqv2/fix_target_opp_index");
    }
}

fn snapdragon_apply(mode: u8) {
    let kgsl = "/sys/class/kgsl/kgsl-3d0";

    if mode == 4 {
        setvalue_unlocked("1", &format!("{}/force_clk_on", kgsl));
        setvalue_unlocked("1", &format!("{}/default_pwrlevel", kgsl));
        setvalue_unlocked("0", &format!("{}/bus_split", kgsl));
        setvalue_unlocked("0", &format!("{}/throttling", kgsl));
        setvalue("3", &format!("{}/devfreq/adrenoboost", kgsl));
        setvalue_unlocked("1", "/sys/module/msm_perfmon/parameters/touch_boost_enable");
        setvalue_unlocked("1", "/sys/module/msm_perfmon/parameters/touch_boost_freq");
        setvalue("1", "/sys/module/msm_performance/parameters/touchboost");
        setvalue("N", "/sys/module/adreno_idler/parameters/adreno_idler_active");
    } else if mode == 3 {
        setvalue_unlocked("0", &format!("{}/force_clk_on", kgsl));
        setvalue_unlocked("0", &format!("{}/default_pwrlevel", kgsl));
        setvalue_unlocked("1", &format!("{}/bus_split", kgsl));
        setvalue_unlocked("1", &format!("{}/throttling", kgsl));
        setvalue_unlocked("0", &format!("{}/devfreq/adrenoboost", kgsl));
        setvalue_unlocked("0", "/sys/module/msm_perfmon/parameters/touch_boost_enable");
        setvalue_unlocked("0", "/sys/module/msm_perfmon/parameters/touch_boost_freq");
        setvalue_unlocked("0", "/sys/module/msm_performance/parameters/touchboost");
        setvalue_unlocked("N", "/sys/module/adreno_idler/parameters/adreno_idler_active");
    } else if mode == 2 {
        setvalue_unlocked("0", &format!("{}/force_clk_on", kgsl));
        setvalue_unlocked("0", &format!("{}/default_pwrlevel", kgsl));
        setvalue_unlocked("1", &format!("{}/bus_split", kgsl));
        setvalue_unlocked("1", &format!("{}/throttling", kgsl));
        setvalue_unlocked("0", &format!("{}/devfreq/adrenoboost", kgsl));
        setvalue_unlocked("0", "/sys/module/msm_perfmon/parameters/touch_boost_enable");
        setvalue_unlocked("0", "/sys/module/msm_perfmon/parameters/touch_boost_freq");
        setvalue_unlocked("0", "/sys/module/msm_performance/parameters/touchboost");
        setvalue("Y", "/sys/module/adreno_idler/parameters/adreno_idler_active");
    } else {
        setvalue_unlocked("0", &format!("{}/force_clk_on", kgsl));
        setvalue_unlocked("0", &format!("{}/default_pwrlevel", kgsl));
        setvalue_unlocked("1", &format!("{}/bus_split", kgsl));
        setvalue_unlocked("1", &format!("{}/throttling", kgsl));
        setvalue_unlocked("0", &format!("{}/devfreq/adrenoboost", kgsl));
        setvalue_unlocked("0", "/sys/module/msm_perfmon/parameters/touch_boost_enable");
        setvalue_unlocked("0", "/sys/module/msm_perfmon/parameters/touch_boost_freq");
        setvalue_unlocked("0", "/sys/module/msm_performance/parameters/touchboost");
        setvalue_unlocked("N", "/sys/module/adreno_idler/parameters/adreno_idler_active");
    }

    let (up, down, g_up, g_down) = if mode == 4 {
        ("75", "65", "85", "8")
    } else if mode == 3 {
        ("85", "75", "90", "8")
    } else {
        ("95", "85", "100", "10")
    };
    setvalue(up, "/proc/sys/kernel/sched_upmigrate");
    setvalue(down, "/proc/sys/kernel/sched_downmigrate");
    setvalue(g_up, "/proc/sys/kernel/sched_group_upmigrate");
    setvalue(g_down, "/proc/sys/kernel/sched_group_downmigrate");

    if let Ok(entries) = fs::read_dir("/sys/devices/system/cpu") {
        for entry in entries.flatten() {
            let name = entry.file_name().to_string_lossy().to_string();
            if name.starts_with("cpu") && name.len() > 3 && name[3..].chars().all(|c| c.is_ascii_digit()) {
                setvalue("0", entry.path().join("core_ctl/enable").to_str().unwrap_or(""));
            }
        }
    }

    if mode == 4 {
        setvalue("performance", &format!("{}/devfreq/governor", kgsl));
    } else if mode == 3 {
        setvalue("simple_ondemand", &format!("{}/devfreq/governor", kgsl));
    } else if mode == 2 {
        setvalue("powersave", &format!("{}/devfreq/governor", kgsl));
    } else {
        setvalue("msm-adreno-tz", &format!("{}/devfreq/governor", kgsl));
    }
}

fn exynos_apply(mode: u8) {
    if let Some(mali_dir) = scan_platform_dirs(".mali") {
        let policy = if mode == 4 { "always_on" } else { "coarse_demand" };
        setvalue(policy, &format!("{}/power_policy", mali_dir));
    }

    let gpu_avail = "/sys/kernel/gpu/gpu_available_frequencies";
    if let Ok(content) = fs::read_to_string(gpu_avail) {
        let freqs: Vec<u64> = content
            .split_whitespace()
            .filter_map(|s| s.parse().ok())
            .collect();
        if !freqs.is_empty() {
            let max_f = freqs.iter().max().unwrap();
            let min_f = freqs.iter().min().unwrap();
            let mid_f = freqs[freqs.len() / 2];
            let (max_val, min_val) = if mode == 4 {
                (*max_f, *max_f)
            } else if mode == 3 {
                (*max_f, mid_f)
            } else {
                (*max_f, *min_f)
            };
            setvalue(&max_val.to_string(), "/sys/kernel/gpu/gpu_max_clock");
            setvalue(&min_val.to_string(), "/sys/kernel/gpu/gpu_min_clock");
        }
    }

    if mode == 4 {
        setvalue("performance", "/sys/class/devfreq/devfreq_mif/governor");
    } else if mode == 2 {
        setvalue("powersave", "/sys/class/devfreq/devfreq_mif/governor");
    } else {
        setvalue("simple_ondemand", "/sys/class/devfreq/devfreq_mif/governor");
    }
}

fn unisoc_apply(mode: u8) {
    let (margin, down_rate, up_rate) = if mode == 4 {
        ("50", "10000", "0")
    } else if mode == 3 {
        ("30", "2000", "500")
    } else if mode == 2 {
        ("5", "500", "2000")
    } else {
        ("15", "1000", "1000")
    };

    if let Ok(entries) = fs::read_dir("/sys/devices/system/cpu/cpufreq") {
        for entry in entries.flatten() {
            let name = entry.file_name().to_string_lossy().to_string();
            if name.starts_with("policy") {
                let uscfreq = entry.path().join("uscfreq");
                setvalue(margin, uscfreq.join("freq_margin").to_str().unwrap_or(""));
                setvalue(down_rate, uscfreq.join("down_rate_limit_us").to_str().unwrap_or(""));
                setvalue(up_rate, uscfreq.join("up_rate_limit_us").to_str().unwrap_or(""));
            }
        }
    }

    let ddr_val = if mode == 4 {
        "1866"
    } else if mode == 3 {
        "1244"
    } else {
        "0"
    };
    setvalue(ddr_val, "/sys/class/devfreq/scene-frequency/sprd-governor/scaling_force_ddr_freq");

    let thermal_val = if mode == 4 { "0" } else { "1" };
    setvalue(thermal_val, "/sys/module/zte_misc/parameters/thermal_control_en");

    if mode == 4 {
        setvalue("performance", "/sys/class/devfreq/.gpu/governor");
    } else if mode == 2 {
        setvalue("powersave", "/sys/class/devfreq/.gpu/governor");
    } else {
        setvalue("simple_ondemand", "/sys/class/devfreq/.gpu/governor");
    }
}

fn tensor_apply(mode: u8) {
    if let Some(mali_dir) = scan_platform_dirs(".mali") {
        let avail_path = format!("{}/available_frequencies", mali_dir);
        if let Ok(content) = fs::read_to_string(&avail_path) {
            let freqs: Vec<u64> = content
                .split_whitespace()
                .filter_map(|s| s.parse().ok())
                .collect();
            if !freqs.is_empty() {
                let max_f = *freqs.iter().max().unwrap();
                let min_f = *freqs.iter().min().unwrap();
                let mid_f = freqs[freqs.len() / 2];
                let (max_val, min_val) = if mode == 4 {
                    (max_f, max_f)
                } else if mode == 3 {
                    (max_f, mid_f)
                } else {
                    (max_f, min_f)
                };
                setvalue(&max_val.to_string(), &format!("{}/scaling_max_freq", mali_dir));
                setvalue(&min_val.to_string(), &format!("{}/scaling_min_freq", mali_dir));
            }
        }
    }

    if mode == 4 {
        setvalue("performance", "/sys/class/devfreq/devfreq_mif/governor");
    } else if mode == 2 {
        setvalue("powersave", "/sys/class/devfreq/devfreq_mif/governor");
    } else {
        setvalue("simple_ondemand", "/sys/class/devfreq/devfreq_mif/governor");
    }
}

fn tegra_apply(mode: u8) {
    let base = "/sys/kernel/tegra_gpu";
    let avail = format!("{}/available_frequencies", base);
    if let Ok(content) = fs::read_to_string(&avail) {
        let freqs: Vec<u64> = content
            .split_whitespace()
            .filter_map(|s| s.parse().ok())
            .collect();
        if !freqs.is_empty() {
            let max_f = *freqs.iter().max().unwrap();
            let min_f = *freqs.iter().min().unwrap();
            let mid_f = freqs[freqs.len() / 2];
            let (cap, floor) = if mode == 4 {
                (max_f, max_f)
            } else if mode == 3 {
                (max_f, mid_f)
            } else {
                (max_f, min_f)
            };
            setvalue(&cap.to_string(), &format!("{}/gpu_cap_rate", base));
            setvalue(&floor.to_string(), &format!("{}/gpu_floor_rate", base));
        }
    }
}

pub fn soc_apply(mode: u8, soc: u8) {
    match soc {
        1 => mediatek_apply(mode),
        2 => snapdragon_apply(mode),
        3 => exynos_apply(mode),
        4 => unisoc_apply(mode),
        5 => tensor_apply(mode),
        6 => tegra_apply(mode),
        _ => {}
    }
}
