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
use std::path::Path;
use std::sync::Mutex;
use std::sync::atomic::{AtomicBool, Ordering};

static IS_IDLE_CHARGING_ACTIVE: AtomicBool = AtomicBool::new(false);
static IS_CHARGE_LIMIT_ACTIVE: AtomicBool = AtomicBool::new(false);
static BYPASS_CURRENT_LIMIT: AtomicBool = AtomicBool::new(false);
static ORIGINAL_CURRENTS: Mutex<Option<Vec<(String, String)>>> = Mutex::new(None);
static BYPASS_VERIFY_AT: Mutex<Option<std::time::Instant>> = Mutex::new(None);

const CHARGE_SWITCH_PATH: &str = "/sys/class/power_supply/battery/input_suspend";
const BYPASS_SWITCH_PATH: &str = "/sys/kernel/bypass_charge/bypass_charging";
const CURRENT_LIMIT_PATH: &str = "/sys/class/power_supply/battery/constant_charge_current_max";
const CURRENT_LIMIT_VALUE: &str = "100000";
const CHARGE_ENABLE: &str = "0";
const CHARGE_DISABLE: &str = "1";

fn bypass_switch_path() -> &'static str {
    if Path::new(BYPASS_SWITCH_PATH).exists() {
        BYPASS_SWITCH_PATH
    } else {
        CHARGE_SWITCH_PATH
    }
}

fn battery_is_discharging() -> bool {
    fs::read_to_string("/sys/class/power_supply/battery/status")
        .is_ok_and(|status| status.trim() == "Discharging")
}

pub fn is_idle_charging_active() -> bool {
    IS_IDLE_CHARGING_ACTIVE.load(Ordering::Relaxed)
}

fn write_sysfs(path: &Path, val: &str) {
    if let Ok(current_val) = fs::read_to_string(path)
        && current_val.trim() == val.trim()
    {
        return;
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

pub fn enable_idle_charging() {
    verify_bypass_fallback();

    let is_first_time = !IS_IDLE_CHARGING_ACTIVE.load(Ordering::Relaxed);

    if is_first_time {
        let switch_path = bypass_switch_path();
        let path = Path::new(switch_path);
        let mut original_val = fs::read_to_string(path)
            .map(|value| value.trim().to_string())
            .unwrap_or_default();
        if original_val.is_empty() || original_val == CHARGE_DISABLE {
            original_val = CHARGE_ENABLE.to_string();
        }

        if let Ok(mut store) = ORIGINAL_CURRENTS.lock() {
            *store = Some(vec![(switch_path.to_string(), original_val)]);
        }
        IS_IDLE_CHARGING_ACTIVE.store(true, Ordering::Relaxed);
        BYPASS_CURRENT_LIMIT.store(false, Ordering::Relaxed);

        if switch_path == CHARGE_SWITCH_PATH
            && let Ok(mut pending) = BYPASS_VERIFY_AT.lock()
        {
            *pending = Some(std::time::Instant::now());
        }

        write_sysfs(path, CHARGE_DISABLE);
        return;
    }

    if !BYPASS_CURRENT_LIMIT.load(Ordering::Relaxed) {
        write_sysfs(Path::new(bypass_switch_path()), CHARGE_DISABLE);
    }
}

fn verify_bypass_fallback() {
    let due = BYPASS_VERIFY_AT.lock().ok().is_some_and(|mut pending| {
        let ready = pending
            .as_ref()
            .is_some_and(|start| start.elapsed() >= std::time::Duration::from_millis(1200));
        if ready {
            *pending = None;
        }
        ready
    });

    if !due || has_online_input() == Some(false) || !battery_is_discharging() {
        return;
    }

    let originals = ORIGINAL_CURRENTS
        .lock()
        .ok()
        .and_then(|mut store| store.take());
    let Some(originals) = originals else {
        return;
    };

    for (path_str, val) in originals {
        write_sysfs(Path::new(&path_str), &val);
    }

    let Ok(current) = fs::read_to_string(CURRENT_LIMIT_PATH) else {
        return;
    };
    let original_current = current.trim().to_string();
    if original_current.is_empty() {
        return;
    }

    write_sysfs(Path::new(CURRENT_LIMIT_PATH), CURRENT_LIMIT_VALUE);
    let _ = fs::write(
        crate::config::AUTD_BYPASS_LIMIT_BACKUP_PATH,
        &original_current,
    );
    BYPASS_CURRENT_LIMIT.store(true, Ordering::Relaxed);
    if let Ok(mut store) = ORIGINAL_CURRENTS.lock() {
        *store = Some(vec![(CURRENT_LIMIT_PATH.to_string(), original_current)]);
    }
}

fn recover_bypass_limit() {
    let backup_path = crate::config::AUTD_BYPASS_LIMIT_BACKUP_PATH;
    if let Ok(value) = fs::read_to_string(backup_path) {
        let value = value.trim();
        if !value.is_empty() {
            write_sysfs(Path::new(CURRENT_LIMIT_PATH), value);
        }
        let _ = fs::remove_file(backup_path);
    }
}

pub fn disable_idle_charging() {
    if !IS_IDLE_CHARGING_ACTIVE.load(Ordering::Relaxed) {
        return;
    }

    if let Ok(mut pending) = BYPASS_VERIFY_AT.lock() {
        *pending = None;
    }

    if let Ok(mut store) = ORIGINAL_CURRENTS.lock() {
        if let Some(originals) = store.take() {
            for (path_str, val) in originals {
                write_sysfs(Path::new(&path_str), &val);
            }
        } else {
            write_sysfs(Path::new(bypass_switch_path()), CHARGE_ENABLE);
        }
    }

    IS_IDLE_CHARGING_ACTIVE.store(false, Ordering::Relaxed);
    BYPASS_CURRENT_LIMIT.store(false, Ordering::Relaxed);
    let _ = fs::remove_file(crate::config::AUTD_BYPASS_LIMIT_BACKUP_PATH);
}

pub fn reset_charging_states() {
    IS_IDLE_CHARGING_ACTIVE.store(false, Ordering::Relaxed);
    BYPASS_CURRENT_LIMIT.store(false, Ordering::Relaxed);
    if let Ok(mut store) = ORIGINAL_CURRENTS.lock() {
        *store = None;
    }
    if let Ok(mut pending) = BYPASS_VERIFY_AT.lock() {
        *pending = None;
    }
    recover_bypass_limit();
}

fn read_flag(path: &str) -> bool {
    fs::read(path).is_ok_and(|bytes| bytes.first() == Some(&b'1'))
}

fn read_config_number(path: &str, default: i32) -> i32 {
    fs::read_to_string(path)
        .ok()
        .and_then(|value| value.trim().parse::<i32>().ok())
        .unwrap_or(default)
}

fn charge_limit_marker_active() -> bool {
    read_flag(crate::config::AUTD_CHARGE_LIMIT_STATE_PATH)
}

fn is_charge_input_suspended() -> bool {
    fs::read_to_string(CHARGE_SWITCH_PATH).is_ok_and(|value| value.trim() == CHARGE_DISABLE)
}

fn set_charge_suspended(suspend: bool) {
    let path = Path::new(CHARGE_SWITCH_PATH);

    if suspend {
        let _ = fs::write(crate::config::AUTD_CHARGE_LIMIT_STATE_PATH, "1");
        write_sysfs(path, CHARGE_DISABLE);
        IS_CHARGE_LIMIT_ACTIVE.store(true, Ordering::Relaxed);
    } else {
        write_sysfs(path, CHARGE_ENABLE);
        let _ = fs::write(crate::config::AUTD_CHARGE_LIMIT_STATE_PATH, "0");
        IS_CHARGE_LIMIT_ACTIVE.store(false, Ordering::Relaxed);
    }
}

fn has_online_input() -> Option<bool> {
    if let Ok(entries) = fs::read_dir("/sys/class/power_supply") {
        let mut found_online = false;

        for entry in entries.flatten() {
            if entry.file_name().to_string_lossy() == "battery" {
                continue;
            }

            let online_path = entry.path().join("online");
            if let Ok(value) = fs::read_to_string(&online_path) {
                found_online = true;
                if value.trim() == "1" {
                    return Some(true);
                }
            }
        }

        if found_online {
            return Some(false);
        }
    }

    None
}

pub fn is_charger_connected() -> bool {
    if let Some(online) = has_online_input() {
        return online;
    }

    if let Ok(status) = fs::read_to_string("/sys/class/power_supply/battery/status") {
        return matches!(status.trim(), "Charging" | "Full" | "Not charging");
    }

    false
}

pub fn enforce_charge_limit() {
    let enabled = read_flag(crate::config::AUTD_CHARGE_LIMIT_ENABLE_PATH);
    let active = IS_CHARGE_LIMIT_ACTIVE.load(Ordering::Relaxed) || charge_limit_marker_active();

    if is_idle_charging_active() {
        return;
    }

    if !enabled {
        if active {
            set_charge_suspended(false);
        }
        return;
    }

    let Some(level) = get_battery_level_opt() else {
        return;
    };

    if !is_charger_connected() {
        if active {
            set_charge_suspended(false);
        }
        return;
    }

    let stop = read_config_number(crate::config::AUTD_CHARGE_LIMIT_STOP_PATH, 80).clamp(30, 100);
    let resume = read_config_number(
        crate::config::AUTD_CHARGE_LIMIT_RESUME_PATH,
        (stop - 5).max(20),
    )
    .clamp(20, stop - 1);

    if level >= stop {
        if !is_charge_input_suspended() {
            let announce = !active;
            set_charge_suspended(true);
            if announce {
                crate::utils::cmd::send_toast(&format!("Charging paused at {}%", level));
            }
        }
    } else if level <= resume && active {
        set_charge_suspended(false);
        crate::utils::cmd::send_toast("Charging resumed");
    }
}

pub fn recover_charge_limit_state() {
    if charge_limit_marker_active() {
        set_charge_suspended(false);
    } else {
        IS_CHARGE_LIMIT_ACTIVE.store(false, Ordering::Relaxed);
    }
}

pub fn restore_charge_limit() {
    if IS_CHARGE_LIMIT_ACTIVE.load(Ordering::Relaxed) || charge_limit_marker_active() {
        set_charge_suspended(false);
    }
}

pub fn get_battery_level_opt() -> Option<i32> {
    let bytes = fs::read("/sys/class/power_supply/battery/capacity").ok()?;
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
        Some(val.clamp(0, 100))
    } else {
        None
    }
}

pub fn get_battery_level() -> i32 {
    get_battery_level_opt().unwrap_or(100)
}

pub fn is_android_powersave() -> bool {
    if let Ok(bytes) = fs::read(crate::config::AUTD_PS_STATE_PATH) {
        return bytes.first() == Some(&b'1');
    }
    false
}
use regex::Regex;
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

    let output = Command::new("dumpsys").arg("batterystats").output();

    if let Ok(out) = output {
        let stdout = String::from_utf8_lossy(&out.stdout);

        let mut in_stats_block = false;

        let re_time_on_battery =
            Regex::new(r"Time on battery:\s*(.*?)\s*\(.*?\)\s*realtime,\s*(.*?)\s*\(").unwrap();
        let re_time_on_battery_screen_off =
            Regex::new(r"Time on battery screen off:\s*(.*?)\s*\(").unwrap();
        let re_time_on_battery_screen_doze =
            Regex::new(r"Time on battery screen doze:\s*(.*?)\s*\(").unwrap();
        let re_total_run_time =
            Regex::new(r"Total run time:\s*(.*?)\s*realtime,\s*(.*?)\s*uptime").unwrap();
        let re_screen_on = Regex::new(r"Screen on:\s*(.*?)\s*\(").unwrap();

        for line in stdout.lines() {
            let line = line.trim();

            if line.starts_with("Statistics since last charge:") {
                in_stats_block = true;
                continue;
            }

            if in_stats_block {
                if line.starts_with("CONNECTIVITY POWER SUMMARY START")
                    || line.starts_with("Cellular Statistics:")
                {
                    break;
                }

                if line.starts_with("Last learned battery capacity:") {
                    stats.last_learned_capacity_mah = parse_mah(line);
                } else if line.starts_with("Time on battery:") {
                    if let Some(caps) = re_time_on_battery.captures(line) {
                        stats.time_on_battery_realtime_ms = parse_time_to_ms(&caps[1]);
                        stats.time_on_battery_uptime_ms = parse_time_to_ms(&caps[2]);
                    }
                } else if line.starts_with("Time on battery screen off:") {
                    if let Some(caps) = re_time_on_battery_screen_off.captures(line) {
                        stats.time_on_battery_screen_off_ms = parse_time_to_ms(&caps[1]);
                    }
                } else if line.starts_with("Time on battery screen doze:") {
                    if let Some(caps) = re_time_on_battery_screen_doze.captures(line) {
                        stats.time_on_battery_screen_doze_ms = parse_time_to_ms(&caps[1]);
                    }
                } else if line.starts_with("Total run time:") {
                    if let Some(caps) = re_total_run_time.captures(line) {
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
                    stats.start_clock_time =
                        line.replace("Start clock time:", "").trim().to_string();
                } else if line.starts_with("Connectivity changes:") {
                    stats.connectivity_changes = line
                        .replace("Connectivity changes:", "")
                        .trim()
                        .parse()
                        .unwrap_or(0);
                } else if line.starts_with("Total full wakelock time:") {
                    stats.total_full_wakelock_time_ms = parse_time_to_ms(line);
                } else if line.starts_with("Screen on:")
                    && let Some(caps) = re_screen_on.captures(line)
                {
                    stats.screen_on_duration_ms = parse_time_to_ms(&caps[1]);
                }
            }
        }

        stats.deep_sleep_ms = stats
            .time_on_battery_realtime_ms
            .saturating_sub(stats.time_on_battery_uptime_ms);
        stats.awake_screen_off_ms = stats
            .time_on_battery_uptime_ms
            .saturating_sub(stats.screen_on_duration_ms);

        let design_capacity = if stats.last_learned_capacity_mah > 0.0 {
            stats.last_learned_capacity_mah
        } else {
            4000.0
        };

        let screen_on_hrs = stats.screen_on_duration_ms as f64 / 3_600_000.0;
        if screen_on_hrs > 0.0 {
            stats.active_drain_rate_per_hr =
                (stats.screen_on_discharge_mah / design_capacity * 100.0) / screen_on_hrs;
        }

        let screen_off_hrs = stats.time_on_battery_screen_off_ms as f64 / 3_600_000.0;
        if screen_off_hrs > 0.0 {
            stats.idle_drain_rate_per_hr =
                (stats.screen_off_discharge_mah / design_capacity * 100.0) / screen_off_hrs;
        }
    }

    stats
}
