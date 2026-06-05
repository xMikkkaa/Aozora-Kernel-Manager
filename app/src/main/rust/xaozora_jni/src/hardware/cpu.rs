use serde::{Serialize, Deserialize};
use jni::JNIEnv;
use jni::objects::{JClass, JString};
use jni::sys::jstring;

use crate::utils::shell::{read_system_file, execute_cmd};

#[derive(Serialize, Deserialize)]
pub struct CpuClusterConfig {
    pub id: i32,
    pub name: String,
    pub available_freqs: Vec<String>,
    pub available_governors: Vec<String>,
    pub min_freq: String,
    pub max_freq: String,
    pub governor: String,
}

pub fn get_cluster_config(cluster_cpu_id: i32, name: &str) -> CpuClusterConfig {
    let base_path = format!("/sys/devices/system/cpu/cpu{}/cpufreq", cluster_cpu_id);
    
    let available_freqs_str = read_system_file(&format!("{}/scaling_available_frequencies", base_path));
    let mut available_freqs: Vec<String> = available_freqs_str
        .split_whitespace()
        .filter(|s| !s.is_empty())
        .map(String::from)
        .collect();

    let available_govs_str = read_system_file(&format!("{}/scaling_available_governors", base_path));
    let available_governors: Vec<String> = available_govs_str
        .split_whitespace()
        .filter(|s| !s.is_empty())
        .map(String::from)
        .collect();

    let min_freq = read_system_file(&format!("{}/scaling_min_freq", base_path));
    let max_freq = read_system_file(&format!("{}/scaling_max_freq", base_path));
    let governor = read_system_file(&format!("{}/scaling_governor", base_path));

    if !min_freq.is_empty() && !available_freqs.contains(&min_freq) {
        available_freqs.push(min_freq.clone());
    }
    if !max_freq.is_empty() && !available_freqs.contains(&max_freq) {
        available_freqs.push(max_freq.clone());
    }
    
    available_freqs.sort_by_key(|f| f.parse::<i64>().unwrap_or(0));

    CpuClusterConfig {
        id: cluster_cpu_id,
        name: name.to_string(),
        available_freqs,
        available_governors,
        min_freq,
        max_freq,
        governor,
    }
}

pub fn apply_cluster_config(cluster_cpu_id: i32, min_freq: &str, max_freq: &str, governor: &str) {
    let base_path = format!("/sys/devices/system/cpu/cpu{}/cpufreq", cluster_cpu_id);

    let cmd = format!(
        "chmod 644 {base}/scaling_min_freq {base}/scaling_max_freq {base}/scaling_governor; \
         cat {base}/cpuinfo_min_freq > {base}/scaling_min_freq 2>/dev/null; \
         cat {base}/cpuinfo_max_freq > {base}/scaling_max_freq 2>/dev/null; \
         echo {max} > {base}/scaling_max_freq 2>/dev/null; \
         echo {min} > {base}/scaling_min_freq 2>/dev/null; \
         echo {gov} > {base}/scaling_governor 2>/dev/null; \
         chmod 444 {base}/scaling_min_freq {base}/scaling_max_freq {base}/scaling_governor 2>/dev/null",
        base = base_path, max = max_freq, min = min_freq, gov = governor
    );
    
    execute_cmd(&cmd);
}

// JNI bindings

#[no_mangle]
pub extern "system" fn Java_com_xaozora_manager_core_utils_CpuControlUtils_getClusterConfigJson<'local>(
    mut env: JNIEnv<'local>,
    _class: JClass,
    cluster_cpu_id: jni::sys::jint,
    name: JString,
) -> jstring {
    let name: String = env.get_string(&name).unwrap().into();
    let config = get_cluster_config(cluster_cpu_id as i32, &name);
    
    let json_str = serde_json::to_string(&config).unwrap_or_else(|_| "{}".to_string());
    let output = env.new_string(json_str).unwrap();
    output.into_raw()
}

#[no_mangle]
pub extern "system" fn Java_com_xaozora_manager_core_utils_CpuControlUtils_applyClusterConfig(
    mut env: JNIEnv,
    _class: JClass,
    cluster_cpu_id: jni::sys::jint,
    min_freq: JString,
    max_freq: JString,
    governor: JString,
) {
    let min_freq: String = env.get_string(&min_freq).unwrap().into();
    let max_freq: String = env.get_string(&max_freq).unwrap().into();
    let governor: String = env.get_string(&governor).unwrap().into();
    
    apply_cluster_config(cluster_cpu_id as i32, &min_freq, &max_freq, &governor);
}
