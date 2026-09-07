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

use std::fmt::Write;
use std::fs;
use std::sync::atomic::Ordering;
use std::thread::sleep;
use std::time::Duration;

use crate::{RUNNING, config, monitor, process, utils};

pub fn perform_cleanup() -> bool {
    let mut actually_cleaned = false;
    if fs::metadata(config::AUTD_STATUS_PATH).is_ok() {
        let _ = fs::remove_file(config::AUTD_STATUS_PATH);
        actually_cleaned = true;
    }
    let _ = fs::remove_file(config::AUTD_AWAKE_DEBUG_LOG);
    let _ = fs::remove_file(config::AUTD_PS_STATE_PATH);
    monitor::battery::disable_idle_charging();
    actually_cleaned
}

pub fn interruptible_sleep(secs: u64) {
    let iterations = secs * 2;
    for _ in 0..iterations {
        if !RUNNING.load(Ordering::SeqCst) {
            break;
        }
        sleep(Duration::from_millis(500));
    }
}

pub fn run_autd() {
    config::ensure_app_dir();
    process::thread_opt::init_cpuset();

    let mut last_mode = String::with_capacity(64);
    let mut user_base = String::with_capacity(64);
    user_base.push_str("balance");
    let mut msg_buffer = String::with_capacity(300);

    let mut low_bat_notif_sent = false;
    let mut idle_cycles = 0;

    interruptible_sleep(1);
    monitor::battery::init_backup_once();
    monitor::battery::reset_charging_states();

    let mut last_hydra_pid: i32 = 0;
    let mut sched_lib_active = false;

    utils::cmd::send_toast("xAozora Daemon (AUTD) Started");
    monitor::display::log_active_method("AUTD Active: Monitoring system state...");

    let fifo_path = config::AUTD_EVENT_PIPE;
    let _ = fs::remove_file(fifo_path);
    let c_path = std::ffi::CString::new(fifo_path).unwrap();
    unsafe {
        libc::mkfifo(c_path.as_ptr(), 0o666);
    }
    let _ = std::process::Command::new("chmod")
        .arg("666")
        .arg(fifo_path)
        .status();

    let mut fifo_file = fs::OpenOptions::new()
        .read(true)
        .write(true)
        .open(fifo_path)
        .expect("Failed to open AUTD event pipe");

    use std::os::unix::io::AsRawFd;
    let fifo_fd = fifo_file.as_raw_fd();

    let mut bat_level = monitor::battery::get_battery_level();
    let mut is_awake_state = monitor::display::is_awake();

    while RUNNING.load(Ordering::SeqCst) {
        if !is_awake_state {
            monitor::display::log_active_method("Screen OFF. Entering Deep Sleep Protocol.");
            utils::cmd::apply_mode("powersave");
            utils::cmd::send_toast("Deep Sleep Protocol: Active");

            while !is_awake_state && RUNNING.load(Ordering::SeqCst) {
                let mut pfd = libc::pollfd {
                    fd: fifo_fd,
                    events: libc::POLLIN,
                    revents: 0,
                };
                let ret = unsafe { libc::poll(&mut pfd, 1, -1) };
                if ret > 0 && (pfd.revents & libc::POLLIN) != 0 {
                    let mut buf = [0u8; 128];
                    if let Ok(n) = std::io::Read::read(&mut fifo_file, &mut buf) {
                        let msg = String::from_utf8_lossy(&buf[..n]);
                        for part in msg.split(|c| c == '|' || c == '\n') {
                            if part.starts_with("BAT:") {
                                bat_level = part[4..].trim().parse().unwrap_or(bat_level);
                            } else if part.starts_with("SCR:") {
                                is_awake_state = part[4..].trim() == "1";
                            }
                        }
                    }
                }
            }

            if RUNNING.load(Ordering::SeqCst) {
                monitor::display::log_active_method("Screen back ON. Resuming normal operation.");
                utils::cmd::send_toast("Resuming Normal Operation");
            }
            continue;
        }

        user_base.clear();
        if let Ok(bytes) = fs::read(config::AUTD_BASE_MODE_PATH) {
            let s = String::from_utf8_lossy(&bytes);
            let trimmed = s.trim();
            if trimmed.is_empty() {
                user_base.push_str("balance");
            } else {
                user_base.push_str(trimmed);
            }
        } else {
            user_base.push_str("balance");
        }

        let is_optimize_allowed = if let Ok(bytes) = fs::read(config::AUTD_OPT_ALLOW_PATH) {
            bytes.first() == Some(&b'1')
        } else {
            true
        };

        let is_idle_charging_enabled = if let Ok(bytes) = fs::read(config::AUTD_IDLE_CHARGING_PATH)
        {
            bytes.first() == Some(&b'1')
        } else {
            false
        };

        let ps_active = monitor::battery::is_android_powersave();

        process::game_det::load_filelist_if_changed();

        let game_check = process::game_det::find_game_process();
        let game_found = game_check.is_some();
        let hydra_supported = std::path::Path::new(config::KERNEL_HYDRA_PID_PATH).exists();
        let sched_lib_supported = std::path::Path::new(config::KERNEL_SCHED_LIB_NAME_PATH).exists();
        let user_wants_hydra = if let Ok(bytes) = fs::read(config::AUTD_HYDRA_ENABLE_PATH) {
            bytes.first() != Some(&b'0')
        } else {
            true
        };
        let hydra_enabled = hydra_supported && user_wants_hydra;

        if game_found && is_idle_charging_enabled {
            monitor::battery::enable_idle_charging();
        } else {
            monitor::battery::disable_idle_charging();
        }

        if let Some((current_game, chosen_mode, game_pid)) = game_check {
            if last_mode != chosen_mode {
                utils::cmd::apply_mode(&chosen_mode);

                msg_buffer.clear();
                let _ = write!(msg_buffer, "Game: {} (Mode: {})", current_game, chosen_mode);
                utils::cmd::send_toast(&msg_buffer);

                last_mode.clear();
                last_mode.push_str(&chosen_mode);
                idle_cycles = 0;
            }

            if game_pid > 0 {
                if is_optimize_allowed {
                    if hydra_enabled {
                        if last_hydra_pid != game_pid {
                            let _ = fs::write(config::KERNEL_HYDRA_PID_PATH, game_pid.to_string());
                            last_hydra_pid = game_pid;
                        }
                    } else {
                        if last_hydra_pid != 0 {
                            let _ = fs::write(config::KERNEL_HYDRA_PID_PATH, "0");
                            last_hydra_pid = 0;
                        }
                        process::thread_opt::optimize_game_threads(game_pid);
                    }

                    if sched_lib_supported && !sched_lib_active {
                        let _ = fs::write(config::KERNEL_SCHED_LIB_MASK_PATH, "255");
                        let _ =
                            fs::write(config::KERNEL_SCHED_LIB_NAME_PATH, config::SCHED_LIB_GAMES);
                        sched_lib_active = true;
                    }
                } else {
                    if last_hydra_pid != 0 {
                        let _ = fs::write(config::KERNEL_HYDRA_PID_PATH, "0");
                        last_hydra_pid = 0;
                    }
                    if sched_lib_supported && sched_lib_active {
                        let _ = fs::write(config::KERNEL_SCHED_LIB_MASK_PATH, "0");
                        let _ = fs::write(config::KERNEL_SCHED_LIB_NAME_PATH, " ");
                        sched_lib_active = false;
                    }
                }
            }
        } else if bat_level <= 20 || ps_active {
            if last_mode != "powersave" {
                utils::cmd::apply_mode("powersave");
                utils::cmd::send_toast("Mode: Powersave (Battery Low/System Saver)");

                last_mode.clear();
                last_mode.push_str("powersave");
                idle_cycles = 0;
            }
            if hydra_enabled && last_hydra_pid != 0 {
                let _ = fs::write(config::KERNEL_HYDRA_PID_PATH, "0");
                last_hydra_pid = 0;
            }
            if sched_lib_supported && sched_lib_active {
                let _ = fs::write(config::KERNEL_SCHED_LIB_MASK_PATH, "0");
                let _ = fs::write(config::KERNEL_SCHED_LIB_NAME_PATH, " ");
                sched_lib_active = false;
            }
        } else {
            if last_mode != user_base {
                utils::cmd::apply_mode(&user_base);

                msg_buffer.clear();
                let _ = write!(msg_buffer, "Mode: {}", user_base);
                utils::cmd::send_toast(&msg_buffer);

                last_mode.clear();
                last_mode.push_str(&user_base);
                idle_cycles = 0;
            }
            if hydra_enabled && last_hydra_pid != 0 {
                let _ = fs::write(config::KERNEL_HYDRA_PID_PATH, "0");
                last_hydra_pid = 0;
            }
            if sched_lib_supported && sched_lib_active {
                let _ = fs::write(config::KERNEL_SCHED_LIB_MASK_PATH, "0");
                let _ = fs::write(config::KERNEL_SCHED_LIB_NAME_PATH, " ");
                sched_lib_active = false;
            }
        }

        if bat_level <= 20 && !low_bat_notif_sent {
            utils::cmd::send_toast("Battery 20%! System switched to Powersave.");
            low_bat_notif_sent = true;
        } else if bat_level > 20 {
            low_bat_notif_sent = false;
        }

        let _ = fs::write(config::AUTD_STATUS_PATH, &last_mode);

        if !game_found && bat_level > 20 && !ps_active {
            idle_cycles += 1;
        } else {
            idle_cycles = 0;
        }

        let timeout_ms = if idle_cycles > 10 { 10000 } else { 3000 };
        let mut pfd = libc::pollfd {
            fd: fifo_fd,
            events: libc::POLLIN,
            revents: 0,
        };
        let ret = unsafe { libc::poll(&mut pfd, 1, timeout_ms) };
        if ret > 0 && (pfd.revents & libc::POLLIN) != 0 {
            let mut buf = [0u8; 128];
            if let Ok(n) = std::io::Read::read(&mut fifo_file, &mut buf) {
                let msg = String::from_utf8_lossy(&buf[..n]);
                for part in msg.split(|c| c == '|' || c == '\n') {
                    if part.starts_with("BAT:") {
                        bat_level = part[4..].trim().parse().unwrap_or(bat_level);
                    } else if part.starts_with("SCR:") {
                        is_awake_state = part[4..].trim() == "1";
                    }
                }
            }
        }
    }

    perform_cleanup();
}
