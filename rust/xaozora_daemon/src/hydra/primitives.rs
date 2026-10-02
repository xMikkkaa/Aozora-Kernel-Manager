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

#[allow(dead_code)]
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

#[allow(dead_code)]
pub fn set_gpu_freq(min: u64, max: u64) {
    if min > 0 {
        setvalue(&min.to_string(), "/sys/kernel/gpu/gpu_min_clock");
    }
    if max > 0 {
        setvalue(&max.to_string(), "/sys/kernel/gpu/gpu_max_clock");
    }
}

#[allow(dead_code)]
pub fn tune_walt(hs_l: u64, hs_b: u64, rtg_l: u64, rtg_b: u64, up: u64, down: u64) {
    for policy in list_policies() {
        let (hs, rtg) = if policy == 0 {
            (hs_l, rtg_l)
        } else {
            (hs_b, rtg_b)
        };
        let walt_dir = policy_path(policy, "walt");
        let schedutil_dir = policy_path(policy, "schedutil");
        if hs > 0 {
            setvalue(&hs.to_string(), &format!("{}/hispeed_freq", walt_dir));
            setvalue(&hs.to_string(), &format!("{}/hispeed_freq", schedutil_dir));
        }
        if rtg > 0 {
            setvalue(&rtg.to_string(), &format!("{}/rtg_boost_freq", walt_dir));
        }
        if up > 0 {
            setvalue(&up.to_string(), &format!("{}/up_rate_limit_us", walt_dir));
            setvalue(
                &up.to_string(),
                &format!("{}/up_rate_limit_us", schedutil_dir),
            );
        }
        if down > 0 {
            setvalue(
                &down.to_string(),
                &format!("{}/down_rate_limit_us", walt_dir),
            );
            setvalue(
                &down.to_string(),
                &format!("{}/down_rate_limit_us", schedutil_dir),
            );
        }
    }
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

#[allow(dead_code)]
pub fn tune_uclamp(min: u64, latency_sensitive: u64) {
    setvalue(&min.to_string(), "/dev/cpuctl/top-app/cpu.uclamp.min");
    setvalue(
        &latency_sensitive.to_string(),
        "/dev/cpuctl/top-app/cpu.uclamp.latency_sensitive",
    );
}

#[allow(dead_code)]
pub fn tune_sched_lat(base_slice_ns: u64) {
    setvalue("1", "/proc/sys/kernel/sched_child_runs_first");
    setvalue("32", "/proc/sys/kernel/sched_nr_migrate");
    setvalue("50000", "/proc/sys/kernel/sched_migration_cost_ns");
    if base_slice_ns > 0 {
        setvalue(
            &base_slice_ns.to_string(),
            "/proc/sys/kernel/sched_base_slice_ns",
        );
    }
}

#[allow(dead_code)]
pub fn tune_bore(bore: u64, penalty_offset: u64, penalty_scale: u64) {
    setvalue(&bore.to_string(), "/proc/sys/kernel/sched_bore");
    setvalue(
        &penalty_offset.to_string(),
        "/proc/sys/kernel/sched_burst_penalty_offset",
    );
    setvalue(
        &penalty_scale.to_string(),
        "/proc/sys/kernel/sched_burst_penalty_scale",
    );
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
