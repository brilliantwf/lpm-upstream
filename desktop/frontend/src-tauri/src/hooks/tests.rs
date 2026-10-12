use super::codex::*;
use super::sh::*;
use super::test_support::*;
use super::*;

/// A per-session key alone can't tell the tab's agent from one it launched:
/// both report the same pane. The reporting pid is what places them in the
/// process tree, so every hook that speaks for an agent must carry one.
#[test]
fn every_agent_hook_reports_the_pid_it_ran_under() {
    let dir = tempfile::tempdir().unwrap();
    let claude = settings_at(dir.path(), "{}");
    install_claude_hooks_at(&claude).unwrap();
    let v: Value = serde_json::from_slice(&std::fs::read(&claude).unwrap()).unwrap();
    for (ev, entries) in v["hooks"].as_object().unwrap() {
        for e in entries.as_array().unwrap() {
            let cmd = e["hooks"][0]["command"].as_str().unwrap();
            assert!(
                cmd.contains(" --reporter-pid=$PPID\""),
                "claude {ev}: {cmd}"
            );
        }
    }
    let codex = merge_codex_hooks(b"{}").unwrap();
    let v: Value = serde_json::from_slice(&codex).unwrap();
    for (ev, entries) in v["hooks"].as_object().unwrap() {
        for e in entries.as_array().unwrap() {
            let cmd = e["hooks"][0]["command"].as_str().unwrap();
            assert!(cmd.contains(" --reporter-pid=$PPID\""), "codex {ev}: {cmd}");
        }
    }
}

/// `$PPID` has to expand in the hook's own shell — the process the agent
/// spawned — so it must be staged into `m`, never sent single-quoted.
#[test]
fn pid_option_expands_at_staging_time() {
    for cmd in [
        send_cmd("set_status 'p' k Running"),
        codex_status_cmd("set_status 'p' k Running"),
        claude_stop_cmd(),
        claude_resume_cmd(),
        codex_pre_tool_use_cmd(),
        codex_permission_request_cmd(),
    ] {
        let stage = cmd.find("m=\"").expect("message staged");
        let pid = cmd
            .find(" --reporter-pid=$PPID\"")
            .expect("pid option staged");
        assert!(stage < pid, "pid rides inside the staged message: {cmd}");
    }
}

#[test]
fn strip_hooks_json_removes_ours_and_keeps_user_hooks() {
    let installed = merge_claude_hooks(
        br#"{"model":"opus","hooks":{"Stop":[{"matcher":"","hooks":[{"type":"command","command":"my-own-hook"}]}]}}"#,
    )
    .unwrap();
    let stripped = strip_hooks_json(&installed).expect("strip changes settings");
    let v: Value = serde_json::from_slice(&stripped).unwrap();
    assert_eq!(v["model"], "opus");
    assert!(!has_marker(&v["hooks"]), "lpm hooks gone");
    assert_eq!(
        v["hooks"]["Stop"][0]["hooks"][0]["command"], "my-own-hook",
        "user hook kept"
    );
    assert!(
        strip_hooks_json(&stripped).is_none(),
        "second strip is a no-op"
    );
    assert!(strip_hooks_json(br#"{"model":"opus"}"#).is_none());
    assert!(strip_hooks_json(b"{ not json").is_none());
}

#[test]
fn strip_hooks_json_at_handles_missing_file() {
    let dir = tempfile::tempdir().unwrap();
    strip_hooks_json_at(&dir.path().join("settings.json")).unwrap();
}

#[test]
fn send_cmd_stages_message_and_guards_backgrounded_delivery() {
    let s = send_cmd("clear_status 'x' k");
    // The env-recovery preamble runs BEFORE the message is staged (staging
    // interpolates project/pane, which recovery may have just restored).
    let recover = s
        .find("tmux showenv -g LPM_SOCKET_PATH")
        .expect("recovery preamble");
    let stage = s
        .find(r#"m="clear_status 'x' k --reporter-pid=$PPID""#)
        .expect("message staged in m");
    assert!(recover < stage, "recovery must precede staging: {s}");
    // Gate: live socket AND known project AND known pane, so a socket-only
    // recovery never sends `set_status ''`.
    assert!(s.contains(
        r#"{ [ -n "$LPM_SOCKET_PATH" ] && [ -S "$LPM_SOCKET_PATH" ] && [ -n "$LPM_PROJECT_NAME" ] && [ -n "$LPM_PANE_ID" ] && "#
    ), "{s}");
    assert!(
        s.contains("& } >/dev/null 2>&1; "),
        "backgrounded and silenced"
    );
    assert!(s.ends_with(MARKER));
}

#[test]
fn send_cmd_uses_portable_nc_python_perl_chain() {
    let s = send_cmd("clear_status 'x' k");
    let nc = s.find("nc -w1 -U").expect("nc primary");
    let py = s.find("python3 -c").expect("python3 fallback");
    let pl = s.find("perl -MIO::Socket::UNIX").expect("perl fallback");
    assert!(nc < py && py < pl, "nc -> python3 -> perl order");
    // The nc send reads the staged variable, not stdin (a failed nc can drain it).
    assert!(s.contains("printf '%s\\n' \"$m\" | nc -w1 -U \"$LPM_SOCKET_PATH\""));
    // Fallbacks receive the payload via the environment, never inlined.
    assert!(s.contains("LPM_MSG=\"$m\" LPM_SOCK=\"$LPM_SOCKET_PATH\" python3"));
    assert!(s.contains("LPM_MSG=\"$m\" LPM_SOCK=\"$LPM_SOCKET_PATH\" perl"));
}

/// The socket splits frames like a shell, so the name must survive quoting.
#[cfg(unix)]
#[test]
fn a_project_name_with_an_apostrophe_still_reaches_the_socket() {
    let payload = r#"{"session_id":"s1","transcript_path":"/t","cwd":"/c","hook_event_name":"PreToolUse","tool_name":"Read","tool_input":{}}"#;
    let msg = run_hook_in_project(&claude_installed("PreToolUse"), payload, "Bob's app")
        .expect("frame delivered");
    assert!(msg.starts_with(r#"set_status 'Bob'"'"'s app' claude_code_s1 Running "#), "{msg}");
    let codex = codex_status_cmd("set_status '$pq' codex_$LPM_PANE_ID Done");
    let msg = run_hook_in_project(&codex, r#"{"session_id":"t"}"#, "Bob's app").unwrap();
    assert!(msg.starts_with(r#"set_status 'Bob'"'"'s app' codex_pane-1 Done"#), "{msg}");
}

#[test]
fn capture_resume_cmd_shape() {
    let s = codex_resume_cmd();
    assert!(s.contains("session_id"), "extracts session_id from stdin");
    assert!(s.contains("set_resume '$pq' $LPM_PANE_ID $sid --provider=codex"));
    assert!(s.contains("[ -S \"$LPM_SOCKET_PATH\" ]") && s.contains("[ -n \"$sid\" ]"));
    // Now also gated on a known project (not just pane) so we never resume ''.
    assert!(
        s.contains("[ -n \"$LPM_PROJECT_NAME\" ]"),
        "project gate added: {s}"
    );
    // Recovery preamble precedes the staged message.
    let recover = s.find("tmux showenv").expect("recovery preamble");
    let stage = s.find("m=\"set_resume").expect("message staged in m");
    assert!(recover < stage, "recovery must precede staging: {s}");
    assert!(s.ends_with(MARKER));
}
