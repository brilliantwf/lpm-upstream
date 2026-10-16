package cx.lpm.mobile.feature.activity

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import cx.lpm.mobile.core.designsystem.R as DesignR
import cx.lpm.mobile.core.designsystem.component.EmptyState
import cx.lpm.mobile.core.designsystem.component.SegmentedControl
import cx.lpm.mobile.core.designsystem.theme.LpmTheme

/** What the screen's own controls do; the list's rows have [ActivityListActions]. */
class ActivityActions(
    val back: () -> Unit = {},
    val search: (String) -> Unit = {},
    val beginSearch: () -> Unit = {},
    val endSearch: () -> Unit = {},
    val pickKind: (ActivityKindFilter) -> Unit = {},
    val toggleGrouped: () -> Unit = {},
    val refresh: () -> Unit = {},
    val list: ActivityListActions = ActivityListActions()
)

/**
 * Every agent, automation, and running service across the paired Mac's projects,
 * ordered by what is waiting on you. The phone's port of the desktop's Activity view:
 * the same rows, the same attention order, the same words.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActivityScreen(
    state: ActivityUiState,
    query: String,
    searching: Boolean,
    kind: ActivityKindFilter,
    now: Long,
    refreshing: Boolean,
    actions: ActivityActions,
    modifier: Modifier = Modifier,
    connectionStatusBar: @Composable () -> Unit = {}
) {
    val visible = remember(state.activity, query, kind) {
        filterActivity(state.activity, query, kind)
    }
    Column(
        modifier
            .fillMaxSize()
            .background(LpmTheme.colors.systemGroupedBackground)
    ) {
        ActivityTopBar(state, query, searching, actions)
        connectionStatusBar()
        SegmentedControl(
            options = ActivityKindFilter.entries.map { it.label },
            selectedIndex = kind.ordinal,
            onSelect = { actions.pickKind(ActivityKindFilter.entries[it]) },
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 10.dp)
        )
        PullToRefreshBox(
            isRefreshing = refreshing,
            onRefresh = actions.refresh,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            ActivityList(state, visible, now, actions.list)
            if (visible.isEmpty) {
                EmptyView(
                    state = state,
                    narrowed = query.isNotBlank() || kind != ActivityKindFilter.All,
                    modifier = Modifier.align(Alignment.Center)
                )
            }
        }
    }
}

// An empty list means "nothing is running" only when the Mac is actually answering;
// while it refuses this device there is nothing to report on.
@Composable
private fun EmptyView(state: ActivityUiState, narrowed: Boolean, modifier: Modifier = Modifier) {
    val macLabel = state.notConnectedTo
    val (title, icon, detail) = when {
        macLabel != null -> Triple(
            stringResource(R.string.activity_not_connected, macLabel),
            DesignR.drawable.ic_devices,
            stringResource(R.string.activity_not_connected_detail)
        )

        narrowed -> Triple(
            stringResource(R.string.activity_nothing_matches),
            DesignR.drawable.ic_search,
            stringResource(R.string.activity_nothing_matches_detail)
        )

        else -> Triple(
            stringResource(R.string.activity_nothing_running),
            DesignR.drawable.ic_bedtime,
            stringResource(R.string.activity_nothing_running_detail)
        )
    }
    EmptyState(
        title = title,
        icon = painterResource(icon),
        modifier = modifier,
        description = {
            Text(
                text = detail,
                style = LpmTheme.typography.body,
                color = LpmTheme.colors.secondaryLabel,
                textAlign = TextAlign.Center
            )
        }
    )
}
