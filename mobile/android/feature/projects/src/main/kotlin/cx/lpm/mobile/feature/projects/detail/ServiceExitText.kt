package cx.lpm.mobile.feature.projects.detail

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import cx.lpm.mobile.core.model.Service
import cx.lpm.mobile.feature.projects.R

/**
 * How a started service ended, in the desktop's words: "exited with code 1", "exited",
 * or "stopped" when the shell couldn't say.
 */
@Composable
fun serviceExitText(service: Service): String {
    val code = service.exitCode ?: return stringResource(R.string.service_exit_stopped)
    return if (code == 0) {
        stringResource(R.string.service_exit_exited)
    } else {
        stringResource(R.string.service_exit_code, code)
    }
}

/** "web exited with code 1, worker exited with code 2". */
@Composable
fun crashSummaryText(crashed: List<Service>): String =
    crashed.map { stringResource(R.string.service_exit_summary, it.name, serviceExitText(it)) }
        .joinToString(", ")
