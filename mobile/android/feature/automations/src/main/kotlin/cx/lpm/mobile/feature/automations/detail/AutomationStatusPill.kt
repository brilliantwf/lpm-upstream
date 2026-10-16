package cx.lpm.mobile.feature.automations.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cx.lpm.mobile.core.designsystem.component.ActivityIndicator
import cx.lpm.mobile.core.designsystem.component.ActivityIndicatorSize
import cx.lpm.mobile.core.designsystem.component.StatusDot
import cx.lpm.mobile.core.designsystem.theme.LpmTheme
import cx.lpm.mobile.core.model.AutomationJob
import cx.lpm.mobile.feature.automations.R
import cx.lpm.mobile.feature.automations.format.AutomationTone
import cx.lpm.mobile.feature.automations.format.automationElapsedText
import cx.lpm.mobile.feature.automations.ui.color
import kotlinx.coroutines.delay

/**
 * The one line that answers "what is this doing right now" — running (with a clock
 * ticking each second), off, broken, or when it runs next ([text]).
 */
@Composable
fun AutomationStatusPill(
    job: AutomationJob,
    text: String,
    tone: AutomationTone,
    modifier: Modifier = Modifier
) {
    val since = job.runningSince
    val shown = if (job.running && since != null) runningText(since) else text
    val tint = tone.color()
    Row(
        modifier = modifier
            .background(tint.copy(alpha = 0.15f), CircleShape)
            .padding(horizontal = 11.dp, vertical = 7.dp),
        horizontalArrangement = Arrangement.spacedBy(7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (job.running) {
            ActivityIndicator(size = ActivityIndicatorSize.Mini)
        } else {
            StatusDot(color = tint)
        }
        Text(
            text = shown,
            style = LpmTheme.typography.footnote.copy(
                fontWeight = FontWeight.Medium,
                fontFeatureSettings = "tnum"
            ),
            color = if (job.valid) LpmTheme.colors.label else LpmTheme.colors.red,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/** "Running for 4m 12s", ticking in place. */
@Composable
private fun runningText(since: Long): String {
    var now by remember { mutableLongStateOf(System.currentTimeMillis() / 1000) }
    LaunchedEffect(since) {
        while (true) {
            now = System.currentTimeMillis() / 1000
            delay(TICK_MS)
        }
    }
    return stringResource(R.string.detail_running_for, automationElapsedText(since, now))
}

private const val TICK_MS = 1_000L
