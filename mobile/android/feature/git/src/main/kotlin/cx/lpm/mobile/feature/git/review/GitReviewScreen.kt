package cx.lpm.mobile.feature.git.review

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cx.lpm.mobile.core.designsystem.R as DesignR
import cx.lpm.mobile.core.designsystem.component.GlassIconButton
import cx.lpm.mobile.core.designsystem.component.LpmTopBar
import cx.lpm.mobile.core.designsystem.component.TopBarTitle
import cx.lpm.mobile.core.designsystem.theme.LpmTheme
import cx.lpm.mobile.feature.git.R
import cx.lpm.mobile.feature.git.diff.DiffBlockKind
import cx.lpm.mobile.feature.git.diff.DiffCodeBlock
import cx.lpm.mobile.feature.git.diff.HunkSeparator
import cx.lpm.mobile.feature.git.diff.rememberDiffColors
import cx.lpm.mobile.feature.git.ui.GitNoticeBanner
import kotlinx.coroutines.launch

/** What the review screen's controls do. */
@Immutable
class ReviewActions(
    val back: () -> Unit = {},
    val refresh: () -> Unit = {},
    val retry: () -> Unit = {},
    val fileAppeared: (String) -> Unit = {},
    val retryDiff: (String) -> Unit = {},
    val toggleSelected: (String) -> Unit = {},
    val toggleCollapsed: (String) -> Unit = {},
    val toggleViewed: (String) -> Unit = {},
    val showAll: (String) -> Unit = {},
    val ask: (String) -> Unit = {},
    val setMessage: (String) -> Unit = {},
    val generateMessage: () -> Unit = {},
    val commit: () -> Unit = {},
    val createPullRequest: () -> Unit = {}
)

/**
 * The Git review screen (iOS `GitReviewView`): "Changes" over the +A −D, a jump menu,
 * then every changed file with its whole diff, the commit form and "Create Pull
 * Request". Pull to refresh.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GitReviewScreen(
    state: GitReviewUiState,
    refreshing: Boolean,
    modifier: Modifier = Modifier,
    actions: ReviewActions = ReviewActions(),
    listState: LazyListState = rememberLazyListState()
) {
    val scope = rememberCoroutineScope()
    Column(
        modifier
            .fillMaxSize()
            .background(LpmTheme.colors.systemBackground)
    ) {
        LpmTopBar(
            leading = {
                GlassIconButton(
                    icon = painterResource(DesignR.drawable.ic_arrow_back_ios_new),
                    contentDescription = stringResource(R.string.back),
                    onClick = actions.back
                )
            },
            trailing = {
                if (state.files.isNotEmpty()) {
                    JumpMenu(
                        files = state.files,
                        onJump = { path ->
                            val index = state.rows.indexOfFirst { it.key == "h:$path" }
                            if (index >= 0) scope.launch { listState.animateScrollToItem(index) }
                        }
                    )
                }
            }
        ) { ChangesTitle(state.added, state.removed) }
        Box(Modifier.weight(1f)) {
            PullToRefreshBox(
                isRefreshing = refreshing,
                onRefresh = actions.refresh,
                modifier = Modifier.fillMaxSize()
            ) {
                ReviewList(state, actions, listState)
                state.overlay?.let { ReviewOverlayContent(it, state.loadError, actions.retry) }
            }
            GitNoticeBanner(state.notice, Modifier.align(Alignment.TopCenter))
        }
    }
}

@Composable
private fun ReviewList(state: GitReviewUiState, actions: ReviewActions, listState: LazyListState) {
    val colors = rememberDiffColors()
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        items(state.rows, key = { it.key }, contentType = { it::class }) { row ->
            when (row) {
                is ReviewRow.ChangedHeader -> ChangedFilesHeader(row)

                ReviewRow.NoChanges -> NoChangesRow()

                is ReviewRow.FileHeader -> {
                    LaunchedEffect(row.path) { actions.fileAppeared(row.path) }
                    FileHeaderBar(row, actions)
                }

                is ReviewRow.Block -> if (row.block.kind == DiffBlockKind.Hunk) {
                    HunkSeparator(row.block)
                } else {
                    DiffCodeBlock(row.block, colors, Modifier.fillMaxWidth())
                }

                is ReviewRow.Note -> FileNoteRow(row.note, onRetry = {
                    actions.retryDiff(row.path)
                })

                is ReviewRow.ShowAll -> ShowAllRow(row, onClick = { actions.showAll(row.path) })

                ReviewRow.Commit -> CommitSection(state, actions)

                ReviewRow.CreatePullRequest -> CreatePullRequestRow(actions.createPullRequest)
            }
        }
    }
}

/** "Changes" over the +A −D across every parsed file, GitHub-style. */
@Composable
private fun ChangesTitle(added: Int, removed: Int) {
    val title = stringResource(R.string.git_review_title)
    if (added == 0 && removed == 0) return TopBarTitle(title = title)
    TopBarTitle(
        title = title,
        subtitleContent = {
            val style = LpmTheme.typography.footnote.copy(
                fontWeight = FontWeight.SemiBold,
                fontFeatureSettings = "tnum"
            )
            // A stack of its own on iOS, a little looser than a navigation subtitle.
            Row(
                modifier = Modifier.padding(top = 3.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    stringResource(R.string.git_review_added, added),
                    style = style,
                    color = LpmTheme.colors.green
                )
                Text(
                    stringResource(R.string.git_review_removed, removed),
                    style = style,
                    color = LpmTheme.colors.red
                )
            }
        }
    )
}
