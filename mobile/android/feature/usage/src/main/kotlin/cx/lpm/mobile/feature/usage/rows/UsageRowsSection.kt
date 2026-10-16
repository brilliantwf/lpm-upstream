package cx.lpm.mobile.feature.usage.rows

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation3.runtime.NavKey
import cx.lpm.mobile.core.designsystem.component.LinkButton
import cx.lpm.mobile.core.designsystem.component.ListSection
import cx.lpm.mobile.core.model.SessionKey
import cx.lpm.mobile.core.navigation.UsageKey
import cx.lpm.mobile.feature.usage.R

/**
 * The USAGE section of the projects list (no [project]) or of a project's screen,
 * wired to its ViewModel. While it's on screen it keeps the meters, today's spend and
 * the Claude accounts fresh; "Details" opens the Usage screen.
 */
@Composable
fun UsageRowsRoute(
    onNavigate: (NavKey) -> Unit,
    modifier: Modifier = Modifier,
    session: SessionKey = SessionKey.Live,
    project: String? = null,
    viewModel: UsageRowsViewModel = hiltViewModel<UsageRowsViewModel, UsageRowsViewModel.Factory>(
        creationCallback = { it.create(UsageRowsKey(session, project)) }
    )
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val haptics = LocalHapticFeedback.current
    LaunchedEffect(viewModel, lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) { viewModel.feed() }
    }
    UsageRowsSection(
        state = state,
        onDetails = { onNavigate(UsageKey(session)) },
        onSwitch = if (state.switches) {
            { account ->
                haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
                viewModel.switchTo(account)
            }
        } else {
            null
        },
        modifier = modifier
    )
}

/** The usage rows as a list section under a "Usage" header with "Details" beside it. */
@Composable
fun UsageRowsSection(
    state: UsageRowsUiState,
    onDetails: () -> Unit,
    onSwitch: ((String) -> Unit)?,
    modifier: Modifier = Modifier
) {
    if (state.rows.isEmpty()) return
    var expanded by rememberSaveable { mutableStateOf(listOf<String>()) }
    val alpha by animateFloatAsState(if (state.shown) 1f else 0f, tween(150), label = "shown")
    ListSection(
        modifier = modifier
            .alpha(alpha)
            .then(if (state.shown) Modifier else Modifier.clearAndSetSemantics {}),
        header = stringResource(R.string.rows_header),
        headerAction = {
            LinkButton(text = stringResource(R.string.rows_details), onClick = onDetails)
        }
    ) {
        state.rows.forEach { item ->
            val id = item.row.id
            key(id) {
                UsageRowView(
                    item = item,
                    inUse = state.inUse,
                    expanded = id in expanded,
                    now = state.now,
                    macNow = state.macNow,
                    onToggle = { expanded = if (id in expanded) expanded - id else expanded + id },
                    onSwitch = onSwitch
                )
            }
        }
    }
}
