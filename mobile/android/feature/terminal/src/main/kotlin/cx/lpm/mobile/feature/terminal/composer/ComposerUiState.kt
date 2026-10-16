package cx.lpm.mobile.feature.terminal.composer

import androidx.annotation.StringRes
import cx.lpm.mobile.core.model.ComposerAction
import cx.lpm.mobile.core.model.SessionKey
import cx.lpm.mobile.core.model.SlashCommand

/**
 * What a composer writes to: a terminal, or — without [terminalTools] — a reply
 * box with its own destination (an automation run's follow-up), keyed by
 * [termId] all the same.
 */
data class ComposerTarget(
    val termId: String,
    val project: String,
    val label: String,
    val session: SessionKey,
    val terminalTools: Boolean = true
)

data class ComposerUiState(
    val composer: ComposerState = ComposerState(),
    /** The terminal's own tools: special keys, slash commands, its output as context, memory, duplicates. */
    val terminalTools: Boolean = true,
    val slashMatches: List<SlashCommand> = emptyList(),
    val slashArgumentHint: String? = null,
    /** An "@fragment" is being typed, so the mention menu shows (its Context rows at least). */
    val mentionActive: Boolean = false,
    val mentions: MentionResults = MentionResults(),
    /** Session memory lives on the Mac running the agent, so an SSH project has none here. */
    val memoryAvailable: Boolean = false,
    /** The AI CLI the terminal runs, deciding how a memory invocation is written. */
    val terminalCli: String = "",
    val composerActions: List<ComposerAction> = emptyList(),
    val variantCounts: Map<String, Int> = emptyMap(),
    /** A "Heads up" alert's message. */
    @param:StringRes val sendWarning: Int? = null
) {
    private val hasText: Boolean get() = composer.text.isNotBlank()

    val canSend: Boolean
        get() = when (composer.sendState) {
            is SendState.Ready, is SendState.DroppedFailures -> !composer.transforming
            SendState.Pending, SendState.Empty -> false
        }

    /** Save as draft and the duplicates run are worth offering only with text to act on. */
    val sendMenuUseful: Boolean get() = hasText && !composer.transforming

    val canRewrite: Boolean get() = hasText && !composer.transforming

    /** A terminal always offers its own output; a reply box only shows actual matches. */
    val showsMentionMenu: Boolean get() = mentionActive && (terminalTools || !mentions.isEmpty)

    fun variants(key: String): Int = variantCounts[key] ?: 1
}

/** The tab strip's summary of each tab; the active tab's preview stays blank while it's typed into. */
data class TabStripItem(val id: String, val preview: String, val attachmentCount: Int)

internal val ComposerState.tabStripItems: List<TabStripItem>
    get() = tabs.mapIndexed { index, tab ->
        TabStripItem(
            id = tab.id,
            preview = if (index == activeIndex) "" else tab.preview,
            attachmentCount = tab.attachments.size
        )
    }
