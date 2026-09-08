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

use std::collections::BTreeMap;
use std::fs;
use std::path::Path;

fn set_value(file: &str, value: &str) {
    if Path::new(file).exists() {
        let _ = fs::write(file, value);
    }
}

fn parse_cpu_list(value: &str) -> Vec<u32> {
    let mut cpus = Vec::new();
    for part in value.split(',') {
        let part = part.trim();
        if part.is_empty() {
            continue;
        }
        if let Some((start, end)) = part.split_once('-') {
            if let (Ok(s), Ok(e)) = (start.trim().parse::<u32>(), end.trim().parse::<u32>())
                && s <= e
            {
                cpus.extend(s..=e);
            }
        } else if let Ok(cpu) = part.parse::<u32>() {
            cpus.push(cpu);
        }
    }
    cpus.sort_unstable();
    cpus.dedup();
    cpus
}

fn detect_present_cpus() -> Vec<u32> {
    for path in [
        "/sys/devices/system/cpu/present",
        "/sys/devices/system/cpu/possible",
        "/sys/devices/system/cpu/online",
    ] {
        if let Ok(content) = fs::read_to_string(path) {
            let cpus = parse_cpu_list(content.trim());
            if !cpus.is_empty() {
                return cpus;
            }
        }
    }

    let mut cpus = Vec::new();
    for cpu in 0..32 {
        if Path::new(&format!("/sys/devices/system/cpu/cpu{}", cpu)).exists() {
            cpus.push(cpu);
        } else if cpu > 0 && !cpus.is_empty() {
            break;
        }
    }
    cpus
}

fn read_policy_max_freq(cpu: u32) -> Option<u64> {
    let cpufreq_dir = Path::new("/sys/devices/system/cpu/cpufreq");
    let entries = fs::read_dir(cpufreq_dir).ok()?;
    for entry in entries.flatten() {
        let related = entry.path().join("related_cpus");
        let freq_file = entry.path().join("cpuinfo_max_freq");
        if let Ok(content) = fs::read_to_string(&related)
            && parse_cpu_list(content.trim()).contains(&cpu)
            && let Ok(freq) = fs::read_to_string(&freq_file)
            && let Ok(value) = freq.trim().parse::<u64>()
            && value > 0
        {
            return Some(value);
        }
    }
    None
}

fn read_cpu_max_freq(cpu: u32) -> Option<u64> {
    for file in [
        format!(
            "/sys/devices/system/cpu/cpu{}/cpufreq/cpuinfo_max_freq",
            cpu
        ),
        format!(
            "/sys/devices/system/cpu/cpu{}/cpufreq/scaling_max_freq",
            cpu
        ),
    ] {
        if let Ok(content) = fs::read_to_string(&file)
            && let Ok(value) = content.trim().parse::<u64>()
            && value > 0
        {
            return Some(value);
        }
    }
    read_policy_max_freq(cpu)
}

fn detect_perf_cpus() -> Vec<u32> {
    let present = detect_present_cpus();
    if present.is_empty() {
        return Vec::new();
    }

    let mut by_freq: BTreeMap<u64, Vec<u32>> = BTreeMap::new();
    for cpu in &present {
        if let Some(freq) = read_cpu_max_freq(*cpu) {
            by_freq.entry(freq).or_default().push(*cpu);
        }
    }

    if by_freq.is_empty() {
        let start = (present.len() as u32) / 2;
        return present.into_iter().filter(|cpu| *cpu >= start).collect();
    }

    let threshold = present.len().div_ceil(3).max(2);
    let mut perf = Vec::new();
    for cpus in by_freq.values().rev() {
        let mut sorted = cpus.clone();
        sorted.sort_unstable();
        perf.extend(sorted);
        if perf.len() >= threshold {
            break;
        }
    }
    perf.sort_unstable();
    perf
}

fn format_cpu_list(cpus: &[u32]) -> String {
    if cpus.is_empty() {
        return String::new();
    }
    let mut sorted = cpus.to_vec();
    sorted.sort_unstable();
    sorted.dedup();

    let mut parts = Vec::new();
    let mut range_start = sorted[0];
    let mut range_end = sorted[0];
    for cpu in sorted.iter().skip(1) {
        if *cpu == range_end + 1 {
            range_end = *cpu;
        } else {
            if range_start == range_end {
                parts.push(range_start.to_string());
            } else {
                parts.push(format!("{}-{}", range_start, range_end));
            }
            range_start = *cpu;
            range_end = *cpu;
        }
    }
    if range_start == range_end {
        parts.push(range_start.to_string());
    } else {
        parts.push(format!("{}-{}", range_start, range_end));
    }
    parts.join(",")
}

fn ensure_cpuset() -> bool {
    if !Path::new(crate::config::GAME_MODE_DIR).exists()
        && fs::create_dir_all(crate::config::GAME_MODE_DIR).is_err()
    {
        return false;
    }
    true
}

pub fn init_cpuset() {
    if !ensure_cpuset() {
        return;
    }

    let mems_path = format!("{}/mems", crate::config::GAME_MODE_DIR);
    set_value(&mems_path, "0");

    let perf_cpus = detect_perf_cpus();
    if !perf_cpus.is_empty() {
        let cpus_path = format!("{}/cpus", crate::config::GAME_MODE_DIR);
        set_value(&cpus_path, &format_cpu_list(&perf_cpus));
    }

    let uclamp_boosted_path = format!("{}/uclamp.boosted", crate::config::GAME_MODE_DIR);
    set_value(&uclamp_boosted_path, "1");

    let uclamp_min_path = format!("{}/uclamp.min", crate::config::GAME_MODE_DIR);
    set_value(&uclamp_min_path, "100");
}

pub fn optimize_game_threads(pid: i32) {
    if pid <= 0 {
        return;
    }
    if !ensure_cpuset() {
        return;
    }
    let value = pid.to_string();
    let procs_path = format!("{}/cgroup.procs", crate::config::GAME_MODE_DIR);
    if Path::new(&procs_path).exists() {
        let _ = fs::write(&procs_path, &value);
    }
    let tasks_path = format!("{}/tasks", crate::config::GAME_MODE_DIR);
    if Path::new(&tasks_path).exists() {
        let _ = fs::write(&tasks_path, &value);
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn parses_range_list() {
        assert_eq!(parse_cpu_list("0-7"), vec![0, 1, 2, 3, 4, 5, 6, 7]);
        assert_eq!(parse_cpu_list("0-3,4-7"), vec![0, 1, 2, 3, 4, 5, 6, 7]);
        assert_eq!(parse_cpu_list("0-3,7"), vec![0, 1, 2, 3, 7]);
        assert_eq!(parse_cpu_list(""), Vec::<u32>::new());
    }

    #[test]
    fn formats_ranges() {
        assert_eq!(format_cpu_list(&[4, 5, 6, 7]), "4-7");
        assert_eq!(format_cpu_list(&[7]), "7");
        assert_eq!(format_cpu_list(&[4, 5, 6, 8, 9]), "4-6,8-9");
        assert_eq!(format_cpu_list(&[]), "");
    }
}
