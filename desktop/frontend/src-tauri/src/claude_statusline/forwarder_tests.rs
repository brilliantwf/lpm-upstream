use super::*;

const CLI: &str = r"C:\Users\dev\AppData\Local\lpm\lpm-cli.exe";

fn cli() -> HookForm {
    HookForm::Cli(PathBuf::from(CLI))
}

#[test]
fn statusline_wrap_forwards_and_derives_account() {
    let cmd = statusline_command(&Value::Null);
    assert!(cmd.contains("agent_limits"), "forwards to the socket verb");
    assert!(cmd.contains("--payload-b64="));
    assert!(
        cmd.contains("CLAUDE_CONFIG_DIR"),
        "derives account at runtime"
    );
    assert!(cmd.contains(STATUSLINE_MARKER));
    // No prior statusline -> nothing chained.
    assert!(!cmd.contains("printf %s \"$i\" | ( "));
}

#[test]
fn statusline_forwarder_uses_portable_chain_over_staged_message() {
    let cmd = statusline_command(&Value::Null);
    // stdin is drained first (i=$(cat)), the base64 payload is staged into `m`,
    // then the portable nc -> python3 -> perl chain sends it.
    let cat = cmd.find("i=$(cat)").expect("stdin drained first");
    let stage = cmd.find("m=\"agent_limits").expect("payload staged in m");
    let nc = cmd.find("nc -w1 -U").expect("nc primary");
    let py = cmd.find("python3 -c").expect("python3 fallback");
    let pl = cmd.find("perl -MIO::Socket::UNIX").expect("perl fallback");
    assert!(
        cat < stage && stage < nc && nc < py && py < pl,
        "ordering preserved"
    );
    // agent_limits keeps its own trailing-`&` backgrounding, not the `{ … & }` form.
    assert!(cmd.contains("} >/dev/null 2>&1 &"));
}

#[test]
fn statusline_install_preserves_and_chains_user_command() {
    let body = br#"{"model":"opus","statusLine":{"type":"command","command":"my-line.sh","padding":2}}"#;
    let out = install_statusline(body).expect("install changes settings");
    let v: Value = serde_json::from_slice(&out).unwrap();
    assert_eq!(v["model"], "opus", "siblings preserved");
    assert_eq!(v["statusLine"]["padding"], 2, "padding preserved");
    let cmd = v["statusLine"]["command"].as_str().unwrap();
    assert!(
        cmd.contains("printf %s \"$i\" | ( my-line.sh )"),
        "chains original"
    );
    assert_eq!(v["statusLine"]["refreshInterval"], 30);
    // Restore returns the exact original statusLine.
    let restored = remove_statusline(&out).expect("restore changes settings");
    let r: Value = serde_json::from_slice(&restored).unwrap();
    assert_eq!(r["statusLine"]["command"], "my-line.sh");
    assert_eq!(r["statusLine"]["padding"], 2);
    assert!(r["statusLine"].get("refreshInterval").is_none());
}

#[test]
fn statusline_install_is_idempotent() {
    let body = br#"{"statusLine":{"type":"command","command":"orig"}}"#;
    let first = install_statusline(body).unwrap();
    assert!(
        install_statusline(&first).is_none(),
        "second install is a no-op (no nesting)"
    );
    // And the embedded original still restores to exactly "orig".
    let restored = remove_statusline(&first).unwrap();
    let r: Value = serde_json::from_slice(&restored).unwrap();
    assert_eq!(r["statusLine"]["command"], "orig");
}

#[test]
fn statusline_install_without_prior_removes_on_restore() {
    let out = install_statusline(b"{}").expect("install changes empty settings");
    let v: Value = serde_json::from_slice(&out).unwrap();
    assert_eq!(v["statusLine"]["type"], "command");
    let restored = remove_statusline(&out).expect("restore changes settings");
    let r: Value = serde_json::from_slice(&restored).unwrap();
    assert!(
        r.get("statusLine").is_none(),
        "no prior statusline -> removed"
    );
}

#[test]
fn statusline_keeps_users_faster_refresh_interval() {
    let body = br#"{"statusLine":{"type":"command","command":"x","refreshInterval":5}}"#;
    let out = install_statusline(body).unwrap();
    let v: Value = serde_json::from_slice(&out).unwrap();
    assert_eq!(
        v["statusLine"]["refreshInterval"], 5,
        "5s beats the 30s default"
    );
}

#[test]
fn remove_leaves_foreign_statusline_untouched() {
    let body = br#"{"statusLine":{"type":"command","command":"not-ours.sh"}}"#;
    assert!(
        remove_statusline(body).is_none(),
        "a statusline we didn't install is never modified"
    );
}

#[test]
fn install_and_remove_roundtrip_on_disk() {
    let dir = tempfile::tempdir().unwrap();
    let path = dir.path().join("settings.json");
    std::fs::write(&path, r#"{"model":"opus"}"#).unwrap();
    install_claude_statusline_at(&path).unwrap();
    let v: Value = serde_json::from_slice(&std::fs::read(&path).unwrap()).unwrap();
    assert!(v["statusLine"]["command"]
        .as_str()
        .unwrap()
        .contains("agent_limits"));
    remove_claude_statusline_at(&path).unwrap();
    let v2: Value = serde_json::from_slice(&std::fs::read(&path).unwrap()).unwrap();
    assert_eq!(v2["model"], "opus");
    assert!(v2.get("statusLine").is_none());
}

#[test]
fn install_creates_settings_when_absent() {
    let dir = tempfile::tempdir().unwrap();
    let path = dir.path().join("settings.json"); // does not exist
    install_claude_statusline_at(&path).unwrap();
    assert!(path.exists(), "explicit opt-in creates the file");
    let v: Value = serde_json::from_slice(&std::fs::read(&path).unwrap()).unwrap();
    assert_eq!(v["statusLine"]["type"], "command");
}

// ---- status line templates ----

#[test]
fn cli_statusline_forwarder_chains_and_restores_the_users_line() {
    let original = json!({ "type": "command", "command": "my-line.sh", "padding": 2 });
    let cmd = statusline_command_in(&original, &cli());
    assert!(cmd.starts_with(
        "i=$(cat); printf %s \"$i\" | 'C:/Users/dev/AppData/Local/lpm/lpm-cli.exe' hook claude statusline >/dev/null 2>&1 &"
    ));
    assert!(cmd.contains(" printf %s \"$i\" | ( my-line.sh ) "));
    assert_eq!(unwrap_statusline(&cmd), Some(original.clone()));

    let settings = json!({ "statusLine": { "type": "command", "command": cmd } });
    let restored = remove_statusline(&serde_json::to_vec(&settings).unwrap()).unwrap();
    let v: Value = serde_json::from_slice(&restored).unwrap();
    assert_eq!(v["statusLine"], original);

    let bare = statusline_command_in(&Value::Null, &cli());
    assert!(!bare.contains("( "), "nothing to chain: {bare}");
    assert_eq!(unwrap_statusline(&bare), Some(Value::Null));
}

#[test]
fn shell_statusline_is_unchanged_by_the_dispatch() {
    let original = json!({ "type": "command", "command": "my-line.sh" });
    if cfg!(not(windows)) {
        assert_eq!(
            statusline_command(&original),
            statusline_command_in(&original, &HookForm::Shell)
        );
    }
    let sh = statusline_command_in(&original, &HookForm::Shell);
    assert!(sh.starts_with("acct=default; case \"${CLAUDE_CONFIG_DIR:-}\""));
}
