// Claude Code's status line: the usage-limit forwarder lpm chains in front of
// the user's own line, and the built-in and custom lines Settings applies.
use crate::hookform::HookForm;
use crate::hooks::{account_settings_copies, claude_settings_path, profile_settings};
use base64::Engine;
use serde::Serialize;
use serde_json::{json, Value};
use std::path::{Path, PathBuf};

mod ai;
mod preview;
mod script;

pub use ai::*;
pub use preview::*;
pub use script::*;

const STATUSLINE_MARKER: &str = "# lpm-statusline:";

// ---- Claude usage-limit statusline forwarder --------------------------------
//
// Consent-gated: enabling "Claude usage limits" installs a `statusLine` command
// into ~/.claude/settings.json that reads the statusline JSON on stdin, forwards
// it (base64) into the status socket as `agent_limits <account> --payload-b64=…`,
// and — if the user already had a statusline — chains their original command so
// their display is unchanged. The forwarder derives the account from the live
// CLAUDE_CONFIG_DIR, so a single install covers every account (per-account
// settings.json symlink to this one file). Disable restores the exact prior
// statusLine, which is preserved base64-encoded in the trailing marker.

fn claude_config_dir_env() -> &'static str {
    crate::config::CLAUDE_CONFIG_DIR_ENV
}

/// Build the wrapper `command` string. `original` is the user's prior statusLine
/// object (or Null when there was none); it is both chained inline (via its
/// `command`) and embedded base64 in the trailing marker for exact restore.
fn statusline_command(original: &Value) -> String {
    statusline_command_in(original, &crate::hookform::local())
}

/// In the CLI form the forwarder derives the account itself, from either path
/// separator, so the wrapper only captures stdin and hands it over.
fn statusline_command_in(original: &Value, form: &HookForm) -> String {
    let original_cmd = original.get("command").and_then(Value::as_str);
    let embedded = base64::engine::general_purpose::STANDARD
        .encode(serde_json::to_vec(original).unwrap_or_default());
    let mut s = match form {
        HookForm::Shell => statusline_forward_sh(),
        HookForm::Cli(cli) => format!("i=$(cat); {}", crate::hookform::statusline_forward(cli)),
    };
    if let Some(orig) = original_cmd {
        s.push(' ');
        s.push_str("printf %s \"$i\" | ( ");
        s.push_str(orig);
        s.push_str(" )");
    }
    s.push(' ');
    s.push_str(STATUSLINE_MARKER);
    s.push_str(&embedded);
    s
}

fn statusline_forward_sh() -> String {
    let env = claude_config_dir_env();
    let mut s = format!(
        "acct=default; case \"${{{env}:-}}\" in */claude-accounts/*) acct=\"${{{env}##*/}}\";; esac; "
    );
    s.push_str("i=$(cat); ");
    let deliver = crate::sockdeliver::delivery_group();
    s.push_str(&format!(
        "printf %s \"$i\" | base64 | tr -d '\\n' | {{ IFS= read -r b; m=\"agent_limits ${{acct:-default}} --payload-b64=$b\"; [ -n \"$LPM_SOCKET_PATH\" ] && [ -S \"$LPM_SOCKET_PATH\" ] && {deliver}; }} >/dev/null 2>&1 &"
    ));
    s
}

/// If `command` is an lpm-installed wrapper, return the embedded original
/// statusLine value (an object, or Null when the user had no prior statusLine);
/// None when the command is not ours.
fn unwrap_statusline(command: &str) -> Option<Value> {
    let idx = command.rfind(STATUSLINE_MARKER)?;
    let b64 = command[idx + STATUSLINE_MARKER.len()..].trim();
    let bytes = base64::engine::general_purpose::STANDARD.decode(b64).ok()?;
    serde_json::from_slice(&bytes).ok()
}

/// The user's true prior statusLine, unwrapping our own wrapper if present so a
/// reinstall never nests. Null when there was no usable prior statusLine.
fn prior_statusline(settings: &Value) -> Value {
    let prior = settings.get("statusLine");
    let cmd = prior.and_then(|v| v.get("command")).and_then(Value::as_str);
    match cmd {
        Some(c) => match unwrap_statusline(c) {
            Some(embedded) => embedded,
            None => prior.cloned().unwrap_or(Value::Null),
        },
        None => Value::Null,
    }
}

/// Merge the statusLine forwarder into settings bytes, returning Some(new bytes)
/// on change and None when unchanged or the input is not a JSON object. Pure so
/// install/restore share one tested transform.
fn install_statusline(data: &[u8]) -> Option<Vec<u8>> {
    let before = serde_json::from_slice::<Value>(data).ok()?;
    let mut settings = before.clone();
    settings.as_object()?; // must be an object
    let original = prior_statusline(&settings);
    let command = statusline_command(&original);

    let mut slo = original.as_object().cloned().unwrap_or_default();
    slo.insert("type".into(), json!("command"));
    slo.insert("command".into(), json!(command));
    // Keep meters fresh while idle; never slow down a user's faster interval.
    let refresh = slo
        .get("refreshInterval")
        .and_then(Value::as_i64)
        .filter(|n| *n > 0)
        .unwrap_or(30)
        .min(30);
    slo.insert("refreshInterval".into(), json!(refresh));

    settings
        .as_object_mut()
        .unwrap()
        .insert("statusLine".into(), Value::Object(slo));
    if settings == before {
        return None;
    }
    serde_json::to_string_pretty(&settings)
        .ok()
        .map(String::into_bytes)
}

/// Restore the pre-install statusLine (or remove it if there was none), only
/// touching an lpm-installed wrapper. None when unchanged or not ours.
fn remove_statusline(data: &[u8]) -> Option<Vec<u8>> {
    let before = serde_json::from_slice::<Value>(data).ok()?;
    let cmd = before
        .get("statusLine")
        .and_then(|v| v.get("command"))
        .and_then(Value::as_str)?;
    let embedded = unwrap_statusline(cmd)?; // not ours -> leave untouched
    let mut settings = before.clone();
    let obj = settings.as_object_mut()?;
    match embedded {
        Value::Null => {
            obj.remove("statusLine");
        }
        other => {
            obj.insert("statusLine".into(), other);
        }
    }
    if settings == before {
        return None;
    }
    serde_json::to_string_pretty(&settings)
        .ok()
        .map(String::into_bytes)
}

fn install_claude_statusline_at(path: &Path) -> Result<(), String> {
    // On explicit opt-in, create a minimal settings.json when absent (unlike the
    // status hooks, which never create the file) so the meter can turn on.
    let data = std::fs::read(path).unwrap_or_else(|_| b"{}".to_vec());
    if let Some(out) = install_statusline(&data) {
        if let Some(parent) = path.parent() {
            std::fs::create_dir_all(parent)
                .map_err(|e| format!("cannot create {}: {e}", parent.display()))?;
        }
        crate::fsatomic::write(path, &out, crate::fsatomic::Mode::Preserve(0o644))
            .map_err(|e| format!("cannot write Claude settings: {e}"))?;
    }
    Ok(())
}

fn remove_claude_statusline_at(path: &Path) -> Result<(), String> {
    let Ok(data) = std::fs::read(path) else {
        return Ok(()); // no file -> nothing to restore
    };
    if let Some(out) = remove_statusline(&data) {
        crate::fsatomic::write(path, &out, crate::fsatomic::Mode::Preserve(0o644))
            .map_err(|e| format!("cannot write Claude settings: {e}"))?;
    }
    Ok(())
}

/// Install or uninstall the Claude usage-limit forwarder. The frontend toggle
/// owns the persisted `claudeLimitsEnabled` flag; this only performs the
/// filesystem side effect, then tells paired phones about the new state — they
/// have no other way to learn Claude usage was switched off here.
#[tauri::command(async)]
pub fn apply_claude_limits(app: tauri::AppHandle, enabled: bool) -> Result<(), String> {
    let path = claude_settings_path();
    if enabled {
        install_claude_statusline_at(&path)?;
    } else {
        remove_claude_statusline_at(&path)?;
    }
    for copy in account_settings_copies() {
        let _ = if enabled {
            install_claude_statusline_at(&copy)
        } else {
            remove_claude_statusline_at(&copy)
        };
    }
    crate::remote::set_claude_limits_enabled(&app, enabled);
    Ok(())
}

/// Startup re-apply: silently reinstall the forwarder when the user previously
/// enabled it (mirrors the skill/CLI refresh — startup only refreshes opt-ins).
pub fn reapply_claude_limits_if_enabled() {
    let enabled = crate::config::load_settings()
        .get("claudeLimitsEnabled")
        .and_then(Value::as_bool)
        .unwrap_or(false);
    if enabled {
        let _ = install_claude_statusline_at(&claude_settings_path());
        for copy in account_settings_copies() {
            let _ = install_claude_statusline_at(&copy);
        }
    }
}

/// App uninstall: drop the usage-limit forwarder first — it chains whatever sat
/// underneath, possibly our template — then restore the user's own line.
pub(crate) fn restore_for_uninstall() -> Result<(), String> {
    let settings = claude_settings_path();
    let forwarder = remove_claude_statusline_at(&settings);
    let statusline = restore_statusline_at(&settings, &statuslines_dir());
    for path in account_settings_copies().into_iter().chain(profile_settings()) {
        let _ = remove_claude_statusline_at(&path);
    }
    forwarder.and(statusline)
}

/// The forwarder alone, leaving the user's chosen line.
#[cfg(windows)]
pub(crate) fn remove_forwarders() {
    let settings = std::iter::once(claude_settings_path())
        .chain(account_settings_copies())
        .chain(profile_settings());
    for path in settings {
        let _ = remove_claude_statusline_at(&path);
    }
}

// ---- Claude status line templates -------------------------------------------
//
// Built-in status line looks the user can pick in Settings. Applying one writes
// its shell script under ~/.lpm/statuslines/ and points the *underlying*
// statusLine at it — composing with the usage-limit forwarder above: if the live
// statusLine is our forwarder wrapper, the template becomes the wrapped original
// and the forwarder is re-chained on top; otherwise the template object is
// written directly. "My status line" restores the snapshot taken the first time
// a template replaced a non-lpm original.

fn statuslines_dir() -> PathBuf {
    crate::config::lpm_dir().join("statuslines")
}

/// Single-quote a path for embedding in a `sh <path>` command.
fn sh_quote(s: &str) -> String {
    format!("'{}'", s.replace('\'', "'\\''"))
}

/// The id of the built-in template a statusLine object points at, if any —
/// matched against the concrete script paths under `dir`.
fn detect_template_id(statusline: &Value, dir: &Path) -> Option<String> {
    let cmd = statusline.get("command").and_then(Value::as_str)?;
    for id in ["minimal", "context", "meters", "vibrant", "custom", "ai"] {
        let path = dir.join(format!("lpm-{id}.sh"));
        if cmd.contains(&crate::hookform::script_path(&path)) {
            return Some(id.to_string());
        }
    }
    None
}

fn write_template_script(dir: &Path, id: &str, source: &str) -> Result<PathBuf, String> {
    std::fs::create_dir_all(dir).map_err(|e| format!("cannot create {}: {e}", dir.display()))?;
    let path = dir.join(format!("lpm-{id}.sh"));
    crate::fsatomic::write(
        &path,
        source.as_bytes(),
        crate::fsatomic::Mode::Exact(0o755),
    )
    .map_err(|e| format!("cannot write status line script: {e}"))?;
    Ok(path)
}

fn read_custom_spec(dir: &Path) -> CustomSpec {
    std::fs::read(dir.join("custom.json"))
        .ok()
        .and_then(|d| serde_json::from_slice::<CustomSpec>(&d).ok())
        .unwrap_or_else(default_custom_spec)
}

fn read_snapshot(dir: &Path) -> Option<Value> {
    let data = std::fs::read(dir.join("original.json")).ok()?;
    serde_json::from_slice(&data).ok()
}

/// Replace the underlying statusLine with `new_original` (an object, or Null to
/// remove it), preserving the usage-limit forwarder wrapper when present by
/// re-chaining it on top. Pure: bytes in, Some(bytes) on change else None.
fn set_original_statusline(data: &[u8], new_original: &Value) -> Option<Vec<u8>> {
    let before = serde_json::from_slice::<Value>(data).ok()?;
    let mut settings = before.clone();
    settings.as_object()?;
    let wrapped = settings
        .get("statusLine")
        .and_then(|v| v.get("command"))
        .and_then(Value::as_str)
        .map(|c| unwrap_statusline(c).is_some())
        .unwrap_or(false);
    {
        let obj = settings.as_object_mut().unwrap();
        match new_original {
            Value::Null => {
                obj.remove("statusLine");
            }
            other => {
                obj.insert("statusLine".into(), other.clone());
            }
        }
    }
    let out = if wrapped {
        let bytes = serde_json::to_vec(&settings).ok()?;
        match install_statusline(&bytes) {
            Some(rewrapped) => serde_json::from_slice::<Value>(&rewrapped).ok()?,
            None => settings,
        }
    } else {
        settings
    };
    if out == before {
        return None;
    }
    serde_json::to_string_pretty(&out)
        .ok()
        .map(String::into_bytes)
}

/// Save the current non-lpm original as the "My status line" snapshot. Taken the
/// first time a template replaces a non-lpm original; refreshed only when the
/// live original is a different non-null non-lpm statusline (user changed it by
/// hand); never taken when the original is already one of our templates.
fn maybe_snapshot(original: &Value, dir: &Path) -> Result<(), String> {
    if detect_template_id(original, dir).is_some() {
        return Ok(());
    }
    let should_write = match read_snapshot(dir) {
        None => true,
        Some(prev) => !original.is_null() && *original != prev,
    };
    if should_write {
        std::fs::create_dir_all(dir)
            .map_err(|e| format!("cannot create {}: {e}", dir.display()))?;
        let bytes = serde_json::to_vec_pretty(original).map_err(|e| e.to_string())?;
        crate::fsatomic::write(
            &dir.join("original.json"),
            &bytes,
            crate::fsatomic::Mode::Preserve(0o644),
        )
        .map_err(|e| format!("cannot write status line snapshot: {e}"))?;
    }
    Ok(())
}

/// Write a generated status line script for `id` and point the underlying
/// statusLine at it, snapshotting the prior non-lpm line and preserving the
/// usage-limit forwarder wrapper if present.
fn apply_statusline_script(
    settings_path: &Path,
    dir: &Path,
    id: &str,
    source: &str,
) -> Result<(), String> {
    // Explicit opt-in creates a minimal settings.json when absent, like the
    // usage-limit install does.
    let data = std::fs::read(settings_path).unwrap_or_else(|_| b"{}".to_vec());
    let settings = serde_json::from_slice::<Value>(&data)
        .map_err(|e| format!("invalid JSON in Claude settings: {e}"))?;
    maybe_snapshot(&prior_statusline(&settings), dir)?;

    let script = write_template_script(dir, id, source)?;
    let command = format!(
        "{} {}",
        crate::hookform::statusline_shell(),
        sh_quote(&crate::hookform::script_path(&script))
    );
    let template = json!({ "type": "command", "command": command });

    if let Some(out) = set_original_statusline(&data, &template) {
        if let Some(parent) = settings_path.parent() {
            std::fs::create_dir_all(parent)
                .map_err(|e| format!("cannot create {}: {e}", parent.display()))?;
        }
        crate::fsatomic::write(settings_path, &out, crate::fsatomic::Mode::Preserve(0o644))
            .map_err(|e| format!("cannot write Claude settings: {e}"))?;
    }
    Ok(())
}

fn apply_template_at(settings_path: &Path, dir: &Path, id: &str) -> Result<(), String> {
    let spec = preset_spec(id).ok_or_else(|| format!("unknown status line template: {id}"))?;
    let source = build_custom_statusline(&spec)?;
    apply_statusline_script(settings_path, dir, id, &source)
}

/// Apply the user's Custom status line, persisting the spec to custom.json so it
/// survives switching to a preset and back.
fn apply_custom_at(settings_path: &Path, dir: &Path, spec: &CustomSpec) -> Result<(), String> {
    let source = build_custom_statusline(spec)?; // validates before any write
    std::fs::create_dir_all(dir).map_err(|e| format!("cannot create {}: {e}", dir.display()))?;
    let bytes = serde_json::to_vec_pretty(spec).map_err(|e| e.to_string())?;
    crate::fsatomic::write(
        &dir.join("custom.json"),
        &bytes,
        crate::fsatomic::Mode::Preserve(0o644),
    )
    .map_err(|e| format!("cannot write status line spec: {e}"))?;
    apply_statusline_script(settings_path, dir, "custom", &source)
}

fn restore_statusline_at(settings_path: &Path, dir: &Path) -> Result<(), String> {
    let Ok(data) = std::fs::read(settings_path) else {
        return Ok(()); // no settings file -> nothing to restore
    };
    let settings = serde_json::from_slice::<Value>(&data)
        .map_err(|e| format!("invalid JSON in Claude settings: {e}"))?;
    let target = match read_snapshot(dir) {
        Some(snap) => snap,
        None => {
            // No snapshot: clear only our own template; leave a real user line alone.
            if detect_template_id(&prior_statusline(&settings), dir).is_some() {
                Value::Null
            } else {
                return Ok(());
            }
        }
    };
    if let Some(out) = set_original_statusline(&data, &target) {
        crate::fsatomic::write(settings_path, &out, crate::fsatomic::Mode::Preserve(0o644))
            .map_err(|e| format!("cannot write Claude settings: {e}"))?;
    }
    Ok(())
}

fn apply_claude_statusline_at(
    settings_path: &Path,
    dir: &Path,
    template: &str,
) -> Result<(), String> {
    match template {
        "current" => restore_statusline_at(settings_path, dir),
        "minimal" | "context" | "meters" | "vibrant" => {
            apply_template_at(settings_path, dir, template)
        }
        "custom" => apply_custom_at(settings_path, dir, &read_custom_spec(dir)),
        "ai" => apply_ai_at(settings_path, dir),
        other => Err(format!("unknown status line template: {other}")),
    }
}

/// Re-apply the previously generated AI status line (its script is stored under
/// lpm-ai.sh). No-op with a friendly error if nothing was generated yet.
fn apply_ai_at(settings_path: &Path, dir: &Path) -> Result<(), String> {
    let source = std::fs::read_to_string(dir.join("lpm-ai.sh"))
        .map_err(|_| "No AI status line yet. Describe one and generate it first.".to_string())?;
    apply_statusline_script(settings_path, dir, "ai", &source)
}

fn read_ai_description(dir: &Path) -> String {
    std::fs::read(dir.join("ai.json"))
        .ok()
        .and_then(|d| serde_json::from_slice::<Value>(&d).ok())
        .and_then(|v| {
            v.get("description")
                .and_then(Value::as_str)
                .map(str::to_string)
        })
        .unwrap_or_default()
}

#[derive(Serialize)]
#[serde(rename_all = "camelCase")]
pub struct ClaudeStatuslineState {
    pub selected: String,
    pub has_custom: bool,
    pub custom: CustomSpec,
    pub has_saved_custom: bool,
    pub ai_description: String,
}

fn claude_statusline_state_at(settings_path: &Path, dir: &Path) -> ClaudeStatuslineState {
    let original = std::fs::read(settings_path)
        .ok()
        .and_then(|d| serde_json::from_slice::<Value>(&d).ok())
        .map(|s| prior_statusline(&s))
        .unwrap_or(Value::Null);
    let template = detect_template_id(&original, dir);
    let has_custom =
        dir.join("original.json").exists() || (template.is_none() && !original.is_null());
    ClaudeStatuslineState {
        selected: template.unwrap_or_else(|| "current".into()),
        has_custom,
        custom: read_custom_spec(dir),
        has_saved_custom: dir.join("custom.json").exists(),
        ai_description: read_ai_description(dir),
    }
}

fn refresh_active_template_at(settings_path: &Path, dir: &Path) -> Result<(), String> {
    let Ok(data) = std::fs::read(settings_path) else {
        return Ok(());
    };
    let Ok(settings) = serde_json::from_slice::<Value>(&data) else {
        return Ok(());
    };
    let Some(id) = detect_template_id(&prior_statusline(&settings), dir) else {
        return Ok(());
    };
    let Some(spec) = preset_spec(&id) else {
        return Ok(());
    };
    let source = build_custom_statusline(&spec)?;
    let path = dir.join(format!("lpm-{id}.sh"));
    if std::fs::read_to_string(&path).ok().as_deref() == Some(source.as_str()) {
        return Ok(());
    }
    write_template_script(dir, &id, &source)?;
    Ok(())
}

pub fn refresh_active_claude_statusline_template() {
    let _ = refresh_active_template_at(&claude_settings_path(), &statuslines_dir());
}

/// Which status line is effective on this Mac, whether there is a prior line to
/// restore, and the saved Custom spec (or the default when none). Read-only.
#[tauri::command(async)]
pub fn get_claude_statusline_state() -> ClaudeStatuslineState {
    claude_statusline_state_at(&claude_settings_path(), &statuslines_dir())
}

/// Apply a built-in status line template ("minimal"/"context"/"meters"), the
/// saved Custom line ("custom"), or restore the user's own line ("current").
#[tauri::command(async)]
pub fn apply_claude_statusline(template: String) -> Result<(), String> {
    apply_claude_statusline_at(&claude_settings_path(), &statuslines_dir(), &template)
}

/// Validate and apply a Custom status line composed in Settings.
#[tauri::command(async)]
pub fn apply_claude_statusline_custom(spec: Value) -> Result<(), String> {
    let spec: CustomSpec =
        serde_json::from_value(spec).map_err(|e| format!("invalid status line spec: {e}"))?;
    apply_custom_at(&claude_settings_path(), &statuslines_dir(), &spec)
}

/// The Custom spec behind a built-in preset, so the editor can open pre-filled
/// with that preset's segments when the user chooses to tweak it.
#[tauri::command(async)]
pub fn claude_statusline_preset_spec(id: String) -> Result<CustomSpec, String> {
    preset_spec(&id).ok_or_else(|| format!("unknown status line template: {id}"))
}


#[cfg(test)]
mod forwarder_tests;

#[cfg(test)]
mod tests;
