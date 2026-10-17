package cx.lpm.mobile.feature.git.branch

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cx.lpm.mobile.core.designsystem.R as DesignR
import cx.lpm.mobile.core.designsystem.component.ActivityIndicator
import cx.lpm.mobile.core.designsystem.component.AlertButton
import cx.lpm.mobile.core.designsystem.component.AlertRole
import cx.lpm.mobile.core.designsystem.component.AlertTextField
import cx.lpm.mobile.core.designsystem.component.CapsuleButton
import cx.lpm.mobile.core.designsystem.component.EmptyState
import cx.lpm.mobile.core.designsystem.component.GlassCapsuleButton
import cx.lpm.mobile.core.designsystem.component.GlassIconButton
import cx.lpm.mobile.core.designsystem.component.ListDefaults
import cx.lpm.mobile.core.designsystem.component.ListHeaderStyle
import cx.lpm.mobile.core.designsystem.component.ListSection
import cx.lpm.mobile.core.designsystem.component.LpmAlert
import cx.lpm.mobile.core.designsystem.theme.LpmTheme
import cx.lpm.mobile.core.model.GitBranch
import cx.lpm.mobile.core.navigation.GitBranchKey
import cx.lpm.mobile.feature.git.R
import cx.lpm.mobile.feature.git.ui.BarSpinner
import cx.lpm.mobile.feature.git.ui.SheetBar

/** The switch-branch sheet's content, wired to its ViewModel; it closes after a switch. */
@Composable
fun GitBranchRoute(
    key: GitBranchKey,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: GitBranchViewModel = hiltViewModel<GitBranchViewModel, GitBranchViewModel.Factory>(
        creationCallback = { it.create(key) }
    )
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var naming by remember { mutableStateOf(false) }
    val dismiss by rememberUpdatedState(onDismiss)
    LaunchedEffect(state.switched) { if (state.switched) dismiss() }
    GitBranchSheet(
        state = state,
        onDismiss = onDismiss,
        onNewBranch = { naming = true },
        onCheckout = viewModel::checkout,
        onRetry = viewModel::retry,
        modifier = modifier
    )
    if (naming) {
        NewBranchAlert(onCreate = viewModel::createBranch, onDismiss = { naming = false })
    }
    val error = state.error
    if (state.branches != null && error != null && !naming) {
        LpmAlert(
            title = stringResource(R.string.git_branch_switch_failed),
            message = error,
            buttons = listOf(AlertButton(stringResource(R.string.ok), AlertRole.Cancel)),
            onDismissRequest = viewModel::dismissError
        )
    }
}

/**
 * "Switch Branch" (iOS `GitBranchSheet`): Cancel and "+" over the local branches,
 * then remote-only ones; or the list's loading, failure or empty state.
 */
@Composable
fun GitBranchSheet(
    state: GitBranchUiState,
    onDismiss: () -> Unit,
    onNewBranch: () -> Unit,
    onCheckout: (GitBranch) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier.fillMaxSize()) {
        SheetBar(
            title = stringResource(R.string.git_branch_title),
            leading = {
                GlassCapsuleButton(text = stringResource(R.string.cancel), onClick = onDismiss)
            },
            trailing = {
                if (state.creating) {
                    BarSpinner()
                } else {
                    GlassIconButton(
                        icon = painterResource(DesignR.drawable.ic_add),
                        contentDescription = stringResource(R.string.git_branch_new),
                        onClick = onNewBranch,
                        iconSize = 30.dp
                    )
                }
            }
        )
        val branches = state.branches
        when {
            !branches.isNullOrEmpty() -> BranchList(state, onCheckout)

            branches == null && state.error != null -> Centered {
                EmptyState(
                    title = stringResource(R.string.git_branch_load_failed),
                    icon = painterResource(DesignR.drawable.ic_warning),
                    description = {
                        Text(
                            text = state.error,
                            style = LpmTheme.typography.body,
                            color = LpmTheme.colors.secondaryLabel,
                            textAlign = TextAlign.Center
                        )
                    },
                    actions = {
                        CapsuleButton(
                            text = stringResource(R.string.git_retry),
                            onClick = onRetry,
                            prominent = true,
                            textStyle = LpmTheme.typography.body
                        )
                    }
                )
            }

            branches == null -> Centered {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    ActivityIndicator()
                    Text(
                        text = stringResource(R.string.git_branch_loading),
                        style = LpmTheme.typography.footnote,
                        color = LpmTheme.colors.secondaryLabel
                    )
                }
            }

            else -> Centered {
                EmptyState(
                    title = stringResource(R.string.git_branch_none),
                    icon = painterResource(DesignR.drawable.ic_call_split)
                )
            }
        }
    }
}

@Composable
private fun BranchList(state: GitBranchUiState, onCheckout: (GitBranch) -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(top = ListDefaults.ContentTopPadding, bottom = 24.dp)
    ) {
        listOf(R.string.git_branch_local to state.local, R.string.git_branch_remote to state.remote)
            .filter { it.second.isNotEmpty() }
            .forEach { (header, branches) ->
                ListSection(header = stringResource(header), headerStyle = ListHeaderStyle.Title) {
                    branches.forEach { branch ->
                        BranchRow(
                            branch = branch,
                            current = state.isCurrent(branch),
                            checkingOut = state.checkingOut,
                            loading = state.loading,
                            onClick = { onCheckout(branch) }
                        )
                    }
                }
            }
    }
}

@Composable
private fun Centered(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { content() }
}

/** "New branch": a name, then Create checks it out off HEAD. */
@Composable
private fun NewBranchAlert(onCreate: (String) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf("") }
    LpmAlert(
        title = stringResource(R.string.git_branch_new),
        message = stringResource(R.string.git_branch_new_message),
        textField = {
            AlertTextField(
                value = name,
                onValueChange = { name = it },
                placeholder = stringResource(R.string.git_branch_new_placeholder),
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.None,
                    autoCorrectEnabled = false
                )
            )
        },
        buttons = listOf(
            AlertButton(stringResource(R.string.cancel), AlertRole.Cancel),
            AlertButton(stringResource(R.string.git_branch_create)) { onCreate(name) }
        ),
        onDismissRequest = onDismiss
    )
}
