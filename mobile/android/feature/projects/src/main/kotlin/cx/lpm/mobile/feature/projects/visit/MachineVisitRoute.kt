package cx.lpm.mobile.feature.projects.visit

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.NavKey
import cx.lpm.mobile.core.designsystem.R as DesignR
import cx.lpm.mobile.core.designsystem.component.ActivityIndicator
import cx.lpm.mobile.core.designsystem.component.EmptyState
import cx.lpm.mobile.core.designsystem.component.GlassIconButton
import cx.lpm.mobile.core.designsystem.component.LpmTopBar
import cx.lpm.mobile.core.designsystem.theme.LpmTheme
import cx.lpm.mobile.core.navigation.ProjectKey
import cx.lpm.mobile.feature.projects.ProjectsSlots
import cx.lpm.mobile.feature.projects.R
import cx.lpm.mobile.feature.projects.detail.ProjectDetailRoute

/**
 * A machine's project opened while the live Mac stays connected: every screen under
 * it talks to the visit's own session. A spinner until the machine lists its
 * projects, "Project unavailable" once it doesn't have this one or the visit ended.
 */
@Composable
fun MachineVisitRoute(
    key: ProjectKey,
    onNavigate: (NavKey) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    slots: ProjectsSlots = ProjectsSlots(),
    viewModel: MachineVisitViewModel =
        hiltViewModel<MachineVisitViewModel, MachineVisitViewModel.Factory>(
            creationCallback = { it.create(key) }
        )
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    // The screen's own first asks can go out before the visit's link is up.
    LaunchedEffect(state.ready) { if (state.ready) viewModel.reload() }
    when (state.content) {
        MachineVisitContent.Detail -> ProjectDetailRoute(
            key = key,
            onNavigate = onNavigate,
            onBack = onBack,
            modifier = modifier,
            slots = slots
        )

        MachineVisitContent.Loading -> VisitPlaceholder(loading = true, onBack, modifier)

        MachineVisitContent.Unavailable -> VisitPlaceholder(loading = false, onBack, modifier)
    }
}

@Composable
private fun VisitPlaceholder(loading: Boolean, onBack: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier
            .fillMaxSize()
            .background(LpmTheme.colors.systemBackground)
    ) {
        LpmTopBar(
            title = "",
            leading = {
                GlassIconButton(
                    icon = painterResource(DesignR.drawable.ic_arrow_back_ios_new),
                    contentDescription = stringResource(R.string.back),
                    onClick = onBack
                )
            }
        )
        Box(
            Modifier
                .weight(1f)
                .fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            if (loading) {
                ActivityIndicator()
            } else {
                EmptyState(
                    title = stringResource(R.string.visit_unavailable),
                    icon = painterResource(DesignR.drawable.ic_folder)
                )
            }
        }
    }
}
