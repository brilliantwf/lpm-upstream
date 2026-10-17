// Phone-side copy of an agent's answers: the `answers` verb behind the copy key
// on the phone's terminal screen.
//
// The phone names a terminal, never a conversation. The Mac maps it to the agent
// session the desktop's tab tree reported for it, then reads that session's
// transcript exactly as the desktop's copy pill does, so the clipboard gets the
// original markdown. Transcripts reach tens of megabytes, so the read runs on a
// worker and answers through the out-queue.
use crate::agent_last_answer::{agent_recent_answers, RecentAnswer};
use serde_json::{json, Value};
use std::sync::mpsc::SyncSender;

pub const FEATURE: &str = "agentAnswers";

/// The desktop's pick-list length.
const MAX_ANSWERS: usize = 20;
/// What one list reply may carry over a phone's link. The newest answer always
/// goes out whole, whatever its size.
const REPLY_BUDGET: usize = 1 << 20;

/// The agent conversation a terminal belongs to, as the desktop reported it.
#[derive(Debug, Clone, PartialEq)]
pub struct AgentSession {
    pub provider: String,
    pub session_id: String,
}

impl AgentSession {
    pub fn from_value(v: &Value) -> Option<Self> {
        let field = |k: &str| v.get(k).and_then(Value::as_str).filter(|s| !s.is_empty());
        Some(Self {
            provider: field("provider")?.to_string(),
            session_id: field("sessionId")?.to_string(),
        })
    }
}

pub fn handle(out: &SyncSender<String>, v: &Value, session: Option<AgentSession>) {
    let field = |k: &str| {
        v.get(k)
            .and_then(Value::as_str)
            .map(str::to_string)
            .unwrap_or_default()
    };
    let id = field("id");
    let project = field("project");
    let limit = clamp_limit(v.get("limit").and_then(Value::as_u64));
    let req_id = v.get("reqId").cloned().unwrap_or(Value::Null);
    let out = out.clone();
    std::thread::spawn(move || {
        let mut reply = match read(project, session, limit) {
            Ok(answers) => json!({ "ok": true, "answers": answers }),
            Err(e) => json!({ "ok": false, "error": e }),
        };
        reply["t"] = json!("answers");
        reply["id"] = json!(id);
        if !req_id.is_null() {
            reply["reqId"] = req_id;
        }
        let _ = out.try_send(reply.to_string());
    });
}

/// A terminal with no reported session (a fresh Codex before its first hook, a
/// plain shell) has nothing to copy yet, which is an answer, not an error.
fn read(
    project: String,
    session: Option<AgentSession>,
    limit: usize,
) -> Result<Vec<RecentAnswer>, String> {
    let Some(session) = session else {
        return Ok(Vec::new());
    };
    agent_recent_answers(project, session.provider, session.session_id, limit)
        .map(|answers| within_budget(answers, REPLY_BUDGET))
}

fn clamp_limit(limit: Option<u64>) -> usize {
    limit
        .map_or(1, |n| usize::try_from(n).unwrap_or(MAX_ANSWERS))
        .clamp(1, MAX_ANSWERS)
}

/// Keeps answers newest first until `budget` bytes are spent, dropping the
/// oldest. The first answer is kept even when it alone is over budget.
fn within_budget(answers: Vec<RecentAnswer>, budget: usize) -> Vec<RecentAnswer> {
    let mut used = 0;
    answers
        .into_iter()
        .enumerate()
        .take_while(|(i, answer)| {
            used += answer.text.len();
            *i == 0 || used <= budget
        })
        .map(|(_, answer)| answer)
        .collect()
}

#[cfg(test)]
#[path = "remote_answers_tests.rs"]
mod tests;
