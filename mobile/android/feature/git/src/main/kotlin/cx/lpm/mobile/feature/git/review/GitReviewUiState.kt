package cx.lpm.mobile.feature.git.review

import androidx.compose.runtime.Immutable
import cx.lpm.mobile.core.data.git.GitNotice
import cx.lpm.mobile.core.data.git.GitOpError
import cx.lpm.mobile.core.model.GitFile
import cx.lpm.mobile.core.model.TerminalInfo

/** What the review screen shows instead of its list. */
enum class ReviewOverlay { Skeleton, Unreachable, NotRepo, NoChanges }

/**
 * The review screen (iOS `GitReviewView`): its rows, the title's +A −D across every
 * parsed file, the commit sheet's message and files, and the project's terminals for
 * "Ask agent…".
 */
@Immutable
data class GitReviewUiState(
    val project: String = "",
    val overlay: ReviewOverlay? = ReviewOverlay.Skeleton,
    val loadError: String? = null,
    val ghCli: Boolean = false,
    val rows: List<ReviewRow> = emptyList(),
    val commitFiles: List<CommitFile> = emptyList(),
    /** Files that still have merge conflicts, for the desktop's ConflictNotice. */
    val conflicted: List<String> = emptyList(),
    val added: Int = 0,
    val removed: Int = 0,
    val message: String = "",
    val selectedCount: Int = 0,
    val generating: Boolean = false,
    val committing: Boolean = false,
    val canCommit: Boolean = false,
    /** Bumped by each successful commit, which closes the commit sheet. */
    val commitTick: Int = 0,
    val opError: GitOpError? = null,
    val notice: GitNotice? = null,
    val terminals: List<TerminalInfo> = emptyList()
)

/** A file in the commit sheet: in the commit or not, and its +N −M once parsed. */
@Immutable
data class CommitFile(val file: GitFile, val selected: Boolean, val added: Int?, val removed: Int?)

/** One "Ask agent…": the file and the diff to attach. */
@Immutable
data class AskContext(val path: String, val diffText: String)
