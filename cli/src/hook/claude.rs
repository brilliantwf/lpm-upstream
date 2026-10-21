// Claude Code's hook events as socket frames.
use super::{
    clear_live_frame, reporter, resume_frame, status_frame, str_field, token, HookEnv, DONE, ERROR, HELD_WAITING, WAITING,
};
use crate::statussock::quote_arg;
use serde_json::Value;

const CLAUDE_RUNNING: &str = "Running --icon=bolt --color=#4C8DFF";

const CLAUDE_PROMPTED: &str = "Running --icon=bolt --color=#4C8DFF --prompt";

/// A main-thread tool call in flight, which draws nothing of its own.
const CLAUDE_TOOL: &str = "Running --icon=bolt --color=#4C8DFF --tool";

/// A manual /compact, whose end takes back only the Running it started.
const CLAUDE_COMPACTING: &str = "Running --icon=bolt --color=#4C8DFF --compact";

/// Prompts the harness writes itself: a background task reporting in, a
/// teammate's message. They wake the session rather than come from the user.
const WAKE_TAGS: [&str; 3] = ["<task-notification", "<agent-message", "<teammate-message"];

/// Dialogs of several steps, which take more than one answer.
const FORM_TOOLS: [&str; 2] = ["AskUserQuestion", "ExitPlanMode"];

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
    // the session is working or failed. Its id goes into frames only when it is
    // a plain token, as in the sh form.
    let main_thread = payload.get("agent_id").is_none();
    let sub_agent = str_field(payload, "agent_id").filter(|id| plain(id));
    if !main_thread && sub_agent.is_none() {
        return Vec::new();
    }
    let main = |value: &str| -> Vec<String> {
        main_thread.then(|| status_frame(&key, value, env)).into_iter().collect()
    };
    let asker = sub_agent.unwrap_or("main");
    match event {
        "SessionStart" => sid
            .map(|sid| resume_frame("claude", sid, env))
            .into_iter()
            .collect(),
        // A prompt submitted mid-turn queues behind it (desktop socketsrv.rs).
        "UserPromptSubmit" if woke(payload) => main(&format!("{CLAUDE_PROMPTED} --wake")),
        "UserPromptSubmit" => main(CLAUDE_PROMPTED),
        "PreToolUse" => main(CLAUDE_TOOL),
        "PreCompact" => main(CLAUDE_COMPACTING),
        // A sub-agent carrying on speaks only to a question it asked itself.
        "PostToolUse" | "PostToolUseFailure" | "ElicitationResult" => match sub_agent {
            Some(id) => vec![status_frame(&key, &format!("{CLAUDE_RUNNING} --answered={id}"), env)],
            None => main(CLAUDE_RUNNING),
        },
        "PostCompact" => vec![format!("{} --compact{}", live_clear(&key, env), reporter(env))],
        "PermissionRequest" => {
            let form = str_field(payload, "tool_name").is_some_and(|tool| FORM_TOOLS.contains(&tool));
            let form = if form { " --form" } else { "" };
            vec![status_frame(&key, &format!("{WAITING} --asker={asker}{form}"), env)]
        }
        // Only the 6 s reminder of a dialog: it can't say whose dialog it is.
        "Notification" => vec![status_frame(&key, WAITING, env)],
        // An MCP dialog: only its ElicitationResult may end this Waiting.
        "Elicitation" => vec![status_frame(&key, &format!("{HELD_WAITING} --asker={asker}"), env)],
        "Stop" => vec![status_frame(&key, &stop_report(payload), env)],
        "StopFailure" => main(ERROR),
        // A sub-agent's or teammate's stint of work; Claude's own side agents
        // report no type.
        "SubagentStart" | "SubagentStop" => {
            let phase = if event == "SubagentStart" { "start" } else { "stop" };
            sub_agent
                .filter(|_| str_field(payload, "agent_type").is_some())
                .map(|id| child_frame(&key, id, phase, env))
                .into_iter()
                .collect()
        }
        "SessionEnd" => vec![clear_live_frame(&key, env)],
        _ => Vec::new(),
    }
}

fn plain(id: &str) -> bool {
    id.chars().all(|c| c.is_ascii_alphanumeric() || "_.-".contains(c))
}

fn woke(payload: &Value) -> bool {
    str_field(payload, "prompt").is_some_and(|prompt| WAKE_TAGS.iter().any(|tag| prompt.starts_with(tag)))
}

/// `clear_status <project> <key> --live`, without the reporter.
fn live_clear(key: &str, env: &HookEnv) -> String {
    format!("clear_status {} {} --live", quote_arg(&env.project), token(key))
}

/// A sub-agent's stint of work on the tab, as the sh form reports it.
fn child_frame(key: &str, child: &str, phase: &str, env: &HookEnv) -> String {
    format!(
        "agent_child {} {} {} {phase} --pane={}{}",
        quote_arg(&env.project),
        token(key),
        token(child),
        token(&env.pane),
        reporter(env)
    )
}

/// The Stop's report, with the task list it stopped beside (an empty one
/// included), as the sh form sends it: for the app to tell a build it will be
/// woken by from a server, and to see which sub-agents are still alive.
fn stop_report(payload: &Value) -> String {
    let status = stop_status(payload);
    let tasks = payload.get("background_tasks").and_then(Value::as_array);
    match tasks {
        Some(tasks) => format!("{status} --bg-hex={}", hex(Value::Array(tasks.clone()).to_string().as_bytes())),
        None => status.to_string(),
    }
}

fn hex(bytes: &[u8]) -> String {
    bytes.iter().map(|b| format!("{b:02x}")).collect()
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
