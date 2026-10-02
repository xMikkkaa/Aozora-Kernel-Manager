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

use super::primitives::setvalue;

pub fn optimize_boot_tune() {
    setvalue("1", "/sys/module/workqueue/parameters/power_efficient");

    if let Ok(entries) = fs::read_dir("/sys/block") {
        for entry in entries.flatten() {
            let name = entry.file_name().to_string_lossy().to_string();
            if name.starts_with("loop") {
                continue;
            }
            setvalue("0", entry.path().join("queue/iostats").to_str().unwrap_or(""));
        }
    }
    for dev in ["sda", "dm-0", "mmcblk0", "mmcblk1", "mmcblk0rpmb"] {
        setvalue("0", &format!("/sys/block/{}/queue/iostats", dev));
    }

    setvalue("64", "/proc/sys/kernel/random/read_wakeup/threshold");
    setvalue("128", "/proc/sys/kernel/random/write_wakeup/threshold");
    setvalue("128", "/proc/sys/kernel/random/read_wakeup_threshold");
    setvalue("1024", "/proc/sys/kernel/random/write_wakeup_threshold");

    setvalue("1", "/sys/kernel/rcu_normal");
    setvalue("0", "/sys/kernel/rcu_expedited");

    setvalue("0", "/dev/stune/top-app/schedtune.boost");
    setvalue("1", "/dev/stune/top-app/schedtune.prefer_idle");

    setvalue("1", "/proc/sys/kernel/sched_energy_aware");
    setvalue("1", "/proc/sys/kernel/sched_child_runs_first");
    setvalue("0", "/proc/sys/kernel/sched_autogroup_enabled");

    setvalue("0", "/sys/module/mmc_core/parameters/use_spi_crc");
    setvalue("0", "/sys/module/cpufreq_bouncing/parameters/enable");

    for path in [
        "/proc/sys/kernel/panic",
        "/proc/sys/kernel/panic_on_oops",
        "/proc/sys/kernel/panic_on_warn",
        "/proc/sys/kernel/panic_on_rcu_stall",
        "/sys/module/kernel/parameters/panic",
        "/sys/module/kernel/parameters/panic_on_warn",
        "/sys/module/kernel/parameters/pause_on_oops",
        "/sys/module/kernel/panic_on_rcu_stall",
    ] {
        setvalue("0", path);
    }

    setvalue("0", "/proc/sys/vm/extra_free_kbytes");
    setvalue("0", "/proc/sys/vm/oom_kill_allocating_task");
    setvalue("0", "/proc/sys/debug/exception-trace");

    setvalue("0", "/sys/kernel/debug/dri/0/debug/enable");
    setvalue("0", "/sys/kernel/debug/gpu/enable");
    setvalue("0", "/sys/kernel/debug/gpumemdebug");
    setvalue("1", "/sys/module/spurious/parameters/noirqdebug");
    setvalue("1", "/sys/kernel/debug/hwcomposer/disable_debug");

    if let Ok(entries) = fs::read_dir("/sys/devices/platform/soc") {
        for entry in entries.flatten() {
            let name = entry.file_name().to_string_lossy().to_string();
            if name.contains("mali") {
                setvalue("1", entry.path().join("js_ctx_scheduling_mode").to_str().unwrap_or(""));
            }
        }
    }
}
