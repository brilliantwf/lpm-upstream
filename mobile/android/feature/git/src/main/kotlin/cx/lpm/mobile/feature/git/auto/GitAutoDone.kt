package cx.lpm.mobile.feature.git.auto

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import cx.lpm.mobile.core.designsystem.R as DesignR
import cx.lpm.mobile.core.designsystem.component.ActivityIndicator
import cx.lpm.mobile.core.designsystem.component.ActivityIndicatorSize
import cx.lpm.mobile.core.designsystem.component.ListRow
import cx.lpm.mobile.core.designsystem.component.ListSection
import cx.lpm.mobile.core.designsystem.theme.LpmTheme
import cx.lpm.mobile.core.model.GitAutoKind
import cx.lpm.mobile.core.model.GitAutoRun
import cx.lpm.mobile.feature.git.R

/**
 * A finished run: a green check over what happened, the commit message or the
 * branch → base, and for a pull request Open and "Switch to <base> and pull".
 */
@Composable
internal fun DoneSection(run: GitAutoRun, switching: Boolean, onSwitch: () -> Unit) {
    val colors = LpmTheme.colors
    ListSection {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 21.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                painter = painterResource(DesignR.drawable.ic_check_circle_fill),
                contentDescription = null,
                tint = colors.green,
                modifier = Modifier.size(45.dp)
            )
            Text(
                text = stringResource(headline(run)),
                style = LpmTheme.typography.headline,
                textAlign = TextAlign.Center
            )
            val mono = LpmTheme.typography.footnote.copy(fontFamily = FontFamily.Monospace)
            if (run.kind == GitAutoKind.Pr && run.branch.isNotEmpty() && run.base.isNotEmpty()) {
                Text(
                    text = stringResource(R.string.git_auto_branch_to_base, run.branch, run.base),
                    style = mono,
                    color = colors.secondaryLabel,
                    textAlign = TextAlign.Center
                )
            } else if (run.message.isNotEmpty()) {
                SelectionContainer {
                    Text(
                        text = run.message,
                        style = mono,
                        color = colors.secondaryLabel,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
        if (run.kind == GitAutoKind.Pr) {
            if (run.url.isNotEmpty()) {
                OpenLinkRow(
                    stringResource(R.string.git_auto_open_github),
                    run.url,
                    enabled = !switching
                )
            }
            if (run.base.isNotEmpty()) SwitchBaseRow(run.base, switching, onSwitch)
        }
    }
}

@Composable
private fun SwitchBaseRow(base: String, switching: Boolean, onClick: () -> Unit) {
    val tint = if (switching) LpmTheme.colors.tertiaryLabel else LpmTheme.colors.tint
    ListRow(
        onClick = onClick,
        enabled = !switching,
        leading = {
            Icon(
                painter = painterResource(DesignR.drawable.ic_call_split),
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(22.dp)
            )
        },
        trailing = { if (switching) ActivityIndicator(size = ActivityIndicatorSize.Small) }
    ) {
        Text(
            text = stringResource(
                if (switching) R.string.git_auto_switching_base else R.string.git_auto_switch_base,
                base
            ),
            style = LpmTheme.typography.body,
            color = tint
        )
    }
}

/** A row opening [url] in the browser. */
@Composable
internal fun OpenLinkRow(title: String, url: String, enabled: Boolean = true) {
    val uriHandler = LocalUriHandler.current
    val tint = if (enabled) LpmTheme.colors.tint else LpmTheme.colors.tertiaryLabel
    ListRow(
        onClick = { runCatching { uriHandler.openUri(url) } },
        enabled = enabled,
        leading = {
            Icon(
                painter = painterResource(DesignR.drawable.ic_explore),
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(22.dp)
            )
        }
    ) {
        Text(text = title, style = LpmTheme.typography.body, color = tint)
    }
}

private fun headline(run: GitAutoRun): Int = when (run.kind) {
    GitAutoKind.Commit -> R.string.git_auto_committed

    GitAutoKind.CommitPush -> if (run.rebased) {
        R.string.git_auto_committed_rebased_pushed
    } else {
        R.string.git_auto_committed_pushed
    }

    GitAutoKind.Pr -> R.string.git_auto_pr_created
}
