// Gemini CLI: lpm's hooks in its settings.json, which it runs through `bash -c`
// (PowerShell on Windows) with the event's JSON on stdin, `session_id` first.
use super::sh::{sid_prep, Hook};
use super::{
    append_hook, changed_json, install_json_at, install_remote_json, strip_hooks_json_at,
    strip_lpm_hooks, Agent, MARKER,
};
use crate::hookform::HookForm;
use serde_json::{json, Value};

pub(super) const AGENT: Agent = Agent {
    name: "gemini",
    install,
    install_remote,
    uninstall,
};

const REMOTE_DIR: &str = "\"${GEMINI_CLI_HOME:-$HOME}/.gemini\"";
const REMOTE_SETTINGS: &str = "\"${GEMINI_CLI_HOME:-$HOME}/.gemini/settings.json\"";

/// The events lpm reports on: a turn starts at BeforeAgent and ends at
/// AfterAgent, which fires for the outermost turn only, so a sub-agent's work
/// never ends it. Notification fires as a tool's confirmation dialog opens; the
/// tool's AfterTool is the first sign the agent is back at work.
pub(super) const GEMINI_EVENTS: [&str; 6] = [
    "BeforeAgent",
    "BeforeTool",
    "AfterTool",
    "Notification",
    "AfterAgent",
    "SessionEnd",
];

fn settings_path() -> Option<std::path::PathBuf> {
    crate::agentdirs::gemini_dir().map(|dir| dir.join("settings.json"))
}

fn install() {
    if let Some(path) = settings_path() {
        let _ = install_json_at(&path, |data| merge_gemini_hooks_in(data, &crate::hookform::local()));
    }
}

fn install_remote(ssh: &crate::config::SshSettings) -> bool {
    install_remote_json(ssh, REMOTE_DIR, REMOTE_SETTINGS, merge_gemini_hooks)
}

fn uninstall() -> Result<(), String> {
    settings_path().map_or(Ok(()), |path| strip_hooks_json_at(&path))
}

/// Remote hosts always get the sh form.
pub(super) fn merge_gemini_hooks(data: &[u8]) -> Option<Vec<u8>> {
    merge_gemini_hooks_in(data, &HookForm::Shell)
}

/// Pure: settings.json with lpm's hooks in place of any earlier ones, or None
/// when unchanged. Empty input starts the file; anything that isn't a JSON
/// object (Gemini also reads comments, which serde can't keep) is left alone.
pub(super) fn merge_gemini_hooks_in(data: &[u8], form: &HookForm) -> Option<Vec<u8>> {
    let mut settings = if data.iter().all(u8::is_ascii_whitespace) {
        json!({})
    } else {
        serde_json::from_slice::<Value>(data).ok()?
    };
    let original = serde_json::from_slice::<Value>(data).unwrap_or(Value::Null);
    let obj = settings.as_object_mut()?;
    if !obj.get("hooks").is_some_and(Value::is_object) {
        obj.insert("hooks".into(), json!({}));
    }
    let hooks = obj.get_mut("hooks")?.as_object_mut()?;
    strip_lpm_hooks(hooks);
    for event in GEMINI_EVENTS {
        let command = match form {
            HookForm::Shell => gemini_cmd(event),
            HookForm::Cli(cli) => crate::hookform::powershell_command(cli, "gemini", event, MARKER),
        };
        append_hook(hooks, event, gemini_entry(&command));
    }
    changed_json(&settings, &original)
}

fn gemini_entry(command: &str) -> Value {
    json!({ "matcher": "", "hooks": [ { "name": "lpm", "type": "command", "command": command } ] })
}

/// Keyed by Gemini's own session id, like Claude's. Gemini reads a hook's stderr
/// as its output when stdout is empty and shows it to the user, so the hook
/// silences it first.
fn gemini_cmd(event: &str) -> String {
    let key = "gemini_${sid:-$LPM_PANE_ID}";
    let (running_icon, running_color) = crate::agentkind::AgentKind::Gemini.running_look();
    let frame = match event {
        "Notification" => format!("set_status '$pq' {key} Waiting --icon=bell --color=#f59e0b --pane=$LPM_PANE_ID"),
        "AfterAgent" => format!("set_status '$pq' {key} Done --icon=checkmark --color=#4ade80 --pane=$LPM_PANE_ID"),
        "SessionEnd" => format!("clear_status '$pq' {key} --live"),
        _ => format!(
            "set_status '$pq' {key} Running --icon={running_icon} --color={running_color} --pane=$LPM_PANE_ID"
        ),
    };
    let prep = sid_prep();
    Hook {
        read: "exec 2>/dev/null;",
        prep: &prep,
        frame: &frame,
        ..Default::default()
    }
    .line()
}

#[cfg(test)]
#[path = "gemini_tests.rs"]
mod tests;
