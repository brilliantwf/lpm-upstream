package cx.lpm.mobile.feature.git.review

import androidx.compose.runtime.Immutable
import cx.lpm.mobile.core.data.git.GitNotice
import cx.lpm.mobile.core.data.git.GitOpError
import cx.lpm.mobile.core.model.GitFile
import cx.lpm.mobile.core.model.TerminalInfo

/** What the review screen shows instead of its list. */
enum class ReviewOverlay { Skeleton, Unreachable, NotRepo }

/**
 * The review screen (iOS `GitReviewView`): its rows, the title's +A −D across every
 * parsed file, the commit form, and the project's terminals for "Ask agent…".
 */
@Immutable
data class GitReviewUiState(
    val project: String = "",
    val overlay: ReviewOverlay? = ReviewOverlay.Skeleton,
    val loadError: String? = null,
    val rows: List<ReviewRow> = emptyList(),
    val files: List<GitFile> = emptyList(),
    val added: Int = 0,
    val removed: Int = 0,
    val message: String = "",
    val selectedCount: Int = 0,
    val generating: Boolean = false,
    val committing: Boolean = false,
    val canCommit: Boolean = false,
    val opError: GitOpError? = null,
    val notice: GitNotice? = null,
    val terminals: List<TerminalInfo> = emptyList()
)

/** One "Ask agent…": the file and the diff to attach. */
@Immutable
data class AskContext(val path: String, val diffText: String)
