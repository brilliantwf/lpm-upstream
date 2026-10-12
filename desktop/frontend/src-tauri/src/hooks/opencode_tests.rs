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
            "set_status 'proj' opencode_ses_1 Running --icon=bolt --color=#FAB283 --pane=pane-1",
            "set_status 'proj' opencode_ses_1 Waiting --icon=bell --color=#f59e0b --pane=pane-1 --hold",
            "set_status 'proj' opencode_ses_1 Done --icon=checkmark --color=#4ade80 --pane=pane-1",
            "set_status 'proj' opencode_ses_1 Error --icon=warning --color=#ef4444 --pane=pane-1",
            "clear_status 'proj' opencode_ses_1 --live",
        ]
    );
}

#[cfg(unix)]
fn status(sid: &str, kind: &str) -> Value {
    json!({ "type": "session.status", "properties": { "sessionID": sid, "status": { "type": kind } } })
}

#[cfg(unix)]
fn idle(sid: &str) -> [Value; 2] {
    [status(sid, "idle"), json!({ "type": "session.idle", "properties": { "sessionID": sid } })]
}

#[cfg(unix)]
fn created(sid: &str, parent: &str) -> Value {
    json!({ "type": "session.created", "properties": { "info": { "id": sid, "parentID": parent } } })
}

#[cfg(unix)]
fn asked(kind: &str, sid: &str, id: &str) -> Value {
    json!({ "type": format!("{kind}.asked"), "properties": { "id": id, "sessionID": sid } })
}

#[cfg(unix)]
fn replied(sid: &str, id: &str, reply: &str) -> Value {
    json!({ "type": "permission.replied", "properties": { "sessionID": sid, "requestID": id, "reply": reply } })
}

#[cfg(unix)]
fn error(sid: &str, name: &str) -> Value {
    json!({ "type": "session.error", "properties": { "sessionID": sid, "error": { "name": name, "data": {} } } })
}

/// Feeds the plugin's event hook as OpenCode does.
#[cfg(unix)]
const HARNESS: &str = "const { LpmStatus } = await import(process.argv[2])\n\
    const plugin = await LpmStatus({})\n\
    for (const event of JSON.parse(process.argv[3])) await plugin.event({ event })\n\
    await plugin.dispose()\n";

/// Event sequences as OpenCode 1.19 sends them, captured from its TUI.
#[cfg(unix)]
#[test]
fn the_plugin_follows_opencode_turns() {
    let turn = |middle: Vec<Value>| -> Vec<Value> {
        let mut events = vec![status("r", "busy"), status("r", "busy")];
        events.extend(middle);
        events.extend(idle("r"));
        events
    };
    let cases: Vec<(&str, Vec<Value>, &[&str])> = vec![
        ("finished", turn(vec![status("r", "busy")]), &["running r", "done r"]),
        (
            "approved",
            turn(vec![asked("permission", "r", "p1"), replied("r", "p1", "once"), status("r", "busy")]),
            &["running r", "waiting r", "running r", "done r"],
        ),
        (
            "rejected",
            turn(vec![asked("permission", "r", "p1"), replied("r", "p1", "reject")]),
            &["running r", "waiting r", "clear r"],
        ),
        (
            "interrupted",
            [turn(vec![error("r", "MessageAbortedError")]), idle("r").to_vec()].concat(),
            &["running r", "clear r"],
        ),
        ("failed", turn(vec![error("r", "APIError")]), &["running r", "error r"]),
        (
            "compacted after overflowing",
            turn(vec![error("r", "ContextOverflowError"), status("r", "busy")]),
            &["running r", "done r"],
        ),
        (
            "question dismissed",
            turn(vec![
                asked("question", "r", "q1"),
                json!({ "type": "question.rejected", "properties": { "sessionID": "r", "requestID": "q1" } }),
            ]),
            &["running r", "waiting r", "clear r"],
        ),
        (
            "two approvals open",
            turn(vec![
                asked("permission", "r", "p1"),
                asked("permission", "r", "p2"),
                replied("r", "p1", "once"),
                replied("r", "p2", "always"),
            ]),
            &["running r", "waiting r", "running r", "done r"],
        ),
        (
            "sub-agent asks and is turned down",
            turn(vec![
                created("c", "r"),
                status("c", "busy"),
                asked("permission", "c", "p1"),
                replied("c", "p1", "reject"),
                error("c", "APIError"),
                idle("c")[0].clone(),
                status("r", "busy"),
            ]),
            &["running r", "waiting r", "running r", "done r"],
        ),
    ];
    for (case, events, expected) in cases {
        let Some(states) = reported(&PLUGIN, HARNESS, events) else { return };
        assert_eq!(states, expected, "{case}");
    }
}
