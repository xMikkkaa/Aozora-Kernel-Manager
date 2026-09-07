use std::fs::{self, OpenOptions};
use std::io::Write;
use std::os::unix::fs::PermissionsExt;
use std::thread;
use std::time::{Duration, SystemTime, UNIX_EPOCH};

pub fn run_logger(output_path: String) {
    println!("Aozora Battery Logger started. Logging to {}", output_path);

    crate::config::ensure_battmon_dir();

    while crate::RUNNING.load(std::sync::atomic::Ordering::SeqCst) {
        let timestamp = SystemTime::now()
            .duration_since(UNIX_EPOCH)
            .unwrap()
            .as_secs();

        let capacity = read_file_trim("/sys/class/power_supply/battery/capacity")
            .or_else(|| read_file_trim("/sys/class/power_supply/bms/capacity"))
            .unwrap_or_else(|| "0".to_string());

        let status = read_file_trim("/sys/class/power_supply/battery/status")
            .or_else(|| read_file_trim("/sys/class/power_supply/bms/status"))
            .unwrap_or_else(|| "Unknown".to_string());

        let charge_full = read_file_trim("/sys/class/power_supply/bms/charge_full")
            .or_else(|| read_file_trim("/sys/class/power_supply/battery/charge_full"))
            .unwrap_or_else(|| "0".to_string());

        let current_now = read_file_trim("/sys/class/power_supply/battery/current_now")
            .or_else(|| read_file_trim("/sys/class/power_supply/bms/current_now"))
            .unwrap_or_else(|| "0".to_string());

        let log_entry = format!(
            "{{\"timestamp\": {}, \"capacity\": {}, \"status\": \"{}\", \"charge_full\": {}, \"current_now\": {}}}\n",
            timestamp, capacity, status, charge_full, current_now
        );

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

        if let Ok(metadata) = fs::metadata(&stats_path) {
            let mut perms = metadata.permissions();
            perms.set_mode(0o666);
            let _ = fs::set_permissions(&stats_path, perms);
        }

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
