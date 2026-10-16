package cx.lpm.mobile.core.ui

import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import cx.lpm.mobile.core.model.shortDuration
import kotlinx.coroutines.delay

/**
 * "45s", "4m", "2h 5m" since [since] (unix millis), or held at [until] once the work
 * ended. A running reading redraws only when its text changes: each second for the
 * first minute, then on each minute.
 */
@Composable
fun ElapsedText(
    since: Long,
    until: Long?,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    style: TextStyle = LocalTextStyle.current
) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    if (until == null) {
        LaunchedEffect(since) {
            while (true) {
                now = System.currentTimeMillis()
                val elapsed = now - since
                val step = if (elapsed < SECONDS_FOR_MS) 1_000L else 60_000L
                delay(step - Math.floorMod(elapsed, step))
            }
        }
    }
    Text(
        text = shortDuration((until ?: now) - since),
        modifier = modifier,
        color = color,
        style = style.copy(fontFeatureSettings = "tnum"),
        maxLines = 1
    )
}

private const val SECONDS_FOR_MS = 60_000L
