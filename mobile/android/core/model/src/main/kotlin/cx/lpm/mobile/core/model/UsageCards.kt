package cx.lpm.mobile.core.model

import java.text.Collator
import kotlin.math.roundToLong

/** One card on the Usage screen: a tool's reading with its heading resolved. */
data class UsageCard(
    val key: String,
    val data: ProviderLimits,
    val title: String,
    val subtitle: String?
) {
    val id: String get() = key
}

/**
 * The Claude cards, one per account, ordered by title then key so the list does not
 * reshuffle between snapshots. Empty while Claude reporting is off on the Mac — no
 * window ever arrives then, and the screen turns that into its own panel. As on the
 * desktop, an account is titled by the name it was given on the Mac (its id until the
 * accounts arrive) over its signed-in email, or over its id when no email is known.
 */
fun claudeCards(snapshot: LimitsSnapshot, accounts: List<ClaudeAccountInfo>): List<UsageCard> {
    if (!snapshot.claudeEnabled) return emptyList()
    val collator = Collator.getInstance()
    return snapshot.entries
        .filter { it.value.provider == "claude" }
        .map { (key, data) ->
            val id = data.accountId?.ifEmpty { null } ?: CLAUDE_MAIN_LOGIN
            val account = accounts.firstOrNull { it.id == id }
            val email = account?.email?.ifEmpty { null }
            if (id == CLAUDE_MAIN_LOGIN) {
                UsageCard(key, data, title = "Claude", subtitle = email)
            } else {
                val name = account?.name
                UsageCard(
                    key,
                    data,
                    title = name ?: id,
                    subtitle = email ?: if (name == null) null else id
                )
            }
        }
        .sortedWith { lhs, rhs ->
            collator.compare(lhs.title, rhs.title).takeIf { it != 0 }
                ?: collator.compare(lhs.key, rhs.key)
        }
}

/**
 * Codex reports a single entry per Mac; take the freshest one anyway so a stray
 * duplicate key can never pin the card to an older reading.
 */
fun codexEntry(snapshot: LimitsSnapshot): ProviderLimits? =
    snapshot.entries.values.filter { it.provider == "codex" }.maxByOrNull { it.updatedAt }

/**
 * An account's 5-hour and weekly use as one line ("5h 72% · 7d 41%"), the desktop
 * menu's meters in words. Windows that have already turned over are left out. Null
 * when there is no current reading. `now` is unix millis.
 */
fun claudeUsageLine(snapshot: LimitsSnapshot?, account: String, now: Double): String? {
    val data = snapshot?.entries?.get("claude:$account") ?: return null
    val parts = listOf("5h" to data.fiveHour, "7d" to data.weekly).mapNotNull { (label, win) ->
        if (win == null || !win.usedPercent.isFinite()) return@mapNotNull null
        if (win.resetsAt > 0 && win.resetsAt * 1000.0 <= now) return@mapNotNull null
        "$label ${win.usedPercent.roundToLong()}%"
    }
    return parts.ifEmpty { null }?.joinToString(" · ")
}
