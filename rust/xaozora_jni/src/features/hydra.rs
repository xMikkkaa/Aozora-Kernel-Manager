use jni::objects::JClass;
use jni::sys::jstring;
use jni::{errors::ThrowRuntimeExAndDefault, EnvUnowned};
use serde::Serialize;

use crate::utils::shell::check_file_exists;

#[derive(Serialize)]
pub struct HydraInfo {
    pub name: String,
    pub version: String,
    pub helper_installed: bool,
    pub profiles: Vec<String>,
}

pub fn get_hydra_info() -> HydraInfo {
    HydraInfo {
        name: "Aozora-Hydra".to_string(),
        version: "1.0.0".to_string(),
        helper_installed: check_file_exists("/system/bin/powersave"),
        profiles: vec![
            "powersave".to_string(),
            "balance".to_string(),
            "performance".to_string(),
            "gaming".to_string(),
            "gaming2".to_string(),
        ],
    }
}

#[no_mangle]
pub extern "system" fn Java_com_xaozora_manager_services_ProfileTileService_getHydraInfoJson<
    'local,
>(
    mut env: EnvUnowned<'local>,
    _class: JClass,
) -> jstring {
    env.with_env(|env| -> jni::errors::Result<jstring> {
        let info = get_hydra_info();
        let json_str = serde_json::to_string(&info).unwrap_or_else(|_| "{}".to_string());
        let output = env.new_string(json_str).unwrap();
        Ok(output.into_raw())
    })
    .resolve::<ThrowRuntimeExAndDefault>()
}
