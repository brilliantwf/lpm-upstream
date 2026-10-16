package cx.lpm.mobile.feature.automations.conversation

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp

/**
 * A scrolling column over live output that keeps the newest line in view while the
 * reader is at the end, and leaves them where they are once they scroll back to read,
 * so the next poll doesn't drag them down again. [returnToEnd] turning true takes the
 * reader back to the end wherever they are, as sending a reply does.
 */
@Composable
fun FollowingColumn(
    returnToEnd: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    val scroll = rememberScrollState()
    val slack = with(LocalDensity.current) { FOLLOW_SLACK.toPx() }
    val follow = remember { FollowState() }
    // Only scrolling moves the reader; new output just grows the content and can't
    // count as them leaving the end.
    LaunchedEffect(scroll) {
        snapshotFlow { scroll.value }.collect { value ->
            if (!follow.moving) follow.atEnd = scroll.maxValue - value <= slack
        }
    }
    LaunchedEffect(scroll) {
        snapshotFlow { scroll.maxValue }.collect { max ->
            if (!follow.atEnd) return@collect
            follow.moving = true
            try {
                if (follow.appeared) scroll.animateScrollTo(max) else scroll.scrollTo(max)
                follow.appeared = true
            } finally {
                follow.moving = false
            }
        }
    }
    LaunchedEffect(returnToEnd) {
        if (!returnToEnd) return@LaunchedEffect
        follow.atEnd = true
        scroll.animateScrollTo(scroll.maxValue)
    }
    Column(modifier.verticalScroll(scroll), content = content)
}

private class FollowState {
    var atEnd = true
    var moving = false
    var appeared = false
}

/** How far above the end still counts as reading it: a couple of lines. */
private val FOLLOW_SLACK = 40.dp
