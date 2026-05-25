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

use std::collections::HashSet;
use std::fs;
use std::io::{Cursor, Write};
use std::sync::Mutex;

static OPTIMIZED_SET: Mutex<Option<HashSet<i32>>> = Mutex::new(None);

pub fn clear_optimized_set() {
    if let Ok(mut set_opt) = OPTIMIZED_SET.lock() {
        if let Some(set) = set_opt.as_mut() {
            set.clear();
        }
    }
}

pub fn optimize_game_threads(pid: i32) {
    let mut path_buf = [0u8; 64];
    let mut cursor = Cursor::new(&mut path_buf[..]);
    let _ = write!(cursor, "/proc/{}/task/", pid);
    let len = cursor.position() as usize;

    if let Ok(path_str) = std::str::from_utf8(&path_buf[..len]) {
        if let Ok(entries) = fs::read_dir(path_str) {
            for entry in entries.flatten() {
                if let Some(name) = entry.file_name().to_str() {
                    match name.as_bytes().first() {
                        Some(b) if b.is_ascii_digit() => {}
                        _ => continue,
                    }

                    if let Ok(tid) = name.parse::<i32>() {
                        let already_optimized = if let Ok(mut set_opt) = OPTIMIZED_SET.lock() {
                            if set_opt.is_none() {
                                *set_opt = Some(HashSet::with_capacity(128));
                            }
                            if let Some(set) = set_opt.as_ref() {
                                set.contains(&tid)
                            } else {
                                false
                            }
                        } else {
                            false
                        };

                        if already_optimized {
                            continue;
                        }

                        let mut mask: libc::cpu_set_t = unsafe { std::mem::zeroed() };
                        for i in 0usize..32usize {
                            unsafe { libc::CPU_SET(i, &mut mask) };
                        }

                        let res = unsafe { libc::sched_setaffinity(tid, std::mem::size_of::<libc::cpu_set_t>(), &mask) };

                        if res == 0 {
                            if let Ok(mut set_opt) = OPTIMIZED_SET.lock() {
                                if let Some(set) = set_opt.as_mut() {
                                    set.insert(tid);
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}