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

use std::fs::{self, File};
use std::io::Write;
use std::process::Command;
use std::sync::Mutex;

static LAST_AWAKE_METHOD: Mutex<String> = Mutex::new(String::new());

pub fn log_active_method(method: &str) {
    if let Ok(mut last_method) = LAST_AWAKE_METHOD.lock() {
        if last_method.as_str() != method {
            if let Ok(mut file) = File::create(crate::config::AUTD_AWAKE_DEBUG_LOG) {
                let _ = writeln!(file, "Active Method: {}", method);
            }

            last_method.clear();
            last_method.push_str(method);
        }
    }
}

pub fn is_awake() -> bool {
    if let Ok(entries) = fs::read_dir("/sys/class/backlight/") {
        for entry in entries.flatten() {
            let name = entry.file_name();
            if name == "." || name == ".." {
                continue;
            }

            let brightness_path = entry.path().join("brightness");
            if let Ok(bytes) = fs::read(brightness_path) {
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
                    log_active_method("SysFS Backlight");
                    return val > 0;
                }
            }
        }
    }

    if let Ok(entries) = fs::read_dir("/sys/class/drm/") {
        for entry in entries.flatten() {
            if let Some(name) = entry.file_name().to_str() {
                if name.starts_with("card0-") && !name.contains("virtual") {
                    let enabled_path = entry.path().join("enabled");
                    if let Ok(bytes) = fs::read(enabled_path) {
                        if bytes.starts_with(b"enabled") {
                            log_active_method("SysFS DRM (Enabled Check)");
                            return true;
                        } else if bytes.starts_with(b"disabled") {
                            log_active_method("SysFS DRM (Enabled Check)");
                            return false;
                        }
                    }
                }
            }
        }
    }

    if let Ok(output) = Command::new("/system/bin/dumpsys").arg("power").output() {
        let target = b"mWakefulness=Awake";
        if output.stdout.windows(target.len()).any(|w| w == target) {
            log_active_method("Fallback Dumpsys");
            return true;
        }
    }

    log_active_method("Fallback Dumpsys");
    false
}
