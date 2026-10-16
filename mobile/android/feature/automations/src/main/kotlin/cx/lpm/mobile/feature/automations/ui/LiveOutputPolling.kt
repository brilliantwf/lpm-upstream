package cx.lpm.mobile.feature.automations.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay

/** While a run is going and the screen is showing, ask for its output every 2 s. */
@Composable
fun LiveOutputPolling(running: Boolean, poll: () -> Unit) {
    if (!running) return
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val currentPoll by rememberUpdatedState(poll)
    LaunchedEffect(lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                currentPoll()
                delay(POLL_MS)
            }
        }
    }
}

private const val POLL_MS = 2_000L
