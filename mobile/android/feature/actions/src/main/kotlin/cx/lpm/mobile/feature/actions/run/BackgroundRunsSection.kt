package cx.lpm.mobile.feature.actions.run

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cx.lpm.mobile.core.designsystem.R
import cx.lpm.mobile.core.designsystem.component.ActivityIndicator
import cx.lpm.mobile.core.designsystem.component.ActivityIndicatorSize
import cx.lpm.mobile.core.designsystem.component.DisclosureChevron
import cx.lpm.mobile.core.designsystem.component.ListLeadingSlot
import cx.lpm.mobile.core.designsystem.component.ListRow
import cx.lpm.mobile.core.designsystem.component.ListSection
import cx.lpm.mobile.core.designsystem.component.ListSectionSpacing
import cx.lpm.mobile.core.designsystem.component.StatusDot
import cx.lpm.mobile.core.designsystem.theme.LpmTheme
import cx.lpm.mobile.core.model.BackgroundRunInfo
import cx.lpm.mobile.core.model.SessionKey
import cx.lpm.mobile.feature.actions.R as ActionsR

/**
 * A project's "Background runs" section, under its services: each run with a live
 * status, tapped to reopen its output. Nothing shows until there's a run.
 */
@Composable
fun BackgroundRunsSection(
    session: SessionKey,
    project: String,
    viewModel: BackgroundRunsViewModel =
        hiltViewModel<BackgroundRunsViewModel, BackgroundRunsViewModel.Factory>(
            key = "background-runs:$session:$project",
            creationCallback = { it.create(project, session) }
        )
) {
    val rows by viewModel.uiState.collectAsStateWithLifecycle()
    // The open run stays up even once the Mac reaps it: the sheet says so.
    var openId by rememberSaveable { mutableStateOf<String?>(null) }
    var openLabel by rememberSaveable { mutableStateOf("") }
    LaunchedEffect(viewModel) { viewModel.keepCurrent() }
    BackgroundRunsList(rows) {
        openId = it.runId
        openLabel = it.label
    }
    openId?.let { runId ->
        BackgroundRunSheet(
            project = project,
            runId = runId,
            label = openLabel,
            session = session,
            onDismiss = { openId = null }
        )
    }
}

@Composable
internal fun BackgroundRunsList(rows: List<BackgroundRunRow>, onOpen: (BackgroundRunInfo) -> Unit) {
    if (rows.isEmpty()) return
    ListSection(
        header = stringResource(ActionsR.string.actions_runs_header),
        spacing = ListSectionSpacing.Compact
    ) {
        rows.forEach { row ->
            key(row.run.runId) { RunRow(row, onClick = { onOpen(row.run) }) }
        }
    }
}

/** The action's label over a status dot and line; a spinner while it runs. */
@Composable
private fun RunRow(row: BackgroundRunRow, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = LpmTheme.colors
    ListRow(
        modifier = modifier,
        onClick = onClick,
        verticalPadding = 7.dp,
        leadingSlot = ListLeadingSlot(width = 24.dp, spacing = 12.dp),
        leading = {
            Icon(
                painter = painterResource(R.drawable.ic_bolt),
                contentDescription = null,
                tint = colors.tint,
                modifier = Modifier.size(22.dp)
            )
        },
        trailing = {
            if (row.running) {
                ActivityIndicator(size = ActivityIndicatorSize.Small)
            } else {
                DisclosureChevron()
            }
        }
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                text = row.run.label,
                style = LpmTheme.typography.body.copy(fontWeight = FontWeight.Medium),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                StatusDot(row.status.color(), size = 6.dp)
                Text(
                    text = row.status.rowText(),
                    style = LpmTheme.typography.caption1,
                    color = colors.secondaryLabel,
                    maxLines = 1
                )
            }
        }
    }
}
