use jni::objects::{JClass, JString};
use jni::sys::jstring;
use jni::{errors::ThrowRuntimeExAndDefault, EnvUnowned};
use serde::{Deserialize, Serialize};

use crate::utils::shell::{
    check_file_exists, execute_cmd, execute_cmd_and_get_output, read_system_file,
};

const LEGACY_BASE: &str = "/sys/kernel/gpu";
const LEGACY_DEVFREQ: &str = "/sys/class/kgsl/kgsl-3d0/devfreq";

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum GpuKind {
    SysKernelGpu,
    KgslAdreno,
    MaliPlatform,
    MaliDevfreq,
    GpuDevfreq,
    Tegra,
}

impl GpuKind {
    pub fn as_str(self) -> &'static str {
        match self {
            GpuKind::SysKernelGpu => "sys_kernel_gpu",
            GpuKind::KgslAdreno => "kgsl_adreno",
            GpuKind::MaliPlatform => "mali_platform",
            GpuKind::MaliDevfreq => "mali_devfreq",
            GpuKind::GpuDevfreq => "gpu_devfreq",
            GpuKind::Tegra => "tegra",
        }
    }
}

pub struct GpuBackend {
    pub kind: GpuKind,
    pub freq_table: String,
    pub set_min: String,
    pub set_max: String,
    pub governor: Option<String>,
}

fn scan_platform_mali() -> Option<String> {
    let out =
        execute_cmd_and_get_output("ls -d /sys/devices/platform/*mali* 2>/dev/null | head -1");
    let p = out.trim();
    if p.is_empty() {
        None
    } else {
        Some(p.to_string())
    }
}

fn scan_devfreq(pattern: &str) -> Option<String> {
    let out = execute_cmd_and_get_output(&format!(
        "ls /sys/class/devfreq 2>/dev/null | grep -m1 '{}'",
        pattern
    ));
    let name = out.trim();
    if name.is_empty() {
        None
    } else {
        Some(name.to_string())
    }
}

pub fn detect_gpu_backend() -> Option<GpuBackend> {
    if check_file_exists(&format!("{}/gpu_freq_table", LEGACY_BASE)) {
        return Some(GpuBackend {
            kind: GpuKind::SysKernelGpu,
            freq_table: format!("{}/gpu_freq_table", LEGACY_BASE),
            set_min: format!("{}/gpu_min_clock", LEGACY_BASE),
            set_max: format!("{}/gpu_max_clock", LEGACY_BASE),
            governor: Some(format!("{}/gpu_governor", LEGACY_BASE)),
        });
    }
    if check_file_exists(&format!("{}/gpu_available_frequencies", LEGACY_BASE)) {
        return Some(GpuBackend {
            kind: GpuKind::SysKernelGpu,
            freq_table: format!("{}/gpu_available_frequencies", LEGACY_BASE),
            set_min: format!("{}/gpu_min_clock", LEGACY_BASE),
            set_max: format!("{}/gpu_max_clock", LEGACY_BASE),
            governor: Some(format!("{}/gpu_governor", LEGACY_BASE)),
        });
    }
    if check_file_exists(&format!("{}/available_frequencies", LEGACY_DEVFREQ)) {
        return Some(GpuBackend {
            kind: GpuKind::KgslAdreno,
            freq_table: format!("{}/available_frequencies", LEGACY_DEVFREQ),
            set_min: format!("{}/min_freq", LEGACY_DEVFREQ),
            set_max: format!("{}/max_freq", LEGACY_DEVFREQ),
            governor: Some(format!("{}/governor", LEGACY_DEVFREQ)),
        });
    }
    if let Some(mali) = scan_platform_mali() {
        let avail = format!("{}/available_frequencies", mali);
        if check_file_exists(&avail) {
            return Some(GpuBackend {
                kind: GpuKind::MaliPlatform,
                freq_table: avail,
                set_min: format!("{}/scaling_min_freq", mali),
                set_max: format!("{}/scaling_max_freq", mali),
                governor: None,
            });
        }
    }
    if let Some(node) = scan_devfreq("mali") {
        let base = format!("/sys/class/devfreq/{}", node);
        let avail = format!("{}/available_frequencies", base);
        if check_file_exists(&avail) {
            return Some(GpuBackend {
                kind: GpuKind::MaliDevfreq,
                freq_table: avail,
                set_min: format!("{}/min_freq", base),
                set_max: format!("{}/max_freq", base),
                governor: Some(format!("{}/governor", base)),
            });
        }
    }
    if let Some(node) = scan_devfreq("gpu") {
        let base = format!("/sys/class/devfreq/{}", node);
        let avail = format!("{}/available_frequencies", base);
        if check_file_exists(&avail) {
            return Some(GpuBackend {
                kind: GpuKind::GpuDevfreq,
                freq_table: avail,
                set_min: format!("{}/min_freq", base),
                set_max: format!("{}/max_freq", base),
                governor: Some(format!("{}/governor", base)),
            });
        }
    }
    if check_file_exists("/sys/kernel/tegra_gpu/available_frequencies") {
        return Some(GpuBackend {
            kind: GpuKind::Tegra,
            freq_table: "/sys/kernel/tegra_gpu/available_frequencies".to_string(),
            set_min: "/sys/kernel/tegra_gpu/gpu_floor_rate".to_string(),
            set_max: "/sys/kernel/tegra_gpu/gpu_cap_rate".to_string(),
            governor: None,
        });
    }
    None
}

fn read_freq_list(path: &str) -> Vec<String> {
    read_system_file(path)
        .split_whitespace()
        .filter(|s| !s.is_empty())
        .map(String::from)
        .collect()
}

fn write_locked(path: &str, value: &str) {
    execute_cmd(&format!(
        "chmod 644 {path}; echo {value} > {path}; chmod 444 {path}",
        path = path,
        value = value
    ));
}

#[derive(Serialize, Deserialize)]
pub struct GpuConfig {
    pub available_freqs: Vec<String>,
    pub available_governors: Vec<String>,
    pub min_freq: String,
    pub max_freq: String,
    pub governor: String,
    pub adreno_boost_supported: bool,
    pub adreno_boost: String,
}

pub fn get_gpu_config() -> GpuConfig {
    let backend = detect_gpu_backend();

    let (freq_path, min_path, max_path, gov_path) = match &backend {
        Some(b) => (
            b.freq_table.clone(),
            b.set_min.clone(),
            b.set_max.clone(),
            b.governor.clone(),
        ),
        None => (
            format!("{}/gpu_freq_table", LEGACY_BASE),
            format!("{}/gpu_min_clock", LEGACY_BASE),
            format!("{}/gpu_max_clock", LEGACY_BASE),
            Some(format!("{}/gpu_governor", LEGACY_BASE)),
        ),
    };

    let mut available_freqs = read_freq_list(&freq_path);

    let available_governors = gov_path
        .as_ref()
        .map(|p| read_freq_list(p))
        .unwrap_or_default();

    let min_freq = read_system_file(&min_path);
    let max_freq = read_system_file(&max_path);
    let governor = gov_path
        .as_ref()
        .map(|p| read_system_file(p))
        .unwrap_or_default();

    if !min_freq.is_empty() && !available_freqs.contains(&min_freq) {
        available_freqs.push(min_freq.clone());
    }
    if !max_freq.is_empty() && !available_freqs.contains(&max_freq) {
        available_freqs.push(max_freq.clone());
    }
    available_freqs.sort_by_key(|f| f.parse::<i64>().unwrap_or(0));

    let adreno_boost_supported = check_file_exists(&format!("{}/adrenoboost", LEGACY_DEVFREQ));
    let adreno_boost = if adreno_boost_supported {
        read_system_file(&format!("{}/adrenoboost", LEGACY_DEVFREQ))
    } else {
        "0".to_string()
    };

    GpuConfig {
        available_freqs,
        available_governors,
        min_freq,
        max_freq,
        governor,
        adreno_boost_supported,
        adreno_boost,
    }
}

pub fn apply_gpu_config(
    min_freq: &str,
    max_freq: &str,
    governor: &str,
    adreno_boost: Option<&str>,
) {
    let backend = detect_gpu_backend();

    let (min_path, max_path, gov_path) = match &backend {
        Some(b) => (b.set_min.clone(), b.set_max.clone(), b.governor.clone()),
        None => (
            format!("{}/gpu_min_clock", LEGACY_BASE),
            format!("{}/gpu_max_clock", LEGACY_BASE),
            Some(format!("{}/gpu_governor", LEGACY_BASE)),
        ),
    };

    if !governor.is_empty() {
        if let Some(gov) = &gov_path {
            if check_file_exists(gov) {
                write_locked(gov, governor);
            }
        }
    }

    if !max_freq.is_empty() && check_file_exists(&max_path) {
        write_locked(&max_path, max_freq);
    }
    if !min_freq.is_empty() && check_file_exists(&min_path) {
        write_locked(&min_path, min_freq);
    }

    if let Some(boost) = adreno_boost {
        if check_file_exists(&format!("{}/adrenoboost", LEGACY_DEVFREQ)) {
            execute_cmd(&format!(
                "chmod 644 {}/adrenoboost; echo {} > {}/adrenoboost; chmod 444 {}/adrenoboost",
                LEGACY_DEVFREQ, boost, LEGACY_DEVFREQ, LEGACY_DEVFREQ
            ));
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn kind_str_mapping() {
        assert_eq!(GpuKind::SysKernelGpu.as_str(), "sys_kernel_gpu");
        assert_eq!(GpuKind::KgslAdreno.as_str(), "kgsl_adreno");
        assert_eq!(GpuKind::MaliPlatform.as_str(), "mali_platform");
        assert_eq!(GpuKind::MaliDevfreq.as_str(), "mali_devfreq");
        assert_eq!(GpuKind::GpuDevfreq.as_str(), "gpu_devfreq");
        assert_eq!(GpuKind::Tegra.as_str(), "tegra");
    }

    #[test]
    fn parse_freq_list_mixed_whitespace() {
        let raw = "300000 400000\n  500000\n\n600000";
        let parsed: Vec<String> = raw
            .split_whitespace()
            .filter(|s| !s.is_empty())
            .map(String::from)
            .collect();
        assert_eq!(parsed, vec!["300000", "400000", "500000", "600000"]);
    }

    #[test]
    fn sort_by_numeric_key() {
        let mut freqs = vec![
            "900000".to_string(),
            "300000".to_string(),
            "1200000".to_string(),
        ];
        freqs.sort_by_key(|f| f.parse::<i64>().unwrap_or(0));
        assert_eq!(freqs, vec!["300000", "900000", "1200000"]);
    }

    #[test]
    fn empty_freq_list_stays_empty() {
        let raw = "";
        let parsed: Vec<String> = raw
            .split_whitespace()
            .filter(|s| !s.is_empty())
            .map(String::from)
            .collect();
        assert!(parsed.is_empty());
    }
}

// JNI bindings

#[no_mangle]
pub extern "system" fn Java_com_xaozora_manager_core_utils_GpuControlUtils_getGpuConfigJson<
    'local,
>(
    mut env: EnvUnowned<'local>,
    _class: JClass,
) -> jstring {
    env.with_env(|env| -> jni::errors::Result<jstring> {
        let config = get_gpu_config();
        let json_str = serde_json::to_string(&config).unwrap_or_else(|_| "{}".to_string());
        let output = env.new_string(json_str).unwrap();
        Ok(output.into_raw())
    })
    .resolve::<ThrowRuntimeExAndDefault>()
}

#[no_mangle]
pub extern "system" fn Java_com_xaozora_manager_core_utils_GpuControlUtils_applyGpuConfig<
    'local,
>(
    mut env: EnvUnowned<'local>,
    _class: JClass,
    min_freq: JString,
    max_freq: JString,
    governor: JString,
    adreno_boost: JString,
) {
    env.with_env(|env| -> jni::errors::Result<()> {
        let min_freq = min_freq.try_to_string(env).unwrap();
        let max_freq = max_freq.try_to_string(env).unwrap();
        let governor = governor.try_to_string(env).unwrap();

        let adreno_boost_val: Option<String> = if adreno_boost.is_null() {
            None
        } else {
            Some(adreno_boost.try_to_string(env).unwrap())
        };

        let adreno_boost_ref = adreno_boost_val.as_deref();

        apply_gpu_config(&min_freq, &max_freq, &governor, adreno_boost_ref);
        Ok(())
    })
    .resolve::<ThrowRuntimeExAndDefault>();
}
