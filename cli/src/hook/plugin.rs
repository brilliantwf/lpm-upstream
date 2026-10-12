// Agents lpm reaches through a plugin of its own rather than hook commands
// (desktop src-tauri/src/hooks/plugin.rs). The plugin reports each session's
// turn as it changes, keyed by the session like Claude's.
use super::{clear_live_frame, status_frame, str_field, HookEnv, DONE, ERROR, HELD_WAITING};
use serde_json::Value;

/// Each plugin agent's id, status-key prefix and Running look.
const AGENTS: [(&str, &str, &str); 2] = [
    ("opencode", "opencode_", "Running --icon=bolt --color=#FAB283"),
    ("omp", "omp_", "Running --icon=bolt --color=#F97316"),
];

pub(super) fn frames(agent: &str, state: &str, payload: &Value, env: &HookEnv) -> Vec<String> {
    let Some((_, prefix, running)) = AGENTS.iter().find(|(id, ..)| *id == agent) else {
        return Vec::new();
    };
    let key = format!("{prefix}{}", str_field(payload, "session_id").unwrap_or(&env.pane));
    match state {
        "running" => vec![status_frame(&key, running, env)],
        "waiting" => vec![status_frame(&key, HELD_WAITING, env)],
        "done" => vec![status_frame(&key, DONE, env)],
        "error" => vec![status_frame(&key, ERROR, env)],
        "clear" => vec![clear_live_frame(&key, env)],
        _ => Vec::new(),
    }
}
