// The exact preview: renders what Claude Code would show by running the resolved
// script/command against a canonical sample payload, and returns raw stdout
// INCLUDING ANSI escapes so the frontend can paint it like a real terminal line.
use super::*;

/// A throwaway git repo used only so the branch + git-status segments render in
/// the preview (they need a real repo). Created once under the statuslines dir,
/// on branch `main` with a committed file and a dirty edit, so the preview shows
/// `main✳`. Returns its path, or None when git is unavailable.
pub(super) fn ensure_preview_repo(dir: &Path) -> Option<String> {
    let repo = dir.join("my-project");
    if !repo.join(".git").exists() {
        std::fs::create_dir_all(&repo).ok()?;
        let script = format!(
            "cd {} || exit 1\n\
             git init -q . >/dev/null 2>&1 || exit 1\n\
             git symbolic-ref HEAD refs/heads/main >/dev/null 2>&1\n\
             git config user.email lpm@local >/dev/null 2>&1\n\
             git config user.name lpm >/dev/null 2>&1\n\
             git config commit.gpgsign false >/dev/null 2>&1\n\
             printf 'demo\\n' > README.md\n\
             git add README.md >/dev/null 2>&1\n\
             git commit -q -m init >/dev/null 2>&1\n\
             printf 'wip\\n' >> README.md\n",
            sh_quote(&repo.to_string_lossy())
        );
        let _ = run_shell_capture(&script, "");
    }
    repo.join(".git")
        .exists()
        .then(|| repo.to_string_lossy().into_owned())
}

/// The canonical preview payload. When a demo git repo can be prepared its path
/// becomes `.cwd`, so branch + git status render; otherwise a plain path is used.
pub(super) fn preview_payload(dir: &Path) -> String {
    let cwd = ensure_preview_repo(dir).unwrap_or_else(|| "/Users/dev/my-project".to_string());
    let cwd = Value::String(cwd).to_string();
    format!(
        r#"{{"model":{{"display_name":"Opus 4.8"}},"cwd":{cwd},"workspace":{{"current_dir":{cwd}}},"context_window":{{"remaining_percentage":72}},"rate_limits":{{"five_hour":{{"used_percentage":34,"resets_at":"2026-07-20T22:00:00Z"}},"seven_day":{{"used_percentage":62,"resets_at":"2026-07-25T00:00:00Z"}}}},"cost":{{"total_cost_usd":4.2}}}}"#
    )
}

/// Run shell `code` with `payload` on stdin, killing it after ~2s.
/// Returns (exited_success, stdout, stderr). Output is capped.
pub(super) fn run_shell_capture(code: &str, payload: &str) -> (bool, String, String) {
    use std::io::{Read, Write};
    use std::process::Stdio;
    use std::time::{Duration, Instant};

    // The shell Claude Code runs status lines through: sh, or Git Bash on Windows.
    #[cfg(unix)]
    let shell = "sh".to_string();
    #[cfg(windows)]
    let shell = crate::sys::login_shell();
    let child = crate::osproc::command(&shell)
        .arg("-c")
        .arg(code)
        .stdin(Stdio::piped())
        .stdout(Stdio::piped())
        .stderr(Stdio::piped())
        .spawn();
    let Ok(mut child) = child else {
        return (false, String::new(), format!("could not start {shell}"));
    };
    if let Some(mut stdin) = child.stdin.take() {
        let _ = stdin.write_all(payload.as_bytes()); // drops -> EOF for `cat`
    }
    let mut stdout = child.stdout.take();
    let mut stderr = child.stderr.take();

    let start = Instant::now();
    let mut timed_out = false;
    let success = loop {
        match child.try_wait() {
            Ok(Some(status)) => break status.success(),
            Ok(None) => {
                if start.elapsed() > Duration::from_secs(2) {
                    let _ = child.kill();
                    let _ = child.wait();
                    timed_out = true;
                    break false;
                }
                std::thread::sleep(Duration::from_millis(15));
            }
            Err(_) => break false,
        }
    };

    let mut out = String::new();
    if let Some(so) = stdout.take() {
        let _ = so.take(64 * 1024).read_to_string(&mut out);
    }
    let mut err = String::new();
    if let Some(se) = stderr.take() {
        let _ = se.take(8 * 1024).read_to_string(&mut err);
    }
    if timed_out {
        err = "the status line took too long to render".into();
    }
    (success, out, err)
}

/// Resolve a preview selection to the shell code to run, or None when there is
/// nothing to show (e.g. "current" with no prior line).
pub(super) fn resolve_preview_code(
    sel: &Value,
    dir: &Path,
    settings_path: &Path,
) -> Result<Option<String>, String> {
    let kind = sel
        .get("kind")
        .and_then(Value::as_str)
        .ok_or("missing preview kind")?;
    match kind {
        "template" => {
            let id = sel
                .get("id")
                .and_then(Value::as_str)
                .ok_or("missing template id")?;
            let spec = preset_spec(id).ok_or_else(|| format!("unknown template: {id}"))?;
            Ok(Some(build_custom_statusline(&spec)?))
        }
        "custom" => {
            let spec: CustomSpec =
                serde_json::from_value(sel.get("spec").cloned().unwrap_or(Value::Null))
                    .map_err(|e| format!("invalid status line spec: {e}"))?;
            Ok(Some(build_custom_statusline(&spec)?))
        }
        "ai" => Ok(std::fs::read_to_string(dir.join("lpm-ai.sh")).ok()),
        "current" => {
            // The user's own prior line: the snapshot, else the live non-lpm original.
            let target = read_snapshot(dir).unwrap_or_else(|| {
                std::fs::read(settings_path)
                    .ok()
                    .and_then(|d| serde_json::from_slice::<Value>(&d).ok())
                    .map(|s| prior_statusline(&s))
                    .unwrap_or(Value::Null)
            });
            // Only the user's own command counts as "My status line".
            if detect_template_id(&target, dir).is_some() {
                return Ok(None);
            }
            Ok(target
                .get("command")
                .and_then(Value::as_str)
                .map(str::to_string))
        }
        other => Err(format!("unknown preview kind: {other}")),
    }
}

pub(super) fn preview_claude_statusline_at(
    selection: &Value,
    dir: &Path,
    settings_path: &Path,
) -> Result<String, String> {
    match resolve_preview_code(selection, dir, settings_path)? {
        Some(c) if !c.trim().is_empty() => Ok(run_shell_capture(&c, &preview_payload(dir)).1),
        _ => Ok(String::new()),
    }
}

/// Render a status line selection exactly as Claude Code would, returning raw
/// stdout with ANSI escapes intact (empty string when there is nothing to show).
#[tauri::command(async)]
pub fn preview_claude_statusline(selection: Value) -> Result<String, String> {
    preview_claude_statusline_at(&selection, &statuslines_dir(), &claude_settings_path())
}

