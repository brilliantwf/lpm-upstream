use super::*;
use crate::hooks::test_support::*;

#[test]
fn hooks_switched_off_by_claudes_own_settings_are_reported() {
    let user_off = json!({ "disableAllHooks": true });
    let plain = json!({});
    assert!(claude_hooks_disabled(&user_off, None));
    assert!(!claude_hooks_disabled(&plain, None));
    assert!(claude_hooks_disabled(&plain, Some(&json!({ "allowManagedHooksOnly": true }))));
    assert!(claude_hooks_disabled(&plain, Some(&json!({ "disableAllHooks": true }))));
    assert!(!claude_hooks_disabled(&plain, Some(&json!({ "disableAllHooks": false }))));
}

#[test]
fn claude_settings_are_started_where_claude_is_installed() {
    let home = tempfile::tempdir().unwrap();
    let settings = home.path().join(".claude").join("settings.json");
    install_claude_hooks_at(&settings).unwrap();
    assert!(!settings.exists(), "no Claude folder, nothing created");
    std::fs::create_dir_all(settings.parent().unwrap()).unwrap();
    install_claude_hooks_at(&settings).unwrap();
    assert!(claude_hooks_status_at(&settings).hooks_installed);
}

#[test]
fn install_adds_hooks_and_preserves_siblings() {
    let dir = tempfile::tempdir().unwrap();
    let path = settings_at(dir.path(), r#"{"model":"opus","permissions":{"x":1}}"#);
    install_claude_hooks_at(&path).unwrap();

    let v: Value = serde_json::from_slice(&std::fs::read(&path).unwrap()).unwrap();
    // siblings untouched
    assert_eq!(v["model"], "opus");
    assert_eq!(v["permissions"]["x"], 1);
    // all events present, marker installed
    let hooks = v["hooks"].as_object().unwrap();
    for ev in [
        "SessionStart",
        "UserPromptSubmit",
        "PreToolUse",
        "PostToolUse",
        "Notification",
        "Stop",
        "StopFailure",
        "SessionEnd",
    ] {
        assert!(hooks.contains_key(ev), "missing {ev}");
    }
    assert!(has_marker(&v["hooks"]));
    assert_eq!(claude_hooks_status_at(&path).hooks_installed, true);
}

#[test]
fn claude_session_start_reports_provider_aware_session_id() {
    let dir = tempfile::tempdir().unwrap();
    let path = settings_at(dir.path(), "{}");
    install_claude_hooks_at(&path).unwrap();
    let v: Value = serde_json::from_slice(&std::fs::read(&path).unwrap()).unwrap();
    let start = v["hooks"]["SessionStart"].as_array().unwrap();
    assert_eq!(start.len(), 1);
    let cmd = start[0]["hooks"][0]["command"].as_str().unwrap();
    assert!(cmd.contains("session_id"), "{cmd}");
    assert!(
        cmd.contains("set_resume '$pq' $LPM_PANE_ID $sid --provider=claude"),
        "{cmd}"
    );
}

#[test]
fn install_uses_per_session_status_key_with_pane_attribution() {
    let dir = tempfile::tempdir().unwrap();
    let path = settings_at(dir.path(), "{}");
    install_claude_hooks_at(&path).unwrap();
    let v: Value = serde_json::from_slice(&std::fs::read(&path).unwrap()).unwrap();
    for ev in [
        "UserPromptSubmit",
        "PreToolUse",
        "PostToolUse",
        "Notification",
        "Stop",
        "StopFailure",
        "SessionEnd",
    ] {
        for e in v["hooks"][ev].as_array().unwrap() {
            let cmd = e["hooks"][0]["command"].as_str().unwrap();
            // Keyed by claude's session id, with a defensive pane fallback.
            assert!(
                cmd.contains("claude_code_${sid:-$LPM_PANE_ID}"),
                "{ev} not per-session: {cmd}"
            );
            // The session id is captured synchronously from stdin first —
            // either streamed straight through sed, or (Stop, which needs a
            // second look at the payload) slurped into a variable.
            assert!(
                cmd.contains("session_id")
                    && (cmd.contains("sid=$(sed -n") || cmd.contains("p=$(cat)")),
                "{ev} must capture session_id from stdin: {cmd}"
            );
            let sid = cmd.find("sid=$(").unwrap();
            let stage = cmd.find("m=\"").unwrap();
            assert!(sid < stage, "{ev} must drain stdin before staging: {cmd}");
            // Tab attribution stays on the resolved pane id (clear_status, on
            // SessionEnd, keys without a --pane, matching the socket verb).
            if cmd.contains("set_status") {
                assert!(
                    cmd.contains("--pane=$LPM_PANE_ID"),
                    "{ev} keeps pane attribution: {cmd}"
                );
            }
        }
    }
}

#[test]
fn reinstall_migrates_stale_shared_key_and_preserves_user_hooks() {
    let dir = tempfile::tempdir().unwrap();
    // Old install: a Stop hook on the shared `claude_code` key (carries MARKER),
    // next to the user's own Stop hook which must survive the migration.
    let body = r#"{
      "hooks": {
        "Stop": [
          { "matcher": "", "hooks": [ { "type": "command", "command": "old claude_code Done # lpm-hook" } ] },
          { "matcher": "", "hooks": [ { "type": "command", "command": "my-own-hook" } ] }
        ]
      }
    }"#;
    let path = settings_at(dir.path(), body);
    install_claude_hooks_at(&path).unwrap();

    let v: Value = serde_json::from_slice(&std::fs::read(&path).unwrap()).unwrap();
    let stop = v["hooks"]["Stop"].as_array().unwrap();

    // user hook preserved
    assert!(stop
        .iter()
        .any(|e| e["hooks"][0]["command"] == "my-own-hook"));
    // exactly one lpm hook in Stop — old one replaced, not duplicated — now per-session
    let lpm: Vec<&Value> = stop.iter().filter(|e| has_marker(e)).collect();
    assert_eq!(lpm.len(), 1, "stale lpm hook replaced, not duplicated");
    let cmd = lpm[0]["hooks"][0]["command"].as_str().unwrap();
    assert!(
        cmd.contains("claude_code_${sid:-$LPM_PANE_ID}"),
        "migrated to per-session key: {cmd}"
    );
}

#[test]
fn install_is_idempotent() {
    let dir = tempfile::tempdir().unwrap();
    let path = settings_at(dir.path(), "{}");
    install_claude_hooks_at(&path).unwrap();
    let first = std::fs::read_to_string(&path).unwrap();
    install_claude_hooks_at(&path).unwrap(); // second run: marker present -> no-op
    assert_eq!(
        std::fs::read_to_string(&path).unwrap(),
        first,
        "no duplicate hooks"
    );
}

#[test]
fn missing_settings_without_claude_is_noop() {
    let dir = tempfile::tempdir().unwrap();
    let path = dir.path().join(".claude").join("settings.json"); // no Claude folder
    install_claude_hooks_at(&path).unwrap();
    assert!(!path.exists(), "must not create the file");
    let st = claude_hooks_status_at(&path);
    assert!(!st.settings_exists && !st.hooks_installed);
}

#[test]
fn invalid_json_is_left_untouched() {
    let dir = tempfile::tempdir().unwrap();
    let path = settings_at(dir.path(), "{ not json");
    install_claude_hooks_at(&path).unwrap();
    assert_eq!(std::fs::read_to_string(&path).unwrap(), "{ not json");
    let st = claude_hooks_status_at(&path);
    assert!(st.settings_exists && !st.hooks_installed);
}

#[test]
fn reset_removes_then_reinstalls_preserving_siblings() {
    let dir = tempfile::tempdir().unwrap();
    let path = settings_at(dir.path(), r#"{"model":"opus"}"#);
    install_claude_hooks_at(&path).unwrap();
    reset_claude_hooks_at(&path).unwrap();
    let v: Value = serde_json::from_slice(&std::fs::read(&path).unwrap()).unwrap();
    assert_eq!(v["model"], "opus");
    assert!(has_marker(&v["hooks"]), "reinstalled after reset");
    assert_eq!(
        v["hooks"]["Stop"].as_array().unwrap().len(),
        1,
        "no duplication"
    );
}

#[test]
fn merge_returns_change_then_none_and_ignores_invalid() {
    // First merge changes the object; a second merge of the result is a no-op
    // (idempotent) — the same guarantee the remote install relies on.
    let first = merge_claude_hooks(br#"{"model":"opus"}"#).expect("first merge changes");
    let v: Value = serde_json::from_slice(&first).unwrap();
    assert_eq!(v["model"], "opus");
    assert!(has_marker(&v["hooks"]));
    assert!(
        merge_claude_hooks(&first).is_none(),
        "second merge is a no-op"
    );
    // Invalid JSON never rewritten.
    assert!(merge_claude_hooks(b"{ not json").is_none());
}

#[test]
fn merge_upgrades_old_nc_only_claude_hook() {
    // The exact command an older lpm installed (echo | nc -U, no fallbacks).
    let old = "{ [ -n \"$LPM_SOCKET_PATH\" ] && [ -S \"$LPM_SOCKET_PATH\" ] && echo \"set_status '$LPM_PROJECT_NAME' claude_code_$LPM_PANE_ID Done --icon=checkmark --color=#4ade80 --pane=$LPM_PANE_ID\" | nc -w1 -U \"$LPM_SOCKET_PATH\" & } >/dev/null 2>&1; # lpm-hook";
    let body = format!(
        r#"{{ "hooks": {{ "Stop": [
            {{ "matcher": "", "hooks": [ {{ "type": "command", "command": {} }} ] }},
            {{ "matcher": "", "hooks": [ {{ "type": "command", "command": "my-own-hook" }} ] }}
        ] }} }}"#,
        serde_json::to_string(old).unwrap()
    );

    let out = merge_claude_hooks(body.as_bytes()).expect("old command must be upgraded");
    let v: Value = serde_json::from_slice(&out).unwrap();
    let stop = v["hooks"]["Stop"].as_array().unwrap();

    // User hook survives; exactly one lpm hook remains, now on the new chain.
    assert!(stop
        .iter()
        .any(|e| e["hooks"][0]["command"] == "my-own-hook"));
    let lpm: Vec<&Value> = stop.iter().filter(|e| has_marker(e)).collect();
    assert_eq!(lpm.len(), 1, "old lpm hook replaced, not duplicated");
    let cmd = lpm[0]["hooks"][0]["command"].as_str().unwrap();
    assert!(cmd.contains("python3 -c") && cmd.contains("perl -MIO::Socket::UNIX"));
    assert!(!cmd.contains("echo \""), "old echo-based delivery gone");
    assert_ne!(cmd, old, "command text actually changed");
}

#[test]
fn merge_upgrades_pre_recovery_claude_hook_to_add_env_recovery() {
    // A marker'd command from the python/perl generation that predates the
    // tmux env-recovery preamble. Strip-by-marker must replace it with the
    // current text, which now recovers LPM_* before delivering.
    let old = "m=\"set_status 'x' claude_code_$LPM_PANE_ID Done\"; { [ -n \"$LPM_SOCKET_PATH\" ] && [ -S \"$LPM_SOCKET_PATH\" ] && printf '%s\\n' \"$m\" | nc -w1 -U \"$LPM_SOCKET_PATH\" & } >/dev/null 2>&1; # lpm-hook";
    let body = format!(
        r#"{{ "hooks": {{ "Stop": [ {{ "matcher": "", "hooks": [ {{ "type": "command", "command": {} }} ] }} ] }} }}"#,
        serde_json::to_string(old).unwrap()
    );

    let out = merge_claude_hooks(body.as_bytes()).expect("pre-recovery hook must upgrade");
    let v: Value = serde_json::from_slice(&out).unwrap();
    let stop = v["hooks"]["Stop"].as_array().unwrap();
    let lpm: Vec<&Value> = stop.iter().filter(|e| has_marker(e)).collect();
    assert_eq!(lpm.len(), 1, "old lpm hook replaced, not duplicated");
    let cmd = lpm[0]["hooks"][0]["command"].as_str().unwrap();
    assert!(
        cmd.contains("tmux showenv -g LPM_SOCKET_PATH"),
        "env recovery added on upgrade: {cmd}"
    );
    assert_ne!(cmd, old, "command text actually changed");
}

#[cfg(unix)]
#[test]
fn claude_frames_key_by_the_payloads_own_session_id() {
    let cmd = claude_installed("PreToolUse");
    let payload = r#"{"session_id":"s1","transcript_path":"/t","cwd":"/c","hook_event_name":"PreToolUse","tool_name":"mcp__notes__open","tool_input":{"session_id":"foreign"}}"#;
    let msg = run_codex_hook(&cmd, payload).expect("main thread reports");
    assert!(msg.contains(" claude_code_s1 Running "), "{msg}");
}

#[cfg(unix)]
#[test]
fn claude_sub_agents_ask_but_never_speak_for_the_session() {
    let sub = |event: &str| {
        format!(
            r#"{{"session_id":"s1","transcript_path":"/t","cwd":"/c","agent_id":"a7","agent_type":"general-purpose","hook_event_name":"{event}","tool_name":"Read","tool_input":{{}}}}"#
        )
    };
    for event in ["PreToolUse", "PostToolUse", "PostToolUseFailure", "StopFailure"] {
        assert_eq!(run_codex_hook(&claude_installed(event), &sub(event)), None, "{event}");
    }
    let asked = run_codex_hook(&claude_installed("PermissionRequest"), &sub("PermissionRequest"));
    assert!(asked.is_some_and(|m| m.contains(" claude_code_s1 Waiting ")));
}

#[cfg(unix)]
#[test]
fn tool_input_naming_an_agent_id_is_still_the_main_thread() {
    let payload = r#"{"session_id":"s1","transcript_path":"/t","cwd":"/c","hook_event_name":"PreToolUse","tool_name":"Task","tool_input":{"agent_id":"a7"}}"#;
    assert!(run_codex_hook(&claude_installed("PreToolUse"), payload).is_some());
}

/// A Stop whose turn handed off to background work must keep the badge on
/// Running — that turn end is a pause, not a finish, and the harness will
/// re-prompt the session itself. Reporting Done there is the bug where the
/// running indicator vanished mid-workflow.
#[cfg(unix)]
#[test]
fn claude_stop_hook_reports_running_while_background_work_is_in_flight() {
    let cmd = claude_stop_cmd();
    let base = r#"{"session_id":"s1","transcript_path":"/tmp/t.jsonl","cwd":"/tmp/p","hook_event_name":"Stop","stop_hook_active":false,"last_assistant_message":"MSG","background_tasks":TASKS,"session_crons":[]}"#;

    let pending = base.replace("MSG", "Waiting on the audit stage.").replace(
        "TASKS",
        r#"[{"id":"bw1","type":"workflow","status":"running","description":"vtt-uiux-audit","name":"vtt-uiux-audit"}]"#,
    );
    let msg = run_codex_hook(&cmd, &pending).unwrap();
    assert!(
        msg.contains("claude_code_s1 Running"),
        "a turn that ends with work still in flight is not Done: {msg}"
    );

    let settled = base.replace("MSG", "All set.").replace("TASKS", "[]");
    let msg = run_codex_hook(&cmd, &settled).unwrap();
    assert!(
        msg.contains("claude_code_s1 Done"),
        "an empty task list is a real finish: {msg}"
    );

    // Claude older than `background_tasks` must keep reporting Done rather
    // than sticking on Running.
    let legacy = r#"{"session_id":"s1","transcript_path":"/tmp/t.jsonl","cwd":"/tmp/p","hook_event_name":"Stop","stop_hook_active":false}"#;
    let msg = run_codex_hook(&cmd, legacy).unwrap();
    assert!(
        msg.contains("claude_code_s1 Done"),
        "absent background_tasks falls back to Done: {msg}"
    );

    // The assistant's own prose is part of the payload; quoting the field
    // must not fake in-flight work (the real field serializes last, and the
    // greedy match anchors there).
    let decoy = base
        .replace("MSG", r#"I set \"background_tasks\":[{ in the config"#)
        .replace("TASKS", "[]");
    let msg = run_codex_hook(&cmd, &decoy).unwrap();
    assert!(
        msg.contains("claude_code_s1 Done"),
        "quoted background_tasks in prose must not flip the status: {msg}"
    );
}

/// An in-process teammate stays registered as `running` for the whole
/// session and its `isIdle` flag never reaches the hook, so a turn that ends
/// with nothing but teammates is a real finish. Every other type — and every
/// body the shell cannot parse cleanly — must stay Running.
#[cfg(unix)]
#[test]
fn claude_stop_hook_treats_idle_teammates_as_finished() {
    let cmd = claude_stop_cmd();
    let base = r#"{"session_id":"s1","transcript_path":"/tmp/t.jsonl","cwd":"/tmp/p","hook_event_name":"Stop","stop_hook_active":false,"last_assistant_message":"MSG","background_tasks":TASKS,"session_crons":[]}"#;
    let stop = |msg: &str, tasks: &str| {
        let payload = base.replace("MSG", msg).replace("TASKS", tasks);
        run_codex_hook(&cmd, &payload).unwrap()
    };

    let msg = stop(
        "Done.",
        r#"[{"id":"bt1","type":"teammate","status":"running","description":"Apply review fixes to conn changes"}]"#,
    );
    assert!(
        msg.contains("claude_code_s1 Done"),
        "a lone registered teammate is not in-flight work: {msg}"
    );

    let msg = stop(
        "Done.",
        r#"[{"id":"bt1","type":"teammate","status":"running","description":"Apply review fixes"},{"id":"bt2","type":"teammate","status":"running","description":"Draft the release note"}]"#,
    );
    assert!(
        msg.contains("claude_code_s1 Done"),
        "several idle-but-registered teammates are still a finish: {msg}"
    );

    let msg = stop(
        "Kicking off the audit.",
        r#"[{"id":"bt1","type":"teammate","status":"running","description":"x"},{"id":"bt2","type":"workflow","status":"running","description":"y","name":"audit"}]"#,
    );
    assert!(
        msg.contains("claude_code_s1 Running"),
        "a workflow alongside a teammate still pauses the turn: {msg}"
    );

    let msg = stop(
        "Reviewing in the background.",
        r#"[{"id":"bt1","type":"teammate","status":"running","description":"x"},{"id":"bt2","type":"subagent","status":"running","description":"Review the diff","agent_type":"general-purpose"}]"#,
    );
    assert!(
        msg.contains("claude_code_s1 Running"),
        "a backgrounded subagent alongside a teammate still pauses the turn: {msg}"
    );

    let msg = stop(
        "Thinking it over.",
        r#"[{"id":"bt1","type":"cloud session","status":"running","description":"x"}]"#,
    );
    assert!(
        msg.contains("claude_code_s1 Running"),
        "a task type not known to finish silently must not be downgraded: {msg}"
    );

    for ambient in ["dream", "auto-mode scan", "memory import"] {
        let msg = stop(
            "Done.",
            &format!(r#"[{{"id":"bt1","type":"{ambient}","status":"running","description":"x"}}]"#),
        );
        assert!(
            msg.contains("claude_code_s1 Done"),
            "{ambient} never wakes the session, so the turn is over: {msg}"
        );
    }

    // A teammate description is the first ~50 chars of its spawn prompt, so
    // raw `{`, `}`, `[` and `]` land in it routinely — and JSON escapes none
    // of them. Each of these hides a live non-teammate entry behind a `}]`
    // that ends a truncated body on a balanced-looking brace.
    for tasks in [
        r#"[{"id":"bt1","type":"teammate","status":"running","description":"Fix the stray }] in package.json"},{"id":"bt2","type":"workflow","status":"running","description":"deploy","name":"release"}]"#,
        r#"[{"id":"bt1","type":"teammate","status":"running","description":"see }]}x"},{"id":"bt2","type":"subagent","status":"running","description":"Review the diff","agent_type":"general-purpose"}]"#,
        r#"[{"id":"bt1","type":"teammate","status":"running","description":"x}],y"},{"id":"bt2","type":"teammate","status":"running","description":"q"},{"id":"bt3","type":"workflow","status":"running","description":"w"}]"#,
        r#"[{"id":"bt1","type":"teammate","status":"running","description":"rerun step [2] of the plan"},{"id":"bt2","type":"workflow","status":"running","description":"y"}]"#,
    ] {
        let msg = stop("Working.", tasks);
        assert!(
            msg.contains("claude_code_s1 Running"),
            "a bracket in a description must not hide in-flight work: {msg}"
        );
    }

    // The same brackets in an all-teammate array are just text: blanking the
    // strings before the slice keeps the downgrade working instead of
    // silently falling back to Running.
    for tasks in [
        r#"[{"id":"bt1","type":"teammate","status":"running","description":"Fix [BUG-123] in parser"}]"#,
        r#"[{"id":"bt1","type":"teammate","status":"running","description":"Close the array with }] in schema.json"}]"#,
        r#"[{"id":"bt1","type":"teammate","status":"running","description":"say \"hi\" now"}]"#,
    ] {
        let msg = stop("Done.", tasks);
        assert!(
            msg.contains("claude_code_s1 Done"),
            "punctuation in a teammate description is not in-flight work: {msg}"
        );
    }

    // `session_crons` serializes after the array, and its `prompt` is
    // arbitrary user text: the escaped type token cannot match the scan's
    // patterns, and the cron object's own braces and brackets sit past the
    // array's close, so neither reaches the counts.
    let crons = base
        .replace("MSG", "Done.")
        .replace(
            "TASKS",
            r#"[{"id":"bt1","type":"teammate","status":"running","description":"x"}]"#,
        )
        .replace(
            r#""session_crons":[]"#,
            r#""session_crons":[{"id":"c1","schedule":"* * * * *","recurring":true,"prompt":"run the \"type\":\"workflow\" audit over [drafts] and {notes}"}]"#,
        );
    let msg = run_codex_hook(&cmd, &crons).unwrap();
    assert!(
        msg.contains("claude_code_s1 Done"),
        "a cron prompt quoting a task type must not be scanned: {msg}"
    );

    let msg = stop(
        r#"I set \"background_tasks\":[{\"type\":\"workflow\"} in the config"#,
        r#"[{"id":"bt1","type":"teammate","status":"running","description":"x"}]"#,
    );
    assert!(
        msg.contains("claude_code_s1 Done"),
        "a fake background_tasks in prose must not flip the status: {msg}"
    );
}

/// Publishing an Artifact auto-arms a persistent ambient comment watcher
/// that stays registered as a `running` `monitor` task forever, so it is a
/// wake-on-event signal, not in-flight work — same as `session_crons`,
/// which have never held Running. Monitors join teammates in the ignorable
/// set; every other type, and near-miss type names, must still pause.
#[cfg(unix)]
#[test]
fn claude_stop_hook_treats_monitors_as_finished() {
    let cmd = claude_stop_cmd();
    let base = r#"{"session_id":"s1","transcript_path":"/tmp/t.jsonl","cwd":"/tmp/p","hook_event_name":"Stop","stop_hook_active":false,"last_assistant_message":"MSG","background_tasks":TASKS,"session_crons":[]}"#;
    let stop = |msg: &str, tasks: &str| {
        let payload = base.replace("MSG", msg).replace("TASKS", tasks);
        run_codex_hook(&cmd, &payload).unwrap()
    };

    let msg = stop(
        "Published the artifact.",
        r#"[{"id":"bt1","type":"monitor","status":"running","description":"Artifact comment watch (auto-armed on publish)"}]"#,
    );
    assert!(
        msg.contains("claude_code_s1 Done"),
        "a lone artifact watch is not in-flight work: {msg}"
    );

    let msg = stop(
        "Done.",
        r#"[{"id":"bt1","type":"teammate","status":"running","description":"Fix v-06 calendar dates"},{"id":"bt2","type":"monitor","status":"running","description":"Artifact comment watch (auto-armed on publish)"}]"#,
    );
    assert!(
        msg.contains("claude_code_s1 Done"),
        "an idle teammate plus an artifact watch is still a finish: {msg}"
    );

    let msg = stop(
        "Watching CI.",
        r#"[{"id":"bt1","type":"monitor","status":"running","description":"watch the CI run","server":"github","tool":"get_workflow_run"}]"#,
    );
    assert!(
        msg.contains("claude_code_s1 Done"),
        "an MCP monitor with server/tool fields is a wake-later signal: {msg}"
    );

    let msg = stop(
        "Kicking off the audit.",
        r#"[{"id":"bt1","type":"monitor","status":"running","description":"x"},{"id":"bt2","type":"workflow","status":"running","description":"y","name":"audit"}]"#,
    );
    assert!(
        msg.contains("claude_code_s1 Running"),
        "a workflow alongside a monitor still pauses the turn: {msg}"
    );

    let msg = stop(
        "Thinking.",
        r#"[{"id":"bt1","type":"monitors","status":"running","description":"x"}]"#,
    );
    assert!(
        msg.contains("claude_code_s1 Running"),
        "a near-miss type name must not be downgraded: {msg}"
    );

    let msg = stop(
        "Editing.",
        r#"[{"id":"bt1","type":"subagent","status":"running","description":"set \"type\":\"monitor\" in config.json","agent_type":"general-purpose"}]"#,
    );
    assert!(
        msg.contains("claude_code_s1 Running"),
        "a description quoting the monitor type must not hide a live subagent: {msg}"
    );
}

/// A backgrounded shell is a wake-on-exit signal, not work: the harness
/// calls the turn done beside it, and its exit starts a fresh turn that
/// re-asserts Running itself. The payload cannot tell a build from a dev
/// server or job worker left running for the user, and the latter never
/// exits — so a shell-only Stop is a finish. Anything with an agent behind
/// it still pauses.
#[cfg(unix)]
#[test]
fn claude_stop_hook_treats_background_shells_as_finished() {
    let cmd = claude_stop_cmd();
    let base = r#"{"session_id":"s1","transcript_path":"/tmp/t.jsonl","cwd":"/tmp/p","hook_event_name":"Stop","stop_hook_active":false,"last_assistant_message":"MSG","background_tasks":TASKS,"session_crons":[]}"#;
    let stop = |msg: &str, tasks: &str| {
        let payload = base.replace("MSG", msg).replace("TASKS", tasks);
        run_codex_hook(&cmd, &payload).unwrap()
    };

    let msg = stop(
        "Sidekiq is still running in the background for your manual test.",
        r#"[{"id":"bt1","type":"shell","status":"running","description":"Run Sidekiq in the background","command":"cd /tmp/p && bundle exec sidekiq -C config/sidekiq.yml"}]"#,
    );
    assert!(
        msg.contains("claude_code_s1 Done"),
        "a server left running in the background is not in-flight work: {msg}"
    );

    let msg = stop(
        "Building; I will pick up when it finishes.",
        r#"[{"id":"bt1","type":"shell","status":"running","description":"Build the desktop app","command":"cargo build --release 2>&1 | tail -20"}]"#,
    );
    assert!(
        msg.contains("claude_code_s1 Done"),
        "a build whose exit will wake the session is a finish for now: {msg}"
    );

    let msg = stop(
        "Done.",
        r#"[{"id":"bt1","type":"shell","status":"running","description":"Start the dev server","command":"npm run dev"},{"id":"bt2","type":"monitor","status":"running","description":"Artifact comment watch (auto-armed on publish)"},{"id":"bt3","type":"teammate","status":"running","description":"Fix the tests"}]"#,
    );
    assert!(
        msg.contains("claude_code_s1 Done"),
        "shells beside monitors and teammates are still a finish: {msg}"
    );

    let msg = stop(
        "Auditing.",
        r#"[{"id":"bt1","type":"shell","status":"running","description":"Start the dev server","command":"npm run dev"},{"id":"bt2","type":"subagent","status":"running","description":"Audit the UI","agent_type":"general-purpose"}]"#,
    );
    assert!(
        msg.contains("claude_code_s1 Running"),
        "a subagent alongside a shell still pauses the turn: {msg}"
    );

    // A command line is JSON text like a description: its quoting of a
    // task type, and its own braces and brackets, must not reach the scan.
    for tasks in [
        r#"[{"id":"bt1","type":"shell","status":"running","description":"Write config","command":"printf '%s' '{\"type\":\"workflow\"}' > config.json"}]"#,
        r#"[{"id":"bt1","type":"shell","status":"running","description":"List","command":"for f in a[0-9]; do echo ${f}]; done"}]"#,
    ] {
        let msg = stop("Done.", tasks);
        assert!(
            msg.contains("claude_code_s1 Done"),
            "punctuation in a shell command is not in-flight work: {msg}"
        );
    }

    let msg = stop(
        "Thinking.",
        r#"[{"id":"bt1","type":"shells","status":"running","description":"x"}]"#,
    );
    assert!(
        msg.contains("claude_code_s1 Running"),
        "a near-miss type name must not be downgraded: {msg}"
    );
}

#[test]
fn merge_claude_hooks_creates_hooks_from_empty_object() {
    // Fresh remote servers read as `{}`; the merge must still install the hooks.
    let out = merge_claude_hooks(b"{}").expect("empty object gets hooks");
    let v: Value = serde_json::from_slice(&out).unwrap();
    for ev in [
        "UserPromptSubmit",
        "PreToolUse",
        "PostToolUse",
        "Notification",
        "Stop",
        "StopFailure",
        "SessionEnd",
    ] {
        let cmd = v["hooks"][ev][0]["hooks"][0]["command"].as_str().unwrap();
        assert!(cmd.contains("python3 -c"), "{ev} uses the portable chain");
    }
}

/// Nothing fires when a permission prompt is approved, so the Waiting it
/// raised must be taken back by the first event after the tool has run.
#[cfg(unix)]
#[test]
fn claude_post_tool_use_reasserts_running_after_an_approval() {
    let out = merge_claude_hooks(b"{}").unwrap();
    let v: Value = serde_json::from_slice(&out).unwrap();
    let post = v["hooks"]["PostToolUse"].as_array().unwrap();
    assert_eq!(post.len(), 1);
    assert_eq!(post[0]["matcher"], "");
    let cmd = post[0]["hooks"][0]["command"].as_str().unwrap();
    let payload = r#"{"session_id":"s1","transcript_path":"/tmp/t.jsonl","cwd":"/tmp/p","hook_event_name":"PostToolUse","tool_name":"Bash","tool_input":{"command":"cargo build"},"tool_response":{"stdout":"ok"}}"#;
    let msg = run_codex_hook(cmd, payload).unwrap();
    assert!(
        msg.contains("claude_code_s1 Running") && msg.contains("--pane=pane-1"),
        "{msg}"
    );
}

/// Cursor runs the hooks in Claude's settings with its own payload appended as
/// a heredoc, which the marker comment swallowed — the payload's line then ran
/// as shell. The hooks must step aside before the shell reaches it, and leave
/// Claude Code's own runs untouched.
#[cfg(unix)]
#[test]
fn claude_hooks_step_aside_under_cursor_without_running_its_payload() {
    let td = tempfile::tempdir().unwrap();
    let ran = td.path().join("ran");
    let payload = format!(
        r#"{{"conversation_id":"c1","prompt":"fix `touch {}` and $(touch {})","hook_event_name":"beforeSubmitPrompt"}}"#,
        ran.display(),
        ran.display()
    );
    for event in ["SessionStart", "UserPromptSubmit", "Stop", "SessionEnd"] {
        let cmd = format!("{} <<'CURSOR_HOOK_EOF'\n{payload}\nCURSOR_HOOK_EOF", claude_installed(event));
        let msg = run_hook_with_env(&cmd, "", "proj", &[("CURSOR_VERSION", "2026.10.01")]);
        assert_eq!(msg, None, "{event} reported for Cursor");
        assert!(!ran.exists(), "{event} ran the payload as shell");
    }
    let claude_code = r#"{"session_id":"s1","hook_event_name":"UserPromptSubmit","prompt":"hi"}"#;
    let msg = run_codex_hook(&claude_installed("UserPromptSubmit"), claude_code);
    assert!(msg.is_some_and(|m| m.contains(" claude_code_s1 Running ")));
}
