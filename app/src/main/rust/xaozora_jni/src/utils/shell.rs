use std::os::unix::net::UnixStream;
use std::io::{Read, Write};

const SOCKET_PATH: &str = "/data/data/com.xaozora.manager/files/aozora_ipc.sock";

fn send_ipc_command(cmd: &str) -> Option<String> {
    let mut stream = match UnixStream::connect(SOCKET_PATH) {
        Ok(s) => s,
        Err(_) => return None,
    };
    
    let mut payload = cmd.to_string().into_bytes();
    payload.push(0);
    
    if stream.write_all(&payload).is_err() {
        return None;
    }
    
    let mut result = Vec::new();
    let mut buffer = [0; 4096];
    
    loop {
        match stream.read(&mut buffer) {
            Ok(0) => break,
            Ok(n) => {
                result.extend_from_slice(&buffer[..n]);
                if result.ends_with(b"\0EOF\0") {
                    result.truncate(result.len() - 5);
                    break;
                }
            }
            Err(_) => break,
        }
    }
    
    Some(String::from_utf8_lossy(&result).into_owned())
}

pub fn execute_cmd(cmd: &str) -> bool {
    if let Some(output) = send_ipc_command(cmd) {
        if !output.starts_with("Error executing command:") {
            return true;
        }
    }
    
    if let Ok(mut child) = std::process::Command::new("su").arg("-c").arg(cmd).spawn() {
        if let Ok(status) = child.wait() {
            return status.success();
        }
    }
    false
}

pub fn execute_cmd_and_get_output(cmd: &str) -> String {
    if let Some(output) = send_ipc_command(cmd) {
        if !output.starts_with("Error executing command:") {
            return output.trim().to_string();
        }
    }
    
    if let Ok(output) = std::process::Command::new("su").arg("-c").arg(cmd).output() {
        if let Ok(out_str) = String::from_utf8(output.stdout) {
            return out_str.trim().to_string();
        }
    }
    String::new()
}

pub fn read_system_file(path: &str) -> String {
    execute_cmd_and_get_output(&format!("cat {}", path))
}

pub fn write_system_file(path: &str, value: &str) -> bool {
    execute_cmd(&format!("echo '{}' > {}", value, path))
}

pub fn check_file_exists(path: &str) -> bool {
    if std::path::Path::new(path).exists() {
        return true;
    }
    let res = execute_cmd_and_get_output(&format!("if [ -e {} ]; then echo 1; else echo 0; fi", path));
    res.trim() == "1"
}

use jni::JNIEnv;
use jni::objects::{JClass, JString};
use jni::sys::{jboolean, jstring};

#[no_mangle]
pub extern "system" fn Java_com_xaozora_manager_core_shell_RootShellHelper_executeCmd(
    mut env: JNIEnv,
    _class: JClass,
    cmd: JString,
) -> jboolean {
    let cmd: String = env.get_string(&cmd).unwrap().into();
    if execute_cmd(&cmd) { 1 } else { 0 }
}

#[no_mangle]
pub extern "system" fn Java_com_xaozora_manager_core_shell_RootShellHelper_executeCmdAndGetOutput<'local>(
    mut env: JNIEnv<'local>,
    _class: JClass,
    cmd: JString,
) -> jstring {
    let cmd: String = env.get_string(&cmd).unwrap().into();
    let res = execute_cmd_and_get_output(&cmd);
    let output = env.new_string(res).unwrap();
    output.into_raw()
}

#[no_mangle]
pub extern "system" fn Java_com_xaozora_manager_core_shell_RootShellHelper_readSystemFile<'local>(
    mut env: JNIEnv<'local>,
    _class: JClass,
    path: JString,
) -> jstring {
    let path: String = env.get_string(&path).unwrap().into();
    let res = read_system_file(&path);
    let output = env.new_string(res).unwrap();
    output.into_raw()
}

#[no_mangle]
pub extern "system" fn Java_com_xaozora_manager_core_shell_RootShellHelper_writeSystemFile(
    mut env: JNIEnv,
    _class: JClass,
    path: JString,
    value: JString,
) -> jboolean {
    let path: String = env.get_string(&path).unwrap().into();
    let value: String = env.get_string(&value).unwrap().into();
    if write_system_file(&path, &value) { 1 } else { 0 }
}

#[no_mangle]
pub extern "system" fn Java_com_xaozora_manager_core_shell_RootShellHelper_checkFileExists(
    mut env: JNIEnv,
    _class: JClass,
    path: JString,
) -> jboolean {
    let path: String = env.get_string(&path).unwrap().into();
    if check_file_exists(&path) { 1 } else { 0 }
}
