// Live service logs for the phone: `serviceLogsWatch` / `serviceLogsUnwatch`.
//
// The desktop's log pane captures every service pane twice a second and repaints
// when a capture changed (log_streaming). A phone watching one service gets the
// same loop over the wire: a thread per watched pane, capturing on that cadence
// and pushing a `serviceLogs` frame only when the text changed — so a quiet
// service sends nothing and a busy one updates as the desktop's does, instead of
// the phone pulling a full capture every few seconds.
use serde_json::json;
use std::sync::atomic::{AtomicBool, Ordering};
use std::sync::mpsc::{SyncSender, TrySendError};
use std::sync::Arc;
use std::time::Duration;

pub const WATCH_FEATURE: &str = "serviceLogsWatch";

const POLL: Duration = Duration::from_millis(500);
/// The same window a one-shot `serviceLogs` request is capped at.
const LINES: i64 = 200;

/// The connection's watch-map key for one service pane, apart from the project
/// names its git watches are keyed by.
pub fn key(project: &str, pane_index: i64) -> String {
    format!("serviceLogs\u{0}{project}\u{0}{pane_index}")
}

/// Push `pane_index` of `project` to `out` now and whenever its capture changes,
/// until the returned flag is set or the connection's queue is gone.
pub fn start(project: String, pane_index: i64, out: SyncSender<String>) -> Arc<AtomicBool> {
    let stop = Arc::new(AtomicBool::new(false));
    let stopped = stop.clone();
    std::thread::spawn(move || {
        let session = crate::config::spawn_info(&project).map(|info| info.session);
        let mut sent: Option<Result<String, String>> = None;
        while !stopped.load(Ordering::SeqCst) {
            let capture = match (&session, usize::try_from(pane_index)) {
                (Ok(session), Ok(index)) => {
                    crate::log_streaming::capture_service_pane(session, index, LINES)
                }
                (Err(e), _) => Err(e.clone()),
                (_, Err(_)) => Err("invalid pane index".to_string()),
            };
            if sent.as_ref() != Some(&capture) {
                let frame = match &capture {
                    Ok(text) => json!({ "t": "serviceLogs", "project": project,
                        "paneIndex": pane_index, "ok": true, "text": text }),
                    Err(e) => json!({ "t": "serviceLogs", "project": project,
                        "paneIndex": pane_index, "ok": false, "error": e }),
                };
                match out.try_send(frame.to_string()) {
                    Ok(()) => sent = Some(capture),
                    // The phone is behind; this capture goes out on the next tick.
                    Err(TrySendError::Full(_)) => {}
                    Err(TrySendError::Disconnected(_)) => return,
                }
            }
            std::thread::sleep(POLL);
        }
    });
    stop
}
