// Sub-agents that outlive the turn that spawned them.
//
// A Codex root thread can end its turn while the sub-agents it spawned are still
// running tools and editing files; their own hooks don't speak for the tab (only
// the root's do), and the root isn't woken when they finish. A Claude lead stops
// with its teammates mid-task, and a background sub-agent that waited on its own
// shell picks its work up again long after the turn that spawned it. So the
// root's Stop alone would read "finished" all through their work. Both agents
// report each sub-agent's start and stop, so the tab keeps Running while any is
// live, and the root's Done goes out when the last one stops (donehold.rs).
use crate::agentkind::AgentKind;
use crate::status::{is_live, now_millis, StatusEntry, StatusStore};
use std::collections::HashMap;
use std::sync::{Mutex, OnceLock};

/// How long a sub-agent counts as working without a stop: a stop can be lost
/// with a session that was killed, and a stint runs far shorter than this.
const STINT_CAP_MS: i64 = 60 * 60_000;

#[derive(Default)]
struct Family {
    /// Each live sub-agent, with when it started.
    live: HashMap<String, i64>,
    /// When the last of them stopped.
    quiet_since: Option<i64>,
}

fn families() -> &'static Mutex<HashMap<(String, String), Family>> {
    static FAMILIES: OnceLock<Mutex<HashMap<(String, String), Family>>> = OnceLock::new();
    FAMILIES.get_or_init(Default::default)
}

fn id(project: &str, key: &str) -> (String, String) {
    (project.to_string(), key.to_string())
}

pub fn started(project: &str, key: &str, child: &str) {
    let mut families = families().lock().unwrap();
    let family = families.entry(id(project, key)).or_default();
    family.live.insert(child.to_string(), now_millis());
    family.quiet_since = None;
}

pub fn stopped(project: &str, key: &str, child: &str) {
    retain(project, key, |live| live != child);
}

/// Keep only the sub-agents `alive` says are still alive.
pub fn retain(project: &str, key: &str, alive: impl Fn(&str) -> bool) {
    let mut families = families().lock().unwrap();
    let Some(family) = families.get_mut(&id(project, key)) else {
        return;
    };
    let before = family.live.len();
    family.live.retain(|child, _| alive(child));
    if before > 0 && family.live.is_empty() {
        family.quiet_since = Some(now_millis());
    }
}

/// When the last sub-agent of `key` stopped, if none is working now.
pub fn quiet_since(project: &str, key: &str) -> Option<i64> {
    families()
        .lock()
        .unwrap()
        .get(&id(project, key))
        .filter(|f| f.live.is_empty())
        .and_then(|f| f.quiet_since)
}

pub fn busy(project: &str, key: &str) -> bool {
    let now = now_millis();
    families()
        .lock()
        .unwrap()
        .get(&id(project, key))
        .is_some_and(|f| f.live.values().any(|since| now - since < STINT_CAP_MS))
}

/// The root was interrupted or quit: its sub-agents went with it.
pub fn forget(project: &str, key: &str) {
    families().lock().unwrap().remove(&id(project, key));
}

/// The Running a Claude session shows when one of its sub-agents starts work
/// while the session itself shows none — a background sub-agent picking up
/// again after its own shell finished, a teammate a message woke: the tab
/// reads finished, or nothing, while that work runs. `pane` is the tab the
/// report came from, for a session showing nothing at all.
pub fn resumed_work(store: &StatusStore, project: &str, key: &str, pane: Option<&str>) -> Option<StatusEntry> {
    if AgentKind::of_key(key) != Some(AgentKind::Claude) {
        return None;
    }
    let like = match store.get(project, key) {
        Some(e) if is_live(&e.value) => return None,
        Some(e) => e,
        None => StatusEntry {
            key: key.to_string(),
            pane_id: pane.filter(|p| !p.is_empty())?.to_string(),
            ..Default::default()
        },
    };
    Some(crate::agentturn::running(&like))
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::status::{STATUS_DONE, STATUS_RUNNING};

    fn entry(key: &str, value: &str) -> StatusEntry {
        StatusEntry {
            key: key.into(),
            value: value.into(),
            pane_id: "p-1".into(),
            ..Default::default()
        }
    }

    #[test]
    fn a_family_is_busy_until_its_last_sub_agent_stops() {
        assert!(!busy("c1", "codex_p-1"), "no sub-agents");
        started("c1", "codex_p-1", "a1");
        started("c1", "codex_p-1", "a2");
        stopped("c1", "codex_p-1", "a1");
        assert!(busy("c1", "codex_p-1"));
        assert!(quiet_since("c1", "codex_p-1").is_none());
        stopped("c1", "codex_p-1", "a2");
        assert!(!busy("c1", "codex_p-1"));
        assert!(quiet_since("c1", "codex_p-1").is_some(), "the hand-back starts now");
        stopped("c1", "codex_p-1", "a9");
        started("c1", "codex_p-1", "a3");
        assert!(quiet_since("c1", "codex_p-1").is_none());
    }

    // subagent-s6: Ctrl+C stopped the background agent with no SubagentStop;
    // the next Stop's task list no longer had it.
    #[test]
    fn a_sub_agent_missing_from_the_list_is_gone() {
        started("c6", "claude_code_s1", "abb0");
        started("c6", "claude_code_s1", "acc1");
        retain("c6", "claude_code_s1", |child| child == "acc1");
        assert!(busy("c6", "claude_code_s1"));
        retain("c6", "claude_code_s1", |_| false);
        assert!(!busy("c6", "claude_code_s1"));
    }

    #[test]
    fn an_interrupt_forgets_the_sub_agents() {
        started("c2", "codex_p-1", "a1");
        forget("c2", "codex_p-1");
        assert!(!busy("c2", "codex_p-1"));
    }

    // A lost stop must not hold the session's finish back for good.
    #[test]
    fn a_sub_agent_that_never_stopped_stops_counting() {
        started("c3", "claude_code_s1", "a1");
        families()
            .lock()
            .unwrap()
            .get_mut(&id("c3", "claude_code_s1"))
            .unwrap()
            .live
            .insert("a1".into(), now_millis() - STINT_CAP_MS);
        assert!(!busy("c3", "claude_code_s1"));
    }

    // subagent-s3c: the main turn reported its finish, then the sub-agent it
    // left waiting on a shell worked on for half a minute under that Done.
    #[test]
    fn a_sub_agent_working_on_after_the_finish_shows_as_work() {
        let store = StatusStore::new();
        store.set("c4", entry("claude_code_s1", STATUS_DONE));
        let resumed = resumed_work(&store, "c4", "claude_code_s1", None).expect("work shown");
        assert_eq!((resumed.value.as_str(), resumed.pane_id.as_str()), (STATUS_RUNNING, "p-1"));

        store.set("c4", entry("claude_code_s1", STATUS_RUNNING));
        assert!(resumed_work(&store, "c4", "claude_code_s1", None).is_none(), "already working");
        assert!(resumed_work(&store, "c4", "codex_p-1", Some("p-1")).is_none(), "Claude only");
    }

    #[test]
    fn a_sub_agent_working_in_a_quiet_tab_shows_there() {
        let store = StatusStore::new();
        let resumed = resumed_work(&store, "c5", "claude_code_s1", Some("p-3")).expect("work shown");
        assert_eq!(resumed.pane_id, "p-3");
        assert!(resumed_work(&store, "c5", "claude_code_s2", None).is_none(), "no tab to show it on");
    }
}
