// OpenCode's turns as lpm's status plugin for it reports them (desktop
// src-tauri/src/hooks/opencode_plugin.js), keyed by its session like Claude's.
use super::{clear_live_frame, status_frame, str_field, HookEnv, DONE, ERROR, HELD_WAITING};
use serde_json::Value;

const OPENCODE_RUNNING: &str = "Running --icon=bolt --color=#FAB283";

pub(super) fn frames(event: &str, payload: &Value, env: &HookEnv) -> Vec<String> {
    let key = format!("opencode_{}", str_field(payload, "session_id").unwrap_or(&env.pane));
    match event {
        "running" => vec![status_frame(&key, OPENCODE_RUNNING, env)],
        "waiting" => vec![status_frame(&key, HELD_WAITING, env)],
        "done" => vec![status_frame(&key, DONE, env)],
        "error" => vec![status_frame(&key, ERROR, env)],
        "clear" => vec![clear_live_frame(&key, env)],
        _ => Vec::new(),
    }
}
