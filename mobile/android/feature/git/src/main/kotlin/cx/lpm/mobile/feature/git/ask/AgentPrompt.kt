package cx.lpm.mobile.feature.git.ask

import cx.lpm.mobile.core.model.TerminalInfo

/** The prompt "Ask agent…" sends: the instruction, then the file's diff to act on. */
internal fun agentPrompt(instruction: String, path: String, diffText: String): String =
    "${instruction.trim()}\n\nRegarding these changes in $path:\n```diff\n$diffText\n```"

/** The terminal picked to start with: the first running an agent, else the first. */
internal fun defaultTerminal(terminals: List<TerminalInfo>): TerminalInfo? =
    terminals.firstOrNull { it.cli.isNotEmpty() } ?: terminals.firstOrNull()

/** The diff's first 40 lines, then how many more there are. */
internal fun previewLines(diffText: String): PreviewDiff {
    if (diffText.isEmpty()) return PreviewDiff.Empty
    val lines = diffText.split("\n")
    if (lines.size <= PREVIEW_LINES) return PreviewDiff.Whole(diffText)
    return PreviewDiff.Cut(lines.take(PREVIEW_LINES).joinToString("\n"), lines.size - PREVIEW_LINES)
}

internal sealed interface PreviewDiff {
    data object Empty : PreviewDiff

    data class Whole(val text: String) : PreviewDiff

    data class Cut(val text: String, val more: Int) : PreviewDiff
}

private const val PREVIEW_LINES = 40
