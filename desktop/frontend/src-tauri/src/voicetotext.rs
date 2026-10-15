// VoiceToText is a macOS app; elsewhere dictation reports itself unavailable.
// A copy that speaks its local app API records for lpm, which draws its own
// recorder (voicetotext_api.rs); an older copy is toggled through its URL
// scheme and pastes into the focused field itself.
#[cfg(target_os = "macos")]
use crate::voicetotext_api::{self as api, Unavailable};
#[cfg(target_os = "macos")]
use std::process::Command;
use tauri::AppHandle;

#[tauri::command]
pub fn voice_to_text_available() -> bool {
    #[cfg(target_os = "macos")]
    {
        crate::openin::detect_by_paths(&[
            "/Applications/VoiceToText.app",
            "/Applications/VoiceToText-Dev.app",
        ])
        .is_some()
    }
    #[cfg(not(target_os = "macos"))]
    {
        false
    }
}

#[tauri::command(async)]
pub fn voice_to_text_toggle() -> Result<(), String> {
    #[cfg(target_os = "macos")]
    {
        // `-g` launches the handler in the background so VoiceToText never steals
        // focus — the dictated text then pastes into the composer, which stays
        // frontmost.
        let status = Command::new("open")
            .args(["-g", "voicetotext://toggle"])
            .status()
            .map_err(|e| e.to_string())?;
        if !status.success() {
            return Err("could not reach VoiceToText".into());
        }
        Ok(())
    }
    #[cfg(not(target_os = "macos"))]
    {
        Err("Voice dictation is not available on this platform".into())
    }
}

/// Starts recording through the API: "api" once the request is on its way —
/// progress, levels and the transcript arrive as `voice-to-text` events — or
/// "legacy" when only a copy without the API is there, for the caller to fall
/// back to `voice_to_text_toggle`.
#[tauri::command(async)]
pub fn voice_to_text_start(app: AppHandle) -> Result<&'static str, String> {
    #[cfg(target_os = "macos")]
    {
        match api::send(&app, "start") {
            Ok(()) => Ok("api"),
            Err(Unavailable::Legacy) => Ok("legacy"),
            Err(Unavailable::NotInstalled) => Err("VoiceToText is not installed".into()),
            Err(Unavailable::Failed(message)) => Err(message),
        }
    }
    #[cfg(not(target_os = "macos"))]
    {
        let _ = app;
        Err("Voice dictation is not available on this platform".into())
    }
}

/// Stops recording and transcribes; the text arrives as a `transcript` event.
#[tauri::command(async)]
pub fn voice_to_text_stop() -> Result<(), String> {
    #[cfg(target_os = "macos")]
    {
        if api::send_if_connected("stop") {
            Ok(())
        } else {
            Err("VoiceToText isn't recording".into())
        }
    }
    #[cfg(not(target_os = "macos"))]
    {
        Err("Voice dictation is not available on this platform".into())
    }
}

#[tauri::command(async)]
pub fn voice_to_text_cancel() {
    #[cfg(target_os = "macos")]
    api::send_if_connected("cancel");
}
