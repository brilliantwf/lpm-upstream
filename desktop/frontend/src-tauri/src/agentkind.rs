// The agent CLIs whose hooks report status (hooks/), told apart the only way the
// socket and the store can: by the prefix each one's hooks put on its keys.
// A key outside these is a caller's own — `lpm set-status` — and speaks for
// whatever it likes.
//
// Everything the status code needs to know about an agent is its `Spec`; adding
// an agent starts here (see hooks/mod.rs for the rest of the list).

#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub enum AgentKind {
    Claude,
    Codex,
    Gemini,
    Cursor,
    OpenCode,
}

struct Spec {
    /// Its name in `lpm hook <id> <event>`, and the frontend's provider id.
    id: &'static str,
    key_prefix: &'static str,
    /// The icon and color of the Running report its hooks send.
    running_look: (&'static str, &'static str),
    /// Whether its keys name a session, several of which can come and go on one
    /// pane, rather than the pane itself.
    keyed_by_session: bool,
}

impl AgentKind {
    pub const ALL: [AgentKind; 5] = [
        Self::Claude,
        Self::Codex,
        Self::Gemini,
        Self::Cursor,
        Self::OpenCode,
    ];

    fn spec(self) -> Spec {
        match self {
            Self::Claude => Spec {
                id: "claude",
                key_prefix: "claude_code_",
                running_look: ("bolt", "#4C8DFF"),
                keyed_by_session: true,
            },
            Self::Codex => Spec {
                id: "codex",
                key_prefix: "codex_",
                running_look: ("sparkle", "#10A37F"),
                keyed_by_session: false,
            },
            Self::Gemini => Spec {
                id: "gemini",
                key_prefix: "gemini_",
                running_look: ("sparkle", "#4796E3"),
                keyed_by_session: true,
            },
            Self::Cursor => Spec {
                id: "cursor",
                key_prefix: "cursor_",
                running_look: ("bolt", "#8B5CF6"),
                keyed_by_session: false,
            },
            Self::OpenCode => Spec {
                id: "opencode",
                key_prefix: "opencode_",
                running_look: ("bolt", "#FAB283"),
                keyed_by_session: true,
            },
        }
    }

    pub fn id(self) -> &'static str {
        self.spec().id
    }

    pub fn key_prefix(self) -> &'static str {
        self.spec().key_prefix
    }

    pub fn of_key(key: &str) -> Option<Self> {
        Self::ALL
            .into_iter()
            .find(|agent| key.starts_with(agent.key_prefix()))
    }

    pub fn running_look(self) -> (&'static str, &'static str) {
        self.spec().running_look
    }

    pub fn keyed_by_session(self) -> bool {
        self.spec().keyed_by_session
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
        assert_eq!(AgentKind::of_key("opencode_ses_ed12"), Some(AgentKind::OpenCode));
        assert_eq!(AgentKind::of_key("build"), None);
        assert_eq!(AgentKind::of_key("claude"), None);
    }

    #[test]
    fn all_lists_every_kind_once() {
        let position = |kind: AgentKind| match kind {
            AgentKind::Claude => 0,
            AgentKind::Codex => 1,
            AgentKind::Gemini => 2,
            AgentKind::Cursor => 3,
            AgentKind::OpenCode => 4,
        };
        let positions: Vec<usize> = AgentKind::ALL.into_iter().map(position).collect();
        assert_eq!(positions, (0..AgentKind::ALL.len()).collect::<Vec<_>>());
    }

    #[test]
    fn ids_and_prefixes_tell_every_agent_apart() {
        for a in AgentKind::ALL {
            for b in AgentKind::ALL.into_iter().filter(|b| *b != a) {
                assert_ne!(a.id(), b.id());
                assert!(!a.key_prefix().starts_with(b.key_prefix()), "{a:?} keys read as {b:?}'s");
            }
            assert_eq!(AgentKind::of_key(&format!("{}x", a.key_prefix())), Some(a));
        }
    }
}
