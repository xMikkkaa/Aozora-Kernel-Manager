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

use std::collections::{HashMap, HashSet};
use std::fs;
use std::path::Path;
use std::thread::sleep;
use std::time::Duration;

pub const MAX_PATTERNS: usize = 8;
pub const MAX_TOTAL_LEN: usize = 200;
const SAMPLE_INTERVAL: Duration = Duration::from_secs(1);

struct ThreadSample {
    tid: i32,
    comm: String,
    utime: u64,
}

fn set_value(file: &str, value: &str) {
    if Path::new(file).exists() {
        let _ = fs::write(file, value);
    }
}

fn is_junk(name: &str) -> bool {
    if name.is_empty() {
        return true;
    }
    if name.starts_with("binder") {
        return true;
    }
    if name.starts_with("pool-") {
        return true;
    }
    if name.starts_with("Firebase") {
        return true;
    }
    if name.starts_with("GoogleApi") {
        return true;
    }
    if name.starts_with("Chromium") {
        return true;
    }
    let lower = name.to_lowercase();
    if lower.contains("volley") {
        return true;
    }
    if lower.contains("chromium") {
        return true;
    }
    false
}

fn parse_utime(stat: &str) -> Option<u64> {
    let close = stat.rfind(')')?;
    stat[close + 1..]
        .split_whitespace()
        .nth(11)?
        .parse::<u64>()
        .ok()
}

fn read_comm(pid: i32, tid: i32) -> Option<String> {
    let content = fs::read_to_string(format!("/proc/{}/task/{}/comm", pid, tid)).ok()?;
    let name = content.trim().to_string();
    if name.is_empty() { None } else { Some(name) }
}

fn read_utime(pid: i32, tid: i32) -> Option<u64> {
    let content = fs::read_to_string(format!("/proc/{}/task/{}/stat", pid, tid)).ok()?;
    parse_utime(&content)
}

fn snapshot(pid: i32) -> Vec<ThreadSample> {
    let mut out = Vec::new();
    let task_dir = format!("/proc/{}/task", pid);
    let entries = match fs::read_dir(&task_dir) {
        Ok(e) => e,
        Err(_) => return out,
    };
    for entry in entries.flatten() {
        let tid: i32 = match entry.file_name().to_string_lossy().parse() {
            Ok(t) => t,
            Err(_) => continue,
        };
        if let (Some(comm), Some(utime)) = (read_comm(pid, tid), read_utime(pid, tid)) {
            out.push(ThreadSample { tid, comm, utime });
        }
    }
    out
}

fn aggregate_deltas(before: &[ThreadSample], after: &[ThreadSample]) -> Vec<(String, u64)> {
    let mut base: HashMap<i32, (String, u64)> = HashMap::with_capacity(before.len());
    for s in before {
        base.insert(s.tid, (s.comm.clone(), s.utime));
    }
    let mut per_name: HashMap<String, u64> = HashMap::new();
    for s in after {
        if let Some((_, old)) = base.get(&s.tid) {
            let delta = s.utime.saturating_sub(*old);
            if delta > 0 {
                *per_name.entry(s.comm.clone()).or_insert(0) += delta;
            }
        }
    }
    per_name.into_iter().collect()
}

fn select_patterns(mut deltas: Vec<(String, u64)>, max_n: usize, max_len: usize) -> Vec<String> {
    deltas.sort_by(|a, b| b.1.cmp(&a.1).then_with(|| a.0.cmp(&b.0)));
    let mut picked = Vec::new();
    let mut seen = HashSet::new();
    let mut total = 0usize;
    for (name, _) in deltas {
        if picked.len() >= max_n {
            break;
        }
        if is_junk(&name) || !seen.insert(name.clone()) {
            continue;
        }
        let add = name.len() + if picked.is_empty() { 0 } else { 1 };
        if total + add > max_len {
            continue;
        }
        total += add;
        picked.push(name);
    }
    picked
}

fn collect_hot_names(pid: i32) -> Vec<String> {
    let before = snapshot(pid);
    if before.is_empty() {
        return Vec::new();
    }
    sleep(SAMPLE_INTERVAL);
    let after = snapshot(pid);
    if after.is_empty() {
        return Vec::new();
    }
    select_patterns(
        aggregate_deltas(&before, &after),
        MAX_PATTERNS,
        MAX_TOTAL_LEN,
    )
}

fn write_hydra_pid(pid: i32) {
    set_value(crate::config::KERNEL_HYDRA_PID_PATH, "0");
    set_value(crate::config::KERNEL_HYDRA_PID_PATH, &pid.to_string());
}

pub fn feed_hydra_patterns(pid: i32) {
    if pid <= 0 {
        return;
    }
    if !Path::new(crate::config::KERNEL_HYDRA_PATTERNS_PATH).exists() {
        set_value(crate::config::KERNEL_HYDRA_PID_PATH, &pid.to_string());
        return;
    }
    let names = collect_hot_names(pid);
    if names.is_empty() {
        set_value(crate::config::KERNEL_HYDRA_PID_PATH, &pid.to_string());
        return;
    }
    set_value(crate::config::KERNEL_HYDRA_PATTERNS_PATH, &names.join(","));
    write_hydra_pid(pid);
}

pub fn clear_hydra_patterns() {
    set_value(crate::config::KERNEL_HYDRA_PATTERNS_PATH, "");
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn parses_utime_field() {
        let stat = "1234 (mythread) R 1 2 3 4 5 6 7 8 9 10 42 17 0 0 0 0 0";
        assert_eq!(parse_utime(stat), Some(42));
    }

    #[test]
    fn parses_utime_with_parens_in_comm() {
        let stat = "1234 (my (weird) thread) R 1 2 3 4 5 6 7 8 9 10 99 0 0 0 0 0 0";
        assert_eq!(parse_utime(stat), Some(99));
    }

    #[test]
    fn rejects_malformed_stat() {
        assert_eq!(parse_utime(""), None);
        assert_eq!(parse_utime("1234 nomarkers here"), None);
        assert_eq!(parse_utime("1234 (x) R 1 2"), None);
    }

    #[test]
    fn filters_junk_threads() {
        for junk in [
            "",
            "binder:1234_5",
            "binderTransaction",
            "pool-3-thread-1",
            "FirebaseWorker",
            "GoogleApiHandler",
            "ChromiumNet",
            "chromium-helper",
            "OkHttp-volley-0",
            "VolleyCache",
        ] {
            assert!(is_junk(junk), "{junk} should be junk");
        }
    }

    #[test]
    fn keeps_game_threads() {
        for legit in [
            "UEGameThread",
            "ThreadPoolForeg",
            "hwuiTask",
            "AudioTrack",
            "RenderThread",
            "UnityGfxDeviceW",
        ] {
            assert!(!is_junk(legit), "{legit} should be kept");
        }
    }

    fn sample(tid: i32, comm: &str, utime: u64) -> ThreadSample {
        ThreadSample {
            tid,
            comm: comm.to_string(),
            utime,
        }
    }

    #[test]
    fn aggregates_delta_per_comm_name() {
        let before = vec![
            sample(1, "UEGameThread", 100),
            sample(2, "UEGameThread", 100),
            sample(3, "binder:1_2", 500),
            sample(4, "IdleThread", 50),
        ];
        let after = vec![
            sample(1, "UEGameThread", 160),
            sample(2, "UEGameThread", 140),
            sample(3, "binder:1_2", 900),
            sample(4, "IdleThread", 50),
        ];
        let mut deltas = aggregate_deltas(&before, &after);
        deltas.sort_by(|a, b| a.0.cmp(&b.0));
        assert_eq!(
            deltas,
            vec![
                ("UEGameThread".to_string(), 100),
                ("binder:1_2".to_string(), 400),
            ]
        );
    }

    #[test]
    fn selects_hottest_non_junk_first() {
        let deltas = vec![
            ("binder:1_2".to_string(), 900),
            ("UEGameThread".to_string(), 100),
            ("AudioTrack".to_string(), 60),
        ];
        let picked = select_patterns(deltas, MAX_PATTERNS, MAX_TOTAL_LEN);
        assert_eq!(picked, vec!["UEGameThread", "AudioTrack"]);
    }

    #[test]
    fn enforces_count_and_budget_limits() {
        let deltas: Vec<(String, u64)> = (0..20)
            .map(|i| (format!("WorkerThread{:02}", i), 100 - i as u64))
            .collect();
        let picked = select_patterns(deltas, MAX_PATTERNS, MAX_TOTAL_LEN);
        assert_eq!(picked.len(), MAX_PATTERNS);
        let joined = picked.join(",");
        assert!(joined.len() <= MAX_TOTAL_LEN);

        let big: Vec<(String, u64)> = vec![
            ("a".repeat(195), 10),
            ("Short".to_string(), 9),
            ("Tiny".to_string(), 8),
        ];
        let picked = select_patterns(big, MAX_PATTERNS, MAX_TOTAL_LEN);
        assert_eq!(picked, vec!["a".repeat(195), "Tiny".to_string()]);
    }

    #[test]
    fn skips_duplicates_and_empty() {
        let deltas = vec![
            ("UEGameThread".to_string(), 50),
            ("UEGameThread".to_string(), 40),
            ("hwuiTask".to_string(), 30),
        ];
        let picked = select_patterns(deltas, MAX_PATTERNS, MAX_TOTAL_LEN);
        assert_eq!(picked, vec!["UEGameThread", "hwuiTask"]);
        let empty: Vec<(String, u64)> = Vec::new();
        assert!(select_patterns(empty, MAX_PATTERNS, MAX_TOTAL_LEN).is_empty());
    }
}
