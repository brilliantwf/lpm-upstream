//! `lpm logs [service] [--project <name>] [-n <lines>] [--pane <idx>] [--json]`
//! — dump a running service pane's recent scrollback. A service name maps to the
//! pane the app labelled with it; panes are created in run order (profile, then
//! `dependsOn`), which needn't match declaration order. Unlabelled panes (an
//! older daemon) fall back to the declaration-order index. `--pane <idx>`
//! addresses a pane directly.

use crate::config::{self, Ctx};
use crate::error::{resolve_or_infer, RunError};
use crate::sessions;
use serde_json::json;

const MAX_LINES: i64 = 10_000;

/// Pick a service by name (exact, else unambiguous prefix) or, when omitted, the
/// sole declared service. Returns the service's index in declaration order.
/// Errors are usage errors (exit 2); the message lists the declared services.
fn select_service(names: &[String], service: Option<&str>) -> Result<usize, String> {
    let joined = || {
        if names.is_empty() {
            "(none)".to_string()
        } else {
            names.join(", ")
        }
    };
    match service {
        Some(q) => {
            if let Some(i) = names.iter().position(|n| n == q) {
                return Ok(i);
            }
            let cands: Vec<usize> = names
                .iter()
                .enumerate()
                .filter(|(_, n)| n.starts_with(q))
                .map(|(i, _)| i)
                .collect();
            match cands.len() {
                1 => Ok(cands[0]),
                0 => Err(format!(
                    "no service matches {q:?}\ndeclared services: {}",
                    joined()
                )),
                _ => Err(format!(
                    "{q:?} is ambiguous — matches: {}",
                    cands
                        .iter()
                        .map(|i| names[*i].as_str())
                        .collect::<Vec<_>>()
                        .join(", ")
                )),
            }
        }
        None => match names.len() {
            1 => Ok(0),
            0 => Err("project declares no services".to_string()),
            _ => Err(format!("pick a service: {}", joined())),
        },
    }
}

/// The pane running service `name`: the one labelled with it, `None` when the
/// panes are labelled and none is (the service isn't running). Unlabelled panes
/// can only be matched by `ordinal`, the service's declaration-order index.
fn pane_index(labels: &[&str], name: &str, ordinal: usize) -> Option<usize> {
    if labels.is_empty() || labels.iter().any(|l| l.is_empty()) {
        return Some(ordinal);
    }
    labels.iter().position(|l| *l == name)
}

/// Last `n` lines of `text`. `capture-pane -S -<n>` returns the visible pane
/// plus `n` scrollback lines, so the raw capture can exceed the requested count.
fn tail_lines(text: &str, n: i64) -> String {
    let lines: Vec<&str> = text.lines().collect();
    let skip = lines.len().saturating_sub(n.max(0) as usize);
    lines[skip..].join("\n")
}

pub fn run(
    ctx: &Ctx,
    service: Option<&str>,
    project: Option<&str>,
    lines: i64,
    pane: Option<usize>,
    as_json: bool,
) -> Result<(), RunError> {
    let file_name = resolve_or_infer(ctx, project)?;
    let p = config::resolve_project(ctx, &file_name).map_err(RunError::Internal)?;

    if !sessions::session_exists(&p.session) {
        return Err(RunError::NotFound(format!(
            "project {file_name:?} is not running"
        )));
    }
    let panes = sessions::list_panes(&p.session);

    let (idx, service_name) = match pane {
        Some(i) => (i, None),
        None => {
            let names: Vec<String> = p.services.iter().map(|s| s.name.clone()).collect();
            let i = select_service(&names, service).map_err(RunError::NotFound)?;
            let name = names[i].clone();
            let labels: Vec<&str> = panes.iter().map(|p| p.service.as_str()).collect();
            let idx = pane_index(&labels, &name, i).ok_or_else(|| {
                RunError::NotFound(format!("service {name:?} is not running in {file_name:?}"))
            })?;
            if labels.iter().any(|l| l.is_empty()) && panes.len() != names.len() {
                eprintln!(
                    "lpm: warning: session has {} pane(s) for {} declared service(s) — \
the service-to-pane mapping may be off",
                    panes.len(),
                    names.len()
                );
            }
            (idx, Some(name))
        }
    };

    let Some(target) = panes.get(idx) else {
        return Err(RunError::Internal(format!(
            "pane index {idx} is out of range (session has {} pane(s))",
            panes.len()
        )));
    };

    let lines = lines.clamp(1, MAX_LINES);
    let text = sessions::capture_pane(&target.id, lines).map_err(RunError::Internal)?;
    let text = tail_lines(&text, lines);

    if as_json {
        crate::util::print_json(&json!({
            "project": file_name,
            "service": service_name,
            "paneId": target.id,
            "paneIndex": idx,
            "lines": lines,
            "text": text,
        }));
    } else {
        println!("{text}");
    }
    Ok(())
}

#[cfg(test)]
mod tests {
    use super::*;

    fn svc(names: &[&str]) -> Vec<String> {
        names.iter().map(|s| s.to_string()).collect()
    }

    #[test]
    fn selects_exact_then_prefix() {
        let names = svc(&["web", "worker", "db"]);
        assert_eq!(select_service(&names, Some("web")).unwrap(), 0);
        assert_eq!(select_service(&names, Some("db")).unwrap(), 2);
        // "wo" is an unambiguous prefix of "worker".
        assert_eq!(select_service(&names, Some("wo")).unwrap(), 1);
    }

    #[test]
    fn ambiguous_prefix_is_error() {
        let names = svc(&["web", "web2"]);
        assert!(select_service(&names, Some("web")).is_ok()); // exact wins
        assert!(select_service(&names, Some("we")).is_err());
    }

    #[test]
    fn omitted_service_needs_exactly_one() {
        assert_eq!(select_service(&svc(&["only"]), None).unwrap(), 0);
        assert!(select_service(&svc(&["a", "b"]), None).is_err());
        assert!(select_service(&svc(&[]), None).is_err());
    }

    #[test]
    fn tail_keeps_exactly_the_last_n_lines() {
        assert_eq!(tail_lines("a\nb\nc\nd", 2), "c\nd");
        assert_eq!(tail_lines("a\nb", 5), "a\nb");
        assert_eq!(tail_lines("", 3), "");
    }

    #[test]
    fn labelled_panes_resolve_by_service_not_declaration_order() {
        // Started as profile [web, api, worker]; declared (sorted) as api, web, worker.
        let labels = ["web", "api", "worker"];
        assert_eq!(pane_index(&labels, "web", 1), Some(0));
        assert_eq!(pane_index(&labels, "api", 0), Some(1));
        assert_eq!(pane_index(&labels, "worker", 2), Some(2));
    }

    #[test]
    fn labelled_subset_reports_a_stopped_service() {
        // Profile `frontend: [web]` started only web.
        assert_eq!(pane_index(&["web"], "web", 1), Some(0));
        assert_eq!(pane_index(&["web"], "api", 0), None);
    }

    #[test]
    fn unlabelled_panes_fall_back_to_declaration_order() {
        assert_eq!(pane_index(&["", ""], "web", 1), Some(1));
        assert_eq!(pane_index(&[], "web", 1), Some(1));
    }

    #[test]
    fn index_can_exceed_a_partial_pane_set() {
        // A profile that started a subset yields fewer panes than services; the
        // selected index still reflects declaration order, and the caller's
        // range guard (panes.get) is what rejects it.
        let names = svc(&["a", "b", "c"]);
        let idx = select_service(&names, Some("c")).unwrap();
        assert_eq!(idx, 2);
        let panes: [&str; 2] = ["%0", "%1"];
        assert!(panes.get(idx).is_none());
    }
}
