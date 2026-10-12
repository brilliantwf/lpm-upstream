// OpenCode: lpm's status plugin (plugin.rs) in its global plugins folder, which
// it loads every script from.
use super::plugin::Plugin;
use super::Agent;
use crate::agentkind::AgentKind;

pub(super) const AGENT: Agent = Agent {
    kind: AgentKind::OpenCode,
    install,
    install_remote,
    uninstall,
};

const PLUGIN: Plugin = Plugin {
    kind: AgentKind::OpenCode,
    source: include_str!("opencode_plugin.js"),
    path: ["plugins", "lpm-status.js"],
    remote_dir: "${XDG_CONFIG_HOME:-$HOME/.config}/opencode",
};

fn install() {
    PLUGIN.install(crate::agentdirs::opencode_dir());
}

fn install_remote(ssh: &crate::config::SshSettings) -> bool {
    PLUGIN.install_remote(ssh)
}

fn uninstall() -> Result<(), String> {
    PLUGIN.uninstall(crate::agentdirs::opencode_dir())
}

#[cfg(test)]
#[path = "opencode_tests.rs"]
mod tests;
