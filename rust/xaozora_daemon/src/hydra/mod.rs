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

pub mod boot;
pub mod primitives;
pub mod soc;

use primitives::{
    apply_cpu_freqs, compute_cpu_freqs, set_cmd_power_adaptive, set_cpu_gov, set_hwui_target,
    set_settings, shell, shell_output, tune_block_io, tune_block_sched, tune_net, tune_vm_io,
};
use soc::{detect_soc, soc_apply};

pub const HYDRA_NAME: &str = "Aozora-Hydra";
pub const HYDRA_VERSION: &str = "1.0.0";

pub const HYDRA_INFO_PATH: &str = "autd/hydra_info.json";

pub use boot::optimize_boot_tune;

pub fn write_info_json() {
    let helper_installed = std::path::Path::new("/system/bin/powersave").exists();
    let profiles = vec!["powersave", "balance", "performance", "gaming", "gaming2"];
    let info = serde_json::json!({
        "name": HYDRA_NAME,
        "version": HYDRA_VERSION,
        "helper_installed": helper_installed,
        "profiles": profiles,
    });
    let _ = std::fs::write(HYDRA_INFO_PATH, info.to_string());
}

pub fn apply_powersave() {
    let soc = detect_soc();
    let freqs = compute_cpu_freqs(101, 3);
    apply_cpu_freqs(&freqs);
    set_cpu_gov("schedutil");
    tune_block_io(1, 1, 128, 128);
    tune_net(0, 2, 1, 1);
    tune_vm_io(100, 3);
    tune_block_sched("none", 2);
    set_hwui_target(40);
    set_settings("high_priority", "0");
    set_settings("low_priority", "1");
    set_cmd_power_adaptive(true);
    soc_apply(2, soc);
}

pub fn apply_balance() {
    let soc = detect_soc();
    let freqs = compute_cpu_freqs(2, 100);
    apply_cpu_freqs(&freqs);
    set_cpu_gov("schedutil");
    tune_block_io(1, 1, 128, 128);
    tune_net(0, 2, 1, 1);
    tune_vm_io(120, 3);
    tune_block_sched("none", 1);
    set_hwui_target(55);
    set_settings("high_priority", "1");
    set_settings("low_priority", "0");
    set_cmd_power_adaptive(false);
    soc_apply(3, soc);
}

pub fn apply_performance() {
    let soc = detect_soc();
    let freqs = compute_cpu_freqs(100, 100);
    apply_cpu_freqs(&freqs);
    set_cpu_gov("performance");
    tune_block_io(0, 0, 32, 32);
    tune_net(1, 1, 3, 0);
    tune_vm_io(80, 0);
    tune_block_sched("none", 1);
    set_hwui_target(80);
    set_settings("high_priority", "1");
    set_settings("low_priority", "0");
    set_cmd_power_adaptive(false);
    soc_apply(4, soc);
}

pub fn kill_all() {
    shell("cmd activity kill-all");
    let packages = shell_output("pm list packages -3 | cut -f 2 -d ':' | tr -d '\\r'");
    for pkg in packages.lines() {
        let pkg = pkg.trim();
        if pkg.is_empty() {
            continue;
        }
        shell(&format!("am force-stop {} &", pkg));
    }
    shell("wait");
    shell("pm trim-caches 100G");
    let _ = std::fs::write("/proc/sys/vm/drop_caches", "3");
}

pub fn apply_gaming() {
    apply_performance();
    kill_all();
}

pub fn apply_gaming2() {
    apply_performance();
}

pub fn apply_profile(mode: &str) {
    match mode {
        "powersave" => apply_powersave(),
        "balance" => apply_balance(),
        "performance" => apply_performance(),
        "gaming" => apply_gaming(),
        "gaming2" => apply_gaming2(),
        _ => apply_balance(),
    }
}
