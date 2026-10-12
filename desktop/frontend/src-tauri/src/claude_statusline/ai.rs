// A status line written by an AI CLI from the user's description.
use super::*;

pub(super) const AI_STATUSLINE_PROMPT: &str = r#"Write a POSIX sh script for a Claude Code status line.

Claude Code pipes a single JSON object to the script on stdin with these fields (any may be absent):
- .model.display_name        e.g. "Opus 4.8"
- .cwd                        absolute path of the current directory
- .workspace.current_dir     absolute path of the workspace root
- .context_window.remaining_percentage   0-100 number
- .rate_limits.five_hour.used_percentage  0-100 number
- .rate_limits.five_hour.resets_at        ISO 8601 timestamp
- .rate_limits.seven_day.used_percentage  0-100 number
- .rate_limits.seven_day.resets_at        ISO 8601 timestamp
- .cost.total_cost_usd        number, dollars spent this session

Hard rules:
- POSIX sh only (no bash-isms). Read stdin ONCE into a variable, then parse it (jq is available).
- Print exactly ONE line to stdout, no trailing newline.
- Only use jq, git, and standard POSIX tools.
- ANSI color escapes are allowed; emit them with printf '%b'. Gracefully skip any field that is absent or empty.
- A modern, readable look is encouraged: tasteful emoji glyphs, unicode meter bars (e.g. ▇▁ or ●○), and per-segment accent colors are all welcome — but keep it legible and never require a Nerd Font.
- Output ONLY the script. No markdown fences, no commentary, no explanation."#;

pub(super) fn strip_code_fences(s: &str) -> String {
    let t = s.trim();
    if let Some(rest) = t.strip_prefix("```") {
        // Drop the opening fence's language tag line and the closing fence.
        let rest = rest.splitn(2, '\n').nth(1).unwrap_or("");
        let rest = rest.trim_end();
        let rest = rest.strip_suffix("```").unwrap_or(rest);
        return rest.trim().to_string();
    }
    t.to_string()
}

/// If `code` is just `sh`/`bash <path.sh>` (optionally quoted), the referenced
/// script path — so the AI base is the file's content, not the launcher command.
pub(super) fn single_sh_file_ref(code: &str) -> Option<PathBuf> {
    let tokens: Vec<&str> = code.trim().split_whitespace().collect();
    if tokens.len() != 2 || !is_sh_program(tokens[0]) {
        return None;
    }
    let path = tokens[1].trim_matches(|c| c == '\'' || c == '"');
    if path.ends_with(".sh") {
        Some(PathBuf::from(path))
    } else {
        None
    }
}

/// `sh`/`bash`, or on Windows also Git Bash by path (`.../bin/bash.exe`), the
/// form lpm writes its own status line launchers in there.
pub(super) fn is_sh_program(token: &str) -> bool {
    if matches!(token, "sh" | "bash") {
        return true;
    }
    cfg!(windows)
        && Path::new(token)
            .file_name()
            .and_then(|name| name.to_str())
            .is_some_and(|name| {
                name.eq_ignore_ascii_case("bash.exe") || name.eq_ignore_ascii_case("sh.exe")
            })
}

pub(super) fn extract_base_script(code: &str) -> String {
    if let Some(path) = single_sh_file_ref(code) {
        if let Ok(content) = std::fs::read_to_string(&path) {
            return content;
        }
    }
    code.to_string()
}

/// The current status line to use as the base for an AI edit, resolved from the
/// same selection the preview uses. None when there is nothing to build on.
pub(super) fn ai_base_for_selection_at(selection: &Value, dir: &Path, settings_path: &Path) -> Option<String> {
    match resolve_preview_code(selection, dir, settings_path)
        .ok()
        .flatten()
    {
        Some(code) if !code.trim().is_empty() => Some(extract_base_script(&code)),
        _ => None,
    }
}

pub fn ai_base_for_selection(selection: &Value) -> Option<String> {
    ai_base_for_selection_at(selection, &statuslines_dir(), &claude_settings_path())
}

/// The prompt for AI generation. When there is a current line (`base`), the model
/// is asked to modify it; otherwise it writes one from scratch.
pub fn ai_statusline_prompt(description: &str, base: Option<&str>) -> String {
    let mut p = String::from(AI_STATUSLINE_PROMPT);
    if let Some(cur) = base {
        if !cur.trim().is_empty() {
            p.push_str("\n\nThis is the current status line script. Modify it to satisfy the new instruction, keeping everything else the same:\n\n");
            p.push_str(cur.trim());
        }
    }
    p.push_str("\n\nInstruction:\n");
    p.push_str(description.trim());
    p
}

pub(super) fn finalize_ai_statusline_at(
    raw: &str,
    description: &str,
    base_selection: &Value,
    settings_path: &Path,
    dir: &Path,
) -> Result<String, String> {
    let script = strip_code_fences(raw);
    if script.trim().is_empty() {
        return Err("The model returned an empty status line. Try describing it again.".into());
    }
    let (ok, out, err) = run_shell_capture(&script, &preview_payload(dir));
    if !ok || out.trim().is_empty() {
        let detail = if !err.trim().is_empty() {
            err.trim().to_string()
        } else {
            "it produced no output".to_string()
        };
        return Err(format!("The generated status line didn't run: {detail}"));
    }
    write_template_script(dir, "ai", &script)?;
    let meta = json!({ "description": description.trim(), "baseSelection": base_selection });
    let bytes = serde_json::to_vec_pretty(&meta).map_err(|e| e.to_string())?;
    crate::fsatomic::write(
        &dir.join("ai.json"),
        &bytes,
        crate::fsatomic::Mode::Preserve(0o644),
    )
    .map_err(|e| format!("cannot write status line description: {e}"))?;
    apply_statusline_script(settings_path, dir, "ai", &script)?;
    Ok(script)
}

/// Clean, validate, save, and apply an AI-generated status line script. Returns
/// the cleaned script on success; a useful error (including stderr) on failure.
pub fn finalize_ai_statusline(
    raw: &str,
    description: &str,
    base_selection: &Value,
) -> Result<String, String> {
    finalize_ai_statusline_at(
        raw,
        description,
        base_selection,
        &claude_settings_path(),
        &statuslines_dir(),
    )
}
