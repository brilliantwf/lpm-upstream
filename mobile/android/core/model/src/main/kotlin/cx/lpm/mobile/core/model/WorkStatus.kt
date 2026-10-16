package cx.lpm.mobile.core.model

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

// The person-set work status on a project — "what I want done with it", as opposed
// to StatusEntry, which is what the agent is doing. Mirrors the desktop's
// src/workStatus.ts and the Mac's set_work_status, so the row the phone paints
// before the reply lands is the row the Mac writes.

enum class WorkState(val rawValue: String) {
    InProgress("in_progress"),
    Blocked("blocked"),
    Done("done"),
    Custom("custom")
    ;

    /** Null for `custom`, the one state that carries its own name and mark. */
    internal val builtInLabel: String?
        get() = when (this) {
            InProgress -> "In progress"
            Blocked -> "Blocked"
            Done -> "Done"
            Custom -> null
        }

    internal val builtInEmoji: String?
        get() = when (this) {
            InProgress -> "⏳"
            Blocked -> "⛔"
            Done -> "✅"
            Custom -> null
        }

    companion object {
        fun of(rawValue: String?): WorkState? = entries.firstOrNull { it.rawValue == rawValue }
    }
}

/**
 * The status on a project, as the Mac stores it. `label`/`emoji` describe a custom
 * status only; `since` is unix millis, 0 when the Mac wrote no clock.
 */
data class WorkStatus(
    val state: WorkState,
    val label: String?,
    val emoji: String?,
    val note: String?,
    val since: Long
) {
    val displayLabel: String get() = state.builtInLabel ?: label.orEmpty()
    val displayEmoji: String get() = state.builtInEmoji ?: emoji.orEmpty()

    /** The one rule for "the same status": the state, and the label when custom. */
    val key: String get() = workStatusKey(state, label)

    companion object {
        /**
         * Null for a state this build doesn't know, or a custom status with no name —
         * the same two rules that make the Mac drop the key.
         */
        fun parse(o: JsonObject): WorkStatus? {
            val state = WorkState.of(o.string("state").orEmpty()) ?: return null
            val custom = state == WorkState.Custom
            val label = if (custom) o["label"].trimmedOrNull() else null
            if (custom && label == null) return null
            return WorkStatus(
                state = state,
                label = label,
                emoji = if (custom) o["emoji"].trimmedOrNull() else null,
                note = o["note"].trimmedOrNull(),
                since = o.numberLong("since") ?: 0
            )
        }
    }
}

/** One of the user's own statuses, from the Mac's palette. */
data class CustomWorkStatus(val label: String, val emoji: String, val withNote: Boolean = false) {
    val id: String get() = label

    companion object {
        fun parse(o: JsonObject): CustomWorkStatus? {
            val label = o["label"].trimmedOrNull() ?: return null
            return CustomWorkStatus(
                label = label,
                emoji = o.string("emoji").orEmpty(),
                withNote = o.bool("withNote") ?: false
            )
        }
    }
}

/** What applying a menu row asks the Mac for; the Mac stamps `since`. */
data class WorkStatusInput(
    val state: WorkState,
    val label: String? = null,
    val emoji: String? = null,
    val note: String? = null
) {
    val wire: JsonObject
        get() = buildJsonObject {
            put("state", state.rawValue)
            label.trimmedOrNull()?.let { put("label", it) }
            emoji.trimmedOrNull()?.let { put("emoji", it) }
            note.trimmedOrNull()?.let { put("note", it) }
        }

    val key: String get() = workStatusKey(state, label)

    /**
     * The status this input becomes, or null when it names nothing: a custom status
     * with no label is no status at all.
     */
    internal val normalized: WorkStatusInput?
        get() {
            val custom = state == WorkState.Custom
            val label = if (custom) label.trimmedOrNull() else null
            if (custom && label == null) return null
            return WorkStatusInput(
                state = state,
                label = label,
                emoji = if (custom) emoji.trimmedOrNull() else null,
                note = note.trimmedOrNull()
            )
        }
}

private fun workStatusKey(state: WorkState, label: String?): String =
    if (state == WorkState.Custom) "custom:${label.orEmpty()}" else state.rawValue

/**
 * One row of the Status menu: what it applies, how it reads, and whether applying
 * it asks for a line first.
 */
data class WorkStatusChoice(
    val input: WorkStatusInput,
    val label: String,
    val emoji: String,
    val asksNote: Boolean
) {
    val id: String get() = input.key
}

private fun builtInChoice(state: WorkState) = WorkStatusChoice(
    input = WorkStatusInput(state),
    label = state.builtInLabel.orEmpty(),
    emoji = state.builtInEmoji.orEmpty(),
    asksNote = state == WorkState.Blocked
)

fun workStatusChoice(entry: CustomWorkStatus): WorkStatusChoice = WorkStatusChoice(
    input = WorkStatusInput(WorkState.Custom, label = entry.label, emoji = entry.emoji),
    label = entry.label,
    emoji = entry.emoji,
    asksNote = entry.withNote
)

/**
 * The states the menu always offers. Everything else in it is the Mac's palette,
 * which the user can edit down to nothing.
 */
val builtInWorkStatuses: List<WorkStatusChoice> = listOf(
    builtInChoice(WorkState.InProgress),
    builtInChoice(WorkState.Blocked),
    builtInChoice(WorkState.Done)
)

/**
 * The five statuses every Mac ships beside the built-in states, and what the phone
 * assumes of a Mac too old to send them.
 */
val defaultWorkStatusPalette: List<CustomWorkStatus> = listOf(
    CustomWorkStatus(label = "Review", emoji = "👀", withNote = true),
    CustomWorkStatus(label = "Ready", emoji = "🚀"),
    CustomWorkStatus(label = "Waiting", emoji = "⏰", withNote = true),
    CustomWorkStatus(label = "Needs decision", emoji = "❓", withNote = true),
    CustomWorkStatus(label = "Paused", emoji = "⏸️", withNote = true)
)

// Where the work is in its life in the order it moves through them, then why it is
// not moving; the user can reorder the menu, so this is only the default.
val defaultWorkStatusOrder: List<String> = listOf(
    WorkState.InProgress.rawValue,
    "custom:Review",
    "custom:Ready",
    WorkState.Done.rawValue,
    WorkState.Blocked.rawValue,
    "custom:Waiting",
    "custom:Needs decision",
    "custom:Paused"
)

/**
 * The Status menu: the built-in states and the Mac's palette, in the order the user
 * dragged them into. A status the order says nothing about keeps its default
 * place, after the ones it does.
 */
fun workStatusMenu(palette: List<CustomWorkStatus>, order: List<String>): List<WorkStatusChoice> {
    val choices = builtInWorkStatuses + palette.map(::workStatusChoice)
    val ranked = order.ifEmpty { defaultWorkStatusOrder }
    val rank = mutableMapOf<String, Int>()
    ranked.forEachIndexed { i, key -> rank.putIfAbsent(key, i) }
    return choices.sortedBy { rank[it.id] ?: Int.MAX_VALUE }
}

fun sameWorkStatus(current: WorkStatus?, input: WorkStatusInput): Boolean =
    current?.key == input.key

/**
 * The note already on the row, when the choice keeps the same status — so
 * re-applying it edits the line rather than starting from blank.
 */
fun existingNote(current: WorkStatus?, choice: WorkStatusChoice): String =
    if (sameWorkStatus(current, choice.input)) current?.note.orEmpty() else ""

/**
 * What the row shows the instant the menu closes, before the Mac confirms. A
 * note-only edit keeps the clock; a change of status restarts it — including when
 * the status on screen carries no clock at all.
 */
fun nextWorkStatus(current: WorkStatus?, input: WorkStatusInput?, now: Long): WorkStatus? {
    if (input == null) return null
    val next = input.normalized ?: return current
    val kept = if (sameWorkStatus(current, next)) current?.since ?: 0 else 0
    return WorkStatus(
        state = next.state,
        label = next.label,
        emoji = next.emoji,
        note = next.note,
        since = if (kept == 0L) now else kept
    )
}
