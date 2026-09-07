use jni::objects::{JClass, JString};
use jni::sys::jstring;
use jni::JNIEnv;
use serde::{Deserialize, Serialize};

use crate::utils::shell::{check_file_exists, execute_cmd, read_system_file};

const BASE_PATH: &str = "/sys/kernel/gpu";
const DEVFREQ_PATH: &str = "/sys/class/kgsl/kgsl-3d0/devfreq";

#[derive(Serialize, Deserialize)]
pub struct GpuConfig {
    pub available_freqs: Vec<String>,
    pub available_governors: Vec<String>,
    pub min_freq: String,
    pub max_freq: String,
    pub governor: String,
    pub adreno_boost_supported: bool,
    pub adreno_boost: String,
}

pub fn get_gpu_config() -> GpuConfig {
    let available_freqs_str = read_system_file(&format!("{}/gpu_freq_table", BASE_PATH));
    let mut available_freqs: Vec<String> = available_freqs_str
        .split_whitespace()
        .filter(|s| !s.is_empty())
        .map(String::from)
        .collect();

    let available_govs_str = read_system_file(&format!("{}/gpu_available_governor", BASE_PATH));
    let available_governors: Vec<String> = available_govs_str
        .split_whitespace()
        .filter(|s| !s.is_empty())
        .map(String::from)
        .collect();

    let min_freq = read_system_file(&format!("{}/gpu_min_clock", BASE_PATH));
    let max_freq = read_system_file(&format!("{}/gpu_max_clock", BASE_PATH));
    let governor = read_system_file(&format!("{}/gpu_governor", BASE_PATH));

    if !min_freq.is_empty() && !available_freqs.contains(&min_freq) {
        available_freqs.push(min_freq.clone());
    }
    if !max_freq.is_empty() && !available_freqs.contains(&max_freq) {
        available_freqs.push(max_freq.clone());
    }
    available_freqs.sort_by_key(|f| f.parse::<i64>().unwrap_or(0));

    let adreno_boost_supported = check_file_exists(&format!("{}/adrenoboost", DEVFREQ_PATH));
    let adreno_boost = if adreno_boost_supported {
        read_system_file(&format!("{}/adrenoboost", DEVFREQ_PATH))
    } else {
        "0".to_string()
    };

    GpuConfig {
        available_freqs,
        available_governors,
        min_freq,
        max_freq,
        governor,
        adreno_boost_supported,
        adreno_boost,
    }
}

pub fn apply_gpu_config(
    min_freq: &str,
    max_freq: &str,
    governor: &str,
    adreno_boost: Option<&str>,
) {
    // Governor
    execute_cmd(&format!("chmod 644 {}/gpu_governor", BASE_PATH));
    execute_cmd(&format!("echo {} > {}/gpu_governor", governor, BASE_PATH));
    execute_cmd(&format!("chmod 444 {}/gpu_governor", BASE_PATH));

    // Max freq
    execute_cmd(&format!("chmod 644 {}/gpu_max_clock", BASE_PATH));
    execute_cmd(&format!("echo {} > {}/gpu_max_clock", max_freq, BASE_PATH));
    execute_cmd(&format!("chmod 444 {}/gpu_max_clock", BASE_PATH));

    // Min freq
    execute_cmd(&format!("chmod 644 {}/gpu_min_clock", BASE_PATH));
    execute_cmd(&format!("echo {} > {}/gpu_min_clock", min_freq, BASE_PATH));
    execute_cmd(&format!("chmod 444 {}/gpu_min_clock", BASE_PATH));

    if let Some(boost) = adreno_boost {
        if check_file_exists(&format!("{}/adrenoboost", DEVFREQ_PATH)) {
            execute_cmd(&format!("chmod 644 {}/adrenoboost", DEVFREQ_PATH));
            execute_cmd(&format!("echo {} > {}/adrenoboost", boost, DEVFREQ_PATH));
            execute_cmd(&format!("chmod 444 {}/adrenoboost", DEVFREQ_PATH));
        }
    }
}

// JNI bindings

#[no_mangle]
pub extern "system" fn Java_com_xaozora_manager_core_utils_GpuControlUtils_getGpuConfigJson<
    'local,
>(
    env: JNIEnv<'local>,
    _class: JClass,
) -> jstring {
    let config = get_gpu_config();
    let json_str = serde_json::to_string(&config).unwrap_or_else(|_| "{}".to_string());
    let output = env.new_string(json_str).unwrap();
    output.into_raw()
}

#[no_mangle]
pub extern "system" fn Java_com_xaozora_manager_core_utils_GpuControlUtils_applyGpuConfig(
    mut env: JNIEnv,
    _class: JClass,
    min_freq: JString,
    max_freq: JString,
    governor: JString,
    adreno_boost: JString,
) {
    let min_freq: String = env.get_string(&min_freq).unwrap().into();
    let max_freq: String = env.get_string(&max_freq).unwrap().into();
    let governor: String = env.get_string(&governor).unwrap().into();

    let adreno_boost_val: Option<String> = if adreno_boost.is_null() {
        None
    } else {
        Some(env.get_string(&adreno_boost).unwrap().into())
    };

    let adreno_boost_ref = adreno_boost_val.as_deref();

    apply_gpu_config(&min_freq, &max_freq, &governor, adreno_boost_ref);
}
