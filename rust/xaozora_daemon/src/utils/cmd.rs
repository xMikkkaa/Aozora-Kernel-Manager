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
use std::process::{Command, Stdio};

use crate::config;

fn spawn_reaped(command: &mut Command) {
    if let Ok(mut child) = command.spawn() {
        std::thread::spawn(move || {
            let _ = child.wait();
        });
    }
}

fn queue_toast(msg: &str) {
    let msg = msg.replace(['\n', '\r'], " ");
    let mut lines: Vec<String> = fs::read_to_string(config::AUTD_TOAST_QUEUE_PATH)
        .unwrap_or_default()
        .lines()
        .filter(|line| !line.trim().is_empty())
        .map(String::from)
        .collect();
    lines.push(msg);
    if lines.len() > 3 {
        lines.drain(0..lines.len() - 3);
    }
    let _ = fs::write(config::AUTD_TOAST_QUEUE_PATH, lines.join("\n") + "\n");
}

pub fn send_toast(msg: &str) {
    queue_toast(msg);
    let mut command = Command::new("am");
    command
        .args([
            "broadcast",
            "-a",
            "com.xaozora.manager.NOTIFY",
            "-n",
            "com.xaozora.manager/.services.ToastReceiver",
            "--user",
            "0",
            "--include-stopped-packages",
            "--receiver-foreground",
            "--es",
            "message",
            msg,
        ])
        .stdout(Stdio::null())
        .stderr(Stdio::null());
    spawn_reaped(&mut command);
}

pub fn apply_mode(mode: &str) {
    let helper_path = format!("/system/bin/{}", mode);
    if std::path::Path::new(&helper_path).exists() {
        let mut command = Command::new(mode);
        command.stdout(Stdio::null()).stderr(Stdio::null());
        spawn_reaped(&mut command);
    } else {
        xaozora_rupture::apply_profile(mode);
    }
}
