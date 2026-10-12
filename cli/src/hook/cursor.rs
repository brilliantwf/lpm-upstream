// Cursor CLI's hook events as socket frames, keyed by the pane like Codex's.
use super::{clear_live_frame, status_frame, str_field, HookEnv, DONE, ERROR};
use serde_json::Value;

const CURSOR_RUNNING: &str = "Running --icon=bolt --color=#8B5CF6";

pub(super) fn frames(event: &str, payload: &Value, env: &HookEnv) -> Vec<String> {
    let key = format!("cursor_{}", env.pane);
    match event {
        "beforeSubmitPrompt" | "postToolUse" | "postToolUseFailure" => {
            vec![status_frame(&key, CURSOR_RUNNING, env)]
        }
        // An interrupted turn takes back the Running it left.
        "stop" => match str_field(payload, "status") {
            Some("aborted") => vec![clear_live_frame(&key, env)],
            Some("error") => vec![status_frame(&key, ERROR, env)],
            _ => vec![status_frame(&key, DONE, env)],
        },
        "sessionEnd" => vec![clear_live_frame(&key, env)],
        _ => Vec::new(),
    }
}
