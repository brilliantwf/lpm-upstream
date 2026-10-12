use super::tests::{env, none, one};
use super::*;
use std::path::Path;

fn tool_payload(subagent: bool, tool_name: &str, tool_input: &str) -> String {
    let agent = if subagent {
        r#""agent_id":"01a0","agent_type":"default","#
    } else {
        ""
    };
    format!(
        r#"{{"session_id":"s1","turn_id":"t1",{agent}"transcript_path":"/tmp/r.jsonl","cwd":"/tmp/p","permission_mode":"default","tool_name":"{tool_name}","tool_input":{tool_input}}}"#
    )
}

#[test]
fn codex_memory_consolidation_never_speaks_for_the_tab() {
    let payload = |cwd: &str| {
        format!(r#"{{"session_id":"m1","turn_id":"t1","cwd":"{cwd}","hook_event_name":"SessionStart"}}"#)
    };
    none("codex", "SessionStart", &payload("/home/u/.codex/memories"));
    none("codex", "UserPromptSubmit", &payload("/home/u/.codex/memories_v2/x"));
    assert_eq!(frames("codex", "SessionStart", payload("/work/app").as_bytes(), &env()).len(), 2);
}

#[test]
fn codex_sub_agents_report_their_start_and_stop() {
    let payload = r#"{"session_id":"s1","hook_event_name":"SubagentStop","agent_id":"019a-77","agent_type":"default","last_assistant_message":"x"}"#;
    assert_eq!(
        one("codex", "SubagentStop", payload),
        "agent_child 'proj' codex_pane-1 019a-77 stop --reporter-pid=42"
    );
    assert!(one("codex", "SubagentStart", payload).ends_with("019a-77 start --reporter-pid=42"));
    none("codex", "SubagentStart", r#"{"session_id":"s1"}"#);
}

#[test]
fn codex_frames_match_the_sh_hooks() {
    let shell = tool_payload(false, "shell", r#"{"command":["ls"]}"#);
    let running = "set_status 'proj' codex_pane-1 Running --icon=sparkle --color=#10A37F --pane=pane-1 --reporter-pid=42";
    let step = "set_status 'proj' codex_pane-1 Running --icon=sparkle --color=#10A37F --step --pane=pane-1 --reporter-pid=42";
    assert_eq!(one("codex", "UserPromptSubmit", &shell), running);
    assert_eq!(one("codex", "PostToolUse", &shell), running);
    assert_eq!(one("codex", "PreToolUse", &shell), step);
    assert_eq!(
        one("codex", "Stop", &shell),
        "set_status 'proj' codex_pane-1 Done --icon=checkmark --color=#4ade80 --pane=pane-1 --reporter-pid=42"
    );
    assert_eq!(
        frames("codex", "SessionStart", shell.as_bytes(), &env()),
        vec![
            running.to_string(),
            "set_resume 'proj' pane-1 s1 --provider=codex --reporter-pid=42".to_string()
        ]
    );
}

#[test]
fn codex_pre_tool_use_waits_only_on_request_user_input() {
    let ask = tool_payload(false, "request_user_input", r#"{"questions":[]}"#);
    assert!(one("codex", "PreToolUse", &ask).contains("codex_pane-1 Waiting --icon=bell"));
    let decoy = tool_payload(false, "shell", r#"{"tool_name":"request_user_input"}"#);
    assert!(one("codex", "PreToolUse", &decoy).contains("codex_pane-1 Running"));
}

#[test]
fn codex_sub_agents_never_speak_for_the_tab() {
    let sub = tool_payload(true, "request_user_input", r#"{"questions":[]}"#);
    for event in ["UserPromptSubmit", "PreToolUse", "PostToolUse", "Stop"] {
        none("codex", event, &sub);
    }
    none("codex", "SessionStart", &sub);
    let grep = tool_payload(
        false,
        "shell",
        r#"{"command":"grep -rn \"agent_id\" src/"}"#,
    );
    assert!(one("codex", "PostToolUse", &grep).contains("codex_pane-1 Running"));
}

fn permission(transcript: &str) -> String {
    let transcript = serde_json::to_string(transcript).unwrap();
    format!(
        r#"{{"session_id":"s1","transcript_path":{transcript},"cwd":"/tmp/p","permission_mode":"default","tool_name":"shell","tool_input":{{"command":["git","push"]}}}}"#
    )
}

fn rollout(dir: &Path, last_turn_context: &str, trailing: &str) -> String {
    let path = dir.join("rollout.jsonl");
    let stale = r#"{"type":"turn_context","payload":{"approval_policy":"untrusted","approvals_reviewer":"auto_review"}}"#;
    std::fs::write(&path, format!("{stale}\n{last_turn_context}\n{trailing}\n")).unwrap();
    path.to_string_lossy().into_owned()
}

#[test]
fn codex_permission_request_skips_auto_reviewed_sessions() {
    let td = tempfile::tempdir().unwrap();
    for policy in [
        r#""on-request""#,
        r#"{"granular":{"sandbox_approval":true}}"#,
    ] {
        let tc = format!(
            r#"{{"type":"turn_context","payload":{{"approval_policy":{policy},"approvals_reviewer":"auto_review"}}}}"#
        );
        let path = rollout(td.path(), &tc, r#"{"type":"reasoning"}"#);
        none("codex", "PermissionRequest", &permission(&path));
    }
}

#[test]
fn codex_permission_request_waits_when_the_user_decides() {
    let td = tempfile::tempdir().unwrap();
    for tc in [
        r#"{"type":"turn_context","payload":{"approval_policy":"untrusted","approvals_reviewer":"auto_review"}}"#,
        r#"{"type":"turn_context","payload":{"approval_policy":"on-request","approvals_reviewer":"user"}}"#,
    ] {
        let path = rollout(td.path(), tc, "");
        assert!(
            one("codex", "PermissionRequest", &permission(&path)).contains("codex_pane-1 Waiting")
        );
    }
    let missing = td.path().join("gone.jsonl");
    let line = one(
        "codex",
        "PermissionRequest",
        &permission(&missing.to_string_lossy()),
    );
    assert!(line.contains("codex_pane-1 Waiting"));
}

#[test]
fn codex_permission_request_finds_turn_context_behind_bulky_output() {
    let td = tempfile::tempdir().unwrap();
    let tc = r#"{"type":"turn_context","payload":{"approval_policy":"on-request","approvals_reviewer":"auto_review"}}"#;
    let bulk = format!(
        r#"{{"type":"event_msg","payload":{{"text":"{}"}}}}"#,
        "o".repeat(2 << 20)
    );
    let path = rollout(td.path(), tc, &bulk);
    none("codex", "PermissionRequest", &permission(&path));
}

#[test]
fn codex_interrupt_and_session_end_take_back_what_was_live() {
    let p = r#"{"session_id":"t1","turn_id":"u1","cwd":"/tmp/p"}"#;
    for event in ["Interrupt", "SessionEnd"] {
        assert_eq!(
            one("codex", event, p),
            "clear_status 'proj' codex_pane-1 --live --reporter-pid=42"
        );
    }
}
