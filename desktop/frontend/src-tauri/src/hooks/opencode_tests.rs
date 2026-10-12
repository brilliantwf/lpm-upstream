use super::*;
#[cfg(unix)]
use crate::hooks::test_support::*;

/// The `HOOKS` table a generated plugin carries.
fn hooks_of(plugin: &str) -> Value {
    let line = plugin
        .lines()
        .find_map(|line| line.strip_prefix("const HOOKS = "))
        .unwrap();
    serde_json::from_str(line).unwrap()
}

#[test]
fn the_plugin_runs_an_sh_hook_for_every_state() {
    let plugin = plugin(&HookForm::Shell);
    assert!(!plugin.contains(HOOKS_SLOT));
    assert!(plugin.lines().next().unwrap().starts_with("// lpm status plugin"));
    assert!(plugin.contains(MARKER));
    let hooks = hooks_of(&plugin);
    assert_eq!(hooks.as_object().unwrap().len(), OPENCODE_STATES.len());
    for state in OPENCODE_STATES {
        assert_eq!(hooks[state][0], "/bin/sh", "{state}");
        assert_eq!(hooks[state][1][0], "-c", "{state}");
        assert!(hooks[state][1][1].as_str().unwrap().ends_with(MARKER), "{state}");
    }
}

#[test]
fn windows_runs_the_cli_without_a_shell() {
    let cli = HookForm::Cli(PathBuf::from(r"C:\Program Files\lpm\lpm-cli.exe"));
    let hooks = hooks_of(&plugin(&cli));
    for state in OPENCODE_STATES {
        assert_eq!(
            hooks[state],
            json!([r"C:\Program Files\lpm\lpm-cli.exe", ["hook", "opencode", state, MARKER]])
        );
    }
}

#[test]
fn install_waits_for_opencode_rewrites_only_on_change_and_uninstall_takes_only_its_own() {
    let td = tempfile::tempdir().unwrap();
    let dir = td.path().join("opencode");
    let path = plugin_path(&dir);
    install_owned_file(&dir, &path, "one # lpm-hook").unwrap();
    assert!(!dir.exists(), "OpenCode isn't installed: nothing is created");

    std::fs::create_dir(&dir).unwrap();
    install_owned_file(&dir, &path, "one # lpm-hook").unwrap();
    assert_eq!(std::fs::read_to_string(&path).unwrap(), "one # lpm-hook");
    let written = std::fs::metadata(&path).unwrap().modified().unwrap();
    std::thread::sleep(std::time::Duration::from_millis(20));
    install_owned_file(&dir, &path, "one # lpm-hook").unwrap();
    assert_eq!(std::fs::metadata(&path).unwrap().modified().unwrap(), written);
    install_owned_file(&dir, &path, "two # lpm-hook").unwrap();
    assert_eq!(std::fs::read_to_string(&path).unwrap(), "two # lpm-hook");

    remove_owned_file(&path).unwrap();
    assert!(!path.exists());
    std::fs::write(&path, "the user's own").unwrap();
    remove_owned_file(&path).unwrap();
    assert!(path.exists());
}

#[cfg(unix)]
#[test]
fn states_report_under_the_session_like_the_cli_does() {
    let hooks = hooks_of(&plugin(&HookForm::Shell));
    let run = |state: &str| {
        let cmd = hooks[state][1][1].as_str().unwrap();
        run_hook_in_project(cmd, r#"{"session_id":"ses_1"}"#, "proj").expect(state)
    };
    let pane_and_pid = " --pane=pane-1 --reporter-pid=";
    assert!(run("running").starts_with(&format!(
        "set_status 'proj' opencode_ses_1 Running --icon=bolt --color=#FAB283{pane_and_pid}"
    )));
    assert!(run("waiting").starts_with("set_status 'proj' opencode_ses_1 Waiting --icon=bell --color=#f59e0b --pane=pane-1 --hold --reporter-pid="));
    assert!(run("done").starts_with(&format!(
        "set_status 'proj' opencode_ses_1 Done --icon=checkmark --color=#4ade80{pane_and_pid}"
    )));
    assert!(run("error").starts_with(&format!(
        "set_status 'proj' opencode_ses_1 Error --icon=warning --color=#ef4444{pane_and_pid}"
    )));
    assert!(run("clear").starts_with("clear_status 'proj' opencode_ses_1 --live --reporter-pid="));
}

fn status(sid: &str, kind: &str) -> Value {
    json!({ "type": "session.status", "properties": { "sessionID": sid, "status": { "type": kind } } })
}

fn idle(sid: &str) -> [Value; 2] {
    [status(sid, "idle"), json!({ "type": "session.idle", "properties": { "sessionID": sid } })]
}

fn created(sid: &str, parent: &str) -> Value {
    json!({ "type": "session.created", "properties": { "info": { "id": sid, "parentID": parent } } })
}

fn asked(kind: &str, sid: &str, id: &str) -> Value {
    json!({ "type": format!("{kind}.asked"), "properties": { "id": id, "sessionID": sid } })
}

fn replied(sid: &str, id: &str, reply: &str) -> Value {
    json!({ "type": "permission.replied", "properties": { "sessionID": sid, "requestID": id, "reply": reply } })
}

fn error(sid: &str, name: &str) -> Value {
    json!({ "type": "session.error", "properties": { "sessionID": sid, "error": { "name": name, "data": {} } } })
}

/// The states the plugin reports for `events`, run in Node with each state's
/// hook recording what it was given. None when Node isn't installed.
#[cfg(unix)]
fn reported(events: Vec<Value>) -> Option<Vec<String>> {
    if std::process::Command::new("node").arg("--version").output().is_err() {
        eprintln!("node not found: OpenCode plugin turns not checked");
        return None;
    }
    let td = tempfile::tempdir().unwrap();
    let log = td.path().join("log");
    let hooks: Map<String, Value> = OPENCODE_STATES
        .iter()
        .map(|state| {
            let record = format!("printf '%s %s\\n' {state} \"$(cat)\" >> '{}'", log.display());
            (state.to_string(), json!(["/bin/sh", ["-c", record]]))
        })
        .collect();
    let module = td.path().join("lpm-status.mjs");
    std::fs::write(&module, PLUGIN.replace(HOOKS_SLOT, &Value::Object(hooks).to_string())).unwrap();
    let runner = td.path().join("run.mjs");
    std::fs::write(
        &runner,
        "const { LpmStatus } = await import(process.argv[2])\n\
         const plugin = await LpmStatus({})\n\
         for (const event of JSON.parse(process.argv[3])) await plugin.event({ event })\n\
         await plugin.dispose()\n",
    )
    .unwrap();
    let out = std::process::Command::new("node")
        .arg(&runner)
        .arg(&module)
        .arg(Value::Array(events).to_string())
        .output()
        .unwrap();
    assert!(out.status.success(), "{}", String::from_utf8_lossy(&out.stderr));
    let log = std::fs::read_to_string(&log).unwrap_or_default();
    Some(
        log.lines()
            .map(|line| line.replace(r#" {"session_id":"#, " ").replace(['"', '}'], ""))
            .collect(),
    )
}

/// Event sequences as OpenCode 1.19 sends them, captured from its TUI.
#[cfg(unix)]
#[test]
fn the_plugin_follows_opencode_turns() {
    let turn = |middle: Vec<Value>| -> Vec<Value> {
        let mut events = vec![status("r", "busy"), status("r", "busy")];
        events.extend(middle);
        events.extend(idle("r"));
        events
    };
    let cases: Vec<(&str, Vec<Value>, &[&str])> = vec![
        ("finished", turn(vec![status("r", "busy")]), &["running r", "done r"]),
        (
            "approved",
            turn(vec![asked("permission", "r", "p1"), replied("r", "p1", "once"), status("r", "busy")]),
            &["running r", "waiting r", "running r", "done r"],
        ),
        (
            "rejected",
            turn(vec![asked("permission", "r", "p1"), replied("r", "p1", "reject")]),
            &["running r", "waiting r", "clear r"],
        ),
        (
            "interrupted",
            [turn(vec![error("r", "MessageAbortedError")]), idle("r").to_vec()].concat(),
            &["running r", "clear r"],
        ),
        ("failed", turn(vec![error("r", "APIError")]), &["running r", "error r"]),
        (
            "compacted after overflowing",
            turn(vec![error("r", "ContextOverflowError"), status("r", "busy")]),
            &["running r", "done r"],
        ),
        (
            "question dismissed",
            turn(vec![
                asked("question", "r", "q1"),
                json!({ "type": "question.rejected", "properties": { "sessionID": "r", "requestID": "q1" } }),
            ]),
            &["running r", "waiting r", "clear r"],
        ),
        (
            "two approvals open",
            turn(vec![
                asked("permission", "r", "p1"),
                asked("permission", "r", "p2"),
                replied("r", "p1", "once"),
                replied("r", "p2", "always"),
            ]),
            &["running r", "waiting r", "running r", "done r"],
        ),
        (
            "sub-agent asks and is turned down",
            turn(vec![
                created("c", "r"),
                status("c", "busy"),
                asked("permission", "c", "p1"),
                replied("c", "p1", "reject"),
                error("c", "APIError"),
                idle("c")[0].clone(),
                status("r", "busy"),
            ]),
            &["running r", "waiting r", "running r", "done r"],
        ),
    ];
    for (case, events, expected) in cases {
        let Some(states) = reported(events) else { return };
        assert_eq!(states, expected, "{case}");
    }
}
