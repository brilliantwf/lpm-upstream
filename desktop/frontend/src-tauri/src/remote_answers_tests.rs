use super::*;

fn answer(text: &str) -> RecentAnswer {
    RecentAnswer {
        text: text.to_string(),
        at: None,
    }
}

fn texts(answers: &[RecentAnswer]) -> Vec<&str> {
    answers.iter().map(|a| a.text.as_str()).collect()
}

#[test]
fn a_terminal_without_a_session_has_no_answers_yet() {
    assert_eq!(read("demo".into(), None, 1), Ok(Vec::new()));
}

#[test]
fn limit_defaults_to_one_and_stays_within_the_pick_list() {
    assert_eq!(clamp_limit(None), 1);
    assert_eq!(clamp_limit(Some(0)), 1);
    assert_eq!(clamp_limit(Some(20)), 20);
    assert_eq!(clamp_limit(Some(500)), MAX_ANSWERS);
    assert_eq!(clamp_limit(Some(u64::MAX)), MAX_ANSWERS);
}

#[test]
fn budget_drops_the_oldest_answers_first() {
    let kept = within_budget(vec![answer("newest"), answer("middle"), answer("oldest")], 12);
    assert_eq!(texts(&kept), ["newest", "middle"]);
}

#[test]
fn the_newest_answer_goes_out_whole_even_over_budget() {
    let long = "x".repeat(64);
    let kept = within_budget(vec![answer(&long), answer("older")], 10);
    assert_eq!(texts(&kept), [long.as_str()]);
}

#[test]
fn session_needs_both_provider_and_id() {
    let full = json!({ "provider": "claude", "sessionId": "abc-123" });
    assert_eq!(
        AgentSession::from_value(&full),
        Some(AgentSession {
            provider: "claude".into(),
            session_id: "abc-123".into(),
        })
    );
    assert_eq!(AgentSession::from_value(&json!({ "provider": "codex" })), None);
    assert_eq!(
        AgentSession::from_value(&json!({ "provider": "", "sessionId": "abc" })),
        None
    );
    assert_eq!(AgentSession::from_value(&Value::Null), None);
}

#[test]
fn replies_name_the_terminal_and_echo_the_request() {
    let (tx, rx) = std::sync::mpsc::sync_channel(1);
    handle(
        &tx,
        &json!({ "t": "answers", "id": "pty-7", "project": "demo", "reqId": "r1" }),
        None,
    );
    let reply: Value = serde_json::from_str(&rx.recv().unwrap()).unwrap();
    assert_eq!(
        reply,
        json!({ "t": "answers", "id": "pty-7", "reqId": "r1", "ok": true, "answers": [] })
    );
}
