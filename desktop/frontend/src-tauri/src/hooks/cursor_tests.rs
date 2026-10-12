use super::*;
use crate::hooks::strip_hooks_json;
#[cfg(unix)]
use crate::hooks::test_support::*;
use std::path::PathBuf;

fn installed(data: &[u8]) -> Value {
    serde_json::from_slice(&merge_cursor_hooks(data).unwrap()).unwrap()
}

#[test]
fn install_starts_hooks_json_keeps_the_users_own_and_is_idempotent() {
    let fresh = installed(b"");
    assert_eq!(fresh["version"], 1);
    for event in CURSOR_EVENTS {
        let command = fresh["hooks"][event][0]["command"].as_str().unwrap();
        assert!(command.starts_with("sh -c '") && command.ends_with(&format!("{MARKER}'")), "{event}");
    }
    let user = br#"{"version":1,"hooks":{"stop":[{"command":"./audit.sh","loop_limit":3}],"beforeShellExecution":[{"command":"./guard.sh"}]}}"#;
    let merged = merge_cursor_hooks(user).unwrap();
    let v: Value = serde_json::from_slice(&merged).unwrap();
    assert_eq!(v["hooks"]["stop"][0], json!({ "command": "./audit.sh", "loop_limit": 3 }));
    assert_eq!(v["hooks"]["stop"].as_array().unwrap().len(), 2);
    assert_eq!(merge_cursor_hooks(&merged), None);
    let stripped: Value = serde_json::from_slice(&strip_hooks_json(&merged).unwrap()).unwrap();
    assert_eq!(stripped, serde_json::from_slice::<Value>(user).unwrap());
}

#[test]
fn hooks_json_it_cannot_parse_is_left_alone() {
    assert_eq!(merge_cursor_hooks(b"{ // comments\n\"version\": 1 }"), None);
}

#[test]
fn windows_runs_the_cli_through_powershell() {
    let cli = HookForm::Cli(PathBuf::from(r"C:\lpm\lpm-cli.exe"));
    let v: Value = serde_json::from_slice(&merge_cursor_hooks_in(b"", &cli).unwrap()).unwrap();
    assert_eq!(
        v["hooks"]["stop"][0]["command"],
        format!(r"C:\lpm\lpm-cli.exe hook cursor stop '{MARKER}'")
    );
}

/// The installed command for `event`, run as Cursor runs it: the payload as a
/// heredoc appended to the command, nothing on stdin.
#[cfg(unix)]
fn run_as_cursor(event: &str, payload: &str) -> Option<String> {
    let v = installed(b"");
    let cmd = v["hooks"][event][0]["command"].as_str().unwrap();
    let line = format!("{cmd} <<'CURSOR_HOOK_EOF'\n{payload}\nCURSOR_HOOK_EOF");
    run_hook_with_env(&line, "", "proj", &[("CURSOR_VERSION", "2026.10.01")])
}

#[cfg(unix)]
fn stop_payload(status: &str) -> String {
    format!(
        r#"{{"conversation_id":"c1","generation_id":"g1","model":"auto","status":"{status}","loop_count":0,"session_id":"c1","hook_event_name":"stop","cursor_version":"2026.10.01","workspace_roots":["/w"],"user_email":null,"transcript_path":null}}"#
    )
}

#[cfg(unix)]
#[test]
fn a_turn_runs_and_ends_the_way_its_stop_says() {
    let prompt = r#"{"conversation_id":"c1","generation_id":"g1","prompt":"hi","hook_event_name":"beforeSubmitPrompt"}"#;
    let running = "set_status 'proj' cursor_pane-1 Running --icon=bolt --color=#8B5CF6 --pane=pane-1 --reporter-pid=";
    assert!(run_as_cursor("beforeSubmitPrompt", prompt).unwrap().starts_with(running));
    assert!(run_as_cursor("postToolUse", prompt).unwrap().starts_with(running));
    assert!(run_as_cursor("stop", &stop_payload("completed"))
        .unwrap()
        .starts_with("set_status 'proj' cursor_pane-1 Done --icon=checkmark --color=#4ade80 --pane=pane-1 "));
    assert!(run_as_cursor("stop", &stop_payload("error"))
        .unwrap()
        .starts_with("set_status 'proj' cursor_pane-1 Error --icon=warning --color=#ef4444 --pane=pane-1 "));
    assert!(run_as_cursor("stop", &stop_payload("aborted"))
        .unwrap()
        .starts_with("clear_status 'proj' cursor_pane-1 --live --reporter-pid="));
    assert!(run_as_cursor("sessionEnd", prompt)
        .unwrap()
        .starts_with("clear_status 'proj' cursor_pane-1 --live --reporter-pid="));
}

#[cfg(unix)]
#[test]
fn the_payload_is_data_never_shell() {
    let td = tempfile::tempdir().unwrap();
    let ran = td.path().join("ran");
    let prompt = format!(
        r#"{{"conversation_id":"c1","prompt":"it's `touch {}` and $(touch {})","hook_event_name":"beforeSubmitPrompt"}}"#,
        ran.display(),
        ran.display()
    );
    assert!(run_as_cursor("beforeSubmitPrompt", &prompt).is_some());
    assert!(!ran.exists());
}

#[cfg(unix)]
#[test]
fn a_payload_on_stdin_works_too() {
    let v = installed(b"");
    let cmd = v["hooks"]["stop"][0]["command"].as_str().unwrap();
    assert!(run_codex_hook(cmd, &stop_payload("completed")).unwrap().contains(" cursor_pane-1 Done "));
}
