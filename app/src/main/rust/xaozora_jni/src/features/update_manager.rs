use jni::objects::{JClass, JString};
use jni::sys::{jboolean, jstring};
use jni::JNIEnv;
use serde::{Deserialize, Serialize};

use crate::utils::shell::execute_cmd;

#[derive(Serialize, Deserialize, Default)]
pub struct AppUpdateResult {
    pub has_update: bool,
    pub new_version: String,
    pub download_url: String,
    pub release_notes: String,
}

#[derive(Serialize, Deserialize)]
pub struct UpdateCheckResult {
    pub app_update: AppUpdateResult,
}

pub fn check_app_update(current_app_version: &str) -> AppUpdateResult {
    let app_repo_url =
        "https://api.github.com/repos/xMikkkaa/Aozora-Kernel-Manager/releases/latest";

    let response = match ureq::get(app_repo_url)
        .set("User-Agent", "Aozora-Kernel-Manager")
        .call()
    {
        Ok(res) => res,
        Err(_) => return AppUpdateResult::default(),
    };

    let json: serde_json::Value = match response.into_json() {
        Ok(j) => j,
        Err(_) => return AppUpdateResult::default(),
    };

    let tag_name = json["tag_name"].as_str().unwrap_or("").replace("v", "");
    let release_notes = json["body"]
        .as_str()
        .unwrap_or("No release notes provided.")
        .to_string();

    if is_version_greater(&tag_name, current_app_version) {
        let mut download_url = String::new();
        if let Some(assets) = json["assets"].as_array() {
            for asset in assets {
                if let Some(name) = asset["name"].as_str() {
                    if name.ends_with(".apk") {
                        if let Some(url) = asset["browser_download_url"].as_str() {
                            download_url = url.to_string();
                            break;
                        }
                    }
                }
            }
        }

        if !download_url.is_empty() {
            return AppUpdateResult {
                has_update: true,
                new_version: tag_name,
                download_url,
                release_notes,
            };
        }
    }

    AppUpdateResult::default()
}

fn is_version_greater(new_ver: &str, old_ver: &str) -> bool {
    let v1: Vec<i32> = new_ver.split('.').map(|s| s.parse().unwrap_or(0)).collect();
    let v2: Vec<i32> = old_ver.split('.').map(|s| s.parse().unwrap_or(0)).collect();

    let max_len = std::cmp::max(v1.len(), v2.len());
    for i in 0..max_len {
        let n1 = v1.get(i).unwrap_or(&0);
        let n2 = v2.get(i).unwrap_or(&0);
        if n1 > n2 {
            return true;
        }
        if n1 < n2 {
            return false;
        }
    }
    false
}

pub fn perform_app_update(apk_url: &str, apk_temp_path: &str) -> bool {
    let response = match ureq::get(apk_url)
        .set("User-Agent", "Aozora-Kernel-Manager")
        .call()
    {
        Ok(res) => res,
        Err(_) => return false,
    };

    let mut out = match std::fs::File::create(apk_temp_path) {
        Ok(f) => f,
        Err(_) => return false,
    };

    if std::io::copy(&mut response.into_reader(), &mut out).is_err() {
        return false;
    }

    let shell_script = format!(
        r#"
        cp "{0}" /data/local/tmp/aozora_update.apk
        chmod 777 /data/local/tmp/aozora_update.apk
        
        (
          pm install -r -d /data/local/tmp/aozora_update.apk
          
          rm -f "{0}"
          rm -f /data/local/tmp/aozora_update.apk
          
          sleep 1
          am start -n com.xaozora.manager/.MainActivity
        ) >/dev/null 2>&1 &
        "#,
        apk_temp_path
    );

    execute_cmd(&shell_script)
}

#[no_mangle]
pub extern "system" fn Java_com_xaozora_manager_core_network_UpdateManager_checkUpdatesJson<
    'local,
>(
    mut env: JNIEnv<'local>,
    _class: JClass,
    current_app_version: JString,
) -> jstring {
    let version: String = env.get_string(&current_app_version).unwrap().into();
    let update = check_app_update(&version);

    let result = UpdateCheckResult { app_update: update };
    let json_str = serde_json::to_string(&result).unwrap_or_else(|_| "{}".to_string());

    let output = env.new_string(json_str).unwrap();
    output.into_raw()
}

#[no_mangle]
pub extern "system" fn Java_com_xaozora_manager_core_network_UpdateManager_performAppUpdate(
    mut env: JNIEnv,
    _class: JClass,
    apk_url: JString,
    apk_temp_path: JString,
) -> jboolean {
    let url: String = env.get_string(&apk_url).unwrap().into();
    let path: String = env.get_string(&apk_temp_path).unwrap().into();

    if perform_app_update(&url, &path) {
        1
    } else {
        0
    }
}
