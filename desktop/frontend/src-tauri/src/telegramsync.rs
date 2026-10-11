// A Telegram setup made on one Mac reaches the Macs it config-syncs with.
// telegram.json is a synced file; the bot token stays out of it, with the other
// secrets, and travels beside it over the encrypted peer channel into the
// receiving Mac's own secret store — never onto its disk.

use crate::secrets;
use crate::telegramlink::Link;

/// The token to send beside telegram.json: none for a disconnected link, or when
/// this Mac can't read its own.
pub fn outgoing_secret(link_bytes: &[u8]) -> Option<String> {
    if Link::parse(link_bytes).ok()?.is_empty() {
        return None;
    }
    secrets::get(secrets::TELEGRAM_BOT_TOKEN).ok().flatten()
}

/// Takes in the token that came with a synced telegram.json. An empty link was
/// disconnected on the other Mac, so this one lets go of its token too; a link
/// that arrived without one keeps whatever token this Mac already has.
pub fn adopt(link_bytes: &[u8], secret: Option<&str>) -> Result<(), String> {
    let link = Link::parse(link_bytes)?;
    match secret {
        _ if link.is_empty() => secrets::delete(secrets::TELEGRAM_BOT_TOKEN),
        Some(token) => secrets::set(secrets::TELEGRAM_BOT_TOKEN, token),
        None => Ok(()),
    }
}
