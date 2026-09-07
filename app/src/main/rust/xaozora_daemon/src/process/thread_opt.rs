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
use std::path::Path;

fn set_value(file: &str, value: &str) {
    if Path::new(file).exists() {
        let _ = fs::write(file, value);
    }
}

pub fn init_cpuset() {
    if !Path::new(crate::config::GAME_MODE_DIR).exists() {
        if fs::create_dir_all(crate::config::GAME_MODE_DIR).is_err() {
            return;
        }
    }

    let mems_path = format!("{}/mems", crate::config::GAME_MODE_DIR);
    set_value(&mems_path, "0");

    let cpus_path = format!("{}/cpus", crate::config::GAME_MODE_DIR);
    set_value(&cpus_path, "4-7");

    let uclamp_boosted_path = format!("{}/uclamp.boosted", crate::config::GAME_MODE_DIR);
    set_value(&uclamp_boosted_path, "1");

    let uclamp_min_path = format!("{}/uclamp.min", crate::config::GAME_MODE_DIR);
    set_value(&uclamp_min_path, "100");
}

pub fn optimize_game_threads(pid: i32) {
    let tasks_path = format!("{}/tasks", crate::config::GAME_MODE_DIR);
    let _ = fs::write(tasks_path, pid.to_string());
}
