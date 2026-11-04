use std::fs;
use std::io::{Read, Write};
use std::os::unix::fs::PermissionsExt;
use std::os::unix::io::AsRawFd;
use std::os::unix::net::{UnixListener, UnixStream};
use std::process::Command;
use std::sync::atomic::Ordering;
use std::thread;
use std::time::Duration;

use crate::RUNNING;
use crate::config;

const SOCKET_PATH: &str = "/data/data/com.xaozora.manager/files/aozora_ipc.sock";
const PROTOCOL_V2_HANDSHAKE: &str = "XA0Z0RA_V2";

#[repr(C)]
struct Ucred {
    pid: i32,
    uid: u32,
    gid: u32,
}

fn peer_cred_uid(stream: &UnixStream) -> Option<u32> {
    let mut cred = Ucred {
        pid: 0,
        uid: 0,
        gid: 0,
    };
    let mut len = std::mem::size_of::<Ucred>() as libc::socklen_t;
    let result = unsafe {
        libc::getsockopt(
            stream.as_raw_fd(),
            libc::SOL_SOCKET,
            libc::SO_PEERCRED,
            &mut cred as *mut Ucred as *mut libc::c_void,
            &mut len,
        )
    };

    if result == 0 && len as usize >= std::mem::size_of::<Ucred>() {
        Some(cred.uid)
    } else {
        None
    }
}

fn is_authorized(peer_uid: u32, app_uid: Option<u32>) -> bool {
    peer_uid == 0 || app_uid == Some(peer_uid)
}

fn apply_socket_permissions() {
    let chowned = config::app_owner_ids()
        .and_then(|(uid, gid)| {
            std::ffi::CString::new(SOCKET_PATH)
                .ok()
                .map(|c_path| unsafe { libc::chown(c_path.as_ptr(), uid, gid) } == 0)
        })
        .unwrap_or(false);

    let mode = if chowned {
        0o660
    } else {
        println!("IPC: socket owner unavailable; using 0666 with peer credential checks");
        0o666
    };

    let _ = fs::set_permissions(SOCKET_PATH, fs::Permissions::from_mode(mode));
}

pub fn start_ipc_server() {
    let _ = fs::remove_file(SOCKET_PATH);

    let listener = match UnixListener::bind(SOCKET_PATH) {
        Ok(l) => l,
        Err(e) => {
            println!("Failed to bind IPC socket: {}", e);
            return;
        }
    };

    let _ = listener.set_nonblocking(true);
    apply_socket_permissions();
    let app_uid = config::app_owner_ids().map(|(uid, _)| uid);

    while RUNNING.load(Ordering::SeqCst) {
        match listener.accept() {
            Ok((stream, _)) => {
                let _ = stream.set_nonblocking(false);
                thread::spawn(move || handle_client(stream, app_uid));
            }
            Err(ref e) if e.kind() == std::io::ErrorKind::WouldBlock => {
                thread::sleep(Duration::from_millis(100));
            }
            Err(e) => {
                println!("IPC socket error: {}", e);
                break;
            }
        }
    }

    let _ = fs::remove_file(SOCKET_PATH);
}

fn handle_client(mut stream: UnixStream, app_uid: Option<u32>) {
    let Some(peer_uid) = peer_cred_uid(&stream) else {
        println!("IPC: rejected connection without peer credentials");
        return;
    };

    if !is_authorized(peer_uid, app_uid) {
        println!("IPC: rejected connection from uid {}", peer_uid);
        return;
    }

    let _ = stream.set_read_timeout(Some(Duration::from_secs(5)));
    let mut buffer = [0; 4096];
    let mut protocol_v2 = false;
    loop {
        match stream.read(&mut buffer) {
            Ok(0) => break,
            Ok(n) => {
                let req_str = String::from_utf8_lossy(&buffer[..n]);
                let commands: Vec<&str> = req_str.split('\0').filter(|s| !s.is_empty()).collect();

                for cmd in commands {
                    if cmd == PROTOCOL_V2_HANDSHAKE {
                        protocol_v2 = true;
                        continue;
                    }

                    let (code, body) = execute_shell_command(cmd);

                    if protocol_v2 {
                        let header = format!("code={};len={}\n", code, body.len());
                        if stream.write_all(header.as_bytes()).is_err()
                            || stream.write_all(&body).is_err()
                        {
                            break;
                        }
                    } else {
                        let mut response = body;
                        response.extend_from_slice(b"\0EOF\0");
                        if stream.write_all(&response).is_err() {
                            break;
                        }
                    }
                }
            }
            Err(_) => break,
        }
    }
}

fn execute_shell_command(cmd: &str) -> (i32, Vec<u8>) {
    match Command::new("sh").arg("-c").arg(cmd).output() {
        Ok(output) => {
            let code = output.status.code().unwrap_or(-1);
            let mut body = output.stdout;
            body.extend_from_slice(&output.stderr);
            (code, body)
        }
        Err(e) => (-127, format!("Error executing command: {}", e).into_bytes()),
    }
}
