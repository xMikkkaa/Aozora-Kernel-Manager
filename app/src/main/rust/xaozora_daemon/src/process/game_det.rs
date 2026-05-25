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
use std::io::{Cursor, Write};
use std::sync::Mutex;
use std::time::SystemTime;

#[derive(Clone)]
pub struct FileEntry {
    pub base: Vec<u8>,
    pub chosen_mode: String,
}

static FILE_ENTRIES: Mutex<(Option<SystemTime>, Vec<FileEntry>)> = Mutex::new((None, Vec::new()));

pub fn load_filelist_if_changed() {
    let mtime = match fs::metadata(crate::config::FILELIST_PATH).and_then(|m| m.modified()) {
        Ok(t) => t,
        Err(_) => {
            if let Ok(mut cache) = FILE_ENTRIES.lock() {
                cache.0 = None;
                cache.1.clear();
            }
            return;
        }
    };

    let mut cache = match FILE_ENTRIES.lock() {
        Ok(c) => c,
        Err(_) => return,
    };

    if let Some(cached_time) = cache.0 {
        if cached_time == mtime && !cache.1.is_empty() {
            return;
        }
    }

    let content = match fs::read_to_string(crate::config::FILELIST_PATH) {
        Ok(c) => c,
        Err(_) => {
            cache.0 = None;
            cache.1.clear();
            return;
        }
    };

    let mut new_entries = Vec::with_capacity(16);

    for line in content.lines() {
        let line = line.trim();
        if line.is_empty() || line.starts_with('#') {
            continue;
        }

        let (base_name, mode) = if line.ends_with("_g2") {
            (&line[..line.len() - 3], "gaming2")
        } else if line.ends_with("_g") {
            (&line[..line.len() - 2], "gaming")
        } else if line.ends_with("_p") {
            (&line[..line.len() - 2], "performance")
        } else {
            (line, "performance")
        };

        new_entries.push(FileEntry {
            base: base_name.as_bytes().to_vec(),
            chosen_mode: mode.to_string(),
        });
    }

    cache.0 = Some(mtime);
    cache.1 = new_entries;
}

pub fn find_game_process() -> Option<(String, String, i32)> {
    let paths = [
        "/dev/cpuset/top-app/cgroup.procs",
        "/dev/stune/top-app/cgroup.procs",
    ];

    let cache = match FILE_ENTRIES.lock() {
        Ok(c) => c,
        Err(_) => return None,
    };

    if cache.1.is_empty() {
        return None;
    }

    for path in paths {
        if let Ok(bytes) = fs::read(path) {
            let mut current_pid = 0;
            let mut parsing_pid = false;

            for &b in bytes.iter().chain(std::iter::once(&b'\n')) {
                if b.is_ascii_digit() {
                    current_pid = current_pid * 10 + (b - b'0') as i32;
                    parsing_pid = true;
                } else if parsing_pid {
                    let pid = current_pid;
                    current_pid = 0;
                    parsing_pid = false;

                    let mut path_buf = [0u8; 64];
                    let mut cursor = Cursor::new(&mut path_buf[..]);
                    let _ = write!(cursor, "/proc/{}/cmdline", pid);
                    let len = cursor.position() as usize;

                    if let Ok(path_str) = std::str::from_utf8(&path_buf[..len]) {
                        if let Ok(cmdline_bytes) = fs::read(path_str) {
                            if cmdline_bytes.is_empty() || cmdline_bytes[0] == 0 {
                                continue;
                            }

                            let null_pos = cmdline_bytes.iter().position(|&x| x == 0).unwrap_or(cmdline_bytes.len());
                            let mut name_slice = &cmdline_bytes[..null_pos];

                            if let Some(slash_pos) = name_slice.iter().rposition(|&x| x == b'/') {
                                if slash_pos + 1 < name_slice.len() {
                                    name_slice = &name_slice[slash_pos + 1..];
                                }
                            }

                            for entry in &cache.1 {
                                let base_len = entry.base.len();
                                if name_slice.starts_with(&entry.base) {
                                    if name_slice.len() == base_len || name_slice[base_len] == b':' {
                                        return Some((
                                            String::from_utf8_lossy(&entry.base).into_owned(),
                                            entry.chosen_mode.clone(),
                                            pid,
                                        ));
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    None
}