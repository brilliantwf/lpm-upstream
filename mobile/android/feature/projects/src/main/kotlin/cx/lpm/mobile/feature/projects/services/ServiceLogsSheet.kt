package cx.lpm.mobile.feature.projects.services

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
import androidx.compose.runtime.DisposableEffect
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
import cx.lpm.mobile.core.model.Service
import cx.lpm.mobile.core.model.SessionKey
import cx.lpm.mobile.core.navigation.PresentationScope
import cx.lpm.mobile.feature.projects.R

/**
 * A service's recent pane output while it runs, the phone's take on the desktop's
 * service pane, with its Start/Stop in the bar. Always dark, like a terminal.
 */
@Composable
fun ServiceLogsSheet(
    project: String,
    service: Service,
    session: SessionKey,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    PresentationScope { ServiceLogsContent(project, service, session, onDismiss, modifier) }
}

@Composable
private fun ServiceLogsContent(
    project: String,
    service: Service,
    session: SessionKey,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ServiceLogsViewModel =
        hiltViewModel<ServiceLogsViewModel, ServiceLogsViewModel.Factory>(
            creationCallback = { it.create(project, service.name, session) }
        )
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val sheet = rememberModalSheetState()
    LaunchedEffect(viewModel) { viewModel.follow() }
    DisposableEffect(viewModel) { onDispose { viewModel.consume() } }
    LpmTheme(darkTheme = true) {
        ModalSheet(
            onDismissRequest = onDismiss,
            modifier = modifier,
            fullHeight = false,
            state = sheet,
            floating = true
        ) {
            Column(Modifier.fillMaxHeight(SHEET_HEIGHT)) {
                LogsBar(
                    service.name,
                    state,
                    onClose = { sheet.dismiss(onDismiss) },
                    onToggle = viewModel::toggle
                )
                StatusLine(service, state)
                LogsText(state, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun LogsBar(
    title: String,
    state: ServiceLogsUiState,
    onClose: () -> Unit,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp)
            .height(44.dp)
    ) {
        GlassCapsuleButton(
            text = stringResource(R.string.logs_close),
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
        Box(Modifier.align(Alignment.CenterEnd)) {
            if (state.pending) {
                ActivityIndicator(size = ActivityIndicatorSize.Small)
            } else {
                GlassCapsuleButton(
                    text = stringResource(
                        if (state.running) R.string.logs_stop else R.string.logs_start
                    ),
                    onClick = onToggle,
                    textStyle = LpmTheme.typography.headline
                )
            }
        }
    }
}

/** Whether the service runs (or is on its way), its port and a spinner while toggling. */
@Composable
private fun StatusLine(service: Service, state: ServiceLogsUiState, modifier: Modifier = Modifier) {
    val colors = LpmTheme.colors
    val (dot, text) = when {
        state.pending && state.running -> colors.blue to R.string.logs_stopping
        state.pending -> colors.blue to R.string.logs_starting
        state.running -> colors.green to R.string.logs_running
        else -> colors.gray3 to R.string.logs_stopped
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.secondarySystemBackground)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        StatusDot(dot)
        Text(
            text = stringResource(text),
            style = LpmTheme.typography.subheadline.copy(fontWeight = FontWeight.Medium),
            maxLines = 1
        )
        if (service.port > 0) {
            Text(
                text = ":${service.port}",
                style = LpmTheme.typography.subheadline.copy(fontFeatureSettings = "tnum"),
                color = colors.secondaryLabel
            )
        }
        Box(Modifier.weight(1f))
        if (state.pending) ActivityIndicator(size = ActivityIndicatorSize.Small)
    }
}

/** The output, kept scrolled to its end as it grows, or why there's none. */
@Composable
private fun LogsText(state: ServiceLogsUiState, modifier: Modifier = Modifier) {
    val scroll = rememberScrollState()
    LaunchedEffect(state.text) { scroll.scrollTo(scroll.maxValue) }
    val secondary = LpmTheme.colors.secondaryLabel
    Column(
        modifier
            .fillMaxWidth()
            .verticalScroll(scroll)
            .padding(16.dp)
    ) {
        when {
            state.text.isEmpty() && state.error != null ->
                Text(state.error, style = LpmTheme.typography.body, color = secondary)

            state.text.isEmpty() -> Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (state.running) ActivityIndicator(size = ActivityIndicatorSize.Small)
                Text(
                    text = stringResource(
                        if (state.running) R.string.logs_waiting else R.string.logs_not_running
                    ),
                    style = LpmTheme.typography.body,
                    color = secondary
                )
            }

            else -> SelectionContainer {
                Text(
                    text = state.text,
                    style = LpmTheme.typography.caption1.copy(fontFamily = FontFamily.Monospace)
                )
            }
        }
    }
}

/** iOS opens it at the medium detent. */
private const val SHEET_HEIGHT = 0.55f
