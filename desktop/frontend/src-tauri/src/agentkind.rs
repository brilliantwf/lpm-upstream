// The agent CLIs whose hooks report status (hooks/), told apart the only way the
// socket and the store can: by the prefix each one's hooks put on its keys.
// A key outside these is a caller's own — `lpm set-status` — and speaks for
// whatever it likes.

#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub enum AgentKind {
    Claude,
    Codex,
    Gemini,
    Cursor,
}

impl AgentKind {
    const ALL: [AgentKind; 4] = [Self::Claude, Self::Codex, Self::Gemini, Self::Cursor];

    pub fn key_prefix(self) -> &'static str {
        match self {
            Self::Claude => "claude_code_",
            Self::Codex => "codex_",
            Self::Gemini => "gemini_",
            Self::Cursor => "cursor_",
        }
    }

    pub fn of_key(key: &str) -> Option<Self> {
        Self::ALL
            .into_iter()
            .find(|agent| key.starts_with(agent.key_prefix()))
    }

    /// The icon and color of the Running report its hooks send.
    pub fn running_look(self) -> (&'static str, &'static str) {
        match self {
            Self::Claude => ("bolt", "#4C8DFF"),
            Self::Codex => ("sparkle", "#10A37F"),
            Self::Gemini => ("sparkle", "#4796E3"),
            Self::Cursor => ("bolt", "#8B5CF6"),
        }
    }

    /// Whether its keys name a session, several of which can come and go on one
    /// pane, rather than the pane itself.
    pub fn keyed_by_session(self) -> bool {
        matches!(self, Self::Claude | Self::Gemini)
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn keys_name_their_agent() {
        assert_eq!(AgentKind::of_key("claude_code_9a15513b-e4a3"), Some(AgentKind::Claude));
        assert_eq!(AgentKind::of_key("codex_p-1"), Some(AgentKind::Codex));
        assert_eq!(AgentKind::of_key("gemini_s1"), Some(AgentKind::Gemini));
        assert_eq!(AgentKind::of_key("cursor_p-1"), Some(AgentKind::Cursor));
        assert_eq!(AgentKind::of_key("build"), None);
        assert_eq!(AgentKind::of_key("claude"), None);
    }
}
