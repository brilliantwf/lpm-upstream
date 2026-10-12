// Claude Code's hook events as socket frames.
use super::{clear_live_frame, resume_frame, status_frame, str_field, HookEnv, DONE, ERROR, HELD_WAITING, WAITING};
use serde_json::Value;

const CLAUDE_RUNNING: &str = "Running --icon=bolt --color=#4C8DFF";

const CLAUDE_PROMPTED: &str = "Running --icon=bolt --color=#4C8DFF --prompt";

/// Background task types a Claude turn can end beside and still be finished:
/// an idle-but-registered teammate, a wake-on-event monitor, a backgrounded
/// shell, and Claude's own housekeeping (a dream, an auto-mode scan, a memory
/// import), which ends without waking the session. Anything else in
/// `background_tasks` means the turn only paused.
const SETTLED_TASK_TYPES: [&str; 6] = [
    "teammate",
    "monitor",
    "shell",
    "dream",
    "auto-mode scan",
    "memory import",
];

pub(super) fn frames(event: &str, payload: &Value, env: &HookEnv) -> Vec<String> {
    // Cursor runs the hooks in Claude's settings as its own; its hooks of its
    // own report for it (cursor.rs).
    if env.under_cursor {
        return Vec::new();
    }
    let sid = str_field(payload, "session_id");
    // Keyed by Claude's own session id so one session's frames share a key even
    // if the pane id shifts, with the pane as the fallback key.
    let key = format!("claude_code_{}", sid.unwrap_or(&env.pane));
    // A sub-agent's hooks run under the parent's session id and carry
    // `agent_id`; it may raise a question, but only the main thread says whether
    // the session is working or failed.
    let main_thread = payload.get("agent_id").is_none();
    let main = |value: &str| -> Vec<String> {
        main_thread.then(|| status_frame(&key, value, env)).into_iter().collect()
    };
    match event {
        "SessionStart" => sid
            .map(|sid| resume_frame("claude", sid, env))
            .into_iter()
            .collect(),
        // A prompt submitted mid-turn queues behind it (desktop socketsrv.rs).
        "UserPromptSubmit" => main(CLAUDE_PROMPTED),
        "PreToolUse" | "PostToolUse" | "PostToolUseFailure" | "ElicitationResult"
        | "PreCompact" => main(CLAUDE_RUNNING),
        "PostCompact" => vec![clear_live_frame(&key, env)],
        "PermissionRequest" | "Notification" => vec![status_frame(&key, WAITING, env)],
        // An MCP dialog: only its ElicitationResult may end this Waiting.
        "Elicitation" => vec![status_frame(&key, HELD_WAITING, env)],
        "Stop" => vec![status_frame(&key, stop_status(payload), env)],
        "StopFailure" => main(ERROR),
        "SessionEnd" => vec![clear_live_frame(&key, env)],
        _ => Vec::new(),
    }
}

/// `Stop` fires at every turn boundary, including a turn that handed off to
/// background work the harness will wake the session for; that is a pause, so it
/// re-asserts Running. Only a turn with nothing in flight but settled task types
/// is Done. A payload without `background_tasks` (an older Claude), an empty
/// list, or one whose entries aren't objects reports Done, as the sh hook does.
fn stop_status(payload: &Value) -> &'static str {
    let Some(tasks) = payload.get("background_tasks").and_then(Value::as_array) else {
        return DONE;
    };
    if !tasks.first().is_some_and(Value::is_object) {
        return DONE;
    }
    let settled = tasks.iter().all(|task| {
        task.get("type")
            .and_then(Value::as_str)
            .is_some_and(|kind| SETTLED_TASK_TYPES.contains(&kind))
    });
    if settled {
        DONE
    } else {
        CLAUDE_RUNNING
    }
}
