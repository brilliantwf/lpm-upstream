use super::tests::{none, one};

#[test]
fn claude_frames_match_the_sh_hooks() {
    let p = r#"{"session_id":"s1","cwd":"/tmp/p"}"#;
    assert_eq!(
        one("claude", "SessionStart", p),
        "set_resume 'proj' pane-1 s1 --provider=claude --reporter-pid=42"
    );
    assert_eq!(
        one("claude", "UserPromptSubmit", p),
        "set_status 'proj' claude_code_s1 Running --icon=bolt --color=#4C8DFF --prompt --pane=pane-1 --reporter-pid=42"
    );
    for event in [
        "PreToolUse",
        "PostToolUse",
        "PostToolUseFailure",
        "ElicitationResult",
    ] {
        assert_eq!(
            one("claude", event, p),
            "set_status 'proj' claude_code_s1 Running --icon=bolt --color=#4C8DFF --pane=pane-1 --reporter-pid=42"
        );
    }
    for event in ["Notification", "PermissionRequest"] {
        assert_eq!(
            one("claude", event, p),
            "set_status 'proj' claude_code_s1 Waiting --icon=bell --color=#f59e0b --pane=pane-1 --reporter-pid=42"
        );
    }
    assert_eq!(
        one("claude", "Elicitation", p),
        "set_status 'proj' claude_code_s1 Waiting --icon=bell --color=#f59e0b --hold --pane=pane-1 --reporter-pid=42"
    );
    assert_eq!(
        one("claude", "StopFailure", p),
        "set_status 'proj' claude_code_s1 Error --icon=warning --color=#ef4444 --pane=pane-1 --reporter-pid=42"
    );
    assert_eq!(
        one("claude", "SessionEnd", p),
        "clear_status 'proj' claude_code_s1 --live --reporter-pid=42"
    );
}

#[test]
fn claude_keys_by_pane_without_a_session_id_and_skips_resume() {
    assert!(one("claude", "PreToolUse", "{}").contains(" claude_code_pane-1 Running"));
    assert!(one("claude", "PreToolUse", "not json").contains(" claude_code_pane-1 Running"));
    none("claude", "SessionStart", r#"{"session_id":""}"#);
}

fn stop(tasks: &str) -> String {
    let payload = format!(
        r#"{{"session_id":"s1","hook_event_name":"Stop","last_assistant_message":"x","background_tasks":{tasks},"session_crons":[]}}"#
    );
    one("claude", "Stop", &payload)
}

fn stop_is(tasks: &str, value: &str) {
    let line = stop(tasks);
    assert!(
        line.contains(&format!("claude_code_s1 {value} ")),
        "{tasks}\n{line}"
    );
}

#[test]
fn claude_stop_keeps_running_while_background_work_is_in_flight() {
    stop_is(
        r#"[{"id":"bw1","type":"workflow","status":"running","description":"audit"}]"#,
        "Running",
    );
    stop_is(
        r#"[{"id":"b","type":"subagent","status":"running","description":"Review"}]"#,
        "Running",
    );
    stop_is(
        r#"[{"id":"b","type":"cloud session","status":"running","description":"x"}]"#,
        "Running",
    );
    for ambient in ["dream", "auto-mode scan", "memory import"] {
        stop_is(
            &format!(r#"[{{"id":"b","type":"{ambient}","status":"running","description":"x"}}]"#),
            "Done",
        );
    }
    stop_is("[]", "Done");
    assert!(one("claude", "Stop", r#"{"session_id":"s1"}"#).contains("claude_code_s1 Done"));
    let decoy = r#"{"session_id":"s1","last_assistant_message":"I set \"background_tasks\":[{ here","background_tasks":[]}"#;
    assert!(one("claude", "Stop", decoy).contains("claude_code_s1 Done"));
}

#[test]
fn claude_stop_treats_teammates_monitors_and_shells_as_finished() {
    for tasks in [
        r#"[{"id":"b","type":"teammate","status":"running","description":"Fix }] in package.json"}]"#,
        r#"[{"id":"b","type":"teammate","status":"running","description":"Fix [BUG-1]"},{"id":"c","type":"monitor","status":"running","description":"watch"}]"#,
        r#"[{"id":"b","type":"monitor","status":"running","description":"ci","server":"github","tool":"get_workflow_run"}]"#,
        r#"[{"id":"b","type":"shell","status":"running","description":"cfg","command":"printf '{\"type\":\"workflow\"}' > c.json"}]"#,
        r#"[{"id":"b","type":"shell","status":"running","description":"dev","command":"npm run dev"},{"id":"c","type":"teammate","status":"running","description":"t"}]"#,
    ] {
        stop_is(tasks, "Done");
    }
}

#[test]
fn claude_stop_never_downgrades_a_live_entry_behind_settled_ones() {
    for tasks in [
        r#"[{"id":"b","type":"teammate","status":"running","description":"see }]}x"},{"id":"c","type":"subagent","status":"running","description":"Review"}]"#,
        r#"[{"id":"b","type":"monitor","status":"running","description":"x"},{"id":"c","type":"workflow","status":"running","description":"y"}]"#,
        r#"[{"id":"b","type":"shell","status":"running","description":"dev"},{"id":"c","type":"subagent","status":"running","description":"Audit"}]"#,
        r#"[{"id":"b","type":"subagent","status":"running","description":"set \"type\":\"monitor\" in config"}]"#,
        r#"[{"id":"b","type":"monitors","status":"running","description":"x"}]"#,
        r#"[{"id":"b","type":"shells","status":"running","description":"x"}]"#,
    ] {
        stop_is(tasks, "Running");
    }
}

#[test]
fn claude_stop_ignores_task_types_quoted_in_cron_prompts() {
    let payload = r#"{"session_id":"s1","background_tasks":[{"id":"b","type":"teammate","status":"running","description":"x"}],"session_crons":[{"id":"c1","prompt":"run the \"type\":\"workflow\" audit over [drafts]"}]}"#;
    assert!(one("claude", "Stop", payload).contains("claude_code_s1 Done"));
}

#[test]
fn a_claude_sub_agent_can_ask_but_not_speak_for_the_session() {
    let sub = r#"{"session_id":"s1","agent_id":"a7","agent_type":"general-purpose"}"#;
    for event in ["UserPromptSubmit", "PreToolUse", "PostToolUse", "PostToolUseFailure", "StopFailure"] {
        none("claude", event, sub);
    }
    assert!(one("claude", "PermissionRequest", sub).contains("claude_code_s1 Waiting"));
}
