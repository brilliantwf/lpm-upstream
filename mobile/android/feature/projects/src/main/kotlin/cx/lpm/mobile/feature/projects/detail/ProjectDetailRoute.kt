package cx.lpm.mobile.feature.projects.detail

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.NavKey
import cx.lpm.mobile.core.designsystem.component.AlertButton
import cx.lpm.mobile.core.designsystem.component.AlertRole
import cx.lpm.mobile.core.designsystem.component.AlertTextField
import cx.lpm.mobile.core.designsystem.component.LpmAlert
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
    var logsFor by remember { mutableStateOf<Service?>(null) }
    var refreshing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(key) { viewModel.load() }
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
            close = { viewModel.close(it.id) },
            toggleService = { viewModel.toggleService(it.name) },
            openLogs = { logsFor = it },
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
        RenameTerminalAlert(
            initial = term.label,
            onRename = { viewModel.rename(term.id, it) },
            onDismiss = { renaming = null }
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
private fun RenameTerminalAlert(
    initial: String,
    onRename: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf(initial) }
    LpmAlert(
        title = stringResource(R.string.tab_rename_title),
        textField = {
            AlertTextField(name, { name = it }, stringResource(R.string.tab_rename_field))
        },
        buttons = listOf(
            AlertButton(stringResource(R.string.cancel), AlertRole.Cancel),
            AlertButton(stringResource(R.string.tab_rename)) { onRename(name) }
        ),
        onDismissRequest = onDismiss
    )
}

private const val REFRESH_SETTLE_MS = 500L
