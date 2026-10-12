use super::*;
#[cfg(unix)]
use crate::hooks::plugin::tests::{frames_by_state, reported};
use crate::hooks::plugin::tests::assert_hooks_for_every_state;
#[cfg(unix)]
use serde_json::{json, Value};

#[test]
fn the_plugin_runs_a_hook_for_every_state() {
    assert_hooks_for_every_state(&PLUGIN);
}

#[cfg(unix)]
#[test]
fn states_report_under_the_session_like_the_cli_does() {
    assert_eq!(
        frames_by_state(&PLUGIN),
        [
            "set_status 'proj' omp_ses_1 Running --icon=bolt --color=#F97316 --pane=pane-1",
            "set_status 'proj' omp_ses_1 Waiting --icon=bell --color=#f59e0b --pane=pane-1 --hold",
            "set_status 'proj' omp_ses_1 Done --icon=checkmark --color=#4ade80 --pane=pane-1",
            "set_status 'proj' omp_ses_1 Error --icon=warning --color=#ef4444 --pane=pane-1",
            "clear_status 'proj' omp_ses_1 --live",
        ]
    );
}

/// `name` with `event` in main session `s`.
#[cfg(unix)]
fn main_in(s: &str, name: &str, event: Value) -> Value {
    json!([name, event, s, "main"])
}

#[cfg(unix)]
fn main(name: &str, event: Value) -> Value {
    main_in("s1", name, event)
}

#[cfg(unix)]
fn sub(name: &str, event: Value) -> Value {
    json!([name, event, "s2", "sub"])
}

#[cfg(unix)]
fn start() -> Value {
    main("agent_start", json!({}))
}

#[cfg(unix)]
fn end_event(stop: &str, will_continue: bool) -> Value {
    let messages = json!([{ "role": "user" }, { "role": "assistant", "stopReason": stop }, { "role": "toolResult" }]);
    json!({ "messages": messages, "willContinue": will_continue })
}

#[cfg(unix)]
fn end(stop: &str) -> Value {
    main("agent_end", end_event(stop, false))
}

#[cfg(unix)]
fn tool(phase: &str, name: &str, id: &str) -> Value {
    main(&format!("tool_execution_{phase}"), json!({ "toolName": name, "toolCallId": id }))
}

#[cfg(unix)]
fn approval(phase: &str, id: &str) -> Value {
    main(&format!("tool_approval_{phase}"), json!({ "toolName": "bash", "toolCallId": id }))
}

/// Calls the plugin's handlers as omp does, each session with its own context.
#[cfg(unix)]
const HARNESS: &str = "const { default: lpmStatus } = await import(process.argv[2])\n\
    const handlers = {}\n\
    lpmStatus({ on: (name, handler) => (handlers[name] ??= []).push(handler) })\n\
    for (const [name, event, sid, kind] of JSON.parse(process.argv[3])) {\n\
      const ctx = { agent: { kind }, sessionManager: { getSessionId: () => sid } }\n\
      for (const handler of handlers[name] ?? []) await handler(event, ctx)\n\
    }\n";

/// Event sequences as omp 18.10 sends them, captured from its TUI.
#[cfg(unix)]
#[test]
fn the_plugin_follows_omp_turns() {
    let cases: Vec<(&str, Vec<Value>, &[&str])> = vec![
        ("finished", vec![start(), end("stop")], &["running s1", "done s1"]),
        (
            "approved",
            vec![
                start(),
                tool("start", "bash", "c1"),
                approval("requested", "c1"),
                approval("resolved", "c1"),
                tool("end", "bash", "c1"),
                end("stop"),
            ],
            &["running s1", "waiting s1", "running s1", "done s1"],
        ),
        (
            "two approvals open",
            vec![
                start(),
                approval("requested", "c1"),
                approval("requested", "c2"),
                approval("resolved", "c1"),
                approval("resolved", "c2"),
                end("stop"),
            ],
            &["running s1", "waiting s1", "running s1", "done s1"],
        ),
        (
            "question answered",
            vec![start(), tool("start", "ask", "c1"), tool("end", "ask", "c1"), end("stop")],
            &["running s1", "waiting s1", "running s1", "done s1"],
        ),
        (
            "question approved before it is asked",
            vec![
                start(),
                tool("start", "ask", "c1"),
                approval("requested", "c1"),
                approval("resolved", "c1"),
                tool("end", "ask", "c1"),
                end("stop"),
            ],
            &["running s1", "waiting s1", "running s1", "done s1"],
        ),
        (
            "question dismissed",
            vec![start(), tool("start", "ask", "c1"), tool("end", "ask", "c1"), end("aborted")],
            &["running s1", "waiting s1", "running s1", "clear s1"],
        ),
        ("interrupted", vec![start(), end("aborted")], &["running s1", "clear s1"]),
        ("failed", vec![start(), end("error")], &["running s1", "error s1"]),
        (
            "retried, then finished",
            vec![start(), main("agent_end", end_event("error", true)), start(), end("stop")],
            &["running s1", "done s1"],
        ),
        (
            "a background sub-agent's result picked up",
            vec![
                start(),
                tool("start", "task", "c1"),
                tool("end", "task", "c1"),
                main("agent_end", end_event("stop", true)),
                sub("agent_start", json!({})),
                sub("tool_approval_requested", json!({ "toolName": "bash", "toolCallId": "c2" })),
                sub("tool_approval_resolved", json!({ "toolName": "bash", "toolCallId": "c2" })),
                sub("agent_end", end_event("toolUse", false)),
                sub("session_shutdown", json!({})),
                start(),
                end("stop"),
            ],
            &["running s1", "done s1"],
        ),
        (
            "a new session mid-turn",
            vec![
                start(),
                main_in("s3", "session_switch", json!({ "reason": "new" })),
                main_in("s3", "agent_start", json!({})),
                main_in("s3", "agent_end", end_event("stop", false)),
            ],
            &["running s1", "clear s1", "running s3", "done s3"],
        ),
        ("quit mid-turn", vec![start(), main("session_shutdown", json!({}))], &["running s1", "clear s1"]),
        (
            "quit after the turn",
            vec![start(), end("stop"), main("session_shutdown", json!({}))],
            &["running s1", "done s1"],
        ),
    ];
    for (case, events, expected) in cases {
        let Some(states) = reported(&PLUGIN, HARNESS, events) else { return };
        assert_eq!(states, expected, "{case}");
    }
}
