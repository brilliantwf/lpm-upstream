// OpenCode: lpm's status plugin in its global plugins folder. OpenCode runs no
// hook commands; it loads every script in that folder and streams its events to
// them. lpm's (opencode_plugin.js) follows each session's turn and, on every
// change, runs that state's hook with the session id on stdin — the sh line, or
// `lpm hook opencode <state>` on Windows — so the app gets the frames every
// other agent's hooks send.
use super::sh::{sid_prep, Frames, Hook};
use super::{install_owned_file, install_remote_owned_file, remove_owned_file, Agent, MARKER};
use crate::agentkind::AgentKind;
use crate::hookform::HookForm;
use serde_json::{json, Map, Value};
use std::path::{Path, PathBuf};

pub(super) const AGENT: Agent = Agent {
    kind: AgentKind::OpenCode,
    install,
    install_remote,
    uninstall,
};

const REMOTE_DIR: &str = "\"${XDG_CONFIG_HOME:-$HOME/.config}/opencode\"";
const REMOTE_PLUGIN: &str = "\"${XDG_CONFIG_HOME:-$HOME/.config}/opencode/plugins/lpm-status.js\"";

/// The states the plugin reports a session's turn in.
pub(super) const OPENCODE_STATES: [&str; 5] = ["running", "waiting", "done", "error", "clear"];

const PLUGIN: &str = include_str!("opencode_plugin.js");
const HOOKS_SLOT: &str = "__LPM_HOOKS__";

fn plugin_path(dir: &Path) -> PathBuf {
    dir.join("plugins").join("lpm-status.js")
}

fn install() {
    if let Some(dir) = crate::agentdirs::opencode_dir() {
        let _ = install_owned_file(dir, &plugin_path(dir), &plugin(&crate::hookform::local()));
    }
}

fn install_remote(ssh: &crate::config::SshSettings) -> bool {
    install_remote_owned_file(ssh, REMOTE_DIR, REMOTE_PLUGIN, &plugin(&HookForm::Shell))
}

fn uninstall() -> Result<(), String> {
    crate::agentdirs::opencode_dir().map_or(Ok(()), |dir| remove_owned_file(&plugin_path(dir)))
}

/// The plugin with each state's hook filled in.
pub(super) fn plugin(form: &HookForm) -> String {
    let hooks: Map<String, Value> = OPENCODE_STATES
        .iter()
        .map(|state| {
            let hook = match form {
                HookForm::Shell => json!(["/bin/sh", ["-c", opencode_cmd(state)]]),
                HookForm::Cli(cli) => json!([cli.to_string_lossy(), ["hook", AGENT.kind.id(), state, MARKER]]),
            };
            (state.to_string(), hook)
        })
        .collect();
    PLUGIN.replace(HOOKS_SLOT, &Value::Object(hooks).to_string())
}

/// Keyed by OpenCode's session id, like Claude's: the top-level session's, which
/// the plugin reports a sub-agent's questions under. Its Waiting is one only the
/// plugin ends — OpenCode reports every answer, and keys typed into a question
/// with several parts don't all answer it.
fn opencode_cmd(state: &str) -> String {
    let frames = Frames::of(AGENT.kind);
    let frame = match state {
        "running" => frames.running(),
        "waiting" => frames.held_waiting(),
        "done" => frames.done(),
        "error" => frames.error(),
        _ => frames.clear_live(),
    };
    let prep = sid_prep();
    Hook {
        prep: &prep,
        frame: &frame,
        ..Default::default()
    }
    .line()
}

#[cfg(test)]
#[path = "opencode_tests.rs"]
mod tests;
