use super::tests::{none, one};

#[test]
fn plugin_frames_match_the_sh_hooks() {
    for (agent, color) in [("opencode", "#FAB283"), ("omp", "#F97316")] {
        let p = r#"{"session_id":"ses_1"}"#;
        assert_eq!(
            one(agent, "running", p),
            format!("set_status 'proj' {agent}_ses_1 Running --icon=bolt --color={color} --pane=pane-1 --reporter-pid=42")
        );
        assert_eq!(
            one(agent, "waiting", p),
            format!("set_status 'proj' {agent}_ses_1 Waiting --icon=bell --color=#f59e0b --hold --pane=pane-1 --reporter-pid=42")
        );
        assert_eq!(
            one(agent, "done", p),
            format!("set_status 'proj' {agent}_ses_1 Done --icon=checkmark --color=#4ade80 --pane=pane-1 --reporter-pid=42")
        );
        assert_eq!(
            one(agent, "error", p),
            format!("set_status 'proj' {agent}_ses_1 Error --icon=warning --color=#ef4444 --pane=pane-1 --reporter-pid=42")
        );
        assert_eq!(
            one(agent, "clear", p),
            format!("clear_status 'proj' {agent}_ses_1 --live --reporter-pid=42")
        );
        none(agent, "session.idle", p);
    }
}

#[test]
fn plugin_frames_key_by_pane_without_a_session_id() {
    assert!(one("opencode", "done", "{}").contains(" opencode_pane-1 Done "));
    assert!(one("omp", "done", "{}").contains(" omp_pane-1 Done "));
}
