//! `lpm hook <agent> <event>` — the agent status hooks as a program rather than
//! a POSIX-sh one-liner. The desktop app installs this form where the sh hooks
//! cannot reach its socket (Windows: no `nc -U`, no AF_UNIX in Python, an MSYS
//! `$PPID`), and the frames it sends are the ones the sh hooks build
//! (desktop src-tauri/src/hooks), so the app cannot tell the two apart. Each
//! agent's events map to frames in its own module here.
//!
//! `lpm hook claude statusline` is the usage-limit forwarder: it reports the
//! statusline payload as `agent_limits <account> --payload-b64=…`.
//!
//! A hook must never get in the agent's way, so this prints nothing and the
//! caller exits 0 whatever happens: Claude Code reads exit 2 as "block this tool
//! call" and adds SessionStart stdout to the model's context.

mod claude;
mod codex;
mod cursor;
mod gemini;
mod transport;

use crate::statussock::quote_arg;
use serde_json::Value;
use std::io::{IsTerminal, Read};
use std::path::PathBuf;
use transport::{deliver, is_socket, parent_pid, status_socket};

const WAITING: &str = "Waiting --icon=bell --color=#f59e0b";
const APPROVAL: &str = "Waiting --icon=bell --color=#f59e0b --approval";
const HELD_WAITING: &str = "Waiting --icon=bell --color=#f59e0b --hold";
const ERROR: &str = "Error --icon=warning --color=#ef4444";
const DONE: &str = "Done --icon=checkmark --color=#4ade80";

/// What a hook run knows about where it is: the LPM_* identity of the pane the
/// agent runs in, and the pid that ran the hook (the agent, or a shell under it),
/// which the app uses to tell a tab's own agent from one it launched.
pub struct HookEnv {
    pub socket: Option<PathBuf>,
    pub project: String,
    pub pane: String,
    pub config_dir: Option<String>,
    pub home: Option<PathBuf>,
    pub codex_home: Option<PathBuf>,
    pub reporter_pid: Option<u32>,
    /// Cursor runs this: it sets CURSOR_VERSION for every hook it runs.
    pub under_cursor: bool,
}

impl HookEnv {
    fn from_process() -> Self {
        let session = tmux_session_env();
        // Inside tmux the session's copy names the tab that attached it; the
        // process's own may be the tab that started the tmux server.
        let var = |name: &str| {
            session
                .get(name)
                .cloned()
                .or_else(|| std::env::var(name).ok())
                .unwrap_or_default()
        };
        HookEnv {
            socket: Some(var("LPM_SOCKET_PATH"))
                .filter(|v| !v.is_empty())
                .map(PathBuf::from),
            project: var("LPM_PROJECT_NAME"),
            pane: var("LPM_PANE_ID"),
            config_dir: std::env::var("CLAUDE_CONFIG_DIR").ok(),
            home: dirs::home_dir(),
            codex_home: std::env::var_os("CODEX_HOME")
                .filter(|v| !v.is_empty())
                .map(PathBuf::from)
                .or_else(|| dirs::home_dir().map(|home| home.join(".codex"))),
            reporter_pid: parent_pid(),
            under_cursor: std::env::var_os("CURSOR_VERSION").is_some(),
        }
    }
}

/// The LPM_* values in the environment of the tmux session this runs in, which
/// tmux keeps per session from the client that attached it (lpm's terminals set
/// that up). Empty outside tmux, or when tmux can't be asked.
fn tmux_session_env() -> std::collections::HashMap<String, String> {
    let (Some(_), Ok(pane)) = (std::env::var_os("TMUX"), std::env::var("TMUX_PANE")) else {
        return Default::default();
    };
    std::process::Command::new("tmux")
        .args(["showenv", "-t", &pane])
        .stderr(std::process::Stdio::null())
        .output()
        .map(|out| parse_tmux_env(&String::from_utf8_lossy(&out.stdout)))
        .unwrap_or_default()
}

/// `showenv` lines (`NAME=value`, or `-NAME` for one removed) -> lpm's values.
fn parse_tmux_env(out: &str) -> std::collections::HashMap<String, String> {
    out.lines()
        .filter_map(|line| line.split_once('='))
        .filter(|(name, value)| name.starts_with("LPM_") && !value.is_empty())
        .map(|(name, value)| (name.to_string(), value.to_string()))
        .collect()
}

/// Entry point for `lpm hook <agent> <event> [ignored...]`. Trailing arguments
/// are accepted and ignored: installs carry the `# lpm-hook` marker there.
pub fn run(args: &[String]) {
    let (Some(agent), Some(event)) = (args.first(), args.get(1)) else {
        return;
    };
    let payload = read_payload();
    let env = HookEnv::from_process();
    let mut lines = frames(agent, event, &payload, &env);
    lines.retain(|line| !line.contains(['\n', '\r']));
    if lines.is_empty() {
        return;
    }
    let socket = if is_statusline(agent, event) {
        env.socket.clone().filter(|path| is_socket(path))
    } else {
        status_socket(env.socket.as_deref(), env.home.as_deref())
    };
    if let Some(socket) = socket {
        deliver(&socket, &lines);
    }
}

/// The agent writes the payload to our stdin and fails its write if we exit
/// without draining it, so it is always read in full — unless stdin is a
/// terminal (someone running the hook by hand), which would block.
fn read_payload() -> Vec<u8> {
    let mut stdin = std::io::stdin();
    let mut payload = Vec::new();
    if !stdin.is_terminal() {
        let _ = stdin.read_to_end(&mut payload);
    }
    payload
}

fn is_statusline(agent: &str, event: &str) -> bool {
    agent == "claude" && event == "statusline"
}

/// The socket lines one hook run sends, in order; empty when it has nothing
/// to report (unknown event, a sub-agent's frame, no pane identity).
pub fn frames(agent: &str, event: &str, payload: &[u8], env: &HookEnv) -> Vec<String> {
    if is_statusline(agent, event) {
        return vec![limits_frame(payload, env)];
    }
    if env.project.is_empty() || env.pane.is_empty() {
        return Vec::new();
    }
    let payload = serde_json::from_slice::<Value>(payload).unwrap_or(Value::Null);
    match agent {
        "claude" => claude::frames(event, &payload, env),
        "codex" => codex::frames(event, &payload, env),
        "gemini" => gemini::frames(event, &payload, env),
        "cursor" => cursor::frames(event, &payload, env),
        _ => Vec::new(),
    }
}

/// Takes back a Running or Waiting the agent can no longer end itself, leaving
/// a finish the user hasn't seen.
fn clear_live_frame(key: &str, env: &HookEnv) -> String {
    format!(
        "clear_status {} {} --live{}",
        quote_arg(&env.project),
        token(key),
        reporter(env)
    )
}

fn limits_frame(payload: &[u8], env: &HookEnv) -> String {
    let end = payload
        .iter()
        .rposition(|&b| b != b'\n')
        .map_or(0, |i| i + 1);
    format!(
        "agent_limits {} --payload-b64={}",
        token(&limits_account(env.config_dir.as_deref())),
        base64(&payload[..end])
    )
}

/// The account a statusline reading is filed under: the last path component of
/// a CLAUDE_CONFIG_DIR inside lpm's `claude-accounts`, else `default`. Either
/// separator counts, so a Windows `…\claude-accounts\work` names `work` too.
pub fn limits_account(config_dir: Option<&str>) -> String {
    config_dir
        .map(|dir| dir.replace('\\', "/"))
        .filter(|dir| dir.contains("/claude-accounts/"))
        .and_then(|dir| dir.rsplit('/').next().map(str::to_string))
        .filter(|id| !id.is_empty())
        .unwrap_or_else(|| "default".to_string())
}

fn status_frame(key: &str, value: &str, env: &HookEnv) -> String {
    format!(
        "set_status {} {} {value} --pane={}{}",
        quote_arg(&env.project),
        token(key),
        token(&env.pane),
        reporter(env)
    )
}

/// A sub-agent of the agent reporting under `key` began or ended.
fn child_frame(key: &str, child: &str, phase: &str, env: &HookEnv) -> String {
    format!(
        "agent_child {} {} {} {phase}{}",
        quote_arg(&env.project),
        token(key),
        token(child),
        reporter(env)
    )
}

fn resume_frame(provider: &str, sid: &str, env: &HookEnv) -> String {
    format!(
        "set_resume {} {} {} --provider={provider}{}",
        quote_arg(&env.project),
        token(&env.pane),
        token(sid),
        reporter(env)
    )
}

fn reporter(env: &HookEnv) -> String {
    env.reporter_pid
        .map(|pid| format!(" --reporter-pid={pid}"))
        .unwrap_or_default()
}

/// An identifier as one socket-command token: bare when it is plainly a token,
/// quoted otherwise so the app's shell_split still reads it whole.
fn token(s: &str) -> String {
    let plain = !s.is_empty()
        && s.chars()
            .all(|c| c.is_ascii_alphanumeric() || "-_.:".contains(c));
    if plain {
        s.to_string()
    } else {
        quote_arg(s)
    }
}

fn str_field<'a>(payload: &'a Value, name: &str) -> Option<&'a str> {
    payload
        .get(name)
        .and_then(Value::as_str)
        .filter(|s| !s.is_empty())
}

pub fn base64(bytes: &[u8]) -> String {
    const TABLE: &[u8; 64] = b"ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/";
    let mut out = String::with_capacity(bytes.len().div_ceil(3) * 4);
    for chunk in bytes.chunks(3) {
        let n = (u32::from(chunk[0]) << 16)
            | (u32::from(*chunk.get(1).unwrap_or(&0)) << 8)
            | u32::from(*chunk.get(2).unwrap_or(&0));
        for i in 0..4 {
            if i <= chunk.len() {
                out.push(TABLE[(n >> (18 - 6 * i)) as usize & 63] as char);
            } else {
                out.push('=');
            }
        }
    }
    out
}

#[cfg(test)]
mod tests;

#[cfg(test)]
mod claude_tests;

#[cfg(test)]
mod codex_tests;

#[cfg(test)]
mod cursor_tests;

#[cfg(test)]
mod gemini_tests;
