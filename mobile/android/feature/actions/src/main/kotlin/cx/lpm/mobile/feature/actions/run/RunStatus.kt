package cx.lpm.mobile.feature.actions.run

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import cx.lpm.mobile.core.data.actions.ActionsState
import cx.lpm.mobile.core.designsystem.theme.LpmTheme
import cx.lpm.mobile.feature.actions.R

/** Where a background run stands, as its status dot and line show it. */
sealed interface RunStatus {
    /** The Mac refused to start it. */
    data class NotStarted(val error: String) : RunStatus

    /** Started, not polled yet. */
    data object Starting : RunStatus

    data object Running : RunStatus

    /** The Mac reaped it: nothing more will come. */
    data object Gone : RunStatus

    data object Stopped : RunStatus

    data class Failed(val error: String) : RunStatus

    data object Done : RunStatus

    companion object {
        fun of(state: ActionsState, runId: String): RunStatus {
            state.startErrors[runId]?.let { return NotStarted(it) }
            val reaped = runId !in state.runs
            val snapshot = state.outputs[runId]
            return when {
                snapshot == null || snapshot.running -> when {
                    reaped -> Gone
                    snapshot == null -> Starting
                    else -> Running
                }

                snapshot.error == CANCELLED -> Stopped

                snapshot.success -> Done

                else -> Failed(snapshot.error)
            }
        }

        private const val CANCELLED = "cancelled"
    }
}

@Composable
internal fun RunStatus.color(): Color {
    val colors = LpmTheme.colors
    return when (this) {
        is RunStatus.NotStarted, is RunStatus.Failed -> colors.red
        RunStatus.Starting, RunStatus.Running -> colors.blue
        RunStatus.Gone -> colors.secondaryLabel
        RunStatus.Stopped -> colors.orange
        RunStatus.Done -> colors.green
    }
}

/** The run sheet's status line. */
@Composable
internal fun RunStatus.text(): String = when (this) {
    is RunStatus.NotStarted -> error
    RunStatus.Starting -> stringResource(R.string.actions_run_starting)
    RunStatus.Running -> stringResource(R.string.actions_run_running)
    RunStatus.Gone -> stringResource(R.string.actions_run_gone)
    RunStatus.Stopped -> stringResource(R.string.actions_run_stopped)
    is RunStatus.Failed -> error.ifEmpty { stringResource(R.string.actions_run_failed) }
    RunStatus.Done -> stringResource(R.string.actions_run_done)
}

/** A background-runs row's shorter status: a refused start, or a plain "Failed". */
@Composable
internal fun RunStatus.rowText(): String = when (this) {
    is RunStatus.NotStarted -> stringResource(R.string.actions_run_not_started)
    is RunStatus.Failed -> stringResource(R.string.actions_run_failed)
    RunStatus.Gone -> stringResource(R.string.actions_run_starting)
    else -> text()
}
