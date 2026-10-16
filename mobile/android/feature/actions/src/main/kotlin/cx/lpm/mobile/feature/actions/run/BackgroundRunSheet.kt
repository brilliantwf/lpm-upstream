package cx.lpm.mobile.feature.actions.run

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cx.lpm.mobile.core.designsystem.component.ActivityIndicator
import cx.lpm.mobile.core.designsystem.component.ActivityIndicatorSize
import cx.lpm.mobile.core.designsystem.component.GlassCapsuleButton
import cx.lpm.mobile.core.designsystem.component.ModalSheet
import cx.lpm.mobile.core.designsystem.component.StatusDot
import cx.lpm.mobile.core.designsystem.component.rememberModalSheetState
import cx.lpm.mobile.core.designsystem.theme.LpmTheme
import cx.lpm.mobile.core.model.SessionKey
import cx.lpm.mobile.core.navigation.PresentationScope
import cx.lpm.mobile.feature.actions.R

/**
 * A headless background run's live output (iOS `BackgroundRunSheet`, the desktop's
 * background-run toast): the log as it streams, a status line, and Stop while it
 * runs. Dark, at the medium detent.
 */
@Composable
fun BackgroundRunSheet(
    project: String,
    runId: String,
    label: String,
    session: SessionKey,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    PresentationScope { RunSheetContent(project, runId, label, session, onDismiss, modifier) }
}

@Composable
private fun RunSheetContent(
    project: String,
    runId: String,
    label: String,
    session: SessionKey,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: BackgroundRunViewModel =
        hiltViewModel<BackgroundRunViewModel, BackgroundRunViewModel.Factory>(
            creationCallback = { it.create(project, runId, session) }
        )
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val sheet = rememberModalSheetState()
    LaunchedEffect(viewModel) { viewModel.follow() }
    LpmTheme(darkTheme = true) {
        ModalSheet(
            onDismissRequest = onDismiss,
            modifier = modifier,
            fullHeight = false,
            state = sheet,
            floating = true,
            plain = true
        ) {
            Column(Modifier.fillMaxHeight(MEDIUM_DETENT)) {
                // The status line's bar material reaches up behind the top bar.
                Column(Modifier.background(LpmTheme.colors.secondarySystemBackground)) {
                    RunBar(
                        title = label,
                        running = state.running,
                        onClose = { sheet.dismiss(onDismiss) },
                        onStop = viewModel::stop
                    )
                    StatusLine(state)
                }
                RunOutput(state, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun RunBar(
    title: String,
    running: Boolean,
    onClose: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp)
            .height(44.dp)
    ) {
        GlassCapsuleButton(
            text = stringResource(R.string.actions_run_close),
            onClick = onClose,
            modifier = Modifier.align(Alignment.CenterStart)
        )
        Text(
            text = title,
            style = LpmTheme.typography.headline,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .align(Alignment.Center)
                .padding(horizontal = 96.dp)
        )
        if (running) {
            GlassCapsuleButton(
                text = stringResource(R.string.actions_run_stop),
                onClick = onStop,
                textStyle = LpmTheme.typography.headline,
                color = LpmTheme.colors.red,
                modifier = Modifier.align(Alignment.CenterEnd)
            )
        }
    }
}

/** A dot in the run's color, where it stands, and a spinner while it runs. */
@Composable
private fun StatusLine(state: BackgroundRunUiState, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        StatusDot(state.status.color())
        Text(
            text = state.status.text(),
            style = LpmTheme.typography.subheadline.copy(fontWeight = FontWeight.Medium),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        if (state.running) ActivityIndicator(size = ActivityIndicatorSize.Small)
    }
}

/** The output, kept scrolled to its end as it grows. */
@Composable
private fun RunOutput(state: BackgroundRunUiState, modifier: Modifier = Modifier) {
    val scroll = rememberScrollState()
    LaunchedEffect(state.text) { scroll.scrollTo(scroll.maxValue) }
    Column(
        modifier
            .fillMaxWidth()
            .verticalScroll(scroll)
            .padding(16.dp)
    ) {
        if (state.text.isEmpty()) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (state.running) ActivityIndicator(size = ActivityIndicatorSize.Small)
                Text(
                    text = stringResource(
                        if (state.running) {
                            R.string.actions_run_waiting
                        } else {
                            R.string.actions_run_no_output
                        }
                    ),
                    style = LpmTheme.typography.body,
                    color = LpmTheme.colors.secondaryLabel
                )
            }
        } else {
            SelectionContainer {
                Text(
                    text = state.text,
                    style = LpmTheme.typography.caption1.copy(fontFamily = FontFamily.Monospace)
                )
            }
        }
    }
}
