package cx.lpm.mobile.feature.projects.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import cx.lpm.mobile.core.designsystem.R
import cx.lpm.mobile.core.designsystem.component.CapsuleButton
import cx.lpm.mobile.core.designsystem.component.EmptyState
import cx.lpm.mobile.core.designsystem.component.LinkButton
import cx.lpm.mobile.core.designsystem.component.ListRow
import cx.lpm.mobile.core.designsystem.component.Shimmer
import cx.lpm.mobile.core.designsystem.component.SkeletonBar
import cx.lpm.mobile.core.designsystem.theme.LpmTheme
import cx.lpm.mobile.feature.projects.R as ProjectsR

/** Ghost of a terminal row, while the tab list loads or a new terminal is on its way. */
@Composable
fun TerminalRowSkeleton(modifier: Modifier = Modifier) {
    ListRow(modifier = modifier.clearAndSetSemantics {}) {
        Shimmer { SkeletonBar(132.dp) }
    }
}

/** A project with nothing to list: the way to open its first terminal. */
@Composable
fun EmptyTerminalsView(onNew: () -> Unit, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        EmptyState(
            title = stringResource(ProjectsR.string.detail_no_terminals),
            icon = painterResource(R.drawable.ic_terminal),
            description = {
                Text(
                    text = stringResource(ProjectsR.string.detail_no_terminals_message),
                    style = LpmTheme.typography.body,
                    color = LpmTheme.colors.secondaryLabel,
                    textAlign = TextAlign.Center
                )
            },
            actions = {
                CapsuleButton(
                    text = stringResource(ProjectsR.string.detail_new_terminal),
                    onClick = onNew,
                    prominent = true,
                    textStyle = LpmTheme.typography.body,
                    leading = {
                        Icon(
                            painter = painterResource(R.drawable.ic_add),
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                )
            }
        )
    }
}

/**
 * The Mac can't read the project's YAML: why, and the raw file — the one editor that
 * can fix it — as the desktop offers Edit Config and Retry.
 */
@Composable
fun ProjectConfigErrorView(
    message: String,
    onEdit: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier
            .fillMaxSize()
            .background(LpmTheme.colors.systemGroupedBackground),
        contentAlignment = Alignment.Center
    ) {
        EmptyState(
            title = stringResource(ProjectsR.string.detail_invalid_config),
            icon = painterResource(R.drawable.ic_error),
            iconTint = LpmTheme.colors.red,
            description = {
                Text(
                    text = message,
                    style = LpmTheme.typography.footnote.copy(fontFamily = FontFamily.Monospace),
                    color = LpmTheme.colors.red,
                    textAlign = TextAlign.Center
                )
            },
            actions = {
                CapsuleButton(
                    text = stringResource(ProjectsR.string.detail_edit_config),
                    onClick = onEdit,
                    prominent = true,
                    textStyle = LpmTheme.typography.body
                )
                LinkButton(
                    text = stringResource(ProjectsR.string.detail_retry),
                    onClick = onRetry,
                    textStyle = LpmTheme.typography.body
                )
            }
        )
    }
}
