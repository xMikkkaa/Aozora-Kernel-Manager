use serde::Serialize;
use jni::JNIEnv;
use jni::objects::{JClass, JString};
use jni::sys::jstring;

use crate::shell::{read_system_file, execute_cmd, check_file_exists};

pub fn get_active_profile() -> String {
    let status_path = "/data/data/com.xaozora.manager/files/autd/autd_status";
    let content = read_system_file(status_path);
    if !content.is_empty() {
        content
    } else {
        "balance".to_string()
    }
}

pub fn get_available_profiles() -> Vec<String> {
    let mut profiles = vec!["powersave".to_string(), "balance".to_string(), "performance".to_string()];
    
    if check_file_exists("/system/bin/gaming") {
        profiles.push("gaming".to_string());
    }
    if check_file_exists("/system/bin/gaming2") {
        profiles.push("gaming2".to_string());
    }
    
    profiles
}

pub fn apply_profile(profile_id: &str) -> bool {
    let autd_dir = "/data/data/com.xaozora.manager/files/autd";
    let cmd = format!("rm -f {0}/autd_base_mode; echo -n '{1}' > {0}/autd_base_mode", autd_dir, profile_id);
    execute_cmd(&cmd)
}

pub fn update_power_save_state(is_power_save: bool) {
    let autd_dir = "/data/data/com.xaozora.manager/files/autd";
    let val = if is_power_save { "1" } else { "0" };
    execute_cmd(&format!("mkdir -p {}; echo -n '{}' > {}/autd_ps_state", autd_dir, val, autd_dir));
}

pub fn reset_battery_stats(enable_autd: bool, enable_battmon: bool) {
    execute_cmd("dumpsys batterystats --reset");
    
    let files_dir = "/data/data/com.xaozora.manager/files";
    let autd_arg = if enable_autd { "--enable-autd" } else { "--disable-autd" };
    let battmon_arg = if enable_battmon { 
        format!("--battery-logger {}/battmon/battery_logger.jsonl", files_dir)
    } else { 
        String::new()
    };
    
    let cmd = format!(
        "cd {} && nohup ./xaozora_daemon {} --reset-stats {} > /dev/null 2>&1 &",
        files_dir, autd_arg, battmon_arg
    );
    execute_cmd(&cmd);
}

#[derive(Serialize)]
pub struct BatteryNotificationData {
    pub active_drain: f64,
    pub idle_drain: f64,
    pub screen_on_ms: i64,
    pub screen_on_mah: f64,
    pub screen_off_ms: i64,
    pub screen_off_mah: f64,
    pub deep_sleep_ms: i64,
    pub awake_ms: i64,
    pub capacity_mah: f64,
}

pub fn get_battery_notification_data() -> BatteryNotificationData {
    let mut data = BatteryNotificationData {
        active_drain: 0.0,
        idle_drain: 0.0,
        screen_on_ms: 0,
        screen_on_mah: 0.0,
        screen_off_ms: 0,
        screen_off_mah: 0.0,
        deep_sleep_ms: 0,
        awake_ms: 0,
        capacity_mah: 4000.0,
    };
    
    let path = "/data/data/com.xaozora.manager/files/battmon/battery_stats.json";
    let content = read_system_file(path);
    if !content.is_empty() {
        if let Ok(json) = serde_json::from_str::<serde_json::Value>(&content) {
            data.active_drain = json["active_drain_rate_per_hr"].as_f64().unwrap_or(0.0);
            data.idle_drain = json["idle_drain_rate_per_hr"].as_f64().unwrap_or(0.0);
            data.screen_on_ms = json["screen_on_duration_ms"].as_i64().unwrap_or(0);
            data.screen_on_mah = json["screen_on_discharge_mah"].as_f64().unwrap_or(0.0);
            data.screen_off_ms = json["time_on_battery_screen_off_ms"].as_i64().unwrap_or(0);
            data.screen_off_mah = json["screen_off_discharge_mah"].as_f64().unwrap_or(0.0);
            data.deep_sleep_ms = json["deep_sleep_ms"].as_i64().unwrap_or(0);
            data.awake_ms = json["awake_screen_off_ms"].as_i64().unwrap_or(0);
            data.capacity_mah = json["last_learned_capacity_mah"].as_f64().unwrap_or(4000.0);
        }
    }
    
    data
}

// JNI bindings

#[no_mangle]
pub extern "system" fn Java_com_xaozora_manager_services_ProfileTileService_getActiveProfile<'local>(
    env: JNIEnv<'local>,
    _class: JClass,
) -> jstring {
    let profile = get_active_profile();
    let output = env.new_string(profile).unwrap();
    output.into_raw()
}

#[no_mangle]
pub extern "system" fn Java_com_xaozora_manager_services_ProfileTileService_getAvailableProfilesJson<'local>(
    env: JNIEnv<'local>,
    _class: JClass,
) -> jstring {
    let profiles = get_available_profiles();
    let json_str = serde_json::to_string(&profiles).unwrap_or_else(|_| "[]".to_string());
    let output = env.new_string(json_str).unwrap();
    output.into_raw()
}

#[no_mangle]
pub extern "system" fn Java_com_xaozora_manager_services_ProfileTileService_applyProfile(
    mut env: JNIEnv,
    _class: JClass,
    profile_id: JString,
) {
    let profile: String = env.get_string(&profile_id).unwrap().into();
    apply_profile(&profile);
}

#[no_mangle]
pub extern "system" fn Java_com_xaozora_manager_services_MonitorService_updatePowerSaveState(
    _env: JNIEnv,
    _class: JClass,
    is_power_save: jni::sys::jboolean,
) {
    update_power_save_state(is_power_save != 0);
}

#[no_mangle]
pub extern "system" fn Java_com_xaozora_manager_services_MonitorService_resetBatteryStats(
    _env: JNIEnv,
    _class: JClass,
    enable_autd: jni::sys::jboolean,
    enable_battmon: jni::sys::jboolean,
) {
    reset_battery_stats(enable_autd != 0, enable_battmon != 0);
}

#[no_mangle]
pub extern "system" fn Java_com_xaozora_manager_services_MonitorService_getBatteryNotificationDataJson<'local>(
    env: JNIEnv<'local>,
    _class: JClass,
) -> jstring {
    let data = get_battery_notification_data();
    let json_str = serde_json::to_string(&data).unwrap_or_else(|_| "{}".to_string());
    let output = env.new_string(json_str).unwrap();
    output.into_raw()
}
