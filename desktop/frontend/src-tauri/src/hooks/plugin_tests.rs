use super::*;

/// The `HOOKS` table a generated plugin carries.
fn hooks_of(plugin: &str) -> Value {
    let line = plugin
        .lines()
        .find_map(|line| line.strip_prefix("const HOOKS = "))
        .unwrap();
    serde_json::from_str(line).unwrap()
}

/// What every plugin is checked for: the agent's half first, under lpm's
/// marker, and a hook for every state — sh, or the CLI without a shell.
pub(in crate::hooks) fn assert_hooks_for_every_state(plugin: &Plugin) {
    let source = plugin.render(&HookForm::Shell);
    assert!(source.starts_with(plugin.source));
    assert!(source.lines().next().unwrap().starts_with("// lpm status plugin"));
    assert!(source.contains(MARKER));
    assert!(!source.contains(HOOKS_SLOT));
    let hooks = hooks_of(&source);
    assert_eq!(hooks.as_object().unwrap().len(), STATES.len());
    for state in STATES {
        assert_eq!(hooks[state][0], "/bin/sh", "{state}");
        assert_eq!(hooks[state][1][0], "-c", "{state}");
        assert!(hooks[state][1][1].as_str().unwrap().ends_with(MARKER), "{state}");
    }

    for state in STATES {
        let line = hooks[state][1][1].as_str().unwrap();
        assert!(!line.contains(" & }"), "{state}: delivered in the background, so out of order");
    }

    let cli = HookForm::Cli(PathBuf::from(r"C:\Program Files\lpm\lpm-cli.exe"));
    let hooks = hooks_of(&plugin.render(&cli));
    for state in STATES {
        assert_eq!(
            hooks[state],
            json!([r"C:\Program Files\lpm\lpm-cli.exe", ["hook", plugin.kind.id(), state, MARKER]])
        );
    }
}

/// The frame each state's sh hook delivers for session `ses_1` of a tab in
/// project `proj`, without the reporter pid that ends it.
#[cfg(unix)]
pub(in crate::hooks) fn frames_by_state(plugin: &Plugin) -> Vec<String> {
    let hooks = hooks_of(&plugin.render(&HookForm::Shell));
    STATES
        .iter()
        .map(|state| {
            let cmd = hooks[state][1][1].as_str().unwrap();
            let frame = crate::hooks::test_support::run_hook_in_project(cmd, r#"{"session_id":"ses_1"}"#, "proj")
                .expect(state);
            frame.split(" --reporter-pid=").next().unwrap().to_string()
        })
        .collect()
}

/// The states `plugin` reports for `events`, run in Node with each state's
/// hook recording what it was given. `harness` is a module that loads the
/// plugin at argv[2] and feeds it the events in argv[3] (JSON) as the agent
/// would. None when Node isn't installed.
#[cfg(unix)]
pub(in crate::hooks) fn reported(plugin: &Plugin, harness: &str, events: Vec<Value>) -> Option<Vec<String>> {
    if std::process::Command::new("node").arg("--version").output().is_err() {
        eprintln!("node not found: {:?} plugin turns not checked", plugin.kind);
        return None;
    }
    let td = tempfile::tempdir().unwrap();
    let log = td.path().join("log");
    let hooks = STATES
        .iter()
        .map(|state| {
            let record = format!("printf '%s %s\\n' {state} \"$(cat)\" >> '{}'", log.display());
            (state.to_string(), json!(["/bin/sh", ["-c", record]]))
        })
        .collect();
    let module = td.path().join("lpm-status.mjs");
    std::fs::write(&module, plugin.with_hooks(hooks)).unwrap();
    let runner = td.path().join("run.mjs");
    std::fs::write(&runner, harness).unwrap();
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

const PLUGIN: Plugin = Plugin {
    kind: AgentKind::OpenCode,
    source: "// lpm status plugin # lpm-hook",
    path: ["plugins", "lpm-status.js"],
    remote_dir: "${XDG_CONFIG_HOME:-$HOME/.config}/opencode",
};

#[test]
fn install_waits_for_the_agent_rewrites_only_on_change_and_uninstall_takes_only_its_own() {
    let td = tempfile::tempdir().unwrap();
    let dir = td.path().join("agent");
    let path = PLUGIN.path_in(&dir);
    PLUGIN.install(Some(&dir));
    assert!(!dir.exists(), "the agent isn't installed: nothing is created");

    std::fs::create_dir(&dir).unwrap();
    PLUGIN.install(Some(&dir));
    let written = std::fs::read_to_string(&path).unwrap();
    assert_eq!(written, PLUGIN.render(&crate::hookform::local()));
    let at = std::fs::metadata(&path).unwrap().modified().unwrap();
    std::thread::sleep(std::time::Duration::from_millis(20));
    PLUGIN.install(Some(&dir));
    assert_eq!(std::fs::metadata(&path).unwrap().modified().unwrap(), at);
    std::fs::write(&path, "an older one # lpm-hook").unwrap();
    PLUGIN.install(Some(&dir));
    assert_eq!(std::fs::read_to_string(&path).unwrap(), written);

    PLUGIN.uninstall(Some(&dir)).unwrap();
    assert!(!path.exists());
    std::fs::write(&path, "the user's own").unwrap();
    PLUGIN.uninstall(Some(&dir)).unwrap();
    assert!(path.exists());
}

#[test]
fn a_host_gets_the_plugin_at_the_same_place_in_the_agents_folder() {
    assert_eq!(
        PLUGIN.remote_paths(),
        (
            r#""${XDG_CONFIG_HOME:-$HOME/.config}/opencode""#.to_string(),
            r#""${XDG_CONFIG_HOME:-$HOME/.config}/opencode/plugins/lpm-status.js""#.to_string(),
        )
    );
    assert_eq!(
        PLUGIN.path_in(Path::new("oc")),
        Path::new("oc").join("plugins").join("lpm-status.js")
    );
}
