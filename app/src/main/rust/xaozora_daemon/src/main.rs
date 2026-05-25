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

mod config;
mod monitor;
mod process;
mod utils;

use std::fmt::Write;
use std::fs;
use std::process::Command;
use std::sync::atomic::{AtomicBool, Ordering};
use std::thread::{self, sleep};
use std::time::Duration;
use std::env;

pub static RUNNING: AtomicBool = AtomicBool::new(true);

extern "C" fn signal_handler(_sig: libc::c_int) {
    RUNNING.store(false, Ordering::SeqCst);
}

fn is_boot_completed() -> bool {
    if let Ok(output) = Command::new("getprop").arg("sys.boot_completed").output() {
        return output.stdout.starts_with(b"1");
    }
    false
}

fn perform_cleanup() {
    let _ = fs::remove_file(config::AUTD_STATUS_PATH);
    let _ = fs::remove_file(config::AUTD_AWAKE_DEBUG_LOG);
    let _ = fs::remove_file(config::AUTD_BASE_MODE_PATH);
    let _ = fs::remove_file(config::AUTD_PS_STATE_PATH);
    monitor::battery::disable_idle_charging();
}

fn run_autd() {
    config::setup_android_env();
    config::ensure_app_dir();

    let mut last_mode = String::with_capacity(64);
    let mut user_base = String::with_capacity(64);
    user_base.push_str("balance");
    let mut msg_buffer = String::with_capacity(300);

    let mut low_bat_notif_sent = false;
    let mut idle_cycles = 0;

    while !is_boot_completed() {
        sleep(Duration::from_secs(1));
    }

    sleep(Duration::from_secs(5));
    
    monitor::battery::init_backup_once();
    monitor::battery::reset_charging_states();

    utils::cmd::send_toast("Automation Daemon Started");

    while RUNNING.load(Ordering::SeqCst) {
        if !monitor::display::is_awake() {
            monitor::display::log_active_method("Screen OFF. Entering Deep Sleep Protocol.");
            utils::cmd::apply_mode("powersave");
            utils::cmd::send_toast("Deep Sleep Protocol: Active");

            while !monitor::display::is_awake() && RUNNING.load(Ordering::SeqCst) {
                sleep(Duration::from_secs(3));
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

        let is_idle_charging_enabled = if let Ok(bytes) = fs::read(config::AUTD_IDLE_CHARGING_PATH) {
            bytes.first() == Some(&b'1')
        } else {
            false
        };

        let bat_level = monitor::battery::get_battery_level();
        let ps_active = monitor::battery::is_android_powersave();

        process::game_det::load_filelist_if_changed();

        let game_check = process::game_det::find_game_process();
        let game_found = game_check.is_some();

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
                    process::thread_opt::optimize_game_threads(game_pid);
                } else {
                    process::thread_opt::clear_optimized_set();
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
            process::thread_opt::clear_optimized_set();
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
            process::thread_opt::clear_optimized_set();
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
            sleep(Duration::from_secs(if idle_cycles > 10 { 10 } else { 3 }));
        } else {
            idle_cycles = 0;
            sleep(Duration::from_secs(3));
        }
    }

    perform_cleanup();
    utils::cmd::send_toast("🛑 xAozora Daemon Stopped");
}

fn main() {
    unsafe {
        libc::signal(libc::SIGTERM, signal_handler as *const () as libc::sighandler_t);
        libc::signal(libc::SIGINT, signal_handler as *const () as libc::sighandler_t);
    }

    let args: Vec<String> = env::args().collect();
    let mut enable_autd = false;
    let mut reset_stats = false;
    let mut logger_path = None;

    let mut i = 1;
    while i < args.len() {
        match args[i].as_str() {
            "--enable-autd" => enable_autd = true,
            "--reset-stats" => reset_stats = true,
            "--battery-logger" => {
                if i + 1 < args.len() {
                    logger_path = Some(args[i + 1].clone());
                    i += 1;
                }
            }
            _ => {}
        }
        i += 1;
    }

    // PID File Logic to ensure strictly ONE instance of this binary
    let pid_path = config::AUTD_DIR.to_owned() + "/xaozora_daemon.pid";
    config::ensure_app_dir();

    if let Ok(existing_pid_str) = fs::read_to_string(&pid_path) {
        if let Ok(pid) = existing_pid_str.trim().parse::<i32>() {
            // Check if process actually exists
            let status = Command::new("kill").arg("-0").arg(pid.to_string()).status();
            if status.is_ok() && status.unwrap().success() {
                // If it exists, kill it to ensure the NEW configuration takes over
                let _ = Command::new("kill").arg("-9").arg(pid.to_string()).status();
                sleep(Duration::from_millis(200));
            }
        }
    }
    // Write current PID
    let _ = fs::write(&pid_path, std::process::id().to_string());

    let mut handles = vec![];

    if let Some(path) = &logger_path {
        let path_clone = path.clone();
        handles.push(thread::spawn(move || {
            utils::logger::run_logger(path_clone);
        }));
    }

    if enable_autd {
        handles.push(thread::spawn(|| {
            run_autd();
        }));
    } else {
        // If AUTD is explicitly disabled, ensure cleanup is done
        perform_cleanup();
    }

    if handles.is_empty() && !reset_stats {
        println!("Usage: xaozora_daemon [--enable-autd] [--reset-stats] [--battery-logger <output_json_path>]");
        let _ = fs::remove_file(&pid_path);
        std::process::exit(1);
    }

    if reset_stats {
        config::ensure_battmon_dir();
        if let Some(path) = &logger_path {
            let _ = fs::remove_file(path);
        }
        let stats_path = config::BATTMON_DIR.to_owned() + "/battery_stats.json";
        let _ = fs::write(&stats_path, "{}");
        println!("Battery stats and logs reset.");

        if !enable_autd && logger_path.is_none() {
            let _ = fs::remove_file(&pid_path);
            return;
        }
    }

    for handle in handles {
        let _ = handle.join();
    }

    let _ = fs::remove_file(&pid_path);
}
