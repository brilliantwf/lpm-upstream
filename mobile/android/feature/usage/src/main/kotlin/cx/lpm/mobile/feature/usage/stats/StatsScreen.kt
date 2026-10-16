package cx.lpm.mobile.feature.usage.stats

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cx.lpm.mobile.core.designsystem.component.SegmentedControl
import cx.lpm.mobile.core.model.AgentStats
import cx.lpm.mobile.core.navigation.StatsKey
import cx.lpm.mobile.feature.usage.R
import cx.lpm.mobile.feature.usage.ui.ErrorCard
import cx.lpm.mobile.feature.usage.ui.UsageScaffold

/** The Stats screen wired to its ViewModel: it loads as it opens and lets go as it closes. */
@Composable
fun StatsRoute(
    key: StatsKey,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: StatsViewModel = hiltViewModel<StatsViewModel, StatsViewModel.Factory>(
        creationCallback = { it.create(key) }
    )
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    DisposableEffect(viewModel) {
        viewModel.open()
        onDispose { viewModel.close() }
    }
    StatsScreen(
        state = state,
        onBack = onBack,
        onRefresh = viewModel::refresh,
        onSelect = viewModel::select,
        onRetry = viewModel::retry,
        modifier = modifier
    )
}

private val Periods = listOf(1, 7, 30, 0)

/**
 * Local agent token usage on the Mac, as the desktop Stats page shows it: a period
 * picker over four summary tiles, the daily activity chart, the provider and project
 * breakdowns, recent sessions and a privacy note.
 */
@Composable
fun StatsScreen(
    state: StatsUiState,
    onBack: () -> Unit,
    onRefresh: suspend () -> Unit,
    onSelect: (Int) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    UsageScaffold(
        title = stringResource(R.string.stats_title),
        onBack = onBack,
        onRefresh = onRefresh,
        modifier = modifier
    ) {
        SegmentedControl(
            options = listOf(
                stringResource(R.string.stats_period_today),
                stringResource(R.string.stats_period_7),
                stringResource(R.string.stats_period_30),
                stringResource(R.string.stats_period_all)
            ),
            selectedIndex = Periods.indexOf(state.days),
            onSelect = { onSelect(Periods[it]) }
        )
        when (val body = state.body) {
            StatsBody.Loading -> StatsLoadingSkeleton()

            is StatsBody.Failed -> ErrorCard(
                title = stringResource(R.string.stats_error_title),
                message = body.message,
                onRetry = onRetry
            )

            is StatsBody.Loaded -> {
                val alpha by animateFloatAsState(
                    if (body.dimmed) 0.6f else 1f,
                    tween(200),
                    label = "dim"
                )
                LoadedStats(body.stats, state.days, state.now, Modifier.alpha(alpha))
            }
        }
    }
}

@Composable
private fun LoadedStats(stats: AgentStats, days: Int, now: Long, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        if (stats.totals.totalTokens == 0L) {
            StatsEmptyCard()
        } else {
            LoadedCards(stats, days, now)
        }
        StatsFooter(totalFiles = stats.sources.sumOf { it.files })
    }
}

@Composable
private fun ColumnScope.LoadedCards(stats: AgentStats, days: Int, now: Long) {
    StatsSummaryTiles(stats, days)
    val chart = chartDays(stats, days)
    if (chart.isNotEmpty()) DailyActivityCard(chart)
    ProviderBreakdownCard(stats.providers, stats.totals.totalTokens)
    TopProjectsCard(stats.projects, days)
    RecentSessionsCard(stats.recentSessions, days, now)
}
