use std::io::{Read, Write};
use std::os::unix::net::UnixStream;
use std::time::Duration;

const SOCKET_PATH: &str = "/data/data/com.xaozora.manager/files/aozora_ipc.sock";
const PROTOCOL_V2_HANDSHAKE: &str = "XA0Z0RA_V2";
const MAX_RESPONSE_BYTES: usize = 8 * 1024 * 1024;
const MAX_HEADER_BYTES: usize = 128;

struct CmdResult {
    code: i32,
    output: String,
}

fn read_response_header(stream: &mut UnixStream) -> Option<(i32, usize)> {
    let mut header = Vec::new();
    let mut byte = [0u8; 1];

    loop {
        if header.len() >= MAX_HEADER_BYTES {
            return None;
        }

        match stream.read(&mut byte) {
            Ok(0) => return None,
            Ok(_) => {
                if byte[0] == b'\n' {
                    break;
                }
                header.push(byte[0]);
            }
            Err(_) => return None,
        }
    }

    let header = String::from_utf8(header).ok()?;
    let mut code = None;
    let mut len = None;

    for part in header.split(';') {
        let (key, value) = part.split_once('=')?;
        match key {
            "code" => code = value.parse::<i32>().ok(),
            "len" => len = value.parse::<usize>().ok(),
            _ => {}
        }
    }

    let code = code?;
    let len = len?;
    if len > MAX_RESPONSE_BYTES {
        return None;
    }

    Some((code, len))
}

fn connect_v2() -> Option<UnixStream> {
    let mut stream = UnixStream::connect(SOCKET_PATH).ok()?;
    stream
        .set_read_timeout(Some(Duration::from_millis(1000)))
        .ok()?;

    let mut probe = Vec::new();
    probe.extend_from_slice(PROTOCOL_V2_HANDSHAKE.as_bytes());
    probe.push(0);
    probe.extend_from_slice(b"true\0");

    stream.write_all(&probe).ok()?;

    match read_response_header(&mut stream) {
        Some((0, 0)) => {}
        _ => return None,
    }

    stream.set_read_timeout(None).ok()?;
    Some(stream)
}

fn send_ipc_command(cmd: &str) -> Option<CmdResult> {
    let mut stream = connect_v2()?;

    let mut payload = cmd.as_bytes().to_vec();
    payload.push(0);
    stream.write_all(&payload).ok()?;

    let (code, len) = read_response_header(&mut stream)?;
    let mut body = vec![0u8; len];
    if len > 0 && stream.read_exact(&mut body).is_err() {
        return None;
    }

    Some(CmdResult {
        code,
        output: String::from_utf8_lossy(&body).into_owned(),
    })
}

pub fn execute_cmd(cmd: &str) -> bool {
    if let Some(result) = send_ipc_command(cmd) {
        return result.code == 0;
    }

    if let Ok(status) = std::process::Command::new("su").arg("-c").arg(cmd).status() {
        return status.success();
    }
    false
}

pub fn execute_cmd_and_get_output(cmd: &str) -> String {
    if let Some(result) = send_ipc_command(cmd) {
        return if result.code == 0 {
            result.output.trim().to_string()
        } else {
            String::new()
        };
    }

    if let Ok(output) = std::process::Command::new("su").arg("-c").arg(cmd).output() {
        if let Ok(out_str) = String::from_utf8(output.stdout) {
            return out_str.trim().to_string();
        }
    }
    String::new()
}

pub fn execute_cmd_detailed(cmd: &str) -> String {
    let result = match send_ipc_command(cmd) {
        Some(result) => result,
        None => match std::process::Command::new("su").arg("-c").arg(cmd).output() {
            Ok(output) => CmdResult {
                code: output.status.code().unwrap_or(-1),
                output: String::from_utf8_lossy(&output.stdout).into_owned(),
            },
            Err(e) => CmdResult {
                code: -127,
                output: format!("Error executing command: {}", e),
            },
        },
    };

    serde_json::json!({ "code": result.code, "output": result.output }).to_string()
}

const SCRIPT_SEPARATOR: &str = "__XA0Z0RA_SEP__";

pub fn execute_script_and_get_output(cmds: &[&str]) -> Vec<String> {
    if cmds.is_empty() {
        return Vec::new();
    }

    let mut script = String::new();
    for cmd in cmds {
        script.push_str("echo ");
        script.push_str(SCRIPT_SEPARATOR);
        script.push_str("; ");
        script.push_str(cmd);
        script.push_str("; ");
    }
    script.push_str("echo ");
    script.push_str(SCRIPT_SEPARATOR);

    let output = execute_cmd_and_get_output(&script);
    if output.is_empty() {
        return vec![String::new(); cmds.len()];
    }

    let mut parts: Vec<String> = output
        .split(SCRIPT_SEPARATOR)
        .map(|part| part.trim().to_string())
        .collect();

    if !parts.is_empty() {
        parts.remove(0);
    }
    parts.truncate(cmds.len());
    parts.resize(cmds.len(), String::new());
    parts
}

pub fn sh_quote(value: &str) -> String {
    let mut quoted = String::with_capacity(value.len() + 2);
    quoted.push('\'');
    for ch in value.chars() {
        match ch {
            '\'' => quoted.push_str("'\\''"),
            _ => quoted.push(ch),
        }
    }
    quoted.push('\'');
    quoted
}

pub fn is_valid_profile_id(value: &str) -> bool {
    !value.is_empty()
        && value
            .chars()
            .all(|c| c.is_ascii_lowercase() || c.is_ascii_digit() || c == '_')
}

pub fn is_valid_frequency(value: &str) -> bool {
    !value.is_empty() && value.chars().all(|c| c.is_ascii_digit())
}

pub fn is_valid_governor(value: &str) -> bool {
    !value.is_empty()
        && value
            .chars()
            .all(|c| c.is_ascii_lowercase() || c.is_ascii_digit() || c == '_' || c == '-')
}

fn read_file_direct(path: &str) -> Option<String> {
    std::fs::read_to_string(path)
        .ok()
        .map(|content| content.trim().to_string())
}

pub fn read_system_file(path: &str) -> String {
    if let Some(content) = read_file_direct(path) {
        return content;
    }

    let output = execute_cmd_and_get_output(&format!("cat {} 2>/dev/null", sh_quote(path)));
    if output.contains("No such file or directory") || output.contains("Permission denied") {
        return String::new();
    }
    output
}

pub fn write_system_file(path: &str, value: &str) -> bool {
    if std::fs::write(path, format!("{}\n", value)).is_ok() {
        return true;
    }
    execute_cmd(&format!("echo {} > {}", sh_quote(value), sh_quote(path)))
}

pub fn check_file_exists(path: &str) -> bool {
    if std::path::Path::new(path).exists() {
        return true;
    }
    let res = execute_cmd_and_get_output(&format!(
        "if [ -e {} ]; then echo 1; else echo 0; fi",
        sh_quote(path)
    ));
    res.trim() == "1"
}

use jni::objects::{JClass, JString};
use jni::sys::{jboolean, jstring};
use jni::{errors::ThrowRuntimeExAndDefault, EnvUnowned};

#[no_mangle]
pub extern "system" fn Java_com_xaozora_manager_core_shell_RootShellHelper_executeCmd<'local>(
    mut env: EnvUnowned<'local>,
    _class: JClass,
    cmd: JString,
) -> jboolean {
    env.with_env(|env| -> jni::errors::Result<jboolean> {
        let cmd = cmd.try_to_string(env)?;
        Ok(execute_cmd(&cmd))
    })
    .resolve::<ThrowRuntimeExAndDefault>()
}

#[no_mangle]
pub extern "system" fn Java_com_xaozora_manager_core_shell_RootShellHelper_executeCmdAndGetOutput<
    'local,
>(
    mut env: EnvUnowned<'local>,
    _class: JClass,
    cmd: JString,
) -> jstring {
    env.with_env(|env| -> jni::errors::Result<jstring> {
        let cmd = cmd.try_to_string(env)?;
        let res = execute_cmd_and_get_output(&cmd);
        let output = env.new_string(res)?;
        Ok(output.into_raw())
    })
    .resolve::<ThrowRuntimeExAndDefault>()
}

#[no_mangle]
pub extern "system" fn Java_com_xaozora_manager_core_shell_RootShellHelper_executeCmdDetailed<
    'local,
>(
    mut env: EnvUnowned<'local>,
    _class: JClass,
    cmd: JString,
) -> jstring {
    env.with_env(|env| -> jni::errors::Result<jstring> {
        let cmd = cmd.try_to_string(env)?;
        let res = execute_cmd_detailed(&cmd);
        let output = env.new_string(res)?;
        Ok(output.into_raw())
    })
    .resolve::<ThrowRuntimeExAndDefault>()
}

#[no_mangle]
pub extern "system" fn Java_com_xaozora_manager_core_shell_RootShellHelper_readSystemFile<
    'local,
>(
    mut env: EnvUnowned<'local>,
    _class: JClass,
    path: JString,
) -> jstring {
    env.with_env(|env| -> jni::errors::Result<jstring> {
        let path = path.try_to_string(env)?;
        let res = read_system_file(&path);
        let output = env.new_string(res)?;
        Ok(output.into_raw())
    })
    .resolve::<ThrowRuntimeExAndDefault>()
}

#[no_mangle]
pub extern "system" fn Java_com_xaozora_manager_core_shell_RootShellHelper_writeSystemFile<
    'local,
>(
    mut env: EnvUnowned<'local>,
    _class: JClass,
    path: JString,
    value: JString,
) -> jboolean {
    env.with_env(|env| -> jni::errors::Result<jboolean> {
        let path = path.try_to_string(env)?;
        let value = value.try_to_string(env)?;
        Ok(write_system_file(&path, &value))
    })
    .resolve::<ThrowRuntimeExAndDefault>()
}

#[no_mangle]
pub extern "system" fn Java_com_xaozora_manager_core_shell_RootShellHelper_checkFileExists<
    'local,
>(
    mut env: EnvUnowned<'local>,
    _class: JClass,
    path: JString,
) -> jboolean {
    env.with_env(|env| -> jni::errors::Result<jboolean> {
        let path = path.try_to_string(env)?;
        Ok(check_file_exists(&path))
    })
    .resolve::<ThrowRuntimeExAndDefault>()
}
