// Codex's hook events as socket frames.
use super::{
    child_frame, clear_live_frame, resume_frame, status_frame, str_field, HookEnv, APPROVAL, DONE, WAITING,
};
use serde_json::Value;
use std::fs::File;
use std::io::{BufRead, BufReader, Seek, SeekFrom};
use std::path::Path;

const CODEX_RUNNING: &str = "Running --icon=sparkle --color=#10A37F";

/// A tool call starting. Unlike the rest it can't settle an approval request
/// still in its grace period: the call's own start can arrive after the request.
const CODEX_STEP: &str = "Running --icon=sparkle --color=#10A37F --step";

/// Tail windows searched for a Codex rollout's last `turn_context` before the
/// whole file: large tool output can push it megabytes behind the write head.
const ROLLOUT_WINDOWS: [u64; 2] = [1 << 20, 16 << 20];

pub(super) fn frames(event: &str, payload: &Value, env: &HookEnv) -> Vec<String> {
    let key = format!("codex_{}", env.pane);
    // Codex runs the tab's hooks inside thread-spawned sub-agents too; their
    // payloads carry `agent_id` and their turns end in SubagentStop, so only the
    // root thread may speak for the tab's status. Its hidden memory-consolidation
    // thread carries no `agent_id` and reports no Stop, but works in the memory
    // folder under CODEX_HOME.
    let root = payload.get("agent_id").is_none() && !in_codex_memories(payload, env);
    let status = |value: &str| root.then(|| status_frame(&key, value, env));
    match event {
        "SessionStart" => status(CODEX_RUNNING)
            .into_iter()
            .chain(
                str_field(payload, "session_id")
                    .filter(|_| root)
                    .map(|sid| resume_frame("codex", sid, env)),
            )
            .collect(),
        "UserPromptSubmit" | "PostToolUse" => status(CODEX_RUNNING).into_iter().collect(),
        "PreToolUse" => {
            let value = if str_field(payload, "tool_name") == Some("request_user_input") {
                WAITING
            } else {
                CODEX_STEP
            };
            status(value).into_iter().collect()
        }
        "PermissionRequest" => {
            let auto = str_field(payload, "transcript_path")
                .is_some_and(|path| routes_to_auto_review(Path::new(path)));
            if auto {
                Vec::new()
            } else {
                vec![status_frame(&key, APPROVAL, env)]
            }
        }
        "Stop" => status(DONE).into_iter().collect(),
        "Interrupt" | "SessionEnd" => vec![clear_live_frame(&key, env)],
        // Only a /compact the user ran; compaction inside a turn is the turn's.
        "PreCompact" | "PostCompact" if str_field(payload, "trigger") != Some("manual") => {
            Vec::new()
        }
        "PreCompact" => status(CODEX_RUNNING).into_iter().collect(),
        "PostCompact" => root.then(|| clear_live_frame(&key, env)).into_iter().collect(),
        "SubagentStart" | "SubagentStop" => {
            let phase = if event == "SubagentStart" { "start" } else { "stop" };
            str_field(payload, "agent_id")
                .map(|child| child_frame(&key, child, phase, env))
                .into_iter()
                .collect()
        }
        _ => Vec::new(),
    }
}

fn in_codex_memories(payload: &Value, env: &HookEnv) -> bool {
    let (Some(cwd), Some(codex_home)) = (str_field(payload, "cwd"), env.codex_home.as_deref())
    else {
        return false;
    };
    Path::new(cwd)
        .strip_prefix(codex_home)
        .ok()
        .and_then(|rest| rest.iter().next())
        .is_some_and(|dir| dir.to_string_lossy().starts_with("memories"))
}

/// Whether Codex sends this session's approvals to its automatic reviewer, which
/// never prompts the user, read from the rollout's last `turn_context`. Any read
/// failure answers no, so the request still shows as Waiting.
fn routes_to_auto_review(rollout: &Path) -> bool {
    let Ok(mut file) = File::open(rollout) else {
        return false;
    };
    let Ok(len) = file.metadata().map(|meta| meta.len()) else {
        return false;
    };
    for window in ROLLOUT_WINDOWS.into_iter().chain([u64::MAX]) {
        let start = len.saturating_sub(window);
        if file.seek(SeekFrom::Start(start)).is_err() {
            return false;
        }
        if let Some(line) = last_line_with(&mut file, br#""type":"turn_context""#) {
            let has = |needle: &[u8]| contains(&line, needle);
            return (has(br#""approval_policy":"on-request""#)
                || has(br#""approval_policy":{"granular""#))
                && has(br#""approvals_reviewer":"auto_review""#);
        }
        if start == 0 {
            break;
        }
    }
    false
}

fn last_line_with(file: &mut File, needle: &[u8]) -> Option<Vec<u8>> {
    let mut last = None;
    for line in BufReader::new(file).split(b'\n') {
        let line = line.ok()?;
        if contains(&line, needle) {
            last = Some(line);
        }
    }
    last
}

fn contains(haystack: &[u8], needle: &[u8]) -> bool {
    haystack
        .windows(needle.len())
        .any(|window| window == needle)
}
