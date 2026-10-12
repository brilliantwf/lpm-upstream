// Cursor CLI (cursor-agent): lpm's hooks in ~/.cursor/hooks.json.
//
// Cursor hands a hook its payload as a heredoc appended to the command
// (`<command> <<'CURSOR_HOOK_EOF'`), which binds to the command's last simple
// command — and a trailing comment would swallow it, leaving the payload's line
// to run as shell. So each hook is one `sh -c '…'` whose stdin is the payload,
// with the marker inside the quoted script. Only observational events are
// used: Cursor reads empty output from a permission hook as no opinion, but lpm
// has no business near those decisions. Its CLI asks for approvals without
// firing any hook, so a Cursor tab never shows Waiting.
use super::sh::{send_cmd, Hook};
use super::{
    append_hook, changed_json, install_json_at, install_remote_json, strip_hooks_json_at,
    strip_lpm_hooks, Agent, MARKER,
};
use crate::hookform::HookForm;
use serde_json::{json, Value};

pub(super) const AGENT: Agent = Agent {
    name: "cursor",
    install,
    install_remote,
    uninstall,
};

const REMOTE_DIR: &str = "\"$HOME/.cursor\"";
const REMOTE_HOOKS: &str = "\"$HOME/.cursor/hooks.json\"";

/// The events lpm reports on. Cursor fires `stop` only when a hooks.json of its
/// own asks for it — Claude's imported hooks don't count.
pub(super) const CURSOR_EVENTS: [&str; 5] = [
    "beforeSubmitPrompt",
    "postToolUse",
    "postToolUseFailure",
    "stop",
    "sessionEnd",
];

fn hooks_path() -> Option<std::path::PathBuf> {
    dirs::home_dir().map(|home| home.join(".cursor").join("hooks.json"))
}

fn install() {
    if let Some(path) = hooks_path() {
        let _ = install_json_at(&path, |data| merge_cursor_hooks_in(data, &crate::hookform::local()));
    }
}

fn install_remote(ssh: &crate::config::SshSettings) -> bool {
    install_remote_json(ssh, REMOTE_DIR, REMOTE_HOOKS, merge_cursor_hooks)
}

fn uninstall() -> Result<(), String> {
    hooks_path().map_or(Ok(()), |path| strip_hooks_json_at(&path))
}

/// Remote hosts always get the sh form.
pub(super) fn merge_cursor_hooks(data: &[u8]) -> Option<Vec<u8>> {
    merge_cursor_hooks_in(data, &HookForm::Shell)
}

/// Pure: hooks.json with lpm's hooks in place of any earlier ones, or None when
/// unchanged. Empty input starts the file; anything that isn't a JSON object
/// (Cursor also reads comments, which serde can't keep) is left alone.
pub(super) fn merge_cursor_hooks_in(data: &[u8], form: &HookForm) -> Option<Vec<u8>> {
    let mut config = if data.iter().all(u8::is_ascii_whitespace) {
        json!({})
    } else {
        serde_json::from_slice::<Value>(data).ok()?
    };
    let original = serde_json::from_slice::<Value>(data).unwrap_or(Value::Null);
    let obj = config.as_object_mut()?;
    obj.entry("version").or_insert(json!(1));
    if !obj.get("hooks").is_some_and(Value::is_object) {
        obj.insert("hooks".into(), json!({}));
    }
    let hooks = obj.get_mut("hooks")?.as_object_mut()?;
    strip_lpm_hooks(hooks);
    for event in CURSOR_EVENTS {
        let command = match form {
            HookForm::Shell => sh_c(&cursor_cmd(event)),
            HookForm::Cli(cli) => crate::hookform::powershell_command(cli, "cursor", event, MARKER),
        };
        append_hook(hooks, event, json!({ "command": command }));
    }
    changed_json(&config, &original)
}

/// `script` as one `sh -c` command, single-quoted for whichever shell Cursor
/// runs it in.
fn sh_c(script: &str) -> String {
    format!("sh -c '{}'", script.replace('\'', r"'\''"))
}

/// Keyed by the pane, like Codex's: the payload leads with its conversation
/// id, but a pane runs one conversation at a time and every event names it.
/// `stop` says how the turn ended: done, failed, or interrupted — which takes
/// back the Running it left.
fn cursor_cmd(event: &str) -> String {
    let key = "cursor_$LPM_PANE_ID";
    let (icon, color) = crate::agentkind::AgentKind::Cursor.running_look();
    match event {
        "stop" => {
            let prep = format!(
                r#"f="set_status '$pq' {key} Done --icon=checkmark --color=#4ade80 --pane=$LPM_PANE_ID"; case "$p" in *\"status\":\"error\"*) f="set_status '$pq' {key} Error --icon=warning --color=#ef4444 --pane=$LPM_PANE_ID";; *\"status\":\"aborted\"*) f="clear_status '$pq' {key} --live";; esac;"#
            );
            Hook {
                read: "[ -t 0 ] || p=$(cat);",
                prep: &prep,
                frame: "$f",
                ..Default::default()
            }
            .line()
        }
        "sessionEnd" => send_cmd(&format!("clear_status '$pq' {key} --live")),
        _ => send_cmd(&format!(
            "set_status '$pq' {key} Running --icon={icon} --color={color} --pane=$LPM_PANE_ID"
        )),
    }
}

#[cfg(test)]
#[path = "cursor_tests.rs"]
mod tests;
