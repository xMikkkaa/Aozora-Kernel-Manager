use serde::{Serialize, Deserialize};
use jni::JNIEnv;
use jni::objects::{JClass, JString};
use jni::sys::jstring;

use crate::utils::shell::read_system_file;

#[derive(Serialize, Deserialize)]
pub struct AppInfoItem {
    pub package_name: String,
    pub name: String,
}

#[derive(Serialize, Deserialize)]
pub struct ConfiguredApp {
    pub package_name: String,
    pub mode: String,
}

pub fn get_configured_apps(app_list_path: &str) -> Vec<ConfiguredApp> {
    let app_list_str = read_system_file(app_list_path);
    if app_list_str.is_empty() {
        return vec![];
    }
    
    app_list_str.lines().filter_map(|line| {
        let trimmed = line.trim();
        let mode = if trimmed.ends_with("_p") {
            "p"
        } else if trimmed.ends_with("_g") {
            "g"
        } else if trimmed.ends_with("_g2") {
            "g2"
        } else {
            return None;
        };
        
        let package_name = if let Some(stripped) = trimmed.strip_suffix(&format!("_{}", mode)) {
            stripped.to_string()
        } else {
            return None;
        };
        
        Some(ConfiguredApp {
            package_name,
            mode: mode.to_string(),
        })
    }).collect()
}

#[no_mangle]
pub extern "system" fn Java_com_xaozora_manager_core_utils_AppManagerUtils_getConfiguredAppsJson<'local>(
    mut env: JNIEnv<'local>,
    _class: JClass,
    app_list_path: JString,
) -> jstring {
    let path: String = env.get_string(&app_list_path).unwrap().into();
    let apps = get_configured_apps(&path);
    
    let json_str = serde_json::to_string(&apps).unwrap_or_else(|_| "[]".to_string());
    let output = env.new_string(json_str).unwrap();
    output.into_raw()
}
