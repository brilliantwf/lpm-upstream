package cx.lpm.mobile.core.navigation

import androidx.navigation3.runtime.NavKey
import cx.lpm.mobile.core.model.SessionKey
import kotlinx.serialization.Serializable

/** "Review Changes": the project's uncommitted changes. */
@Serializable
data class GitReviewKey(val project: String, val session: SessionKey = SessionKey.Live) : NavKey

/** "Switch Branch…". */
@Serializable
data class GitBranchKey(val project: String, val session: SessionKey = SessionKey.Live) : NavKey

/** "Create Pull Request…". */
@Serializable
data class GitPullRequestKey(val project: String, val session: SessionKey = SessionKey.Live) :
    NavKey

/** An AI git shortcut's progress; `start` is the run the menu asked for (a `GitAutoKind`). */
@Serializable
data class GitAutoKey(
    val project: String,
    val start: String,
    val session: SessionKey = SessionKey.Live
) : NavKey
