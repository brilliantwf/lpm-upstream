use super::*;

pub(super) fn env() -> HookEnv {
    HookEnv {
        socket: None,
        project: "proj".into(),
        pane: "pane-1".into(),
        config_dir: None,
        home: None,
        codex_home: Some("/home/u/.codex".into()),
        reporter_pid: Some(42),
        under_cursor: false,
    }
}

pub(super) fn one(agent: &str, event: &str, payload: &str) -> String {
    let lines = frames(agent, event, payload.as_bytes(), &env());
    assert_eq!(lines.len(), 1, "{lines:?}");
    lines.into_iter().next().unwrap()
}

pub(super) fn none(agent: &str, event: &str, payload: &str) {
    let lines = frames(agent, event, payload.as_bytes(), &env());
    assert!(lines.is_empty(), "{lines:?}");
}

#[test]
fn nothing_is_sent_without_a_pane_identity() {
    let mut e = env();
    e.pane.clear();
    assert!(frames("claude", "Stop", b"{}", &e).is_empty());
    let mut e = env();
    e.project.clear();
    assert!(frames("codex", "Stop", b"{}", &e).is_empty());
    none("claude", "Unknown", "{}");
    none("aider", "Stop", "{}");
}

#[test]
fn identifiers_that_are_not_plain_tokens_are_quoted() {
    let mut e = env();
    e.project = "it's".into();
    e.pane = "a b".into();
    e.reporter_pid = None;
    let line = frames("codex", "Stop", b"{}", &e).remove(0);
    assert_eq!(
        line,
        r#"set_status 'it'"'"'s' 'codex_a b' Done --icon=checkmark --color=#4ade80 --pane='a b'"#
    );
}

#[test]
fn statusline_forwards_the_payload_under_the_config_dirs_account() {
    let mut e = env();
    let payload = b"{\"model\":{\"display_name\":\"Opus\"}}\n";
    let line = frames("claude", "statusline", payload, &e).remove(0);
    assert_eq!(
        line,
        format!(
            "agent_limits default --payload-b64={}",
            base64(&payload[..payload.len() - 1])
        )
    );
    e.project.clear();
    e.pane.clear();
    e.config_dir = Some(r"C:\Users\me\.lpm\claude-accounts\work".into());
    let line = frames("claude", "statusline", b"{}", &e).remove(0);
    assert_eq!(line, "agent_limits work --payload-b64=e30=");
}

#[test]
fn limits_account_follows_the_sh_forwarder_rule() {
    assert_eq!(limits_account(None), "default");
    assert_eq!(limits_account(Some("/Users/me/.claude")), "default");
    assert_eq!(
        limits_account(Some("/Users/me/.lpm/claude-accounts/work")),
        "work"
    );
    assert_eq!(
        limits_account(Some("/Users/me/.lpm/claude-accounts/work/")),
        "default"
    );
    assert_eq!(limits_account(Some(r"C:\x\claude-accounts\home")), "home");
}

#[test]
fn base64_matches_the_standard_alphabet_with_padding() {
    assert_eq!(base64(b""), "");
    assert_eq!(base64(b"f"), "Zg==");
    assert_eq!(base64(b"fo"), "Zm8=");
    assert_eq!(base64(b"foo"), "Zm9v");
    assert_eq!(base64(b"foobar"), "Zm9vYmFy");
    assert_eq!(base64(&[0xff, 0xfe, 0x00]), "//4A");
}

#[cfg(unix)]
#[test]
fn delivers_every_frame_to_the_recovered_socket_in_order() {
    use std::io::{BufRead, BufReader, Write};
    use std::os::unix::net::UnixListener;
    let home = tempfile::tempdir().unwrap();
    let lpm = home.path().join(".lpm");
    std::fs::create_dir_all(&lpm).unwrap();
    let listener = UnixListener::bind(lpm.join("lpm.sock")).unwrap();
    let mut e = env();
    e.socket = Some(home.path().join("stale.sock"));
    e.home = Some(home.path().to_path_buf());
    let socket = status_socket(e.socket.as_deref(), e.home.as_deref())
        .expect("falls back to ~/.lpm/lpm.sock");
    let server = std::thread::spawn(move || {
        let (stream, _) = listener.accept().unwrap();
        let mut got = Vec::new();
        for line in BufReader::new(stream.try_clone().unwrap()).lines() {
            got.push(line.unwrap());
            writeln!(&stream, "OK").unwrap();
        }
        got
    });
    let lines = frames("codex", "SessionStart", br#"{"session_id":"s1"}"#, &e);
    deliver(&socket, &lines);
    assert_eq!(server.join().unwrap(), lines);
}

#[cfg(unix)]
#[test]
fn reports_the_process_that_ran_the_hook() {
    assert_eq!(parent_pid(), Some(std::os::unix::process::parent_id()));
}

#[test]
fn a_manual_compact_is_work_and_an_automatic_one_is_the_turns() {
    let claude = r#"{"session_id":"s1","trigger":"manual"}"#;
    assert!(one("claude", "PreCompact", claude).contains("claude_code_s1 Running"));
    assert!(one("claude", "PostCompact", claude).starts_with("clear_status 'proj' claude_code_s1 --live"));
    let manual = r#"{"session_id":"t1","turn_id":"u1","trigger":"manual"}"#;
    assert!(one("codex", "PreCompact", manual).contains("codex_pane-1 Running"));
    assert!(one("codex", "PostCompact", manual).starts_with("clear_status 'proj' codex_pane-1 --live"));
    let auto = r#"{"session_id":"t1","turn_id":"u1","trigger":"auto"}"#;
    none("codex", "PreCompact", auto);
    none("codex", "PostCompact", auto);
}

#[test]
fn a_tmux_session_names_the_tab_that_attached_it() {
    let env = super::parse_tmux_env(
        "LPM_PANE_ID=tab-b\nLPM_PROJECT_NAME=Bob's app\n-LPM_SOCKET_PATH\nTERM=xterm\nLPM_EMPTY=\n",
    );
    assert_eq!(env.get("LPM_PANE_ID").map(String::as_str), Some("tab-b"));
    assert_eq!(env.get("LPM_PROJECT_NAME").map(String::as_str), Some("Bob's app"));
    assert!(!env.contains_key("LPM_SOCKET_PATH") && !env.contains_key("TERM") && !env.contains_key("LPM_EMPTY"));
}
