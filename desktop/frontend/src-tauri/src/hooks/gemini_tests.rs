use super::*;
use crate::hooks::strip_hooks_json;
#[cfg(unix)]
use crate::hooks::test_support::*;
use std::path::PathBuf;

fn installed(data: &[u8]) -> Value {
    serde_json::from_slice(&merge_gemini_hooks(data).unwrap()).unwrap()
}

#[test]
fn install_starts_settings_keeps_the_users_own_and_is_idempotent() {
    let fresh = installed(b"");
    for event in GEMINI_EVENTS {
        let hook = &fresh["hooks"][event][0]["hooks"][0];
        assert_eq!(hook["name"], "lpm", "{event}");
        assert!(hook["command"].as_str().unwrap().ends_with(MARKER), "{event}");
    }
    let user = br#"{"model":{"name":"gemini-3-pro"},"hooks":{"AfterAgent":[{"matcher":"","hooks":[{"type":"command","command":"say done"}]}]}}"#;
    let merged = merge_gemini_hooks(user).unwrap();
    let v: Value = serde_json::from_slice(&merged).unwrap();
    assert_eq!(v["model"]["name"], "gemini-3-pro");
    assert_eq!(v["hooks"]["AfterAgent"].as_array().unwrap().len(), 2);
    assert_eq!(merge_gemini_hooks(&merged), None);
    let stripped: Value = serde_json::from_slice(&strip_hooks_json(&merged).unwrap()).unwrap();
    assert_eq!(
        stripped["hooks"],
        json!({ "AfterAgent": [{ "matcher": "", "hooks": [{ "type": "command", "command": "say done" }] }] })
    );
}

#[test]
fn settings_it_cannot_parse_are_left_alone() {
    assert_eq!(merge_gemini_hooks(b"{ // comments\n}"), None);
    assert_eq!(merge_gemini_hooks(b"[]"), None);
}

#[test]
fn hooks_keep_their_stderr_from_gemini() {
    for event in GEMINI_EVENTS {
        assert!(gemini_cmd(event).starts_with("exec 2>/dev/null; "), "{event}");
    }
}

#[test]
fn windows_runs_the_cli_through_powershell() {
    let cli = HookForm::Cli(PathBuf::from(r"C:\lpm\lpm-cli.exe"));
    let v: Value = serde_json::from_slice(&merge_gemini_hooks_in(b"{}", &cli).unwrap()).unwrap();
    assert_eq!(
        v["hooks"]["AfterAgent"][0]["hooks"][0]["command"],
        format!(r"C:\lpm\lpm-cli.exe hook gemini AfterAgent '{MARKER}'")
    );
}

#[cfg(unix)]
#[test]
fn events_report_under_the_session_like_the_cli_does() {
    let v = installed(b"");
    let run = |event: &str| {
        let cmd = v["hooks"][event][0]["hooks"][0]["command"].as_str().unwrap();
        let payload = format!(
            r#"{{"session_id":"s1","transcript_path":"/t","cwd":"/c","hook_event_name":"{event}","timestamp":"2026-10-12T00:00:00Z","tool_input":{{"session_id":"x"}}}}"#
        );
        run_codex_hook(cmd, &payload).expect(event)
    };
    let running = "set_status 'proj' gemini_s1 Running --icon=sparkle --color=#4796E3 --pane=pane-1 --reporter-pid=";
    for event in ["BeforeAgent", "BeforeTool", "AfterTool"] {
        assert!(run(event).starts_with(running), "{event}");
    }
    assert!(run("Notification").starts_with("set_status 'proj' gemini_s1 Waiting --icon=bell --color=#f59e0b --pane=pane-1 "));
    assert!(run("AfterAgent").starts_with("set_status 'proj' gemini_s1 Done --icon=checkmark --color=#4ade80 --pane=pane-1 "));
    assert!(run("SessionEnd").starts_with("clear_status 'proj' gemini_s1 --live --reporter-pid="));
}
