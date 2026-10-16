package cx.lpm.mobile.feature.connection

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cx.lpm.mobile.core.data.link.ConnectionIssue
import cx.lpm.mobile.core.data.link.IssueAction
import cx.lpm.mobile.core.designsystem.theme.LpmTheme
import cx.lpm.mobile.core.model.SessionKey

/** An issue as a card, for screens that show it in place of their content. */
@Composable
fun IssueCard(
    state: IssueContentState,
    onAction: (IssueAction) -> Unit,
    modifier: Modifier = Modifier,
    hidesDetails: Boolean = false
) {
    IssueContent(
        state = state,
        style = IssueStyle.Card,
        onAction = onAction,
        hidesDetails = hidesDetails,
        modifier = modifier
            .fillMaxWidth()
            .background(LpmTheme.colors.secondarySystemGroupedBackground, RoundedCornerShape(18.dp))
            .padding(16.dp)
    )
}

/**
 * [IssueCard] for `issue` on `session`'s link. [handle] gets each tap first and
 * returns whether it dealt with it (the pairing screen's Try again); the rest go to
 * the session, or open the phone's settings and the Tailscale admin console.
 */
@Composable
fun IssueCardRoute(
    issue: ConnectionIssue,
    modifier: Modifier = Modifier,
    session: SessionKey = SessionKey.Live,
    hidesDetails: Boolean = false,
    handle: (IssueAction) -> Boolean = { false }
) {
    val viewModel = linkViewModel(session)
    val ui by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val state = ui ?: return
    IssueCard(
        state = state.content(issue, IssueStyle.Card),
        hidesDetails = hidesDetails,
        modifier = modifier,
        onAction = { action ->
            if (!handle(action)) context.performIssueAction(action, issue, viewModel::perform)
        }
    )
}

@Composable
internal fun linkViewModel(session: SessionKey): LinkViewModel =
    hiltViewModel<LinkViewModel, LinkViewModel.Factory>(key = "link-$session") {
        it.create(session)
    }
