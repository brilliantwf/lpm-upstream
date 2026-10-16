package cx.lpm.mobile.feature.git.review

import androidx.compose.runtime.Immutable
import cx.lpm.mobile.core.model.GitDiffResult
import cx.lpm.mobile.core.model.GitFile
import cx.lpm.mobile.feature.git.diff.DiffBlock
import cx.lpm.mobile.feature.git.diff.DiffBlockKind
import cx.lpm.mobile.feature.git.diff.ParsedDiff

/** One row of the review list, keyed for the lazy list. */
@Immutable
sealed interface ReviewRow {
    val key: String

    /** "CHANGED FILES (4)", and "1 of 4 reviewed" once any is. */
    data class ChangedHeader(val count: Int, val reviewed: Int) : ReviewRow {
        override val key get() = "changed"
    }

    data object NoChanges : ReviewRow {
        override val key get() = "no-changes"
    }

    /** A file's bar: select, collapse, path, +N −M once parsed, viewed, "…". */
    data class FileHeader(
        val file: GitFile,
        val selected: Boolean,
        val collapsed: Boolean,
        val viewed: Boolean,
        val added: Int?,
        val removed: Int?
    ) : ReviewRow {
        val path: String get() = file.path
        override val key get() = "h:$path"
    }

    data class Block(val path: String, val block: DiffBlock) : ReviewRow {
        override val key get() = "b:$path:${block.id}"
    }

    data class Note(val path: String, val note: FileNote) : ReviewRow {
        override val key get() = "n:$path"
    }

    data class ShowAll(val path: String, val total: Int) : ReviewRow {
        override val key get() = "all:$path"
    }

    data object Commit : ReviewRow {
        override val key get() = "commit"
    }

    data object CreatePullRequest : ReviewRow {
        override val key get() = "pr"
    }
}

/** What a file shows instead of its diff. */
@Immutable
sealed interface FileNote {
    data object Binary : FileNote

    data object Empty : FileNote

    data object Loading : FileNote

    data class Failed(val message: String) : FileNote
}

/** One file of the review, with what's loaded for it. */
internal class ReviewFile(
    val file: GitFile,
    val result: GitDiffResult?,
    val error: String?,
    val parsed: ParsedDiff?,
    val selected: Boolean,
    val viewed: Boolean,
    val collapsed: Boolean,
    val expanded: Boolean
)

/**
 * The review list (iOS `GitReviewView`'s `List`): the changed-files header, each file
 * as a header bar and (unless collapsed) its diff, the commit section, and "Create
 * Pull Request" when the GitHub CLI is there.
 */
internal fun reviewRows(files: List<ReviewFile>, ghCli: Boolean): List<ReviewRow> = buildList {
    if (files.isEmpty()) {
        add(ReviewRow.NoChanges)
    } else {
        add(ReviewRow.ChangedHeader(files.size, files.count { it.viewed }))
        files.forEach { addFile(it) }
        add(ReviewRow.Commit)
    }
    if (ghCli) add(ReviewRow.CreatePullRequest)
}

private fun MutableList<ReviewRow>.addFile(f: ReviewFile) {
    val path = f.file.path
    add(
        ReviewRow.FileHeader(
            file = f.file,
            selected = f.selected,
            collapsed = f.collapsed,
            viewed = f.viewed,
            added = f.parsed?.addedCount,
            removed = f.parsed?.removedCount
        )
    )
    if (f.collapsed) return
    val result = f.result
    val note = when {
        result == null -> f.error?.let(FileNote::Failed) ?: FileNote.Loading
        result.binary -> FileNote.Binary
        result.diff.isBlank() -> FileNote.Empty
        f.parsed == null -> FileNote.Loading
        else -> null
    }
    if (note != null) {
        add(ReviewRow.Note(path, note))
        return
    }
    val parsed = f.parsed ?: return
    val blocks = shownBlocks(parsed, f.expanded)
    blocks.forEach { add(ReviewRow.Block(path, it)) }
    if (blocks.size < parsed.blocks.size) add(ReviewRow.ShowAll(path, parsed.lines.size))
}

/**
 * Whole blocks up to the soft line cap unless expanded, so a monster file doesn't lay
 * out thousands of lines at once. A trailing hunk band with nothing under it goes.
 */
internal fun shownBlocks(parsed: ParsedDiff, expanded: Boolean): List<DiffBlock> {
    if (expanded) return parsed.blocks
    val shown = mutableListOf<DiffBlock>()
    var lines = 0
    for (block in parsed.blocks) {
        if (block.kind == DiffBlockKind.Code) {
            if (lines + block.lineCount > SOFT_CAP) break
            lines += block.lineCount
        }
        shown += block
    }
    if (shown.size < parsed.blocks.size && shown.lastOrNull()?.kind == DiffBlockKind.Hunk) {
        shown.removeAt(shown.lastIndex)
    }
    return shown
}

private const val SOFT_CAP = 600
