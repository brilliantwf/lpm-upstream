use super::tests::{env, none, one};
use super::*;

#[test]
fn gemini_frames_match_the_sh_hooks() {
    let p = r#"{"session_id":"s1","transcript_path":"/t","cwd":"/c","hook_event_name":"BeforeTool","tool_input":{"session_id":"x"}}"#;
    for event in ["BeforeAgent", "BeforeTool", "AfterTool"] {
        assert_eq!(
            one("gemini", event, p),
            "set_status 'proj' gemini_s1 Running --icon=sparkle --color=#4796E3 --pane=pane-1 --reporter-pid=42"
        );
    }
    assert_eq!(
        one("gemini", "Notification", p),
        "set_status 'proj' gemini_s1 Waiting --icon=bell --color=#f59e0b --pane=pane-1 --reporter-pid=42"
    );
    assert_eq!(
        one("gemini", "AfterAgent", p),
        "set_status 'proj' gemini_s1 Done --icon=checkmark --color=#4ade80 --pane=pane-1 --reporter-pid=42"
    );
    assert_eq!(
        one("gemini", "SessionEnd", p),
        "clear_status 'proj' gemini_s1 --live --reporter-pid=42"
    );
    none("gemini", "SessionStart", p);
}

#[test]
fn gemini_keys_by_pane_without_a_session_id() {
    assert!(one("gemini", "AfterAgent", "{}").contains(" gemini_pane-1 Done "));
    assert!(frames("gemini", "AfterAgent", b"{}", &env()).len() == 1);
}
