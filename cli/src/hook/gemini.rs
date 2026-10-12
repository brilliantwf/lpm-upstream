// Gemini CLI's hook events as socket frames, keyed by its session like Claude's.
use super::{clear_live_frame, status_frame, str_field, HookEnv, DONE, WAITING};
use serde_json::Value;

const GEMINI_RUNNING: &str = "Running --icon=sparkle --color=#4796E3";

pub(super) fn frames(event: &str, payload: &Value, env: &HookEnv) -> Vec<String> {
    let key = format!("gemini_{}", str_field(payload, "session_id").unwrap_or(&env.pane));
    match event {
        "BeforeAgent" | "BeforeTool" | "AfterTool" => vec![status_frame(&key, GEMINI_RUNNING, env)],
        // A tool's confirmation dialog opened.
        "Notification" => vec![status_frame(&key, WAITING, env)],
        "AfterAgent" => vec![status_frame(&key, DONE, env)],
        "SessionEnd" => vec![clear_live_frame(&key, env)],
        _ => Vec::new(),
    }
}
