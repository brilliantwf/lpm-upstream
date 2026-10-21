// Questions a sub-agent asks on its session's behalf.
//
// A sub-agent's hooks run under its session's id, so its question lands on the
// session's own status key, while its tool calls — gated out so they can't speak
// for the session (hooks/claude.rs) — say nothing there. Taken as a plain
// Waiting, the next report of the main thread (a tool call of its own, the turn
// pausing on that sub-agent, a teammate lead stopping) replaced it: the tab read
// "working" or "finished" with the dialog still open, the 6 s reminder rang
// "needs you" a second time, and once the main thread went quiet the sweep took
// the badge away altogether. So a Waiting remembers who asked — `main`, or the
// sub-agent's id — and the main thread's reports end only its own question. A
// sub-agent's question ends with that sub-agent's next report (`--answered`),
// its stop, the user's answer (agentturn.rs) or the session's end.
use crate::status::{StatusEntry, MAIN_ASKER, STATUS_ERROR, STATUS_WAITING};

#[derive(Debug, PartialEq)]
pub enum Route {
    /// Store and announce the report as usual.
    Publish,
    /// Keep the Waiting on show, now waiting for these.
    Amend(Vec<String>),
    /// The report changes nothing.
    Ignore,
}

/// Where `incoming` goes, given what `existing` shows. `answered` is the id of
/// the sub-agent that sent it, for a sub-agent's report.
pub fn route(
    existing: Option<&StatusEntry>,
    incoming: &StatusEntry,
    answered: Option<&str>,
) -> Route {
    let open = existing.filter(|e| e.value == STATUS_WAITING && e.pane_id == incoming.pane_id);
    if let Some(asker) = answered {
        let Some(open) = open.filter(|e| e.askers.iter().any(|a| a == asker)) else {
            return Route::Ignore;
        };
        let left = without(&open.askers, asker);
        return if left.is_empty() {
            Route::Publish
        } else {
            Route::Amend(left)
        };
    }
    if incoming.value == STATUS_WAITING {
        return match open {
            Some(open) => Route::Amend(joined(&open.askers, &incoming.askers)),
            None => Route::Publish,
        };
    }
    match open {
        Some(open) if open.sub_agent_asking() && incoming.value != STATUS_ERROR => {
            Route::Amend(without(&open.askers, MAIN_ASKER))
        }
        _ => Route::Publish,
    }
}

fn without(askers: &[String], gone: &str) -> Vec<String> {
    askers.iter().filter(|a| *a != gone).cloned().collect()
}

fn joined(askers: &[String], more: &[String]) -> Vec<String> {
    let mut all = askers.to_vec();
    all.extend(more.iter().filter(|a| !askers.contains(a)).cloned());
    all
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::status::{STATUS_DONE, STATUS_RUNNING};

    fn entry(value: &str, askers: &[&str]) -> StatusEntry {
        StatusEntry {
            key: "claude_code_s1".into(),
            value: value.into(),
            askers: askers.iter().map(|a| a.to_string()).collect(),
            ..Default::default()
        }
    }

    fn amend(askers: &[&str]) -> Route {
        Route::Amend(askers.iter().map(|a| a.to_string()).collect())
    }

    // wl-w9c: the main thread's own tool calls ran on while a sub-agent's dialog
    // stayed open, and each one turned the badge back to working.
    #[test]
    fn the_main_thread_working_on_leaves_a_sub_agents_question_open() {
        let asked = entry(STATUS_WAITING, &["a7"]);
        assert_eq!(
            route(Some(&asked), &entry(STATUS_RUNNING, &[]), None),
            amend(&["a7"])
        );
        assert_eq!(
            route(Some(&asked), &entry(STATUS_DONE, &[]), None),
            amend(&["a7"])
        );
    }

    #[test]
    fn the_main_thread_answers_its_own_question() {
        let asked = entry(STATUS_WAITING, &[MAIN_ASKER]);
        assert_eq!(
            route(Some(&asked), &entry(STATUS_RUNNING, &[]), None),
            Route::Publish
        );
        let both = entry(STATUS_WAITING, &[MAIN_ASKER, "a7"]);
        assert_eq!(
            route(Some(&both), &entry(STATUS_RUNNING, &[]), None),
            amend(&["a7"])
        );
    }

    // A Notification can't say who it is for: such a Waiting is the main
    // thread's to end, as every Waiting used to be.
    #[test]
    fn a_question_nobody_claimed_ends_with_any_report() {
        let asked = entry(STATUS_WAITING, &[]);
        assert_eq!(
            route(Some(&asked), &entry(STATUS_RUNNING, &[]), None),
            Route::Publish
        );
    }

    #[test]
    fn a_sub_agent_carrying_on_ends_only_its_own_question() {
        let asked = entry(STATUS_WAITING, &["a7", "a9"]);
        assert_eq!(
            route(Some(&asked), &entry(STATUS_RUNNING, &[]), Some("a7")),
            amend(&["a9"])
        );
        let one = entry(STATUS_WAITING, &["a9"]);
        assert_eq!(
            route(Some(&one), &entry(STATUS_RUNNING, &[]), Some("a9")),
            Route::Publish
        );
        assert_eq!(
            route(Some(&one), &entry(STATUS_RUNNING, &[]), Some("a7")),
            Route::Ignore
        );
    }

    // A sub-agent's tool calls are gated out of the session's state; only one
    // that answers an open question of its own says anything.
    #[test]
    fn a_sub_agents_report_with_nothing_open_changes_nothing() {
        let working = entry(STATUS_RUNNING, &[]);
        assert_eq!(
            route(Some(&working), &entry(STATUS_RUNNING, &[]), Some("a7")),
            Route::Ignore
        );
        assert_eq!(
            route(None, &entry(STATUS_RUNNING, &[]), Some("a7")),
            Route::Ignore
        );
    }

    // The 6 s permission_prompt reminder carries no agent id: it must neither
    // ring again nor take the question from the sub-agent that asked it.
    #[test]
    fn a_second_question_joins_the_open_one() {
        let asked = entry(STATUS_WAITING, &["a7"]);
        assert_eq!(
            route(Some(&asked), &entry(STATUS_WAITING, &[]), None),
            amend(&["a7"])
        );
        assert_eq!(
            route(Some(&asked), &entry(STATUS_WAITING, &[MAIN_ASKER]), None),
            amend(&["a7", MAIN_ASKER])
        );
        assert_eq!(
            route(None, &entry(STATUS_WAITING, &["a7"]), None),
            Route::Publish
        );
    }

    #[test]
    fn a_failed_turn_is_shown_whoever_was_asking() {
        let asked = entry(STATUS_WAITING, &["a7"]);
        assert_eq!(
            route(Some(&asked), &entry(STATUS_ERROR, &[]), None),
            Route::Publish
        );
    }
}
