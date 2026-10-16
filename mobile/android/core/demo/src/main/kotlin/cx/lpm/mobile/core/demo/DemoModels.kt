package cx.lpm.mobile.core.demo

// The slices of the app's models the simulated Mac reads (WorkStatusModel.swift and
// ClaudeAccountModel.swift), kept here so this module stands alone.

private val workStates = setOf("in_progress", "blocked", "done", "custom")

private fun trimmed(value: Any?): String? =
    value.asString()?.trimmingWhitespacesAndNewlines()?.takeIf { it.isNotEmpty() }

/** The status on a project, as the Mac stores it; `since` is unix millis. */
internal class WorkStatus(
    val state: String,
    val label: String?,
    val emoji: String?,
    val note: String?,
    val since: Long
) {
    /** The one rule for "the same status": the state, and the label when custom. */
    val key: String get() = if (state == "custom") "custom:${label ?: ""}" else state

    companion object {
        /**
         * Null for a state this build doesn't know, or a custom status with no name:
         * the same two rules that make the Mac drop the key.
         */
        fun from(o: Dict): WorkStatus? {
            val state = o.string("state")?.takeIf { it in workStates } ?: return null
            val custom = state == "custom"
            val label = if (custom) trimmed(o["label"]) else null
            if (custom && label == null) return null
            return WorkStatus(
                state = state,
                label = label,
                emoji = if (custom) trimmed(o["emoji"]) else null,
                note = trimmed(o["note"]),
                since = o["since"].numberValue() ?: 0
            )
        }
    }
}

/** One of the user's own statuses, from the Mac's palette. */
internal data class CustomWorkStatus(
    val label: String,
    val emoji: String,
    val withNote: Boolean = false
)

/** The five statuses every Mac ships beside the built-in states. */
internal val defaultWorkStatusPalette = listOf(
    CustomWorkStatus(label = "Review", emoji = "👀", withNote = true),
    CustomWorkStatus(label = "Ready", emoji = "🚀"),
    CustomWorkStatus(label = "Waiting", emoji = "⏰", withNote = true),
    CustomWorkStatus(label = "Needs decision", emoji = "❓", withNote = true),
    CustomWorkStatus(label = "Paused", emoji = "⏸️", withNote = true)
)

/** The main login's account id, as the Mac names it in lists and usage readings. */
internal const val CLAUDE_MAIN_LOGIN = "default"

/** A project's own Claude account keys: one pinned account, or a list to take turns on. */
internal data class ClaudeOwnChoice(val account: String? = null, val accounts: List<String>? = null)

/** What the Claude Account menu sets, matching the desktop's `set_claude_account_choice`. */
internal sealed interface ClaudeAccountChoice {
    data object Parent : ClaudeAccountChoice

    data object Main : ClaudeAccountChoice

    /** "" is the main login. */
    data class Pin(val id: String) : ClaudeAccountChoice

    data class ListOf(val ids: List<String>) : ClaudeAccountChoice

    /** The keys the Mac writes for this choice. */
    fun own(isCopy: Boolean): ClaudeOwnChoice = when (this) {
        Parent -> ClaudeOwnChoice()
        Main -> ClaudeOwnChoice(accounts = if (isCopy) emptyList() else null)
        is Pin -> ClaudeOwnChoice(account = id)
        is ListOf -> ClaudeOwnChoice(accounts = ids)
    }

    companion object {
        fun fromWire(o: Dict): ClaudeAccountChoice? = when (o.string("kind")) {
            "parent" -> Parent
            "main" -> Main
            "pin" -> Pin(o.string("id") ?: "")
            "list" -> ListOf(o.strings("ids") ?: emptyList())
            else -> null
        }
    }
}
