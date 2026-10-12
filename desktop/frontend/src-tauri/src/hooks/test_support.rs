// Helpers the hook and status line tests share.
use std::path::PathBuf;

/// The shell the generated lines run under: `sh`, or Git Bash on Windows.
pub(crate) fn posix_sh() -> String {
    if cfg!(windows) {
        crate::sys::login_shell()
    } else {
        "sh".to_string()
    }
}

pub(crate) fn settings_at(dir: &std::path::Path, body: &str) -> PathBuf {
    let p = dir.join("settings.json");
    std::fs::write(&p, body).unwrap();
    p
}

/// Runs a generated hook command under `sh` with `payload` on stdin and a
/// live unix socket at $LPM_SOCKET_PATH, returning the message the hook
/// delivered (None when the hook suppressed delivery). Exercises the real
/// sed/tail/grep gating and the nc delivery path end to end.
#[cfg(unix)]
pub(crate) fn run_codex_hook(cmd: &str, payload: &str) -> Option<String> {
    run_hook_in_project(cmd, payload, "proj")
}

#[cfg(unix)]
pub(crate) fn run_hook_in_project(cmd: &str, payload: &str, project: &str) -> Option<String> {
    run_hook_with_env(cmd, payload, project, &[])
}

/// `run_hook_in_project` with `env` added to the hook's environment.
#[cfg(unix)]
pub(crate) fn run_hook_with_env(cmd: &str, payload: &str, project: &str, env: &[(&str, &str)]) -> Option<String> {
    use std::io::{Read, Write};
    let td = tempfile::tempdir().unwrap();
    let sock = td.path().join("s.sock");
    let listener = std::os::unix::net::UnixListener::bind(&sock).unwrap();
    listener.set_nonblocking(true).unwrap();

    let mut child = std::process::Command::new("sh")
        .arg("-c")
        .arg(cmd)
        .env("LPM_SOCKET_PATH", &sock)
        .env("LPM_PROJECT_NAME", project)
        .env("LPM_PANE_ID", "pane-1")
        .env("HOME", td.path())
        .env_remove("TMUX")
        .envs(env.iter().copied())
        .stdin(std::process::Stdio::piped())
        .stdout(std::process::Stdio::null())
        .stderr(std::process::Stdio::null())
        .spawn()
        .unwrap();
    child
        .stdin
        .take()
        .unwrap()
        .write_all(payload.as_bytes())
        .unwrap();
    child.wait().unwrap();

    let deadline = std::time::Instant::now() + std::time::Duration::from_millis(1500);
    while std::time::Instant::now() < deadline {
        if let Ok((mut conn, _)) = listener.accept() {
            let mut msg = String::new();
            conn.set_nonblocking(false).unwrap();
            conn.read_to_string(&mut msg).unwrap();
            return Some(msg);
        }
        std::thread::sleep(std::time::Duration::from_millis(25));
    }
    None
}

/// The lpm command installed for a Claude event, as Claude would run it.
#[cfg(unix)]
pub(crate) fn claude_installed(event: &str) -> String {
    let v: serde_json::Value = serde_json::from_slice(&super::claude::merge_claude_hooks(b"{}").unwrap()).unwrap();
    v["hooks"][event][0]["hooks"][0]["command"]
        .as_str()
        .unwrap()
        .to_string()
}
