package cx.lpm.mobile.feature.projects.detail

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.NavKey
import cx.lpm.mobile.core.designsystem.component.AlertButton
import cx.lpm.mobile.core.designsystem.component.AlertRole
import cx.lpm.mobile.core.designsystem.component.LpmConfirmationDialog
import cx.lpm.mobile.core.model.Service
import cx.lpm.mobile.core.model.TerminalInfo
import cx.lpm.mobile.core.navigation.ProjectKey
import cx.lpm.mobile.core.navigation.TerminalKey
import cx.lpm.mobile.core.navigation.YamlConfigKey
import cx.lpm.mobile.feature.projects.ProjectsSlots
import cx.lpm.mobile.feature.projects.R
import cx.lpm.mobile.feature.projects.menu.ProjectMenuToolbar
import cx.lpm.mobile.feature.projects.services.ServiceLogsSheet
import cx.lpm.mobile.feature.projects.ui.SessionAlertsHost
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** A project's screen wired to its ViewModel, the menu, navigation and its sheets. */
@Composable
fun ProjectDetailRoute(
    key: ProjectKey,
    onNavigate: (NavKey) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    slots: ProjectsSlots = ProjectsSlots(),
    viewModel: ProjectDetailViewModel =
        hiltViewModel<ProjectDetailViewModel, ProjectDetailViewModel.Factory>(
            creationCallback = { it.create(key) }
        )
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val alerts by viewModel.alerts.collectAsStateWithLifecycle()
    var renaming by remember { mutableStateOf<TerminalInfo?>(null) }
    var closing by remember { mutableStateOf<TerminalInfo?>(null) }
    var logsFor by remember { mutableStateOf<Service?>(null) }
    var refreshing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    LaunchedEffect(key) { viewModel.load() }
    // Back from a dropped link, the tabs are asked for again.
    var wasLive by remember { mutableStateOf(state.live) }
    LaunchedEffect(state.live) {
        if (state.live && !wasLive) viewModel.reloadAfterReconnect()
        wasLive = state.live
    }
    val openTerminal = { t: TerminalInfo ->
        onNavigate(TerminalKey(key.project, t.id, key.session))
    }

    ProjectDetailScreen(
        state = state,
        refreshing = refreshing,
        onRefresh = {
            scope.launch {
                refreshing = true
                viewModel.load()
                delay(REFRESH_SETTLE_MS)
                refreshing = false
            }
        },
        modifier = modifier,
        actions = ProjectDetailActions(
            back = onBack,
            openTerminal = openTerminal,
            newTerminal = viewModel::newTerminal,
            pin = { viewModel.pin(it.id) },
            rename = { renaming = it },
            // Whatever runs in the tab stops with it, so a close is confirmed first.
            close = {
                haptics.performHapticFeedback(HapticFeedbackType.Reject)
                closing = it
            },
            toggleService = { viewModel.toggleService(it.name) },
            restartService = { viewModel.restartService(it.name) },
            stopService = { viewModel.stopService(it.name) },
            openLogs = { logsFor = it },
            cancelQueued = viewModel::cancelQueued,
            retryQueued = viewModel::retryQueued,
            stopAll = viewModel::stopAll,
            editConfig = { onNavigate(YamlConfigKey(key.project, key.session)) },
            retryConfig = { scope.launch { viewModel.refreshProjects() } }
        ),
        menu = {
            ProjectMenuToolbar(
                project = key.project,
                session = key.session,
                onNavigate = onNavigate,
                onSpawnedTerminal = { t ->
                    viewModel.claimControl(t.id)
                    openTerminal(t)
                },
                slots = slots
            )
        },
        connectionStatusBar = { slots.connectionStatusBar(key.session) },
        belowServices = { slots.backgroundRuns(key.session, key.project) },
        usage = { slots.projectUsage(key.session, key.project) }
    )

    renaming?.let { term ->
        TabRenameAlert(
            term = term,
            onRename = { viewModel.rename(term.id, it) },
            onDismiss = { renaming = null }
        )
    }
    closing?.let { term ->
        CloseTabDialog(
            term = term,
            onClose = { viewModel.close(term.id) },
            onDismiss = { closing = null }
        )
    }
    logsFor?.let { service ->
        ServiceLogsSheet(
            project = key.project,
            service = service,
            session = key.session,
            onDismiss = { logsFor = null }
        )
    }
    SessionAlertsHost(
        alerts = alerts,
        onDismissError = viewModel::dismissError,
        onDismissNotice = viewModel::dismissNotice
    )
}

@Composable
private fun CloseTabDialog(term: TerminalInfo, onClose: () -> Unit, onDismiss: () -> Unit) {
    LpmConfirmationDialog(
        title = stringResource(R.string.tab_close_title, term.label),
        message = stringResource(R.string.tab_close_message),
        buttons = listOf(
            AlertButton(
                stringResource(R.string.tab_close_confirm),
                AlertRole.Destructive,
                onClick = onClose
            ),
            AlertButton(stringResource(R.string.cancel), AlertRole.Cancel)
        ),
        onDismissRequest = onDismiss
    )
}

private const val REFRESH_SETTLE_MS = 500L
