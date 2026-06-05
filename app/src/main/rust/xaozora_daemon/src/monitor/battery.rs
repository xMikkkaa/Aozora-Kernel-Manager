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
use std::path::PathBuf;
use std::sync::Mutex;
use std::sync::atomic::{AtomicBool, Ordering};
use std::os::unix::fs::PermissionsExt;

static IS_IDLE_CHARGING_ACTIVE: AtomicBool = AtomicBool::new(false);
static ORIGINAL_CURRENTS: Mutex<Option<Vec<(String, String)>>> = Mutex::new(None);


pub struct ChargeSwitch {
    pub path: &'static str,
    pub enable: &'static str,
    pub disable: &'static str,
    pub use_max: bool,
}

static SWITCH_CHIMERA: ChargeSwitch = ChargeSwitch { path: "/sys/kernel/bypass_charge/bypass_charging", enable: "0", disable: "1", use_max: false };
static SWITCH_AOZORA: ChargeSwitch = ChargeSwitch { path: "/sys/class/power_supply/battery/input_suspend", enable: "0", disable: "1", use_max: false };
static SWITCH_FALLBACK: ChargeSwitch = ChargeSwitch { path: "/sys/class/power_supply/battery/constant_charge_current_max", enable: "3000000", disable: "100000", use_max: true };



fn read_sysfs_backup(path_str: &str) -> Option<String> {
    if let Ok(content) = fs::read_to_string(crate::config::AUTD_SYSFS_BACKUP_PATH) {
        for line in content.lines() {
            let mut parts = line.split('|');
            if let (Some(p), Some(v)) = (parts.next(), parts.next()) {
                if p == path_str {
                    return Some(v.to_string());
                }
            }
        }
    }
    None
}

fn is_valid_max(val_str: &str) -> bool {
    let trim_val = val_str.trim();
    if trim_val.is_empty() || trim_val == "0" {
        return false;
    }
    if let Ok(num) = trim_val.parse::<i64>() {
        if num < 400 || (num > 10000 && num < 400000) {
            return false;
        }
    } else {
        return false;
    }
    true
}

fn get_safe_fallback(path: &PathBuf, default_enable: &str) -> String {
    if let Some(backup) = read_sysfs_backup(&path.to_string_lossy()) {
        if is_valid_max(&backup) {
            return backup;
        }
    }

    let max_path = path.with_file_name(format!("{}_max", path.file_name().unwrap_or_default().to_string_lossy()));
    if max_path.exists() {
        if let Ok(val) = fs::read_to_string(&max_path) {
            if is_valid_max(&val) {
                return val.trim().to_string();
            }
        }
    }

    let alternative_paths = [
        "/sys/class/power_supply/battery/constant_charge_current_max",
        "/sys/class/power_supply/main/current_max",
        "/sys/class/power_supply/usb/current_max"
    ];
    for alt in alternative_paths.iter() {
        if let Ok(val) = fs::read_to_string(alt) {
            if is_valid_max(&val) {
                return val.trim().to_string();
            }
        }
    }

    default_enable.to_string()
}

pub fn get_active_switches() -> Vec<&'static ChargeSwitch> {
    let mut active = Vec::new();
    let proc_version = std::process::Command::new("cat")
        .arg("/proc/version")
        .output()
        .map(|o| String::from_utf8_lossy(&o.stdout).to_lowercase())
        .unwrap_or_default();
    
    let is_aozora = proc_version.contains("aozora-v9") || proc_version.contains("aozora-v10");
    let is_chimera = proc_version.contains("chimera");
    
    let path_chimera = PathBuf::from(SWITCH_CHIMERA.path);
    let path_aozora = PathBuf::from(SWITCH_AOZORA.path);
    let path_fallback = PathBuf::from(SWITCH_FALLBACK.path);
    
    if is_aozora {
        if path_aozora.exists() {
            active.push(&SWITCH_AOZORA);
        } else if path_fallback.exists() {
            active.push(&SWITCH_FALLBACK);
        }
    } else if is_chimera {
        if path_chimera.exists() {
            active.push(&SWITCH_CHIMERA);
        } else if path_fallback.exists() {
            active.push(&SWITCH_FALLBACK);
        }
    } else {
        if path_fallback.exists() {
            active.push(&SWITCH_FALLBACK);
        }
    }
    
    active
}



fn write_sysfs(path: &PathBuf, val: &str) {
    if let Ok(current_val) = fs::read_to_string(path) {
        if current_val.trim() == val.trim() {
            return;
        }
    }

    if let Ok(metadata) = fs::metadata(path) {
        let mut perms = metadata.permissions();
        perms.set_mode(0o644);
        let _ = fs::set_permissions(path, perms);
    }
    
    let _ = std::process::Command::new("sh")
        .arg("-c")
        .arg(format!("echo '{}' > {}", val, path.display()))
        .status();
}

pub fn init_backup_once() {
    if let Ok(content) = fs::read_to_string(crate::config::AUTD_SYSFS_BACKUP_PATH) {
        if content.lines().count() > 0 {
            return;
        }
    }

    let mut out = String::new();
    let sw = &SWITCH_FALLBACK;
    if sw.use_max {
        let path = PathBuf::from(sw.path);
        let fallback = get_safe_fallback(&path, sw.enable);
        out.push_str(&format!("{}|{}\n", sw.path, fallback));
    }
    
    if !out.is_empty() {
        let _ = fs::write(crate::config::AUTD_SYSFS_BACKUP_PATH, out);
    }
}

pub fn enable_idle_charging() {
    let active_switches = get_active_switches();
    if active_switches.is_empty() {
        return;
    }

    let mut originals = Vec::new();
    let is_first_time = !IS_IDLE_CHARGING_ACTIVE.load(Ordering::Relaxed);

    for sw in &active_switches {
        let path = PathBuf::from(sw.path);
        
        if is_first_time {
            let mut original_val = String::new();
            if sw.use_max {
                original_val = get_safe_fallback(&path, sw.enable);
            } else {
                if let Ok(current_val) = fs::read_to_string(&path) {
                    original_val = current_val.trim().to_string();
                }
                if original_val.is_empty() || original_val == sw.disable {
                    original_val = sw.enable.to_string();
                }
            }
            
            originals.push((sw.path.to_string(), original_val));
        }
        
        write_sysfs(&path, sw.disable);
    }

    if is_first_time {
        if let Ok(mut store) = ORIGINAL_CURRENTS.lock() {
            if store.is_none() && !originals.is_empty() {
                *store = Some(originals);
            }
        }
        IS_IDLE_CHARGING_ACTIVE.store(true, Ordering::Relaxed);
    }
}

pub fn disable_idle_charging() {
    if !IS_IDLE_CHARGING_ACTIVE.load(Ordering::Relaxed) {
        return;
    }
    
    if let Ok(mut store) = ORIGINAL_CURRENTS.lock() {
        if let Some(originals) = store.take() {
            for (path_str, val) in originals {
                let path = PathBuf::from(&path_str);
                write_sysfs(&path, &val);
            }
        } else {
            let active_switches = get_active_switches();
            for sw in active_switches {
                let path = PathBuf::from(sw.path);
                if sw.use_max {
                    write_sysfs(&path, &get_safe_fallback(&path, sw.enable));
                } else {
                    write_sysfs(&path, sw.enable);
                }
            }
        }
    }
    
    IS_IDLE_CHARGING_ACTIVE.store(false, Ordering::Relaxed);
}



pub fn reset_charging_states() {
    IS_IDLE_CHARGING_ACTIVE.store(false, Ordering::Relaxed);
    if let Ok(mut store) = ORIGINAL_CURRENTS.lock() { *store = None; }
}

pub fn get_battery_level() -> i32 {
    if let Ok(bytes) = fs::read("/sys/class/power_supply/battery/capacity") {
        let mut val = 0;
        let mut has_digit = false;
        
        for &b in bytes.iter() {
            if b.is_ascii_digit() {
                val = val * 10 + (b - b'0') as i32;
                has_digit = true;
            } else if has_digit {
                break;
            }
        }
        
        if has_digit {
            return val.clamp(0, 100);
        }
    }
    100
}

pub fn is_android_powersave() -> bool {
    if let Ok(bytes) = fs::read(crate::config::AUTD_PS_STATE_PATH) {
        return bytes.first() == Some(&b'1');
    }
    false
}use regex::Regex;
use serde::{Deserialize, Serialize};
use std::process::Command;

#[derive(Serialize, Deserialize, Debug, Default)]
pub struct AdvancedBatteryStats {
    #[serde(rename = "last_learned_capacity_mah")]
    pub last_learned_capacity_mah: f64,
    #[serde(rename = "time_on_battery_realtime_ms")]
    pub time_on_battery_realtime_ms: u64,
    #[serde(rename = "time_on_battery_uptime_ms")]
    pub time_on_battery_uptime_ms: u64,
    #[serde(rename = "time_on_battery_screen_off_ms")]
    pub time_on_battery_screen_off_ms: u64,
    #[serde(rename = "time_on_battery_screen_doze_ms")]
    pub time_on_battery_screen_doze_ms: u64,
    #[serde(rename = "total_run_time_realtime_ms")]
    pub total_run_time_realtime_ms: u64,
    #[serde(rename = "total_run_time_uptime_ms")]
    pub total_run_time_uptime_ms: u64,
    #[serde(rename = "discharge_mah")]
    pub discharge_mah: f64,
    #[serde(rename = "screen_off_discharge_mah")]
    pub screen_off_discharge_mah: f64,
    #[serde(rename = "screen_doze_discharge_mah")]
    pub screen_doze_discharge_mah: f64,
    #[serde(rename = "screen_on_discharge_mah")]
    pub screen_on_discharge_mah: f64,
    #[serde(rename = "device_light_doze_discharge_mah")]
    pub device_light_doze_discharge_mah: f64,
    #[serde(rename = "device_deep_doze_discharge_mah")]
    pub device_deep_doze_discharge_mah: f64,
    #[serde(rename = "start_clock_time")]
    pub start_clock_time: String,
    #[serde(rename = "connectivity_changes")]
    pub connectivity_changes: u32,
    #[serde(rename = "total_full_wakelock_time_ms")]
    pub total_full_wakelock_time_ms: u64,
    #[serde(rename = "screen_on_duration_ms")]
    pub screen_on_duration_ms: u64,
    
    #[serde(rename = "deep_sleep_ms")]
    pub deep_sleep_ms: u64,
    #[serde(rename = "awake_screen_off_ms")]
    pub awake_screen_off_ms: u64,
    #[serde(rename = "active_drain_rate_per_hr")]
    pub active_drain_rate_per_hr: f64,
    #[serde(rename = "idle_drain_rate_per_hr")]
    pub idle_drain_rate_per_hr: f64,
}

fn parse_time_to_ms(time_str: &str) -> u64 {
    let mut ms = 0;
    
    let re_h = Regex::new(r"(\d+)h").unwrap();
    let re_m = Regex::new(r"(\d+)m\b").unwrap();
    let re_s = Regex::new(r"(\d+)s").unwrap();
    let re_ms = Regex::new(r"(\d+)ms").unwrap();
    
    if let Some(caps) = re_h.captures(time_str) {
        ms += caps[1].parse::<u64>().unwrap_or(0) * 3_600_000;
    }
    if let Some(caps) = re_m.captures(time_str) {
        ms += caps[1].parse::<u64>().unwrap_or(0) * 60_000;
    }
    if let Some(caps) = re_s.captures(time_str) {
        ms += caps[1].parse::<u64>().unwrap_or(0) * 1_000;
    }
    if let Some(caps) = re_ms.captures(time_str) {
        ms += caps[1].parse::<u64>().unwrap_or(0);
    }
    
    ms
}

fn parse_mah(val_str: &str) -> f64 {
    let re = Regex::new(r"([\d\.]+)\s*mAh").unwrap();
    if let Some(caps) = re.captures(val_str) {
        caps[1].parse::<f64>().unwrap_or(0.0)
    } else {
        0.0
    }
}

pub fn fetch_and_parse_stats() -> AdvancedBatteryStats {
    let mut stats = AdvancedBatteryStats::default();

    let output = Command::new("dumpsys")
        .arg("batterystats")
        .output();

    if let Ok(out) = output {
        let stdout = String::from_utf8_lossy(&out.stdout);
        
        let mut in_stats_block = false;
        
        for line in stdout.lines() {
            let line = line.trim();
            
            if line.starts_with("Statistics since last charge:") {
                in_stats_block = true;
                continue;
            }
            
            if in_stats_block {
                if line.is_empty() {
                }
                
                if line.starts_with("CONNECTIVITY POWER SUMMARY START") || line.starts_with("Cellular Statistics:") {
                    break; 
                }
                
                if line.starts_with("Last learned battery capacity:") {
                    stats.last_learned_capacity_mah = parse_mah(line);
                } else if line.starts_with("Time on battery:") {
                    let re = Regex::new(r"Time on battery:\s*(.*?)\s*\(.*?\)\s*realtime,\s*(.*?)\s*\(").unwrap();
                    if let Some(caps) = re.captures(line) {
                        stats.time_on_battery_realtime_ms = parse_time_to_ms(&caps[1]);
                        stats.time_on_battery_uptime_ms = parse_time_to_ms(&caps[2]);
                    }
                } else if line.starts_with("Time on battery screen off:") {
                    let re = Regex::new(r"Time on battery screen off:\s*(.*?)\s*\(").unwrap();
                    if let Some(caps) = re.captures(line) {
                        stats.time_on_battery_screen_off_ms = parse_time_to_ms(&caps[1]);
                    }
                } else if line.starts_with("Time on battery screen doze:") {
                    let re = Regex::new(r"Time on battery screen doze:\s*(.*?)\s*\(").unwrap();
                    if let Some(caps) = re.captures(line) {
                        stats.time_on_battery_screen_doze_ms = parse_time_to_ms(&caps[1]);
                    }
                } else if line.starts_with("Total run time:") {
                    let re = Regex::new(r"Total run time:\s*(.*?)\s*realtime,\s*(.*?)\s*uptime").unwrap();
                    if let Some(caps) = re.captures(line) {
                        stats.total_run_time_realtime_ms = parse_time_to_ms(&caps[1]);
                        stats.total_run_time_uptime_ms = parse_time_to_ms(&caps[2]);
                    }
                } else if line.starts_with("Discharge:") {
                    stats.discharge_mah = parse_mah(line);
                } else if line.starts_with("Screen off discharge:") {
                    stats.screen_off_discharge_mah = parse_mah(line);
                } else if line.starts_with("Screen doze discharge:") {
                    stats.screen_doze_discharge_mah = parse_mah(line);
                } else if line.starts_with("Screen on discharge:") {
                    stats.screen_on_discharge_mah = parse_mah(line);
                } else if line.starts_with("Device light doze discharge:") {
                    stats.device_light_doze_discharge_mah = parse_mah(line);
                } else if line.starts_with("Device deep doze discharge:") {
                    stats.device_deep_doze_discharge_mah = parse_mah(line);
                } else if line.starts_with("Start clock time:") {
                    stats.start_clock_time = line.replace("Start clock time:", "").trim().to_string();
                } else if line.starts_with("Connectivity changes:") {
                    stats.connectivity_changes = line.replace("Connectivity changes:", "").trim().parse().unwrap_or(0);
                } else if line.starts_with("Total full wakelock time:") {
                    stats.total_full_wakelock_time_ms = parse_time_to_ms(line);
                } else if line.starts_with("Screen on:") {
                    let re = Regex::new(r"Screen on:\s*(.*?)\s*\(").unwrap();
                    if let Some(caps) = re.captures(line) {
                        stats.screen_on_duration_ms = parse_time_to_ms(&caps[1]);
                    }
                }
            }
        }
        
        stats.deep_sleep_ms = stats.time_on_battery_realtime_ms.saturating_sub(stats.time_on_battery_uptime_ms);
        stats.awake_screen_off_ms = stats.time_on_battery_uptime_ms.saturating_sub(stats.screen_on_duration_ms);
        
        let design_capacity = if stats.last_learned_capacity_mah > 0.0 { stats.last_learned_capacity_mah } else { 4000.0 };
        
        let screen_on_hrs = stats.screen_on_duration_ms as f64 / 3_600_000.0;
        if screen_on_hrs > 0.0 {
            stats.active_drain_rate_per_hr = (stats.screen_on_discharge_mah / design_capacity * 100.0) / screen_on_hrs;
        }
        
        let screen_off_hrs = stats.time_on_battery_screen_off_ms as f64 / 3_600_000.0;
        if screen_off_hrs > 0.0 {
            stats.idle_drain_rate_per_hr = (stats.screen_off_discharge_mah / design_capacity * 100.0) / screen_off_hrs;
        }
    }
    
    stats
}
