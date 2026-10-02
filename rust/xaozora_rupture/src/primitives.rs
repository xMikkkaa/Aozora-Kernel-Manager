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
use std::os::unix::fs::PermissionsExt;
use std::process::{Command, Stdio};

pub fn shell(cmd: &str) {
    let _ = Command::new("sh")
        .arg("-c")
        .arg(cmd)
        .stdout(Stdio::null())
        .stderr(Stdio::null())
        .status();
}

pub fn shell_output(cmd: &str) -> String {
    Command::new("sh")
        .arg("-c")
        .arg(cmd)
        .stdout(Stdio::piped())
        .stderr(Stdio::null())
        .output()
        .map(|o| String::from_utf8_lossy(&o.stdout).to_string())
        .unwrap_or_default()
}

pub fn setvalue(value: &str, path: &str) {
    if path.is_empty() {
        return;
    }
    let _ = fs::set_permissions(path, fs::Permissions::from_mode(0o644));
    let _ = fs::write(path, value);
    let _ = fs::set_permissions(path, fs::Permissions::from_mode(0o444));
}

pub fn setvalue_unlocked(value: &str, path: &str) {
    if path.is_empty() {
        return;
    }
    let _ = fs::set_permissions(path, fs::Permissions::from_mode(0o644));
    let _ = fs::write(path, value);
}

pub fn list_policies() -> Vec<u32> {
    let mut policies = Vec::new();
    if let Ok(entries) = fs::read_dir("/sys/devices/system/cpu/cpufreq") {
        for entry in entries.flatten() {
            let name = entry.file_name().to_string_lossy().to_string();
            if let Some(num) = name.strip_prefix("policy")
                && let Ok(p) = num.parse::<u32>()
            {
                policies.push(p);
            }
        }
    }
    if policies.is_empty() {
        policies.push(0);
    }
    policies.sort_unstable();
    policies
}

pub fn policy_path(policy: u32, file: &str) -> String {
    format!("/sys/devices/system/cpu/cpufreq/policy{}/{}", policy, file)
}

pub fn read_u64(path: &str) -> u64 {
    fs::read_to_string(path)
        .ok()
        .and_then(|s| s.trim().parse().ok())
        .unwrap_or(0)
}

pub fn get_available_freqs(policy: u32) -> Vec<u64> {
    let path = policy_path(policy, "scaling_available_frequencies");
    fs::read_to_string(&path)
        .ok()
        .map(|c| {
            c.split_whitespace()
                .filter_map(|s| s.parse().ok())
                .collect()
        })
        .unwrap_or_default()
}

pub fn get_cpuinfo_max_freq(policy: u32) -> u64 {
    read_u64(&policy_path(policy, "cpuinfo_max_freq"))
}

pub fn get_cpuinfo_min_freq(policy: u32) -> u64 {
    read_u64(&policy_path(policy, "cpuinfo_min_freq"))
}

pub fn get_target_freq(policy: u32, mode: u8) -> u64 {
    let mut freqs = get_available_freqs(policy);
    if freqs.is_empty() {
        return match mode {
            0 => get_cpuinfo_max_freq(policy),
            1 => get_cpuinfo_min_freq(policy),
            _ => 0,
        };
    }
    freqs.sort_unstable_by(|a, b| b.cmp(a));
    match mode {
        0 => freqs[0],
        1 => *freqs.last().unwrap_or(&0),
        2 => freqs[freqs.len() / 2],
        3 => {
            if freqs.len() > 2 {
                freqs[2]
            } else {
                *freqs.last().unwrap_or(&0)
            }
        }
        _ => 0,
    }
}

pub fn set_cpu_freq(min: u64, max: u64) {
    for policy in list_policies() {
        if min > 0 {
            setvalue(&min.to_string(), &policy_path(policy, "scaling_min_freq"));
        }
        if max > 0 {
            setvalue(&max.to_string(), &policy_path(policy, "scaling_max_freq"));
        }
    }
}

pub fn set_cpu_gov(gov: &str) {
    for policy in list_policies() {
        setvalue(gov, &policy_path(policy, "scaling_governor"));
    }
}

pub struct GpuBackend {
    pub freq_table: String,
    pub set_min: String,
    pub set_max: String,
}

fn path_exists(path: &str) -> bool {
    std::path::Path::new(path).exists()
}

fn scan_devfreq_dirs(pattern: &str) -> Option<String> {
    if let Ok(entries) = fs::read_dir("/sys/class/devfreq") {
        for entry in entries.flatten() {
            let name = entry.file_name().to_string_lossy().to_string();
            if name.contains(pattern) {
                return Some(name);
            }
        }
    }
    None
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

pub fn detect_gpu_backend() -> Option<GpuBackend> {
    if path_exists("/sys/kernel/gpu/gpu_freq_table") {
        return Some(GpuBackend {
            freq_table: "/sys/kernel/gpu/gpu_freq_table".into(),
            set_min: "/sys/kernel/gpu/gpu_min_clock".into(),
            set_max: "/sys/kernel/gpu/gpu_max_clock".into(),
        });
    }
    if path_exists("/sys/kernel/gpu/gpu_available_frequencies") {
        return Some(GpuBackend {
            freq_table: "/sys/kernel/gpu/gpu_available_frequencies".into(),
            set_min: "/sys/kernel/gpu/gpu_min_clock".into(),
            set_max: "/sys/kernel/gpu/gpu_max_clock".into(),
        });
    }
    if path_exists("/sys/class/kgsl/kgsl-3d0/devfreq/available_frequencies") {
        return Some(GpuBackend {
            freq_table: "/sys/class/kgsl/kgsl-3d0/devfreq/available_frequencies".into(),
            set_min: "/sys/class/kgsl/kgsl-3d0/devfreq/min_freq".into(),
            set_max: "/sys/class/kgsl/kgsl-3d0/devfreq/max_freq".into(),
        });
    }
    if let Some(mali) = scan_platform_dirs(".mali") {
        let avail = format!("{}/available_frequencies", mali);
        if path_exists(&avail) {
            return Some(GpuBackend {
                freq_table: avail,
                set_min: format!("{}/scaling_min_freq", mali),
                set_max: format!("{}/scaling_max_freq", mali),
            });
        }
    }
    if let Some(node) = scan_devfreq_dirs("mali") {
        let base = format!("/sys/class/devfreq/{}", node);
        let avail = format!("{}/available_frequencies", base);
        if path_exists(&avail) {
            return Some(GpuBackend {
                freq_table: avail,
                set_min: format!("{}/min_freq", base),
                set_max: format!("{}/max_freq", base),
            });
        }
    }
    if let Some(node) = scan_devfreq_dirs(".gpu") {
        let base = format!("/sys/class/devfreq/{}", node);
        let avail = format!("{}/available_frequencies", base);
        if path_exists(&avail) {
            return Some(GpuBackend {
                freq_table: avail,
                set_min: format!("{}/min_freq", base),
                set_max: format!("{}/max_freq", base),
            });
        }
    }
    if path_exists("/sys/kernel/tegra_gpu/available_frequencies") {
        return Some(GpuBackend {
            freq_table: "/sys/kernel/tegra_gpu/available_frequencies".into(),
            set_min: "/sys/kernel/tegra_gpu/gpu_floor_rate".into(),
            set_max: "/sys/kernel/tegra_gpu/gpu_cap_rate".into(),
        });
    }
    None
}

pub fn apply_gpu_freq(mode: u8) {
    let backend = match detect_gpu_backend() {
        Some(b) => b,
        None => return,
    };
    let freqs: Vec<u64> = fs::read_to_string(&backend.freq_table)
        .ok()
        .map(|c| {
            c.split_whitespace()
                .filter_map(|s| s.parse().ok())
                .collect()
        })
        .unwrap_or_default();
    if freqs.is_empty() {
        return;
    }
    let mut sorted = freqs;
    sorted.sort_unstable();
    let len = sorted.len();
    let (min, max) = if len >= 5 && mode == 4 {
        (sorted[len - 4], sorted[len - 2])
    } else if mode == 2 || mode == 3 {
        (sorted[0], sorted[len - 4])
    } else {
        (sorted[len - 1], sorted[len - 1])
    };
    if min > 0 {
        setvalue(&min.to_string(), &backend.set_min);
    }
    if max > 0 {
        setvalue(&max.to_string(), &backend.set_max);
    }
}

pub fn apply_gpu_freq_max() {
    let backend = match detect_gpu_backend() {
        Some(b) => b,
        None => return,
    };
    let freqs: Vec<u64> = fs::read_to_string(&backend.freq_table)
        .ok()
        .map(|c| {
            c.split_whitespace()
                .filter_map(|s| s.parse().ok())
                .collect()
        })
        .unwrap_or_default();
    if freqs.is_empty() {
        return;
    }
    let mut sorted = freqs;
    sorted.sort_unstable();
    let max = sorted[sorted.len() - 1];
    if max > 0 {
        setvalue(&max.to_string(), &backend.set_max);
        setvalue(&max.to_string(), &backend.set_min);
    }
}

pub fn set_gpu_freq_gaming() {
    let backend = match detect_gpu_backend() {
        Some(b) => b,
        None => return,
    };
    let freqs: Vec<u64> = fs::read_to_string(&backend.freq_table)
        .ok()
        .map(|c| {
            c.split_whitespace()
                .filter_map(|s| s.parse().ok())
                .collect()
        })
        .unwrap_or_default();
    if freqs.len() < 2 {
        return;
    }
    let mut sorted = freqs;
    sorted.sort_unstable();
    let len = sorted.len();
    let min = sorted[len - 4.min(len - 1)];
    let max = sorted[len - 2];
    if min > 0 {
        setvalue(&min.to_string(), &backend.set_min);
    }
    setvalue(&max.to_string(), &backend.set_max);
}

pub fn tune_vm_io(vfs_cache_pressure: u64, page_cluster: u64) {
    if vfs_cache_pressure > 0 {
        setvalue(
            &vfs_cache_pressure.to_string(),
            "/proc/sys/vm/vfs_cache_pressure",
        );
    }
    setvalue(&page_cluster.to_string(), "/proc/sys/vm/page-cluster");
}

pub fn tune_block_io(iostats: u64, add_random: u64, read_ahead_kb: u64, nr_requests: u64) {
    if let Ok(entries) = fs::read_dir("/sys/block") {
        for entry in entries.flatten() {
            let name = entry.file_name().to_string_lossy().to_string();
            if name.starts_with("loop") {
                continue;
            }
            let queue = entry.path().join("queue");
            setvalue(
                &iostats.to_string(),
                queue.join("iostats").to_str().unwrap_or(""),
            );
            setvalue(
                &add_random.to_string(),
                queue.join("add_random").to_str().unwrap_or(""),
            );
            setvalue(
                &read_ahead_kb.to_string(),
                queue.join("read_ahead_kb").to_str().unwrap_or(""),
            );
            setvalue(
                &nr_requests.to_string(),
                queue.join("nr_requests").to_str().unwrap_or(""),
            );
        }
    }
}

pub fn tune_net(low_latency: u64, ecn: u64, fastopen: u64, timestamps: u64) {
    setvalue(
        &low_latency.to_string(),
        "/proc/sys/net/ipv4/tcp_low_latency",
    );
    setvalue(&ecn.to_string(), "/proc/sys/net/ipv4/tcp_ecn");
    setvalue(&fastopen.to_string(), "/proc/sys/net/ipv4/tcp_fastopen");
    setvalue(&timestamps.to_string(), "/proc/sys/net/ipv4/tcp_timestamps");
}

pub fn tune_block_sched(scheduler: &str, rq_affinity: u64) {
    if let Ok(entries) = fs::read_dir("/sys/block") {
        for entry in entries.flatten() {
            let name = entry.file_name().to_string_lossy().to_string();
            if name.starts_with("loop") {
                continue;
            }
            let queue = entry.path().join("queue");
            setvalue(scheduler, queue.join("scheduler").to_str().unwrap_or(""));
            setvalue(
                &rq_affinity.to_string(),
                queue.join("rq_affinity").to_str().unwrap_or(""),
            );
        }
    }
}

pub fn set_hwui_target(percent: u64) {
    shell(&format!(
        "setprop debug.hwui.target_cpu_time_percent {}",
        percent
    ));
    shell(&format!("iorenice -p {} 7 idle", std::process::id()));
    shell(&format!("renice -n 19 -p {}", std::process::id()));
    shell(&format!("taskset -ap 1 {}", std::process::id()));
}

pub fn set_settings(key: &str, value: &str) {
    shell(&format!("settings put secure {} {}", key, value));
}

pub fn set_cmd_power_adaptive(enabled: bool) {
    shell(&format!(
        "cmd power set-adaptive-power-saver-enabled {}",
        if enabled { "true" } else { "false" }
    ));
}

pub fn compute_cpu_freqs(min_mode: u8, max_mode: u8) -> Vec<(u32, u64, u64)> {
    let mut result = Vec::new();
    for policy in list_policies() {
        let min = match min_mode {
            100 => get_cpuinfo_max_freq(policy),
            101 => get_cpuinfo_min_freq(policy),
            _ => get_target_freq(policy, min_mode),
        };
        let max = match max_mode {
            100 => get_cpuinfo_max_freq(policy),
            101 => get_cpuinfo_min_freq(policy),
            _ => get_target_freq(policy, max_mode),
        };
        result.push((policy, min, max));
    }
    result
}

pub fn apply_cpu_freqs(pairs: &[(u32, u64, u64)]) {
    for (policy, min, max) in pairs {
        if *min > 0 {
            setvalue(&min.to_string(), &policy_path(*policy, "scaling_min_freq"));
        }
        if *max > 0 {
            setvalue(&max.to_string(), &policy_path(*policy, "scaling_max_freq"));
        }
    }
}
