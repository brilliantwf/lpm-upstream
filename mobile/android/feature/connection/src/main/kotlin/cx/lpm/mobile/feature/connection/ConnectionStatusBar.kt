package cx.lpm.mobile.feature.connection

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cx.lpm.mobile.core.data.link.IssueAction
import cx.lpm.mobile.core.designsystem.theme.LpmTheme
import cx.lpm.mobile.core.model.SessionKey

/**
 * The bar over a screen for a problem a tap on this phone fixes, with that fix.
 * Connecting, and a Mac that's asleep or not answering, stay in the line under the
 * Mac's name. Nothing shows while [state] is null.
 */
@Composable
fun ConnectionStatusBar(
    state: IssueContentState?,
    onAction: (IssueAction) -> Unit,
    modifier: Modifier = Modifier
) {
    // Keeps the last issue on screen while the bar slides away.
    val last = remember { mutableStateOf(state) }
    SideEffect { if (state != null) last.value = state }
    val shown = state ?: last.value
    AnimatedVisibility(
        visible = state != null,
        modifier = modifier,
        enter = slideInVertically { -it } + fadeIn(),
        exit = slideOutVertically { -it } + fadeOut()
    ) {
        val colors = LpmTheme.colors
        val separator = colors.separator
        shown?.let {
            IssueContent(
                state = it,
                style = IssueStyle.Bar,
                onAction = onAction,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.systemBackground.copy(alpha = 0.94f))
                    .drawBehind {
                        drawLine(
                            separator,
                            Offset(0f, size.height),
                            Offset(size.width, size.height),
                            1f
                        )
                    }
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            )
        }
    }
}

/** [ConnectionStatusBar] for `session`'s link — the slot a screen puts over its list. */
@Composable
fun ConnectionStatusBarRoute(modifier: Modifier = Modifier, session: SessionKey = SessionKey.Live) {
    val viewModel = linkViewModel(session)
    val ui by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val issue = ui?.view?.barIssue
    ConnectionStatusBar(
        state = issue?.let { ui?.content(it, IssueStyle.Bar) },
        modifier = modifier,
        onAction = { action ->
            if (issue != null) context.performIssueAction(action, issue, viewModel::perform)
        }
    )
}
