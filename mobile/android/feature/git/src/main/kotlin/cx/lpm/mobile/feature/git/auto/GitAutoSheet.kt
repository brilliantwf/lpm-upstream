package cx.lpm.mobile.feature.git.auto

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cx.lpm.mobile.core.designsystem.R as DesignR
import cx.lpm.mobile.core.designsystem.component.AlertButton
import cx.lpm.mobile.core.designsystem.component.AlertRole
import cx.lpm.mobile.core.designsystem.component.GlassCapsuleButton
import cx.lpm.mobile.core.designsystem.component.ListDefaults
import cx.lpm.mobile.core.designsystem.component.ListRow
import cx.lpm.mobile.core.designsystem.component.ListSection
import cx.lpm.mobile.core.designsystem.component.LpmAlert
import cx.lpm.mobile.core.designsystem.theme.LpmTheme
import cx.lpm.mobile.core.model.GitAutoRun
import cx.lpm.mobile.core.navigation.GitAutoKey
import cx.lpm.mobile.feature.git.R
import cx.lpm.mobile.feature.git.ui.SheetBar

/**
 * The run sheet wired to its ViewModel: it starts the run the menu asked for once
 * (the Mac answers a second start with the run already going), and closes after
 * "Switch to <base> and pull".
 */
@Composable
fun GitAutoRoute(
    key: GitAutoKey,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: GitAutoViewModel = hiltViewModel<GitAutoViewModel, GitAutoViewModel.Factory>(
        creationCallback = { it.create(key) }
    )
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var started by rememberSaveable { mutableStateOf(false) }
    val haptics = LocalHapticFeedback.current
    LaunchedEffect(Unit) {
        if (!started) viewModel.start()
        started = true
    }
    DisposableEffect(viewModel) {
        viewModel.appeared()
        onDispose { viewModel.disappeared() }
    }
    val dismiss by rememberUpdatedState(onDismiss)
    LaunchedEffect(state.switched) { if (state.switched) dismiss() }
    LaunchedEffect(state.run?.phase) {
        when (state.run?.phase) {
            GitAutoRun.Phase.Done -> haptics.performHapticFeedback(HapticFeedbackType.Confirm)
            GitAutoRun.Phase.Failed -> haptics.performHapticFeedback(HapticFeedbackType.Reject)
            else -> Unit
        }
    }
    GitAutoSheet(
        state = state,
        onDismiss = onDismiss,
        actions = GitAutoActions(
            stop = viewModel::stop,
            retry = viewModel::retry,
            switchToBase = viewModel::switchToBase
        ),
        modifier = modifier
    )
    state.switchError?.let { error ->
        LpmAlert(
            title = stringResource(R.string.git_branch_switch_failed),
            message = error,
            buttons = listOf(AlertButton(stringResource(R.string.ok), AlertRole.Cancel)),
            onDismissRequest = viewModel::dismissSwitchError
        )
    }
}

class GitAutoActions(
    val stop: () -> Unit = {},
    val retry: () -> Unit = {},
    val switchToBase: () -> Unit = {}
)

/**
 * A run's progress and result (iOS `GitAutoSheet`, the desktop's AutoPRModal): the
 * step list, then what it made — the commit, or the pull request with Open and
 * "Switch to <base> and pull" — or why it failed.
 */
@Composable
fun GitAutoSheet(
    state: GitAutoUiState,
    onDismiss: () -> Unit,
    actions: GitAutoActions,
    modifier: Modifier = Modifier
) {
    val run = state.run
    Column(modifier.fillMaxSize()) {
        SheetBar(
            title = run?.kind?.title.orEmpty(),
            leading = {
                GlassCapsuleButton(
                    text = stringResource(
                        if (run?.running ==
                            true
                        ) {
                            R.string.git_auto_close
                        } else {
                            R.string.done
                        }
                    ),
                    onClick = onDismiss,
                    enabled = !state.switching
                )
            },
            trailing = { if (run != null) TrailingAction(run, actions) }
        )
        if (run == null) return@Column
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(top = ListDefaults.ContentTopPadding + HEADERLESS_TOP, bottom = 24.dp)
        ) {
            if (run.phase ==
                GitAutoRun.Phase.Done
            ) {
                DoneSection(run, state.switching, actions.switchToBase)
            }
            ListSection(footer = stepsFooter(run)) {
                run.steps.forEach { GitAutoStepRow(it) }
            }
            if (run.phase == GitAutoRun.Phase.Failed) FailedSection(run)
        }
    }
}

@Composable
private fun TrailingAction(run: GitAutoRun, actions: GitAutoActions) {
    when {
        run.running -> GlassCapsuleButton(
            text = stringResource(
                if (run.canceling) R.string.git_auto_stopping else R.string.git_auto_stop
            ),
            onClick = actions.stop,
            enabled = !run.canceling,
            color = LpmTheme.colors.red
        )

        run.phase == GitAutoRun.Phase.Failed || run.phase == GitAutoRun.Phase.Canceled ->
            GlassCapsuleButton(
                text = stringResource(R.string.git_retry),
                onClick = actions.retry,
                textStyle = LpmTheme.typography.headline
            )
    }
}

@Composable
private fun stepsFooter(run: GitAutoRun): String? = when (run.phase) {
    GitAutoRun.Phase.Running -> run.kind.subtitle
    GitAutoRun.Phase.Canceled -> stringResource(R.string.git_auto_canceled)
    else -> null
}

/** What went wrong, and the pull request if one was opened anyway. */
@Composable
private fun FailedSection(run: GitAutoRun) {
    ListSection {
        ListRow(
            leading = {
                Icon(
                    painter = painterResource(DesignR.drawable.ic_error_fill),
                    contentDescription = null,
                    tint = LpmTheme.colors.red,
                    modifier = Modifier.size(22.dp)
                )
            }
        ) {
            SelectionContainer {
                Text(
                    text = run.error.ifEmpty { stringResource(R.string.git_auto_failed) },
                    style = LpmTheme.typography.body
                )
            }
        }
        if (run.url.isNotEmpty()) OpenLinkRow(stringResource(R.string.git_auto_open_pr), run.url)
    }
}

/** iOS sets a list's first section lower when it has no header, as every section here. */
private val HEADERLESS_TOP = 16.dp
