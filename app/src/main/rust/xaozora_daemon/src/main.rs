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

mod autd;
mod config;
mod ipc;
mod monitor;
mod process;
mod utils;

use std::env;
use std::fs;
use std::sync::atomic::{AtomicBool, Ordering};
use std::thread::{self, sleep};
use std::time::Duration;

pub static RUNNING: AtomicBool = AtomicBool::new(true);

extern "C" fn signal_handler(_sig: libc::c_int) {
    RUNNING.store(false, Ordering::SeqCst);
}

fn main() {
    config::setup_android_env();

    unsafe {
        libc::signal(
            libc::SIGTERM,
            signal_handler as *const () as libc::sighandler_t,
        );
        libc::signal(
            libc::SIGINT,
            signal_handler as *const () as libc::sighandler_t,
        );
    }

    let args: Vec<String> = env::args().collect();
    let mut enable_autd = false;
    let mut disable_autd = false;
    let mut reset_stats = false;
    let mut logger_path = None;

    let mut i = 1;
    while i < args.len() {
        match args[i].as_str() {
            "--enable-autd" => enable_autd = true,
            "--disable-autd" => disable_autd = true,
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

    let pid_path = config::AUTD_DIR.to_owned() + "/xaozora_daemon.pid";
    config::ensure_app_dir();

    if let Ok(existing_pid_str) = fs::read_to_string(&pid_path) {
        if let Ok(pid) = existing_pid_str.trim().parse::<i32>() {
            unsafe {
                if pid != std::process::id() as i32 && libc::kill(pid, 0) == 0 {
                    libc::kill(pid, 15);
                    sleep(Duration::from_millis(500));
                }
            }
        }
    }
    let _ = fs::write(&pid_path, std::process::id().to_string());

    let mut handles = vec![];

    if let Some(path) = &logger_path {
        let path_clone = path.clone();
        handles.push(thread::spawn(move || {
            utils::logger::run_logger(path_clone);
        }));
    }

    handles.push(thread::spawn(|| {
        ipc::start_ipc_server();
    }));

    if enable_autd {
        handles.push(thread::spawn(|| {
            autd::run_autd();
        }));
    } else if disable_autd {
        autd::perform_cleanup();
        if !reset_stats {
            utils::cmd::send_toast("xAozora Daemon (AUTD) Stopped");
        }
    }

    if handles.is_empty() && !reset_stats {
        println!(
            "Usage: xaozora_daemon [--enable-autd | --disable-autd] [--reset-stats] [--battery-logger <output_json_path>]"
        );
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
