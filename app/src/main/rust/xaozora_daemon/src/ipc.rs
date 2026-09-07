use std::fs;
use std::io::{Read, Write};
use std::os::unix::net::{UnixListener, UnixStream};
use std::process::Command;
use std::thread;

const SOCKET_PATH: &str = "/data/data/com.xaozora.manager/files/aozora_ipc.sock";

pub fn start_ipc_server() {
    let _ = fs::remove_file(SOCKET_PATH);

    let listener = match UnixListener::bind(SOCKET_PATH) {
        Ok(l) => l,
        Err(e) => {
            println!("Failed to bind IPC socket: {}", e);
            return;
        }
    };

    let _ = Command::new("chmod").arg("666").arg(SOCKET_PATH).status();

    for stream in listener.incoming() {
        match stream {
            Ok(stream) => {
                thread::spawn(move || handle_client(stream));
            }
            Err(e) => {
                println!("IPC socket error: {}", e);
                break;
            }
        }
    }
}

fn handle_client(mut stream: UnixStream) {
    let mut buffer = [0; 4096];
    loop {
        match stream.read(&mut buffer) {
            Ok(0) => break,
            Ok(n) => {
                let req_str = String::from_utf8_lossy(&buffer[..n]);
                let commands: Vec<&str> = req_str.split('\0').filter(|s| !s.is_empty()).collect();

                for cmd in commands {
                    let output = execute_shell_command(cmd);
                    let mut response = output.into_bytes();
                    response.extend_from_slice(b"\0EOF\0");
                    if stream.write_all(&response).is_err() {
                        break;
                    }
                }
            }
            Err(_) => break,
        }
    }
}

fn execute_shell_command(cmd: &str) -> String {
    let out = Command::new("sh").arg("-c").arg(cmd).output();

    match out {
        Ok(output) => {
            let mut res = String::from_utf8_lossy(&output.stdout).to_string();
            if !output.stderr.is_empty() {
                res.push_str(&String::from_utf8_lossy(&output.stderr));
            }
            res
        }
        Err(e) => format!("Error executing command: {}", e),
    }
}
