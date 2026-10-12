use super::tests::{env, none, one};
use super::*;

#[test]
fn opencode_frames_match_the_sh_hooks() {
    let p = r#"{"session_id":"ses_1"}"#;
    assert_eq!(
        one("opencode", "running", p),
        "set_status 'proj' opencode_ses_1 Running --icon=bolt --color=#FAB283 --pane=pane-1 --reporter-pid=42"
    );
    assert_eq!(
        one("opencode", "waiting", p),
        "set_status 'proj' opencode_ses_1 Waiting --icon=bell --color=#f59e0b --hold --pane=pane-1 --reporter-pid=42"
    );
    assert_eq!(
        one("opencode", "done", p),
        "set_status 'proj' opencode_ses_1 Done --icon=checkmark --color=#4ade80 --pane=pane-1 --reporter-pid=42"
    );
    assert_eq!(
        one("opencode", "error", p),
        "set_status 'proj' opencode_ses_1 Error --icon=warning --color=#ef4444 --pane=pane-1 --reporter-pid=42"
    );
    assert_eq!(
        one("opencode", "clear", p),
        "clear_status 'proj' opencode_ses_1 --live --reporter-pid=42"
    );
    none("opencode", "session.idle", p);
}

#[test]
fn opencode_keys_by_pane_without_a_session_id() {
    assert!(one("opencode", "done", "{}").contains(" opencode_pane-1 Done "));
}
