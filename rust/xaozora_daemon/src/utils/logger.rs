use std::fs::{self, OpenOptions};
use std::io::Write;
use std::os::unix::fs::PermissionsExt;
use std::thread;
use std::time::{Duration, SystemTime, UNIX_EPOCH};

const MAX_LOG_BYTES: u64 = 2 * 1024 * 1024;

fn rotate_if_needed(path: &str) {
    let Ok(metadata) = fs::metadata(path) else {
        return;
    };
    if metadata.len() < MAX_LOG_BYTES {
        return;
    }

    let rotated = format!("{}.1", path);
    let _ = fs::remove_file(&rotated);
    let _ = fs::rename(path, &rotated);
}

fn secure_stats_file(path: &str) {
    if let Some((uid, gid)) = crate::config::app_owner_ids()
        && let Ok(c_path) = std::ffi::CString::new(path)
    {
        unsafe {
            libc::chown(c_path.as_ptr(), uid, gid);
        }
    }

    if let Ok(metadata) = fs::metadata(path) {
        let mut perms = metadata.permissions();
        perms.set_mode(0o660);
        let _ = fs::set_permissions(path, perms);
    }
}

pub fn run_logger(output_path: String) {
    println!("Aozora Battery Logger started. Logging to {}", output_path);

    crate::config::ensure_battmon_dir();

    while crate::RUNNING.load(std::sync::atomic::Ordering::SeqCst) {
        let timestamp = SystemTime::now()
            .duration_since(UNIX_EPOCH)
            .map(|duration| duration.as_secs())
            .unwrap_or(0);

        let capacity = read_file_trim("/sys/class/power_supply/battery/capacity")
            .or_else(|| read_file_trim("/sys/class/power_supply/bms/capacity"))
            .and_then(|value| value.parse::<i64>().ok())
            .unwrap_or(0);

        let status = read_file_trim("/sys/class/power_supply/battery/status")
            .or_else(|| read_file_trim("/sys/class/power_supply/bms/status"))
            .unwrap_or_else(|| "Unknown".to_string());

        let charge_full = read_file_trim("/sys/class/power_supply/bms/charge_full")
            .or_else(|| read_file_trim("/sys/class/power_supply/battery/charge_full"))
            .and_then(|value| value.parse::<i64>().ok())
            .unwrap_or(0);

        let current_now = read_file_trim("/sys/class/power_supply/battery/current_now")
            .or_else(|| read_file_trim("/sys/class/power_supply/bms/current_now"))
            .and_then(|value| value.parse::<i64>().ok())
            .unwrap_or(0);

        let mut log_entry = serde_json::json!({
            "timestamp": timestamp,
            "capacity": capacity,
            "status": status,
            "charge_full": charge_full,
            "current_now": current_now,
        })
        .to_string();
        log_entry.push('\n');

        rotate_if_needed(&output_path);

        if let Ok(mut file) = OpenOptions::new()
            .create(true)
            .append(true)
            .open(&output_path)
        {
            let _ = file.write_all(log_entry.as_bytes());
        }

        let advanced_stats = crate::monitor::battery::fetch_and_parse_stats();
        let advanced_json =
            serde_json::to_string_pretty(&advanced_stats).unwrap_or_else(|_| "{}".to_string());

        let stats_path = crate::config::BATTMON_DIR.to_owned() + "/battery_stats.json";
        if let Ok(mut file) = OpenOptions::new()
            .create(true)
            .write(true)
            .truncate(true)
            .open(&stats_path)
        {
            let _ = file.write_all(advanced_json.as_bytes());
        }

        secure_stats_file(&stats_path);

        for _ in 0..60 {
            if !crate::RUNNING.load(std::sync::atomic::Ordering::SeqCst) {
                break;
            }
            thread::sleep(Duration::from_secs(1));
        }
    }
}

fn read_file_trim(path: &str) -> Option<String> {
    fs::read_to_string(path).map(|s| s.trim().to_string()).ok()
}
