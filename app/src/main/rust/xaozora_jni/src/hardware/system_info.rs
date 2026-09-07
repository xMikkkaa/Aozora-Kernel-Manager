use jni::objects::JClass;
use jni::sys::jstring;
use jni::{errors::ThrowRuntimeExAndDefault, EnvUnowned};
use serde::{Deserialize, Serialize};
use std::sync::atomic::{AtomicU64, Ordering};

use crate::utils::shell::{execute_cmd_and_get_output, read_system_file};

#[derive(Serialize, Deserialize)]
pub struct SystemInfo {
    pub model: String,
    pub device: String,
    pub android: String,
    pub selinux: String,
    pub soc: String,
    pub ram: String,
    pub kernel: String,
    pub uptime: String,
    pub battery: String,
    pub resolution: String,
    pub root_manager: String,
    pub root_version: String,
    pub load_avg: String,
    pub entropy: String,
    pub capacity: String,
    pub governor: String,
    pub battery_health: String,
    pub deep_sleep: String,
    pub wireguard: String,
    pub open_gl: String,
}

#[derive(Serialize, Deserialize)]
pub struct RealTimeMetrics {
    pub cpu_load: f32,
    pub core_freqs: Vec<String>,
    pub core_progress: Vec<f32>,
    pub gpu_load: f32,
    pub gpu_freq: String,
    pub ram_used: String,
    pub ram_total: String,
    pub ram_progress: f32,
    pub swap_used: String,
    pub swap_total: String,
    pub swap_progress: f32,
    pub battery_level: i32,
    pub battery_temp: String,
    pub battery_current: String,
}

fn get_prop(key: &str) -> String {
    let out = execute_cmd_and_get_output(&format!("getprop {}", key));
    if out.is_empty() {
        "-".to_string()
    } else {
        out
    }
}

fn get_soc_info() -> String {
    let board = get_prop("ro.board.platform");
    if board != "-" {
        board
    } else {
        get_prop("ro.hardware")
    }
}

fn get_ram_info() -> String {
    let meminfo = read_system_file("/proc/meminfo");
    for line in meminfo.lines() {
        if line.starts_with("MemTotal:") {
            let kb_str: String = line.chars().filter(|c| c.is_digit(10)).collect();
            if let Ok(kb) = kb_str.parse::<f64>() {
                let gb = kb / 1024.0 / 1024.0;
                return format!("{:.1} GB", gb);
            }
        }
    }
    "-".to_string()
}

fn get_deep_sleep() -> String {
    let mut ts_boot = libc::timespec {
        tv_sec: 0,
        tv_nsec: 0,
    };
    let mut ts_mono = libc::timespec {
        tv_sec: 0,
        tv_nsec: 0,
    };
    unsafe {
        libc::clock_gettime(libc::CLOCK_BOOTTIME, &mut ts_boot);
        libc::clock_gettime(libc::CLOCK_MONOTONIC, &mut ts_mono);
    }

    let elapsed = ts_boot.tv_sec as i64 * 1000 + (ts_boot.tv_nsec / 1_000_000) as i64;
    let uptime_millis = ts_mono.tv_sec as i64 * 1000 + (ts_mono.tv_nsec / 1_000_000) as i64;
    let deep_sleep_millis = elapsed.saturating_sub(uptime_millis);

    let total_seconds = deep_sleep_millis / 1000;
    let days = total_seconds / 86400;
    let hours = (total_seconds % 86400) / 3600;
    let minutes = (total_seconds % 3600) / 60;
    let seconds = total_seconds % 60;

    let mut time_str = String::new();
    if days > 0 {
        time_str.push_str(&format!("{}d ", days));
    }
    if hours > 0 || days > 0 {
        time_str.push_str(&format!("{}h ", hours));
    }
    if minutes > 0 || hours > 0 || days > 0 {
        time_str.push_str(&format!("{}m ", minutes));
    }
    time_str.push_str(&format!("{}s", seconds));

    let pct = if elapsed > 0 {
        (deep_sleep_millis as f64 / elapsed as f64 * 100.0) as i32
    } else {
        0
    };

    format!("{} ({}%)", time_str, pct)
}

fn get_uptime(uptime_s: f64) -> String {
    let total_seconds = uptime_s as u64;
    let days = total_seconds / 86400;
    let hours = (total_seconds % 86400) / 3600;
    let minutes = (total_seconds % 3600) / 60;
    let seconds = total_seconds % 60;

    let mut time_str = String::new();
    if days > 0 {
        time_str.push_str(&format!("{}d ", days));
    }
    if hours > 0 || days > 0 {
        time_str.push_str(&format!("{}h ", hours));
    }
    if minutes > 0 || hours > 0 || days > 0 {
        time_str.push_str(&format!("{}m ", minutes));
    }
    time_str.push_str(&format!("{}s", seconds));

    time_str
}

pub fn fetch_system_info() -> SystemInfo {
    let load_avg_raw = read_system_file("/proc/loadavg");
    let load_avg = if !load_avg_raw.is_empty() {
        load_avg_raw
            .split_whitespace()
            .take(3)
            .collect::<Vec<&str>>()
            .join(" ")
    } else {
        "-".to_string()
    };

    let entropy = read_system_file("/proc/sys/kernel/random/entropy_avail")
        .trim()
        .to_string();

    let uptime_s = read_system_file("/proc/uptime")
        .split_whitespace()
        .next()
        .unwrap_or("0")
        .parse::<f64>()
        .unwrap_or(0.0);

    let wg_version = read_system_file("/sys/module/wireguard/version")
        .trim()
        .to_string();
    let wireguard = if !wg_version.is_empty() {
        wg_version
    } else {
        "Unsupported".to_string()
    };

    let open_gl_output = execute_cmd_and_get_output("dumpsys SurfaceFlinger | grep -i GLES")
        .trim()
        .to_string();
    let open_gl = if let Some(idx) = open_gl_output.find("GLES:") {
        open_gl_output[idx + 5..].trim().to_string()
    } else if !open_gl_output.is_empty() {
        open_gl_output
    } else {
        "-".to_string()
    };

    let gov_str = read_system_file("/sys/devices/system/cpu/cpu0/cpufreq/scaling_governor")
        .trim()
        .to_string();
    let governor = if !gov_str.is_empty() {
        gov_str
    } else {
        "Unknown".to_string()
    };

    let mut charge_full = 0f32;
    let mut charge_full_design = 0f32;
    let bat_paths = ["bms", "battery", "BAT0"];
    for p in &bat_paths {
        let full_str = read_system_file(&format!("/sys/class/power_supply/{}/charge_full", p))
            .trim()
            .to_string();
        let design_str =
            read_system_file(&format!("/sys/class/power_supply/{}/charge_full_design", p))
                .trim()
                .to_string();
        if !full_str.is_empty() && !design_str.is_empty() {
            charge_full = full_str.parse().unwrap_or(0f32);
            charge_full_design = design_str.parse().unwrap_or(0f32);
            break;
        }
    }

    let mut capacity = "Unknown".to_string();
    let mut battery_health = "Unknown".to_string();

    let mut learned_capacity = 0f32;
    if let Ok(content) =
        std::fs::read_to_string("/data/data/com.xaozora.manager/files/battmon/battery_stats.json")
    {
        if let Ok(json) = serde_json::from_str::<serde_json::Value>(&content) {
            if let Some(val) = json["last_learned_capacity_mah"].as_f64() {
                learned_capacity = val as f32;
            }
        }
    }

    if charge_full_design > 0f32 {
        let design_capacity_mah = if charge_full_design > 10000.0 {
            (charge_full_design / 1000.0) as i32
        } else {
            charge_full_design as i32
        };
        let current_capacity = if learned_capacity > 0f32 {
            if learned_capacity > 10000f32 {
                learned_capacity / 1000f32
            } else {
                learned_capacity
            }
        } else {
            if charge_full > 10000f32 {
                charge_full / 1000f32
            } else {
                charge_full
            }
        };

        let mut health_raw = current_capacity / design_capacity_mah as f32 * 100.0;
        if health_raw > 100.0 {
            health_raw = 100.0;
        }
        if health_raw < 0.0 {
            health_raw = 0.0;
        }

        let health_category = if health_raw >= 80.0 {
            "Good"
        } else if health_raw >= 60.0 {
            "Fair"
        } else {
            "Poor"
        };
        capacity = format!("{} mAh", design_capacity_mah);
        battery_health = format!("{:.1}% ({})", health_raw, health_category);
    }

    let kernel = execute_cmd_and_get_output("cat /proc/version");
    let selinux = execute_cmd_and_get_output("getenforce");
    let selinux = if selinux.is_empty() {
        "-".to_string()
    } else {
        selinux
    };

    SystemInfo {
        model: get_prop("ro.product.model"),
        device: get_prop("ro.product.device"),
        android: get_prop("ro.build.version.release"),
        selinux,
        soc: get_soc_info(),
        ram: get_ram_info(),
        kernel: if kernel.is_empty() {
            "-".to_string()
        } else {
            kernel
        },
        uptime: get_uptime(uptime_s),
        battery: format!(
            "{}%",
            read_system_file("/sys/class/power_supply/battery/capacity").trim()
        ),
        resolution: execute_cmd_and_get_output("wm size")
            .replace("Physical size: ", "")
            .trim()
            .to_string(),
        root_manager: "Unknown".to_string(),
        root_version: "Unknown".to_string(),
        load_avg,
        entropy,
        capacity,
        governor,
        battery_health,
        deep_sleep: get_deep_sleep(),
        wireguard,
        open_gl,
    }
}

static LAST_CPU_TOTAL: AtomicU64 = AtomicU64::new(0);
static LAST_CPU_IDLE: AtomicU64 = AtomicU64::new(0);

pub fn poll_hardware() -> RealTimeMetrics {
    // CPU Load
    let mut cpu_load = 0.0;
    let stat = read_system_file("/proc/stat");
    if let Some(cpu_line) = stat.lines().find(|l| l.starts_with("cpu ")) {
        let parts: Vec<&str> = cpu_line.split_whitespace().collect();
        let mut total = 0u64;
        for i in 1..parts.len() {
            total += parts[i].parse::<u64>().unwrap_or(0);
        }
        let idle = if parts.len() > 4 {
            parts[4].parse::<u64>().unwrap_or(0)
        } else {
            0
        };

        let last_total = LAST_CPU_TOTAL.load(Ordering::SeqCst);
        let last_idle = LAST_CPU_IDLE.load(Ordering::SeqCst);

        let diff_idle = idle.saturating_sub(last_idle);
        let diff_total = total.saturating_sub(last_total);

        if diff_total > 0 && last_total > 0 {
            cpu_load = (1.0 - (diff_idle as f32 / diff_total as f32)).clamp(0.0, 1.0);
        }

        LAST_CPU_TOTAL.store(total, Ordering::SeqCst);
        LAST_CPU_IDLE.store(idle, Ordering::SeqCst);
    }

    // CPU Frequencies
    let mut core_freqs = Vec::new();
    let mut core_progress = Vec::new();

    for i in 0..8 {
        let f_str = read_system_file(&format!(
            "/sys/devices/system/cpu/cpu{}/cpufreq/scaling_cur_freq",
            i
        ))
        .trim()
        .to_string();
        let max_str = read_system_file(&format!(
            "/sys/devices/system/cpu/cpu{}/cpufreq/scaling_max_freq",
            i
        ))
        .trim()
        .to_string();

        let freq_val = f_str.parse::<u64>().unwrap_or(0);
        let max_val = max_str.parse::<u64>().unwrap_or(1);

        if f_str.is_empty() {
            core_freqs.push("Offline".to_string());
            core_progress.push(0.0);
        } else {
            let formatted_freq = if f_str.len() > 3 {
                format!("{} MHz", &f_str[..f_str.len() - 3])
            } else {
                format!("{} MHz", f_str)
            };
            core_freqs.push(formatted_freq);

            let prog = if max_val > 0 {
                (freq_val as f32 / max_val as f32).clamp(0.0, 1.0)
            } else {
                0.0
            };
            core_progress.push(prog);
        }
    }

    // GPU Load & Freq
    let g_load_str = read_system_file("/sys/class/kgsl/kgsl-3d0/gpu_busy_percentage")
        .trim()
        .to_string();
    let raw_load_pct = g_load_str
        .replace("%", "")
        .trim()
        .parse::<f32>()
        .unwrap_or(0.0);

    let mut g_freq_val = read_system_file("/sys/class/kgsl/kgsl-3d0/gpuclk")
        .trim()
        .parse::<u64>()
        .unwrap_or(0);
    if g_freq_val == 0 {
        g_freq_val = read_system_file("/sys/class/kgsl/kgsl-3d0/devfreq/cur_freq")
            .trim()
            .parse::<u64>()
            .unwrap_or(0);
    }

    let mut max_freq_val = read_system_file("/sys/class/kgsl/kgsl-3d0/max_gpuclk")
        .trim()
        .parse::<u64>()
        .unwrap_or(0);
    if max_freq_val == 0 {
        max_freq_val = read_system_file("/sys/class/kgsl/kgsl-3d0/devfreq/max_freq")
            .trim()
            .parse::<u64>()
            .unwrap_or(710000000);
    }

    let mut gpu_load = 0.0;
    if max_freq_val > 0 {
        gpu_load =
            ((raw_load_pct / 100.0) * (g_freq_val as f32 / max_freq_val as f32)).clamp(0.0, 1.0);
    }
    let gpu_freq = if g_freq_val > 0 {
        if g_freq_val > 1000000 {
            format!("{} MHz", g_freq_val / 1000000)
        } else if g_freq_val > 1000 {
            format!("{} MHz", g_freq_val / 1000)
        } else {
            format!("{} MHz", g_freq_val)
        }
    } else {
        "-- MHz".to_string()
    };

    // RAM
    let meminfo = read_system_file("/proc/meminfo");
    let mut m_total = 0u64;
    let mut m_avail = 0u64;
    let mut s_total = 0u64;
    let mut s_free = 0u64;

    for line in meminfo.lines() {
        let p: Vec<&str> = line.split_whitespace().collect();
        if p.len() >= 2 {
            let v = p[1].parse::<u64>().unwrap_or(0);
            match p[0] {
                "MemTotal:" => m_total = v,
                "MemAvailable:" => m_avail = v,
                "SwapTotal:" => s_total = v,
                "SwapFree:" => s_free = v,
                _ => {}
            }
        }
    }
    let m_used = m_total.saturating_sub(m_avail);
    let ram_used = format!("{:.1} GB", m_used as f32 / 1048576.0);
    let ram_total = format!("{:.1} GB", m_total as f32 / 1048576.0);
    let ram_progress = if m_total > 0 {
        m_used as f32 / m_total as f32
    } else {
        0.0
    };

    let s_used = s_total.saturating_sub(s_free);
    let swap_used = format!("{:.1} GB", s_used as f32 / 1048576.0);
    let swap_total = format!("{:.1} GB", s_total as f32 / 1048576.0);
    let swap_progress = if s_total > 0 {
        s_used as f32 / s_total as f32
    } else {
        0.0
    };

    // Battery
    let mut t_level = 0;
    let mut t_temp = 0.0f32;
    let bat_dump = crate::utils::shell::execute_cmd_and_get_output("dumpsys battery");
    for line in bat_dump.lines() {
        let info = line.trim();
        if info.starts_with("level: ") {
            t_level = info.replace("level: ", "").parse::<i32>().unwrap_or(0);
        } else if info.starts_with("temperature: ") {
            t_temp = info
                .replace("temperature: ", "")
                .parse::<f32>()
                .unwrap_or(0.0)
                / 10.0;
        }
    }
    let battery_level = t_level;
    let battery_temp = format!("{:.1}°C", t_temp);

    let current_raw = read_system_file("/sys/class/power_supply/battery/current_now")
        .trim()
        .to_string();
    let battery_current = if !current_raw.is_empty() {
        if let Ok(ma) = current_raw.parse::<i64>() {
            if ma.abs() > 10000 {
                format!("{}mA", ma / 1000)
            } else {
                format!("{}mA", ma)
            }
        } else {
            "0mA".to_string()
        }
    } else {
        "-272mA".to_string()
    };

    RealTimeMetrics {
        cpu_load,
        core_freqs,
        core_progress,
        gpu_load,
        gpu_freq,
        ram_used,
        ram_total,
        ram_progress,
        swap_used,
        swap_total,
        swap_progress,
        battery_level,
        battery_temp,
        battery_current,
    }
}

#[no_mangle]
pub extern "system" fn Java_com_xaozora_manager_core_utils_SystemInfoUtils_fetchSystemInfoJson<
    'local,
>(
    mut env: EnvUnowned<'local>,
    _class: JClass,
) -> jstring {
    env.with_env(|env| -> jni::errors::Result<jstring> {
        let info = fetch_system_info();
        let json_str = serde_json::to_string(&info).unwrap_or_else(|_| "{}".to_string());
        let output = env.new_string(json_str).unwrap();
        Ok(output.into_raw())
    })
    .resolve::<ThrowRuntimeExAndDefault>()
}

#[no_mangle]
pub extern "system" fn Java_com_xaozora_manager_core_utils_SystemInfoUtils_pollHardwareJson<
    'local,
>(
    mut env: EnvUnowned<'local>,
    _class: JClass,
) -> jstring {
    env.with_env(|env| -> jni::errors::Result<jstring> {
        let metrics = poll_hardware();
        let json_str = serde_json::to_string(&metrics).unwrap_or_else(|_| "{}".to_string());
        let output = env.new_string(json_str).unwrap();
        Ok(output.into_raw())
    })
    .resolve::<ThrowRuntimeExAndDefault>()
}

#[no_mangle]
pub extern "system" fn Java_com_xaozora_manager_core_utils_SystemInfoUtils_updateSystemState<
    'local,
>(
    _env: EnvUnowned<'local>,
    _class: JClass,
    bat_level: jni::sys::jint,
    is_screen_on: jni::sys::jboolean,
) {
    let scr = if is_screen_on { "1" } else { "0" };
    let msg = format!("BAT:{}|SCR:{}\n", bat_level, scr);

    use std::fs::OpenOptions;
    use std::io::Write;

    if let Ok(mut file) = OpenOptions::new()
        .write(true)
        .open("/data/data/com.xaozora.manager/files/autd/events.pipe")
    {
        let _ = file.write_all(msg.as_bytes());
    }
}
