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

use std::env;
use std::fs;
use std::os::unix::fs::PermissionsExt;

pub const AUTD_DIR: &str = "/data/data/com.xaozora.manager/files/autd";
pub const BATTMON_DIR: &str = "/data/data/com.xaozora.manager/files/battmon";
pub const AUTD_BASE_MODE_PATH: &str = "/data/data/com.xaozora.manager/files/autd/autd_base_mode";
pub const FILELIST_PATH: &str = "/data/data/com.xaozora.manager/files/autd/applist";
pub const AUTD_STATUS_PATH: &str = "/data/data/com.xaozora.manager/files/autd/autd_status";
pub const AUTD_OPT_ALLOW_PATH: &str = "/data/data/com.xaozora.manager/files/autd/autd_opt_allow";
pub const AUTD_PS_STATE_PATH: &str = "/data/data/com.xaozora.manager/files/autd/autd_ps_state";
pub const AUTD_IDLE_CHARGING_PATH: &str = "/data/data/com.xaozora.manager/files/autd/autd_idle_charging";
pub const AUTD_SYSFS_BACKUP_PATH: &str = "/data/data/com.xaozora.manager/files/autd/autd_sysfs_backup";
pub const AUTD_AWAKE_DEBUG_LOG: &str = "/data/data/com.xaozora.manager/files/autd/autd_awake_method.info";

pub fn setup_android_env() {
    unsafe {
        env::set_var(
            "PATH",
            "/sbin:/system/sbin:/system/bin:/system/xbin:/vendor/bin:/vendor/xbin",
        );
        env::set_var("ANDROID_ROOT", "/system");
        env::set_var("ANDROID_DATA", "/data");
        env::set_var("ANDROID_STORAGE", "/storage");
        env::set_var("ANDROID_ART_ROOT", "/apex/com.android.art");
        env::set_var("ANDROID_I18N_ROOT", "/apex/com.android.i18n");
    }
}

pub fn ensure_app_dir() {
    if fs::metadata(AUTD_DIR).is_err() {
        if let Err(e) = fs::create_dir_all(AUTD_DIR) {
            eprintln!("autd: Failed to create AUTD_DIR: {}", e);
        }
    }

    if let Ok(metadata) = fs::metadata(AUTD_DIR) {
        let mut perms = metadata.permissions();
        perms.set_mode(0o777);
        let _ = fs::set_permissions(AUTD_DIR, perms);
    }
}

pub fn ensure_battmon_dir() {
    if fs::metadata(BATTMON_DIR).is_err() {
        if let Err(e) = fs::create_dir_all(BATTMON_DIR) {
            eprintln!("battmon: Failed to create BATTMON_DIR: {}", e);
            return;
        }
    }

    if let Ok(metadata) = fs::metadata(BATTMON_DIR) {
        let mut perms = metadata.permissions();
        perms.set_mode(0o777);
        let _ = fs::set_permissions(BATTMON_DIR, perms);
    }
}