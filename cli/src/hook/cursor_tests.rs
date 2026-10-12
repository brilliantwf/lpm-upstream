use super::tests::{env, none, one};
use super::*;

fn stop(status: &str) -> String {
    let payload = format!(
        r#"{{"conversation_id":"c1","generation_id":"g1","model":"auto","status":"{status}","loop_count":0,"session_id":"c1","hook_event_name":"stop"}}"#
    );
    one("cursor", "stop", &payload)
}

#[test]
fn cursor_frames_match_the_sh_hooks() {
    let p = r#"{"conversation_id":"c1","prompt":"hi","hook_event_name":"beforeSubmitPrompt"}"#;
    for event in ["beforeSubmitPrompt", "postToolUse", "postToolUseFailure"] {
        assert_eq!(
            one("cursor", event, p),
            "set_status 'proj' cursor_pane-1 Running --icon=bolt --color=#8B5CF6 --pane=pane-1 --reporter-pid=42"
        );
    }
    assert_eq!(
        stop("completed"),
        "set_status 'proj' cursor_pane-1 Done --icon=checkmark --color=#4ade80 --pane=pane-1 --reporter-pid=42"
    );
    assert_eq!(
        stop("error"),
        "set_status 'proj' cursor_pane-1 Error --icon=warning --color=#ef4444 --pane=pane-1 --reporter-pid=42"
    );
    assert_eq!(stop("aborted"), "clear_status 'proj' cursor_pane-1 --live --reporter-pid=42");
    assert_eq!(
        one("cursor", "sessionEnd", p),
        "clear_status 'proj' cursor_pane-1 --live --reporter-pid=42"
    );
    none("cursor", "preToolUse", p);
}

#[test]
fn claude_hooks_step_aside_when_cursor_runs_them() {
    let mut e = env();
    e.under_cursor = true;
    assert!(frames("claude", "UserPromptSubmit", br#"{"session_id":"s1"}"#, &e).is_empty());
    assert!(!frames("cursor", "stop", b"{}", &e).is_empty());
    assert!(!frames("claude", "statusline", b"{}", &e).is_empty());
}
