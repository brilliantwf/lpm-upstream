// Telegram as a place automations send their results: a bot the user made with
// @BotFather writing to the chats it's been given — found by the user messaging
// the bot or adding it to a group or channel, so a chat id is never typed.

use crate::secrets;
use crate::sendto::{Destination, Report};
use crate::telegramfmt;
use crate::telegramlink::{Chat, Link};
use serde_json::{json, Value};
use std::time::Duration;

const API: &str = "https://api.telegram.org";
/// Telegram refuses a message over 4096 characters; a longer report goes out
/// in several.
const MAX_MESSAGE_CHARS: usize = 4000;

fn token() -> Result<String, String> {
    secrets::get(secrets::TELEGRAM_BOT_TOKEN)?
        .ok_or_else(|| "Telegram isn't connected. Set it up in Settings → Notifications.".into())
}

/// One Bot API call. The token is part of the URL, so a transport error is
/// reported without it.
fn call(token: &str, method: &str, body: Value) -> Result<Value, String> {
    let client = reqwest::blocking::Client::builder()
        .timeout(Duration::from_secs(20))
        .user_agent("lpm")
        .build()
        .map_err(|e| e.to_string())?;
    let resp = client
        .post(format!("{API}/bot{token}/{method}"))
        .header("content-type", "application/json")
        .body(body.to_string())
        .send()
        .map_err(|e| format!("Couldn't reach Telegram: {}", e.without_url()))?;
    let reply: Value = serde_json::from_str(&resp.text().unwrap_or_default())
        .map_err(|_| "Telegram sent back something unreadable.".to_string())?;
    if reply["ok"].as_bool() == Some(true) {
        Ok(reply["result"].clone())
    } else {
        Err(match reply["description"].as_str() {
            Some(d) => format!("Telegram said: {d}"),
            None => "Telegram turned the request down.".into(),
        })
    }
}

/// A blocking request dropped on an async command worker panics, so the
/// commands make theirs on a thread of their own.
fn off_worker<T: Send + 'static>(f: impl FnOnce() -> Result<T, String> + Send + 'static) -> Result<T, String> {
    std::thread::spawn(f)
        .join()
        .map_err(|_| "background thread panicked".to_string())?
}

fn head(report: &Report) -> String {
    if report.mark.is_empty() {
        report.title.clone()
    } else {
        format!("{} {}", report.mark, report.title)
    }
}

/// Sends one message formatted, or as plain text when Telegram can't read the
/// markup — an odd report is still worth delivering unformatted.
fn send_message(token: &str, chat_id: i64, message: &telegramfmt::Message) -> Result<(), String> {
    let body = |text: &str, html: bool| {
        let mut body = json!({ "chat_id": chat_id, "text": text, "link_preview_options": { "is_disabled": true } });
        if html {
            body["parse_mode"] = json!("HTML");
        }
        body
    };
    match call(token, "sendMessage", body(&message.html, true)) {
        Err(e) if e.contains("can't parse entities") => {
            call(token, "sendMessage", body(&message.plain, false)).map(drop)
        }
        sent => sent.map(drop),
    }
}

pub struct Telegram;

impl Destination for Telegram {
    fn id(&self) -> &'static str {
        "telegram"
    }

    fn label(&self) -> &'static str {
        "Telegram"
    }

    fn send(&self, target: Option<&str>, report: &Report) -> Result<(), String> {
        let token = token()?;
        let link = Link::load();
        let chat_id = match (link.chat(target), target) {
            (Some(chat), _) => chat.chat_id,
            (None, Some(key)) => {
                return Err(format!(
                    "\"{key}\" isn't one of lpm's Telegram chats any more. Pick another in the job's Send to."
                ))
            }
            (None, None) => {
                return Err(
                    "Telegram has no chat to send to yet. Finish setting it up in Settings → Notifications."
                        .into(),
                )
            }
        };
        for message in telegramfmt::messages(&head(report), report.body.as_deref(), MAX_MESSAGE_CHARS) {
            send_message(&token, chat_id, &message)?;
        }
        Ok(())
    }
}

fn chat_json(chat: &Chat) -> Value {
    json!({ "key": chat.key, "name": chat.name, "kind": chat.kind })
}

/// The token inside whatever was pasted — BotFather's whole reply will do.
fn extract_token(text: &str) -> Option<&str> {
    text.split(|c: char| c.is_whitespace() || matches!(c, '`' | '"' | '\'' | ','))
        .find(|word| {
            let Some((id, secret)) = word.split_once(':') else {
                return false;
            };
            id.len() >= 5
                && id.bytes().all(|b| b.is_ascii_digit())
                && secret.len() >= 30
                && secret.bytes().all(|b| b.is_ascii_alphanumeric() || b == b'_' || b == b'-')
        })
}

#[tauri::command(async)]
pub fn telegram_status() -> Value {
    let mut link = Link::load();
    let has_token = secrets::has(secrets::TELEGRAM_BOT_TOKEN);
    if has_token && link.ensure_code() {
        let _ = link.save();
    }
    json!({
        "connected": has_token && !link.chats.is_empty(),
        "hasToken": has_token,
        "bot": link.bot,
        "code": link.code,
        "chats": link.chats.iter().map(chat_json).collect::<Vec<_>>(),
    })
}

/// Checks the token with Telegram before keeping it, and starts the chats over:
/// a new bot can't write to the old one's chats.
#[tauri::command(async)]
pub fn telegram_connect(token: String) -> Result<Value, String> {
    let Some(token) = extract_token(&token).map(str::to_string) else {
        return Err("That doesn't hold a bot token. Paste the reply @BotFather sent.".into());
    };
    off_worker(move || {
        let me = call(&token, "getMe", json!({}))?;
        let bot = me["username"].as_str().unwrap_or_default().to_string();
        secrets::set(secrets::TELEGRAM_BOT_TOKEN, &token)?;
        let link = Link::for_bot(bot.clone());
        link.save()?;
        Ok(json!({ "bot": bot, "code": link.code }))
    })
}

/// What a chat sees the moment it's linked, so the user knows it worked
/// without a separate test.
fn welcome(chat: &Chat) -> &'static str {
    if chat.kind == "private" {
        "lpm is connected. Automations that send to Telegram will post their results in this chat."
    } else {
        "lpm will post automation results here."
    }
}

/// Looks at what the bot has heard since the last look and keeps every new
/// chat — someone messaging it, or it being added to a group or channel.
/// Returns the chats that are new, often none.
#[tauri::command(async)]
pub fn telegram_find_chats() -> Result<Vec<Value>, String> {
    off_worker(|| {
        let token = token()?;
        let mut link = Link::load();
        let mut body = json!({ "timeout": 0 });
        if let Some(offset) = link.offset {
            body["offset"] = json!(offset + 1);
        }
        let updates = call(&token, "getUpdates", body)?;
        let added = link.take_updates(&updates);
        link.save()?;
        for chat in &added {
            let _ = call(&token, "sendMessage", json!({ "chat_id": chat.chat_id, "text": welcome(chat) }));
        }
        Ok(added.iter().map(chat_json).collect())
    })
}

#[tauri::command(async)]
pub fn telegram_test(key: Option<String>) -> Result<(), String> {
    off_worker(move || {
        Telegram.send(
            key.as_deref(),
            &Report {
                mark: "",
                title: "lpm is connected.".into(),
                body: Some(
                    "Automations that send to this chat will post their results here.".into(),
                ),
            },
        )
    })
}

#[tauri::command(async)]
pub fn telegram_remove_chat(key: String) -> Result<(), String> {
    let mut link = Link::load();
    if link.remove(&key) {
        link.save()?;
    }
    Ok(())
}

/// Leaves an empty link rather than no file: config sync treats a missing file as
/// this Mac's own business and would bring the other Mac's back, while an empty
/// one crosses and disconnects that Mac too.
#[tauri::command(async)]
pub fn telegram_disconnect() -> Result<(), String> {
    secrets::delete(secrets::TELEGRAM_BOT_TOKEN)?;
    Link::default().save()
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn finds_the_token_in_whatever_was_pasted() {
        let token = "123456789:AAFakeTokenForTestsOnly_abc-DEF123456";
        assert_eq!(extract_token(token), Some(token));
        let reply = format!("Done! Congratulations on your new bot.\nUse this token to access the HTTP API:\n{token}\nKeep your token secure");
        assert_eq!(extract_token(&reply), Some(token));
        assert_eq!(extract_token(&format!("`{token}`")), Some(token));
        assert_eq!(extract_token("t.me/lpm_bot"), None);
        assert_eq!(extract_token("12:short"), None);
    }

    #[test]
    fn leads_with_the_mark() {
        let report = |mark| Report {
            mark,
            title: "\"Watch\" is done.".into(),
            body: None,
        };
        assert_eq!(head(&report("✅")), "✅ \"Watch\" is done.");
        assert_eq!(head(&report("")), "\"Watch\" is done.");
    }
}
